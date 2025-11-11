package org.soaringmeteo.arome

import org.soaringmeteo.{MeteoData, Wind}
import squants.motion.MetersPerSecond
import squants.thermal.Kelvin
import squants.energy.SpecificEnergy
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
}
