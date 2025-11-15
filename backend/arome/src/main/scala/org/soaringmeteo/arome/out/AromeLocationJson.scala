package org.soaringmeteo.arome.out

import io.circe.Json
import org.slf4j.LoggerFactory
import org.soaringmeteo.arome.AromeData
import org.soaringmeteo.util.WorkReporter
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Generates location-specific JSON files for AROME data.
 *
 * Unlike GFS which uses the full LocationForecasts structure, AROME has a simplified
 * data model with fewer fields. This generates clustered JSON files similar to GFS
 * but with AROME-specific structure.
 */
object AromeLocationJson {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Write one JSON file per 4x4 cluster of points, containing AROME forecast data.
   *
   * @param zoneName Human-readable zone name for logging
   * @param width Grid width
   * @param height Grid height
   * @param targetDir Base directory for zone outputs (location/ will be created inside)
   * @param getData Function to retrieve AROME data for a given (x, y) coordinate
   *                Returns Map[hourOffset, (AromeData, time)]
   */
  def writeForecastsByLocation(
    zoneName: String,
    width: Int,
    height: Int,
    targetDir: os.Path
  )(
    getData: (Int, Int) => Map[Int, (AromeData, OffsetDateTime)]
  ): Unit = {
    val k = 4 // clustering factor, MUST be consistent with frontend
    val size = width * height
    val reporter = new WorkReporter(size / (k * k), s"Writing AROME location forecasts for ${zoneName}", logger)

    val locationDir = targetDir / "location"
    os.makeDir.all(locationDir)

    for {
      (xs, xCluster) <- (0 until width).grouped(k).zipWithIndex
      (ys, yCluster) <- (0 until height).grouped(k).zipWithIndex
    } {
      val forecastsCluster: IndexedSeq[IndexedSeq[Map[Int, (AromeData, OffsetDateTime)]]] =
        for (x <- xs) yield {
          for (y <- ys) yield getData(x, y)
        }

      logger.trace(s"Writing AROME forecast for cluster (${xCluster}-${yCluster}) including x-coordinates ${xs} and y-coordinates ${ys}.")

      val json = Json.arr(forecastsCluster.map { forecastColumns =>
        Json.arr(forecastColumns.map { forecastsByHour =>
          encodeLocationForecasts(forecastsByHour)
        }: _*)
      }: _*)

      // E.g., "12-34.json"
      val fileName = s"${xCluster}-${yCluster}.json"
      os.write.over(
        locationDir / fileName,
        json.deepDropNullValues.noSpaces,
        createFolders = true
      )
      reporter.notifyCompleted()
    }
  }

  /**
   * Encode AROME location forecasts to JSON.
   *
   * Structure:
   * {
   *   "h": elevation (meters),
   *   "d": [ // days
   *     {
   *       "h": [ // hours
   *         {
   *           "t": ISO timestamp,
   *           "v": thermal velocity (dm/s),
   *           "bl": boundary layer depth (m),
   *           "w10": { "u": u-wind (km/h), "v": v-wind (km/h) },
   *           "c": cloud cover (0-100),
   *           "cape": CAPE (J/kg),
   *           "t2m": temperature 2m (°C),
   *           "winds": [ // winds at different heights
   *             { "h": height (m AGL), "u": u-wind (km/h), "v": v-wind (km/h) }
   *           ]
   *         }
   *       ]
   *     }
   *   ]
   * }
   */
  private def encodeLocationForecasts(forecastsByHour: Map[Int, (AromeData, OffsetDateTime)]): Json = {
    if (forecastsByHour.isEmpty) {
      return Json.obj(
        "h" -> Json.fromInt(0),
        "d" -> Json.arr()
      )
    }

    val sortedForecasts = forecastsByHour.toSeq.sortBy(_._1)
    val elevation = sortedForecasts.head._2._1.terrainElevation

    // Group by day
    val forecastsByDay = sortedForecasts
      .groupBy { case (_, (_, time)) => time.toLocalDate }
      .toSeq
      .sortBy(_._1)

    val dayForecastsJson = forecastsByDay.map { case (date, hourForecasts) =>
      val hourForecastsJson = hourForecasts.sortBy(_._1).map { case (_, (data, time)) =>
        encodeHourForecast(data, time)
      }

      Json.obj(
        "h" -> Json.arr(hourForecastsJson: _*)
      )
    }

    Json.obj(
      "h" -> Json.fromInt(elevation.round.toInt),
      "d" -> Json.arr(dayForecastsJson: _*)
    )
  }

  private def encodeHourForecast(data: AromeData, time: OffsetDateTime): Json = {
    // Convert thermal velocity from m/s to dm/s (decimeters per second) to match GFS format
    val thermalVelocityDmPerSec = (data.thermalVelocity * 10).round.toInt

    // Convert winds from m/s to km/h
    val u10Kmh = (data.u10 * 3.6).round.toInt
    val v10Kmh = (data.v10 * 3.6).round.toInt

    // Encode winds at heights
    val windsJson = data.windsAtHeights.toSeq.sortBy(_._1).map { case (height, (u, v)) =>
      Json.obj(
        "h" -> Json.fromInt(height),
        "u" -> Json.fromInt((u * 3.6).round.toInt),  // m/s -> km/h
        "v" -> Json.fromInt((v * 3.6).round.toInt)
      )
    }

    // Encode vertical profiles (for sounding diagrams)
    val profilesJson = data.airDataByAltitude.toSeq.sortBy(_._1).map { case (altitude, airData) =>
      Json.obj(
        "h" -> Json.fromInt(altitude),
        "t" -> Json.fromBigDecimal(BigDecimal(airData.temperature - 273.15).setScale(1, BigDecimal.RoundingMode.HALF_UP)),  // K -> °C
        "td" -> Json.fromBigDecimal(BigDecimal(airData.dewPoint - 273.15).setScale(1, BigDecimal.RoundingMode.HALF_UP)),
        "u" -> Json.fromInt((airData.u * 3.6).round.toInt),  // m/s -> km/h
        "v" -> Json.fromInt((airData.v * 3.6).round.toInt),
        "c" -> Json.fromInt((airData.cloudCover * 100).round.toInt)  // fraction -> percent
      )
    }

    Json.obj(
      "t" -> Json.fromString(time.format(DateTimeFormatter.ISO_DATE_TIME)),
      "v" -> Json.fromInt(thermalVelocityDmPerSec),
      "bl" -> Json.fromInt(data.pblh.round.toInt),
      "w10" -> Json.obj(
        "u" -> Json.fromInt(u10Kmh),
        "v" -> Json.fromInt(v10Kmh)
      ),
      "c" -> Json.fromInt((data.cloudCover * 100).round.toInt),
      "cape" -> Json.fromInt(data.cape.round.toInt),
      "t2m" -> Json.fromBigDecimal(BigDecimal(data.t2mCelsius).setScale(1, BigDecimal.RoundingMode.HALF_UP)),
      "dt2m" -> Json.fromBigDecimal(BigDecimal(data.dewPoint2mCelsius).setScale(1, BigDecimal.RoundingMode.HALF_UP)),
      "mslet" -> Json.fromInt(data.msletHPa.round.toInt),
      "rain" -> Json.fromBigDecimal(BigDecimal(data.totalRain).setScale(1, BigDecimal.RoundingMode.HALF_UP)),
      "snow" -> Json.fromBigDecimal(BigDecimal(data.snowDepth).setScale(2, BigDecimal.RoundingMode.HALF_UP)),
      "iso0" -> data.isothermZero.fold(Json.Null)(z => Json.fromInt(z.round.toInt)),
      "shf" -> Json.fromInt(data.sensibleHeatFlux.round.toInt),
      "lhf" -> Json.fromInt(data.latentHeatFlux.round.toInt),
      "rad" -> Json.fromInt(data.solarRadiation.round.toInt),
      "winds" -> Json.arr(windsJson: _*),
      "p" -> Json.arr(profilesJson: _*)  // 'p' for profiles, matching GFS format
    )
  }
}
