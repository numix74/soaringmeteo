package org.soaringmeteo.parsing

import org.soaringmeteo.{AirData, Wind}
import squants.energy.SpecificEnergy
import squants.motion.Pressure
import squants.radio.Irradiance
import squants.space.Length
import squants.thermal.Temperature

import scala.collection.SortedMap

/**
 * Intermediate data structures between parsers and UnifiedModelData.
 * These structures hold raw parsed data before derived calculations.
 */

/**
 * Surface data (10m / 2m level).
 */
case class SurfaceData(
  elevation: Length,
  temperature: Temperature,      // T2m
  dewPoint: Temperature,          // TD2m
  wind: Wind,                     // Wind at 10m
  pressure: Pressure,             // MSLP
  snowDepth: Length
)

/**
 * Planetary Boundary Layer data.
 */
case class BoundaryLayerData(
  pblDepth: Length,
  wind: Wind,
  sensibleHeatFlux: Irradiance,
  latentHeatFlux: Irradiance
)

/**
 * Global atmospheric data.
 */
case class AtmosphereData(
  totalCloudCover: Int,           // 0-100
  convectiveCloudCover: Int,      // 0-100 (GFS only, 0 for AROME/WRF)
  lowCloudCover: Option[Int],     // 0-100 (AROME: 0-2km)
  mediumCloudCover: Option[Int],  // 0-100 (AROME: 2-5km)
  highCloudCover: Option[Int],    // 0-100 (AROME: >5km)
  totalRain: Length,
  convectiveRain: Length,
  solarRadiation: Irradiance,
  cape: Option[SpecificEnergy],   // Optional - WRF doesn't have CAPE directly
  cin: Option[SpecificEnergy]     // Optional - WRF doesn't have CIN directly
)

/**
 * Vertical profiles at different altitude levels.
 * Used for soundings and multi-level wind data.
 */
case class VerticalProfiles(
  temperatureProfile: SortedMap[Length, Temperature],
  dewPointProfile: SortedMap[Length, Temperature],
  windProfile: SortedMap[Length, Wind],
  cloudCoverProfile: SortedMap[Length, Int],
  isothermZero: Option[Length],
  airDataByAltitude: SortedMap[Length, AirData]
)

/**
 * Complete raw data for a single grid point.
 * Combines surface, boundary layer, atmosphere, and vertical profile data.
 */
case class RawData(
  surface: SurfaceData,
  boundary: BoundaryLayerData,
  atmosphere: AtmosphereData,
  profiles: VerticalProfiles
)
