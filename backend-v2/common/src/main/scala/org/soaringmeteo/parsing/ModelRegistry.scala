package org.soaringmeteo.parsing

/**
 * Registry of weather models with their variable mappings.
 * This allows the generic parser to extract the right variables for each model.
 */
object ModelRegistry {

  /**
   * Variable names mapping for a specific weather model.
   * Optional fields handle models that don't provide certain variables.
   */
  case class VariableMapping(
    // Surface variables (required)
    temperature2m: String,
    dewPoint2m: String,              // Either direct or via RH2m
    relativeHumidity2m: Option[String],  // Used to compute dew point if no direct TD2m
    uWind10m: String,
    vWind10m: String,
    pressure: String,
    snowDepth: Option[String],

    // Boundary layer (required)
    pblHeight: String,

    // Fluxes (required)
    sensibleHeatFlux: String,
    latentHeatFlux: String,
    solarRadiation: String,

    // Cloud cover (required)
    totalCloudCover: String,
    convectiveCloudCover: Option[String],  // Optional: not all models provide it

    // Cloud cover by layer (optional - AROME specific)
    lowCloudCover: Option[String],
    mediumCloudCover: Option[String],
    highCloudCover: Option[String],

    // Precipitation (required)
    totalPrecipRate: String,
    convectivePrecipRate: String,

    // Stability (optional - WRF doesn't have directly)
    cape: Option[String],
    cin: Option[String],

    // Geopotential
    geopotentialSurface: String,
    geopotentialZeroDegC: String,

    // Boundary layer wind
    uWindPBL: String,
    vWindPBL: String,

    // Isobaric levels variables
    temperatureIsobaric: String,
    relativeHumidityIsobaric: String,
    uWindIsobaric: String,
    vWindIsobaric: String,
    geopotentialIsobaric: String,
    cloudCoverIsobaric: String
  )

  sealed trait FileFormat
  object FileFormat {
    case object GRIB extends FileFormat
    case object GRIB2 extends FileFormat
    case object NetCDF extends FileFormat
  }

  sealed trait FileStructure
  object FileStructure {
    case object Single extends FileStructure     // 1 file per hour (GFS)
    case object Grouped extends FileStructure    // Grouped files SP1/SP2/SP3 (AROME)
    case object Timestamped extends FileStructure // wrfout_* files (WRF)
  }

  /**
   * Complete specification for a weather model.
   */
  case class ModelSpec(
    id: String,
    name: String,
    format: FileFormat,
    variables: VariableMapping,
    fileStructure: FileStructure,
    nativeTimeStep: Int,              // Hours between native forecasts (3h for GFS, 1h for AROME)
    maxHourOffset: Int,               // Maximum forecast hour (120 for GFS, 42 for AROME)
    pressureLevels: Seq[Int]          // Isobaric pressure levels in hPa
  )

  // ===== GFS MODEL =====
  val GFS = ModelSpec(
    id = "gfs",
    name = "Global Forecast System (NOAA)",
    format = FileFormat.GRIB,
    variables = VariableMapping(
      // Surface
      temperature2m = "Temperature_height_above_ground",
      dewPoint2m = "",  // Computed from temperature + RH
      relativeHumidity2m = Some("Relative_humidity_height_above_ground"),
      uWind10m = "u-component_of_wind_height_above_ground",
      vWind10m = "v-component_of_wind_height_above_ground",
      pressure = "MSLP_Eta_model_reduction_msl",
      snowDepth = Some("Water_equivalent_of_accumulated_snow_depth_surface"),

      // Boundary layer
      pblHeight = "Planetary_Boundary_Layer_Height_surface",

      // Fluxes
      sensibleHeatFlux = "Sensible_heat_net_flux_surface_3_Hour_Average",
      latentHeatFlux = "Latent_heat_net_flux_surface_3_Hour_Average",
      solarRadiation = "Downward_Short-Wave_Radiation_Flux_surface_3_Hour_Average",

      // Clouds
      totalCloudCover = "Total_cloud_cover_entire_atmosphere_3_Hour_Average", // or 6_Hour_Average
      convectiveCloudCover = Some("Total_cloud_cover_convective_cloud"),

      // Cloud layers (GFS doesn't provide these)
      lowCloudCover = None,
      mediumCloudCover = None,
      highCloudCover = None,

      // Precipitation
      totalPrecipRate = "Precipitation_rate_surface",
      convectivePrecipRate = "Convective_precipitation_rate_surface",

      // Stability
      cape = Some("Convective_available_potential_energy_surface"),
      cin = Some("Convective_inhibition_surface"),

      // Geopotential
      geopotentialSurface = "Geopotential_height_surface",
      geopotentialZeroDegC = "Geopotential_height_zeroDegC_isotherm",

      // Boundary layer wind
      uWindPBL = "u-component_of_wind_planetary_boundary",
      vWindPBL = "v-component_of_wind_planetary_boundary",

      // Isobaric
      temperatureIsobaric = "Temperature_isobaric",
      relativeHumidityIsobaric = "Relative_humidity_isobaric",
      uWindIsobaric = "u-component_of_wind_isobaric",
      vWindIsobaric = "v-component_of_wind_isobaric",
      geopotentialIsobaric = "Geopotential_height_isobaric",
      cloudCoverIsobaric = "Total_cloud_cover_isobaric"
    ),
    fileStructure = FileStructure.Single,
    nativeTimeStep = 3,
    maxHourOffset = 120,
    pressureLevels = Seq(1000, 975, 950, 925, 900, 850, 800, 750, 700, 650, 600, 550, 500, 450, 400, 300, 200)
  )

  // ===== AROME MODEL =====
  val AROME = ModelSpec(
    id = "arome",
    name = "AROME France (Météo-France)",
    format = FileFormat.GRIB2,
    variables = VariableMapping(
      // Surface - AROME uses underscore-separated names
      temperature2m = "Temperature_height_above_ground",
      dewPoint2m = "Dewpoint_temperature_height_above_ground",
      relativeHumidity2m = None,  // AROME has direct dew point
      uWind10m = "u-component_of_wind_height_above_ground",
      vWind10m = "v-component_of_wind_height_above_ground",
      pressure = "Pressure_reduced_to_MSL_msl",
      snowDepth = None,  // Check if available

      // Boundary layer
      pblHeight = "Planetary_boundary_layer_height_surface",

      // Fluxes (accumulated variables)
      sensibleHeatFlux = "Sensible_heat_net_flux_surface_Mixed_intervals_Accumulation",
      latentHeatFlux = "Latent_heat_net_flux_surface_Mixed_intervals_Accumulation",
      solarRadiation = "Net_short_wave_radiation_flux_surface_Mixed_intervals_Accumulation",

      // Clouds (AROME provides detailed layer-by-layer coverage)
      totalCloudCover = "Total_cloud_cover_surface",           // SP1: TCDC
      convectiveCloudCover = None,                             // Not available in AROME

      // Cloud layers (AROME specific - SP2 file)
      lowCloudCover = Some("Low_cloud_cover_surface"),         // SP2: LCDC (0-2km)
      mediumCloudCover = Some("Medium_cloud_cover"),           // SP2: MCDC (2-5km)
      highCloudCover = Some("High_cloud_cover"),               // SP2: HCDC (>5km)

      // Precipitation (accumulated)
      totalPrecipRate = "Total_precipitation_rate_surface_Mixed_intervals_Accumulation",
      convectivePrecipRate = "Convective_precipitation_surface_Mixed_intervals_Accumulation",

      // Stability
      cape = Some("Convective_available_potential_energy_surface_layer"),
      cin = Some("Convective_inhibition_surface"),

      // Geopotential
      geopotentialSurface = "Geometric_height_surface",
      geopotentialZeroDegC = "Geopotential_height_zeroDegC_isotherm",

      // Boundary layer wind - may use surface or specific level
      uWindPBL = "u-component_of_wind",
      vWindPBL = "v-component_of_wind",

      // Isobaric - AROME may use height levels instead
      temperatureIsobaric = "Temperature",
      relativeHumidityIsobaric = "Relative_humidity",
      uWindIsobaric = "u-component_of_wind",
      vWindIsobaric = "v-component_of_wind",
      geopotentialIsobaric = "Geopotential_height",
      cloudCoverIsobaric = "Total_cloud_cover"
    ),
    fileStructure = FileStructure.Grouped,
    nativeTimeStep = 1,
    maxHourOffset = 24,  // AROME provides 25h forecasts (H+0 to H+24)
    pressureLevels = Seq.empty  // AROME uses height levels (250m, 500m, ..., 3000m)
  )

  // ===== WRF MODEL =====
  val WRF = ModelSpec(
    id = "wrf",
    name = "Weather Research & Forecasting",
    format = FileFormat.NetCDF,
    variables = VariableMapping(
      // Surface
      temperature2m = "T2",
      dewPoint2m = "TD2",
      relativeHumidity2m = None,
      uWind10m = "U10",
      vWind10m = "V10",
      pressure = "MSLP",
      snowDepth = Some("SNOWH"),

      // Boundary layer
      pblHeight = "PBLH",

      // Fluxes
      sensibleHeatFlux = "HFX",
      latentHeatFlux = "LH",
      solarRadiation = "SWDOWN",

      // Clouds
      totalCloudCover = "CLDFRA",
      convectiveCloudCover = None,  // WRF doesn't separate convective

      // Cloud layers (WRF doesn't provide these separately)
      lowCloudCover = None,
      mediumCloudCover = None,
      highCloudCover = None,

      // Precipitation
      totalPrecipRate = "RAINNC",
      convectivePrecipRate = "RAINC",

      // Stability - WRF doesn't have CAPE/CIN directly
      cape = None,
      cin = None,

      // Geopotential
      geopotentialSurface = "HGT",
      geopotentialZeroDegC = "HGT",  // May need computation

      // Boundary layer wind
      uWindPBL = "U",
      vWindPBL = "V",

      // 3D variables
      temperatureIsobaric = "T",
      relativeHumidityIsobaric = "RH",
      uWindIsobaric = "U",
      vWindIsobaric = "V",
      geopotentialIsobaric = "HGT",
      cloudCoverIsobaric = "CLDFRA"
    ),
    fileStructure = FileStructure.Timestamped,
    nativeTimeStep = 1,
    maxHourOffset = 48,
    pressureLevels = Seq(1000, 925, 850, 700, 500, 300, 200)  // Typical WRF output levels
  )

  /**
   * Get model specification by ID.
   */
  def forModel(modelId: String): ModelSpec = modelId.toLowerCase match {
    case "gfs" => GFS
    case "arome" => AROME
    case "wrf" => WRF
    case id => throw new IllegalArgumentException(s"Unknown model: $id. Supported: gfs, arome, wrf")
  }

  /**
   * List all available models.
   */
  val allModels: Seq[ModelSpec] = Seq(GFS, AROME, WRF)
}
