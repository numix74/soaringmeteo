package org.soaringmeteo.arome.out

import io.circe.Json
import org.slf4j.LoggerFactory
import org.soaringmeteo.arome.{AromeData, AromeZone}
import org.soaringmeteo.{Forecast, LocationForecasts, Wind, AirData, Winds}
import squants.space.{Meters, Length}
import squants.motion.{MetersPerSecond, Velocity, Pressure, Pascals}
import squants.thermal.{Kelvin, Temperature, Celsius}
import squants.energy.{SpecificEnergy, Grays}
import squants.radio.{Irradiance, WattsPerSquareMeter}
import java.time.OffsetDateTime
import scala.collection.immutable.SortedMap

object AromeLocationJson {
  private val logger = LoggerFactory.getLogger(getClass)

  def writeLocationForecasts(
    zone: AromeZone,
    initTime: OffsetDateTime,
    allData: Map[Int, IndexedSeq[IndexedSeq[AromeData]]],
    outputDir: os.Path
  ): Unit = {
    val width = zone.longitudes.size
    val height = zone.latitudes.size
    val k = 4 // clustering factor
    
    val totalClusters = (width / k) * (height / k)
    var processedClusters = 0
    
    logger.info(s"Writing location forecasts (${totalClusters} clusters)...")
    
    for {
      (xs, xCluster) <- (0 until width).grouped(k).zipWithIndex
      (ys, yCluster) <- (0 until height).grouped(k).zipWithIndex
    } {
      val forecastsCluster: IndexedSeq[IndexedSeq[Map[Int, Forecast]]] =
        for (x <- xs) yield {
          for (y <- ys) yield {
            val forecastsByHour: Map[Int, Forecast] = allData.map { case (hour, gridData) =>
              val aromeData = gridData(x)(y)
              val forecast = aromeDataToForecast(aromeData, initTime.plusHours(hour))
              (hour, forecast)
            }
            forecastsByHour
          }
        }
      
      val json = Json.arr(forecastsCluster.map { forecastColumns =>
        Json.arr(forecastColumns.map { forecastsByHour =>
          val locationForecasts = LocationForecasts(forecastsByHour.toSeq.sortBy(_._1).map(_._2))
          LocationForecasts.jsonEncoder(locationForecasts)
        }: _*)
      }: _*)
      
      val fileName = s"${xCluster}-${yCluster}.json"
      os.write.over(
        outputDir / fileName,
        json.deepDropNullValues.noSpaces,
        createFolders = true
      )
      
      processedClusters += 1
      if (processedClusters % 10 == 0 || processedClusters == totalClusters) {
        val percent = (processedClusters * 100) / totalClusters
        logger.info(s"  Location forecasts: ${percent}% (${processedClusters}/${totalClusters})")
      }
    }
    
    logger.info(s"Location forecasts complete: ${totalClusters} files written")
  }

  private def aromeDataToForecast(data: AromeData, time: OffsetDateTime): Forecast = {
    val elevation = Meters(data.terrainElevation)
    val boundaryLayerDepth = Meters(data.pblh)
    val thermalVelocity = MetersPerSecond(data.thermalVelocity)
    val surfaceWind = Wind(MetersPerSecond(data.u10), MetersPerSecond(data.v10))
    val surfaceTemperature = Kelvin(data.t2m)
    
    // Build airDataByAltitude from windsAtHeights
    val airDataByAltitude: SortedMap[Length, AirData] = 
      SortedMap(data.windsAtHeights.map { case (height, (u, v)) =>
        val altitude = Meters(height)
        val wind = Wind(MetersPerSecond(u), MetersPerSecond(v))
        val tempLapseRate = -0.0065 // K/m
        val temp = Kelvin(data.t2m + (height * tempLapseRate))
        val airData = AirData(
          wind = wind,
          temperature = temp,
          dewPoint = temp.minus(Celsius(5)),
          cloudCover = data.cloudCover.toInt
        )
        (altitude, airData)
      }.toSeq: _*)
    
    // Build Winds using direct constructor (avoid interpolation issues)
    val wind300m = data.windsAtHeights.get(300)
      .map { case (u, v) => Wind(MetersPerSecond(u), MetersPerSecond(v)) }
      .getOrElse(surfaceWind)
    
    val windBLTop = surfaceWind // approximate with surface wind
    
    val wind2000m = data.windsAtHeights.get(2000)
      .map { case (u, v) => Wind(MetersPerSecond(u), MetersPerSecond(v)) }
      .getOrElse(surfaceWind)
    
    val wind3000m = data.windsAtHeights.get(3000)
      .map { case (u, v) => Wind(MetersPerSecond(u), MetersPerSecond(v)) }
      .getOrElse(surfaceWind)
    
    val wind4000m = data.windsAtHeights.get(4000)
      .map { case (u, v) => Wind(MetersPerSecond(u), MetersPerSecond(v)) }
      .getOrElse(surfaceWind)
    
    val winds = Winds(
      `300m AGL` = wind300m,
      soaringLayerTop = windBLTop,
      `2000m AMSL` = wind2000m,
      `3000m AMSL` = wind3000m,
      `4000m AMSL` = wind4000m
    )
    
    val xcPotential = calculateXCPotential(data)
    
    // Use Grays for SpecificEnergy (1 Gray = 1 J/kg)
    val capeValue = SpecificEnergy(data.cape).toOption.getOrElse(Grays(0))
    val cinValue = Grays(0)
    
    Forecast(
      time = time,
      elevation = elevation,
      boundaryLayerDepth = boundaryLayerDepth,
      boundaryLayerWind = surfaceWind,
      thermalVelocity = thermalVelocity,
      totalCloudCover = data.cloudCover.toInt,
      convectiveCloudCover = (data.cloudCover * 0.5).toInt,
      convectiveClouds = None,
      airDataByAltitude = airDataByAltitude,
      mslet = Pascals(101325),
      snowDepth = Meters(0),
      surfaceTemperature = surfaceTemperature,
      surfaceDewPoint = surfaceTemperature.minus(Celsius(5)),
      surfaceWind = surfaceWind,
      totalRain = Meters(0),
      convectiveRain = Meters(0),
      latentHeatNetFlux = WattsPerSquareMeter(data.latentHeatFlux),
      sensibleHeatNetFlux = WattsPerSquareMeter(data.sensibleHeatFlux),
      cape = capeValue,
      cin = cinValue,
      downwardShortWaveRadiationFlux = WattsPerSquareMeter(data.solarRadiation),
      isothermZero = None,
      winds = winds,
      xcFlyingPotential = xcPotential,
      soaringLayerDepth = boundaryLayerDepth
    )
  }

  private def calculateXCPotential(data: AromeData): Int = {
    val thermalScore = math.min(100, (data.thermalVelocity * 20).toInt)
    val blScore = math.min(100, (data.pblh / 30).toInt)
    val cloudScore = math.max(0, 100 - data.cloudCover.toInt)
    ((thermalScore * 0.5 + blScore * 0.3 + cloudScore * 0.2).toInt).max(0).min(100)
  }
}
