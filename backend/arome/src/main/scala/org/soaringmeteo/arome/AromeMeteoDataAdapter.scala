package org.soaringmeteo.arome

import org.soaringmeteo.{AirData, ConvectiveClouds, MeteoData, Wind, XCFlyingPotential}
import squants.motion.MetersPerSecond
import squants.thermal.Kelvin
import squants.energy.SpecificEnergy
import squants.space.Meters
import java.time.OffsetDateTime
import scala.collection.SortedMap

/**
 * Adapter that maps AROME data to the unified MeteoData interface.
 *
 * @param data AROME-specific data structure
 * @param forecastTime Forecast validity time
 */
class AromeMeteoDataAdapter(data: AromeData, forecastTime: OffsetDateTime) extends MeteoData {

  // === Core meteorological fields ===

  def thermalVelocity = MetersPerSecond(data.thermalVelocity)

  def boundaryLayerDepth = data.pblh

  def temperature2m = Kelvin(data.t2m)

  def cape = if (data.cape > 0) SpecificEnergy(data.cape).toOption else None

  def totalCloudCover = (data.cloudCover * 100).toInt

  def totalRain = data.totalRain

  def time = forecastTime

  // === Wind fields ===

  def wind10m = Wind(MetersPerSecond(data.u10), MetersPerSecond(data.v10))

  def surfaceWind = wind10m

  def boundaryLayerWind = {
    // Wind at boundary layer top (PBLH)
    windAtHeightAGL(data.pblh).getOrElse(wind10m)
  }

  def wind300mAGL = windAtHeightAGL(300.0).getOrElse(wind10m)

  def windSoaringLayerTop = {
    // Wind at soaring layer top
    windAtHeightAGL(soaringLayerDepth).getOrElse(boundaryLayerWind)
  }

  def wind2000mAMSL = windAtAltitudeAMSL(2000.0).getOrElse(boundaryLayerWind)

  def wind3000mAMSL = windAtAltitudeAMSL(3000.0).getOrElse(boundaryLayerWind)

  def wind4000mAMSL = windAtAltitudeAMSL(4000.0).getOrElse(boundaryLayerWind)

  // === Wind retrieval helpers ===

  def windAtHeight(heightMeters: Int): Option[Wind] = {
    data.windsAtHeights.get(heightMeters).map { case (u, v) =>
      Wind(MetersPerSecond(u), MetersPerSecond(v))
    }
  }

  /**
   * Get wind at a specific height above ground level (AGL) with interpolation.
   */
  private def windAtHeightAGL(heightAGL: Double): Option[Wind] = {
    data.windAtHeightInterpolated(heightAGL).map { case (u, v) =>
      Wind(MetersPerSecond(u), MetersPerSecond(v))
    }
  }

  /**
   * Get wind at a specific altitude above mean sea level (AMSL) with interpolation.
   */
  private def windAtAltitudeAMSL(altitudeAMSL: Double): Option[Wind] = {
    data.windAtAltitudeAMSL(altitudeAMSL).map { case (u, v) =>
      Wind(MetersPerSecond(u), MetersPerSecond(v))
    }
  }

  // === Soaring-specific fields ===

  def soaringLayerDepth: Double = {
    // Use boundary layer depth as soaring layer depth
    // This is a simplification; ideally we would compute based on temperature profile
    data.pblh
  }

  def convectiveClouds: Option[ConvectiveClouds] = {
    // Convert AROME airData to the format expected by ConvectiveClouds
    val airDataMap: SortedMap[squants.space.Length, AirData] =
      SortedMap.from(
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

    if (airDataMap.nonEmpty) {
      ConvectiveClouds(
        surfaceTemperature = temperature2m,
        surfaceDewPoint = Kelvin(data.dewPoint2m),
        groundLevel = Meters(data.terrainElevation),
        boundaryLayerDepth = Meters(data.pblh),
        airData = airDataMap
      )
    } else {
      // Fallback: no vertical profile data available
      None
    }
  }

  def xcFlyingPotential: Int = {
    XCFlyingPotential(
      thermalVelocity = thermalVelocity,
      soaringLayerDepth = Meters(soaringLayerDepth),
      wind = boundaryLayerWind
    )
  }
}
