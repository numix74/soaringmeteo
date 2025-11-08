package org.soaringmeteo
import squants.energy.SpecificEnergy
import squants.motion.Velocity
import squants.thermal.Temperature
import java.time.OffsetDateTime
/**
 * Unified interface for meteorological data from any source (GFS, AROME, etc.)
 */
trait MeteoData {
  def thermalVelocity: Velocity
  def boundaryLayerDepth: Double  // meters AGL
  def wind10m: Wind
  def windAtHeight(heightMeters: Int): Option[Wind]
  def boundaryLayerWind: Wind
  def surfaceWind: Wind
  def wind300mAGL: Wind
  def windSoaringLayerTop: Wind
  def wind2000mAMSL: Wind
  def wind3000mAMSL: Wind
  def wind4000mAMSL: Wind
  def temperature2m: Temperature
  def cape: Option[SpecificEnergy]
  def totalCloudCover: Int  // 0-100
  def time: OffsetDateTime
  
  // Nouveaux champs pour les couches manquantes
  def soaringLayerDepth: Double  // meters AGL
  def convectiveClouds: Option[ConvectiveClouds]
  def totalRain: Double  // millimeters
  def xcFlyingPotential: Int  // 0-100
}
