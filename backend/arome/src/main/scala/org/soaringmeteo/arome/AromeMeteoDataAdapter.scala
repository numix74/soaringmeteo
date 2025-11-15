package org.soaringmeteo.arome

import org.soaringmeteo.{ConvectiveClouds, MeteoData, Wind}
import squants.motion.MetersPerSecond
import squants.thermal.Kelvin
import squants.energy.SpecificEnergy
import squants.space.Meters
import java.time.OffsetDateTime

class AromeMeteoDataAdapter(data: AromeData, initTime: OffsetDateTime) extends MeteoData {
  def thermalVelocity = MetersPerSecond(data.thermalVelocity)
  def boundaryLayerDepth = data.pblh
  def soaringLayerDepth = data.pblh // For AROME, we use PBLH as soaring layer depth

  def wind10m = Wind(MetersPerSecond(data.u10), MetersPerSecond(data.v10))
  def surfaceWind = wind10m
  def boundaryLayerWind = wind10m // Approximation: use 10m wind for boundary layer

  def windAtHeight(heightMeters: Int) =
    data.windsAtHeights.get(heightMeters).map { case (u, v) =>
      Wind(MetersPerSecond(u), MetersPerSecond(v))
    }

  def wind300mAGL = data.windAtHeightInterpolated(300.0).map { case (u, v) =>
    Wind(MetersPerSecond(u), MetersPerSecond(v))
  }.getOrElse(wind10m)

  def windSoaringLayerTop = data.windAtHeightInterpolated(data.pblh).map { case (u, v) =>
    Wind(MetersPerSecond(u), MetersPerSecond(v))
  }.getOrElse(wind10m)

  def wind2000mAMSL = data.windAtAltitudeAMSL(2000).map { case (u, v) =>
    Wind(MetersPerSecond(u), MetersPerSecond(v))
  }.getOrElse(wind10m)

  def wind3000mAMSL = data.windAtAltitudeAMSL(3000).map { case (u, v) =>
    Wind(MetersPerSecond(u), MetersPerSecond(v))
  }.getOrElse(wind10m)

  def wind4000mAMSL = data.windAtAltitudeAMSL(4000).map { case (u, v) =>
    Wind(MetersPerSecond(u), MetersPerSecond(v))
  }.getOrElse(wind10m)

  def temperature2m = Kelvin(data.t2m)
  def cape = if (data.cape > 0) SpecificEnergy(data.cape).toOption else None
  def totalCloudCover = (data.cloudCover * 100).toInt
  def time = initTime

  // For now, AROME doesn't provide detailed cloud information
  def convectiveClouds: Option[ConvectiveClouds] = None

  // AROME doesn't provide rain data yet
  def totalRain: Double = 0.0

  // Calculate XC flying potential based on thermal velocity, boundary layer depth, and wind
  def xcFlyingPotential: Int = {
    val wstar = data.thermalVelocity
    val pblh = data.pblh
    val windSpeed = data.windSpeed

    // Simple scoring algorithm
    val thermalScore = Math.min(wstar / 3.0, 1.0) * 40 // Max 40 points for thermals
    val pblhScore = Math.min(pblh / 2000.0, 1.0) * 40 // Max 40 points for boundary layer
    val windPenalty = Math.max(0, 1.0 - windSpeed / 40.0) * 20 // Max 20 points for low wind

    Math.max(0, Math.min(100, (thermalScore + pblhScore + windPenalty).toInt))
  }
}
