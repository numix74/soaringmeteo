package org.soaringmeteo.out

import io.circe.Json
import org.slf4j.LoggerFactory
import org.soaringmeteo.{UnifiedModelData, LocationForecasts}

/**
 * JSON data generation for unified backend.
 *
 * For every point of a covered zone, we produce a JSON document containing forecast data for
 * all the time steps.
 *
 * On the frontend, this data is used for the meteograms and sounding diagrams.
 *
 * Adapted from backend/common to work with UnifiedModelData instead of Forecast.
 */
object JsonData {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Write one JSON file per every 16 points, containing the forecast data for
   * the next days.
   *
   * @param zoneName Zone identifier (e.g., "pyrenees")
   * @param width Grid width
   * @param height Grid height
   * @param targetDir Base output directory (zone-specific)
   * @param getData Function to get forecast data for a grid point
   */
  def writeForecastsByLocation(
    zoneName: String,
    width: Int,
    height: Int,
    targetDir: os.Path,
  )(
    getData: (Int, Int) => Map[Int, UnifiedModelData]
  ): Unit = {
    val k = 4 // clustering factor, MUST be consistent with frontend
    val size = width * height
    val totalClusters = size / (k * k)

    logger.info(s"Writing location forecasts for ${zoneName} (${totalClusters} clusters)")

    var completedClusters = 0
    for {
      (xs, xCluster) <- (0 until width).grouped(k).zipWithIndex
      (ys, yCluster) <- (0 until height).grouped(k).zipWithIndex
    } {
      val forecastsCluster: IndexedSeq[IndexedSeq[Map[Int, UnifiedModelData]]] =
        for (x <- xs) yield {
          for (y <- ys) yield getData(x, y)
        }
      logger.trace(s"Writing forecast for cluster (${xCluster}-${yCluster}) including x-coordinates ${xs} and y-coordinates ${ys}.")
      val json =
        Json.arr(forecastsCluster.map { forecastColumns =>
          Json.arr(forecastColumns.map { forecastsByHour =>
            val locationForecasts = LocationForecasts(forecastsByHour.toSeq.sortBy(_._1).map(_._2))
            LocationForecasts.jsonEncoder(locationForecasts)
          }: _*)
        }: _*)
      // E.g., "12-34.json"
      val fileName = s"${xCluster}-${yCluster}.json"
      os.write.over(
        targetDir / "locations" / fileName,
        json.deepDropNullValues.noSpaces,
        createFolders = true
      )

      completedClusters += 1
      if (completedClusters % 10 == 0) {
        logger.debug(s"  Progress: ${completedClusters}/${totalClusters} clusters")
      }
    }

    logger.info(s"Completed location forecasts for ${zoneName}")
  }

}
