package org.soaringmeteo.arome.out

import io.circe.Json
import org.slf4j.LoggerFactory
import org.soaringmeteo.arome.AromeData
import org.soaringmeteo.util.WorkReporter
import org.soaringmeteo.{AirData, ConvectiveClouds, DayForecast, DetailedForecast, LocationForecasts, Wind, Winds, XCFlyingPotential}
import squants.energy.{Joules, SpecificEnergy}
import squants.motion.{KilometersPerHour, MetersPerSecond, Pressure, Pascals}
import squants.radio.{Irradiance, WattsPerSquareMeter}
import squants.space.{Length, Meters, Millimeters}
import squants.thermal.{Kelvin, Temperature}
import java.time.OffsetDateTime

import scala.collection.SortedMap

/**
 * Generates location-specific JSON files for AROME data.
 *
 * This module converts AROME data to the standard LocationForecasts format
 * used by the frontend for meteograms and soundings.
 */
object AromeLocationJson {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Write one JSON file per 4x4 cluster of points, containing AROME forecast data.
   *
   * @param zoneName Human-readable zone name for logging
   * @param width Grid width
   * @param height Grid height
   * @param targetDir Base directory for zone outputs (locations/ will be created inside)
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

    val locationDir = targetDir / "locations"  // Changed from "location" to "locations" to match GFS
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
          val locationForecasts = toLocationForecasts(forecastsByHour)
          LocationForecasts.jsonEncoder(locationForecasts)
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
   * Convert AROME data to LocationForecasts structure.
   * This ensures compatibility with the frontend's expected format.
   */
  private def toLocationForecasts(forecastsByHour: Map[Int, (AromeData, OffsetDateTime)]): LocationForecasts = {
    if (forecastsByHour.isEmpty) {
      return LocationForecasts(
        elevation = Meters(0),
        dayForecasts = Seq.empty
      )
    }

    val sortedForecasts = forecastsByHour.toSeq.sortBy(_._1)
    val elevation = Meters(sortedForecasts.head._2._1.terrainElevation)

    // Convert each AROME data point to a DetailedForecast
    val detailedForecasts: Seq[DetailedForecast] = sortedForecasts.map { case (_, (data, time)) =>
      toDetailedForecast(data, time, elevation)
    }

    // Group by day and compute thunderstorm risk
    val dayForecasts =
      detailedForecasts
        .groupBy(_.time.toLocalDate)
        .filter { case (_, forecasts) => forecasts.nonEmpty }
        .toSeq
        .sortBy(_._1)
        .map { case (forecastDate, hourForecasts) =>
          val sortedHourForecasts = hourForecasts.sortBy(_.time)

          // Calculate thunderstorm risk (simplified version)
          val thunderstormRisk =
            if (sortedHourForecasts.sizeIs >= 3) {
              LocationForecasts.thunderstormRisk(
                sortedHourForecasts(0),
                sortedHourForecasts(sortedHourForecasts.size / 2),
                sortedHourForecasts.last
              )
            } else {
              0
            }

          DayForecast(
            forecastDate,
            sortedHourForecasts,
            thunderstormRisk
          )
        }

    LocationForecasts(
      elevation = elevation,
      dayForecasts = dayForecasts
    )
  }

  /**
   * Convert a single AROME data point to a DetailedForecast.
   */
  private def toDetailedForecast(
    data: AromeData,
    time: OffsetDateTime,
    groundLevel: Length
  ): DetailedForecast = {

    // Calculate XC flying potential
    val thermalVelocity = MetersPerSecond(data.thermalVelocity)
    val boundaryLayerDepth = Meters(data.pblh)
    val boundaryLayerWind = data.keyWinds("500m_AGL") match {
      case Some((u, v)) => Wind(MetersPerSecond(u), MetersPerSecond(v))
      case None => Wind(MetersPerSecond(data.u10), MetersPerSecond(data.v10))
    }

    val xcPotential = XCFlyingPotential(
      thermalVelocity = thermalVelocity,
      soaringLayerDepth = boundaryLayerDepth,
      wind = boundaryLayerWind
    )

    // Convert vertical profile data
    val airDataByAltitude: SortedMap[Length, AirData] = SortedMap.from(
      data.airDataByAltitude.map { case (altitudeMeters, aromeAirData) =>
        val altitude = Meters(altitudeMeters.toDouble)
        val airData = AirData(
          wind = Wind(MetersPerSecond(aromeAirData.u), MetersPerSecond(aromeAirData.v)),
          temperature = Kelvin(aromeAirData.temperature),
          dewPoint = Kelvin(aromeAirData.dewPoint),
          cloudCover = (aromeAirData.cloudCover * 100).toInt
        )
        altitude -> airData
      }
    )

    // Calculate convective clouds (if vertical profile exists)
    val convectiveClouds: Option[ConvectiveClouds] =
      if (airDataByAltitude.nonEmpty) {
        ConvectiveClouds(
          surfaceTemperature = Kelvin(data.t2m),
          surfaceDewPoint = Kelvin(data.dewPoint2m),
          groundLevel = groundLevel,
          boundaryLayerDepth = boundaryLayerDepth,
          airData = airDataByAltitude
        )
      } else {
        None
      }

    // Create winds structure
    val winds = Winds(
      soaringLayerTop = data.keyWinds("500m_AGL") match {
        case Some((u, v)) => Wind(MetersPerSecond(u), MetersPerSecond(v))
        case None => Wind(MetersPerSecond(data.u10), MetersPerSecond(data.v10))
      },
      `300m AGL` = data.keyWinds("300m_AGL") match {
        case Some((u, v)) => Wind(MetersPerSecond(u), MetersPerSecond(v))
        case None => Wind(MetersPerSecond(data.u10), MetersPerSecond(data.v10))
      },
      `2000m AMSL` = data.keyWinds("2000m_AMSL") match {
        case Some((u, v)) => Wind(MetersPerSecond(u), MetersPerSecond(v))
        case None => Wind(MetersPerSecond(data.u10), MetersPerSecond(data.v10))
      },
      `3000m AMSL` = data.keyWinds("3000m_AMSL") match {
        case Some((u, v)) => Wind(MetersPerSecond(u), MetersPerSecond(v))
        case None => Wind(MetersPerSecond(data.u10), MetersPerSecond(data.v10))
      },
      `4000m AMSL` = data.keyWinds("4000m_AMSL") match {
        case Some((u, v)) => Wind(MetersPerSecond(u), MetersPerSecond(v))
        case None => Wind(MetersPerSecond(data.u10), MetersPerSecond(data.v10))
      }
    )

    DetailedForecast(
      time = time,
      xcFlyingPotential = xcPotential,
      boundaryLayerDepth = boundaryLayerDepth,
      boundaryLayerWind = boundaryLayerWind,
      thermalVelocity = thermalVelocity,
      totalCloudCover = (data.cloudCover * 100).toInt,
      convectiveCloudCover = 0,  // AROME doesn't provide this directly
      convectiveClouds = convectiveClouds,
      airDataByAltitude = airDataByAltitude,
      mslet = Pascals(data.mslet),
      snowDepth = Meters(data.snowDepth),
      surfaceTemperature = Kelvin(data.t2m),
      surfaceDewPoint = Kelvin(data.dewPoint2m),
      surfaceWind = Wind(MetersPerSecond(data.u10), MetersPerSecond(data.v10)),
      totalRain = Millimeters(data.totalRain),
      convectiveRain = Millimeters(0),  // AROME doesn't provide this directly
      latentHeatNetFlux = WattsPerSquareMeter(data.latentHeatFlux),
      sensibleHeatNetFlux = WattsPerSquareMeter(data.sensibleHeatFlux),
      cape = SpecificEnergy(data.cape).getOrElse(Joules(0)),
      cin = Joules(0),  // AROME doesn't provide CIN
      downwardShortWaveRadiationFlux = WattsPerSquareMeter(data.solarRadiation),
      isothermZero = data.isothermZero.map(Meters(_)),
      winds = winds
    )
  }
}
