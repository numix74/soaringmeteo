package org.soaringmeteo.arome

import org.soaringmeteo.{ConvectiveClouds, MeteoData, Wind, XCFlyingPotential}
import squants.motion.MetersPerSecond
import squants.thermal.Kelvin
import squants.energy.SpecificEnergy
import squants.space.Meters
import java.time.OffsetDateTime

class AromeMeteoDataAdapter(data: AromeData, initTime: OffsetDateTime) extends MeteoData {
  def thermalVelocity = MetersPerSecond(data.thermalVelocity)
  def boundaryLayerDepth = data.pblh
  def wind10m = Wind(MetersPerSecond(data.u10), MetersPerSecond(data.v10))
  def windAtHeight(heightMeters: Int) =
    data.windsAtHeights.get(heightMeters).map { case (u, v) =>
      Wind(MetersPerSecond(u), MetersPerSecond(v))
    }
  def temperature2m = Kelvin(data.t2m)
  def cape = if (data.cape > 0) SpecificEnergy(data.cape).toOption else None
  def totalCloudCover = (data.cloudCover * 100).toInt
  def time = initTime

  // Wind methods using AromeData interpolation capabilities
  def boundaryLayerWind: Wind = wind10m

  def surfaceWind: Wind = wind10m

  def wind300mAGL: Wind =
    data.windAtHeightInterpolated(300.0)
      .map { case (u, v) => Wind(MetersPerSecond(u), MetersPerSecond(v)) }
      .getOrElse(wind10m)

  def windSoaringLayerTop: Wind =
    data.windAtHeightInterpolated(boundaryLayerDepth)
      .map { case (u, v) => Wind(MetersPerSecond(u), MetersPerSecond(v)) }
      .getOrElse(wind10m)

  def wind2000mAMSL: Wind =
    data.windAtAltitudeAMSL(2000.0)
      .map { case (u, v) => Wind(MetersPerSecond(u), MetersPerSecond(v)) }
      .getOrElse(wind10m)

  def wind3000mAMSL: Wind =
    data.windAtAltitudeAMSL(3000.0)
      .map { case (u, v) => Wind(MetersPerSecond(u), MetersPerSecond(v)) }
      .getOrElse(wind10m)

  def wind4000mAMSL: Wind =
    data.windAtAltitudeAMSL(4000.0)
      .map { case (u, v) => Wind(MetersPerSecond(u), MetersPerSecond(v)) }
      .getOrElse(wind10m)

  // Derived meteorological fields
  def soaringLayerDepth: Double = boundaryLayerDepth

  def convectiveClouds: Option[ConvectiveClouds] = {
    // AromeData doesn't provide the detailed sounding data (temperature/dewpoint profiles)
    // required for ConvectiveClouds.apply(), so we return None for now.
    // This could be enhanced later if AROME provides vertical profile data.
    None
  }

  def totalRain: Double = {
    // AromeData doesn't currently include precipitation data
    // Could be derived from cloudCover as rough estimate, but returning 0.0 for now
    0.0
  }

  def xcFlyingPotential: Int =
    XCFlyingPotential(thermalVelocity, Meters(soaringLayerDepth), boundaryLayerWind)
}
