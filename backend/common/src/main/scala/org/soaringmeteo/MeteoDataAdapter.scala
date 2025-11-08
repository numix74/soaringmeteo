package org.soaringmeteo
import squants.energy.SpecificEnergy
import squants.motion.MetersPerSecond
import squants.thermal.Kelvin
/**
 * Adapter for GFS Forecast data
 */
class GfsMeteoDataAdapter(forecast: Forecast) extends MeteoData {
  def thermalVelocity = forecast.thermalVelocity
  def boundaryLayerDepth = forecast.boundaryLayerDepth.toMeters
  def wind10m = forecast.boundaryLayerWind
  def windAtHeight(heightMeters: Int) = None
  def boundaryLayerWind = forecast.boundaryLayerWind
  def surfaceWind = forecast.surfaceWind
  def wind300mAGL = forecast.winds.`300m AGL`
  def windSoaringLayerTop = forecast.winds.soaringLayerTop
  def wind2000mAMSL = forecast.winds.`2000m AMSL`
  def wind3000mAMSL = forecast.winds.`3000m AMSL`
  def wind4000mAMSL = forecast.winds.`4000m AMSL`
  def temperature2m = forecast.surfaceTemperature
  def cape = Some(forecast.cape)
  def totalCloudCover = forecast.totalCloudCover
  def time = forecast.time
  
  // Nouveaux champs
  def soaringLayerDepth = forecast.soaringLayerDepth.toMeters
  def convectiveClouds = forecast.convectiveClouds
  def totalRain = forecast.totalRain.toMillimeters
  def xcFlyingPotential = forecast.xcFlyingPotential
}
