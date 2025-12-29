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
    convectiveCloudCover: String,

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
      convectiveCloudCover = "Total_cloud_cover_convective_cloud",

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
      // Surface - AROME uses shorter names
      temperature2m = "2 metre temperature",
      dewPoint2m = "2 metre dewpoint temperature",
      relativeHumidity2m = None,  // AROME has direct dew point
      uWind10m = "10 metre U wind component",
      vWind10m = "10 metre V wind component",
      pressure = "Mean sea level pressure",
      snowDepth = Some("Snow depth"),

      // Boundary layer
      pblHeight = "Planetary boundary layer height",

      // Fluxes
      sensibleHeatFlux = "Surface sensible heat flux",
      latentHeatFlux = "Surface latent heat flux",
      solarRadiation = "Surface net solar radiation",

      // Clouds
      totalCloudCover = "Total cloud cover",
      convectiveCloudCover = "Convective cloud cover",  // May not exist in AROME

      // Precipitation
      totalPrecipRate = "Total precipitation",
      convectivePrecipRate = "Convective precipitation",

      // Stability
      cape = Some("Convective available potential energy"),
      cin = Some("Convective inhibition"),

      // Geopotential
      geopotentialSurface = "Geopotential",
      geopotentialZeroDegC = "Geopotential height 0C isotherm",

      // Boundary layer wind - may use surface or specific level
      uWindPBL = "U component of wind",
      vWindPBL = "V component of wind",

      // Isobaric - AROME may use height levels instead
      temperatureIsobaric = "Temperature",
      relativeHumidityIsobaric = "Relative humidity",
      uWindIsobaric = "U component of wind",
      vWindIsobaric = "V component of wind",
      geopotentialIsobaric = "Geopotential height",
      cloudCoverIsobaric = "Total cloud cover"
    ),
    fileStructure = FileStructure.Grouped,
    nativeTimeStep = 1,
    maxHourOffset = 24,  // AROME provides 24h forecasts (00H-24H in 4 groups)
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
      convectiveCloudCover = "CLDFRA",  // WRF doesn't separate convective

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
