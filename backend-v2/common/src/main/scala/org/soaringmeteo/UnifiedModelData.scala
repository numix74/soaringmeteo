package org.soaringmeteo

import squants.energy.SpecificEnergy
import squants.motion.{Pressure, Velocity}
import squants.radio.{Irradiance, WattsPerSquareMeter}
import squants.space.Length
import squants.thermal.Temperature

import java.time.OffsetDateTime
import scala.collection.SortedMap

/**
 * Unified data structure for all weather models (GFS, AROME, WRF, etc).
 * Replaces model-specific structures like Forecast, AromeData, WrfData.
 *
 * All fields use Squants for type-safe physical units.
 * Optional fields (cape, cin) handle models that don't provide certain variables.
 */
case class UnifiedModelData(
  // Temporal and spatial metadata
  time: OffsetDateTime,
  elevation: Length,                      // Terrain elevation (m AMSL)

  // Boundary layer and thermals
  boundaryLayerDepth: Length,             // PBL depth (m AGL)
  thermalVelocity: Velocity,              // W* thermal updraft velocity (m/s)
  soaringLayerDepth: Length,              // Usable soaring altitude (m AGL)

  // Surface and boundary layer winds
  surfaceWind: Wind,                      // Wind at 10m
  boundaryLayerWind: Wind,                // Wind at PBL top
  winds: Winds,                           // Multi-level winds

  // Temperature and humidity
  surfaceTemperature: Temperature,        // T2m
  surfaceDewPoint: Temperature,           // TD2m

  // Pressure
  mslet: Pressure,                        // Mean sea level pressure

  // Clouds and precipitation
  totalCloudCover: Int,                   // 0-100%
  convectiveCloudCover: Int,              // 0-100% (GFS only, 0 for AROME/WRF)
  lowCloudCover: Option[Int],             // 0-100% (AROME: 0-2km)
  mediumCloudCover: Option[Int],          // 0-100% (AROME: 2-5km)
  highCloudCover: Option[Int],            // 0-100% (AROME: >5km)
  convectiveClouds: Option[ConvectiveClouds],
  totalRain: Length,                      // Total precipitation
  convectiveRain: Length,                 // Convective precipitation
  snowDepth: Length,                      // Snow depth

  // Energy fluxes
  sensibleHeatNetFlux: Irradiance,        // Sensible heat flux
  latentHeatNetFlux: Irradiance,          // Latent heat flux
  downwardShortWaveRadiationFlux: Irradiance, // Solar radiation

  // Atmospheric stability
  cape: Option[SpecificEnergy],           // Convective Available Potential Energy (optional - WRF doesn't have)
  cin: Option[SpecificEnergy],            // Convective Inhibition (optional - WRF doesn't have)

  // Isotherms
  isothermZero: Option[Length],           // 0°C isotherm altitude

  // Vertical profiles (for soundings)
  airDataByAltitude: SortedMap[Length, AirData],

  // Derived products
  xcFlyingPotential: Int                  // Cross-country flying potential (0-100)
)

object UnifiedModelData {
  /**
   * Creates UnifiedModelData from raw parser data.
   * Applies unit conversions and calculates derived values.
   */
  def fromRawData(
    time: OffsetDateTime,
    surface: org.soaringmeteo.parsing.SurfaceData,
    boundary: org.soaringmeteo.parsing.BoundaryLayerData,
    atmosphere: org.soaringmeteo.parsing.AtmosphereData,
    profiles: org.soaringmeteo.parsing.VerticalProfiles
  ): UnifiedModelData = {

    // Calculate derived thermal velocity
    val thermals = Thermals.velocity(
      boundary.sensibleHeatFlux,
      boundary.pblDepth
    )

    // Calculate convective clouds first (needed for soaring depth)
    val convClouds = ConvectiveClouds(
      surface.temperature,
      surface.dewPoint,
      surface.elevation,
      boundary.pblDepth,
      profiles.airDataByAltitude
    )

    // Calculate soaring layer depth
    val soaringDepth = Thermals.soaringLayerDepth(
      surface.elevation,
      boundary.pblDepth,
      convClouds
    )

    // Calculate XC flying potential
    val xcPotential = XCFlyingPotential(
      thermals,
      soaringDepth,
      boundary.wind
    )

    // Build winds from profiles
    val winds = Winds(profiles.airDataByAltitude, surface.elevation, soaringDepth)

    UnifiedModelData(
      time = time,
      elevation = surface.elevation,
      boundaryLayerDepth = boundary.pblDepth,
      thermalVelocity = thermals,
      soaringLayerDepth = soaringDepth,
      surfaceWind = surface.wind,
      boundaryLayerWind = boundary.wind,
      winds = winds,
      surfaceTemperature = surface.temperature,
      surfaceDewPoint = surface.dewPoint,
      mslet = surface.pressure,
      totalCloudCover = atmosphere.totalCloudCover,
      convectiveCloudCover = atmosphere.convectiveCloudCover,
      lowCloudCover = atmosphere.lowCloudCover,
      mediumCloudCover = atmosphere.mediumCloudCover,
      highCloudCover = atmosphere.highCloudCover,
      convectiveClouds = convClouds,
      totalRain = atmosphere.totalRain,
      convectiveRain = atmosphere.convectiveRain,
      snowDepth = surface.snowDepth,
      sensibleHeatNetFlux = boundary.sensibleHeatFlux,
      latentHeatNetFlux = boundary.latentHeatFlux,
      downwardShortWaveRadiationFlux = atmosphere.solarRadiation,
      cape = atmosphere.cape,
      cin = atmosphere.cin,
      isothermZero = profiles.isothermZero,
      airDataByAltitude = profiles.airDataByAltitude,
      xcFlyingPotential = xcPotential
    )
  }
}
