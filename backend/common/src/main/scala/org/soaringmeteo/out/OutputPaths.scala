package org.soaringmeteo.out

/**
 * Harmonized output paths for all weather models.
 *
 * All models (GFS, WRF, AROME, future ICON, etc.) follow the same directory structure:
 *
 * {{{
 * <baseOutputDir>/
 * └── <formatVersion>/           # Ex: "7"
 *     └── <model>/                # Ex: "gfs", "wrf", "arome"
 *         ├── <initDateString>/   # Ex: "2025-11-15T06"
 *         │   ├── <zone>/         # Ex: "pays-basque", "pyrenees"
 *         │   │   ├── maps/
 *         │   │   │   └── <hour>/ # Ex: "00", "01", "02"
 *         │   │   │       ├── thermal-velocity.png
 *         │   │   │       ├── wind-10m.mvt
 *         │   │   │       └── ...
 *         │   │   └── location/
 *         │   │       ├── <clusterId>.json
 *         │   │       └── ...
 *         │   └── forecast.json   # Forecast metadata
 *         └── ...
 * }}}
 *
 * @param baseOutputDir The root output directory (default: /home/user/soaringmeteo/output)
 */
object OutputPaths {

  /**
   * Get the versioned output directory for all models.
   *
   * @param baseOutputDir Base output directory
   * @return Path like: baseOutputDir/<formatVersion>/
   */
  def versionedOutputDir(baseOutputDir: os.Path): os.Path =
    baseOutputDir / formatVersion.toString

  /**
   * Get the model-specific output directory.
   *
   * @param baseOutputDir Base output directory
   * @param model Model name ("gfs", "wrf", "arome", etc.)
   * @return Path like: baseOutputDir/<formatVersion>/<model>/
   */
  def modelOutputDir(baseOutputDir: os.Path, model: String): os.Path =
    versionedOutputDir(baseOutputDir) / model

  /**
   * Get the output directory for a specific forecast run.
   *
   * @param baseOutputDir Base output directory
   * @param model Model name ("gfs", "wrf", "arome", etc.)
   * @param initDateString Initialization date string (format: "YYYY-MM-DDTHH")
   * @return Path like: baseOutputDir/<formatVersion>/<model>/<initDateString>/
   */
  def runOutputDir(baseOutputDir: os.Path, model: String, initDateString: String): os.Path =
    modelOutputDir(baseOutputDir, model) / initDateString

  /**
   * Get the output directory for a specific zone within a forecast run.
   *
   * @param baseOutputDir Base output directory
   * @param model Model name ("gfs", "wrf", "arome", etc.)
   * @param initDateString Initialization date string
   * @param zone Zone identifier ("pays-basque", "pyrenees", etc.)
   * @return Path like: baseOutputDir/<formatVersion>/<model>/<initDateString>/<zone>/
   */
  def zoneOutputDir(
    baseOutputDir: os.Path,
    model: String,
    initDateString: String,
    zone: String
  ): os.Path =
    runOutputDir(baseOutputDir, model, initDateString) / zone

  /**
   * Get the maps directory for a specific zone.
   *
   * @param baseOutputDir Base output directory
   * @param model Model name
   * @param initDateString Initialization date string
   * @param zone Zone identifier
   * @return Path like: baseOutputDir/<formatVersion>/<model>/<initDateString>/<zone>/maps/
   */
  def mapsDir(
    baseOutputDir: os.Path,
    model: String,
    initDateString: String,
    zone: String
  ): os.Path =
    zoneOutputDir(baseOutputDir, model, initDateString, zone) / "maps"

  /**
   * Get the maps directory for a specific hour.
   *
   * @param baseOutputDir Base output directory
   * @param model Model name
   * @param initDateString Initialization date string
   * @param zone Zone identifier
   * @param hour Hour offset (0, 1, 2, ...)
   * @return Path like: baseOutputDir/<formatVersion>/<model>/<initDateString>/<zone>/maps/<hour>/
   */
  def hourMapsDir(
    baseOutputDir: os.Path,
    model: String,
    initDateString: String,
    zone: String,
    hour: Int
  ): os.Path =
    mapsDir(baseOutputDir, model, initDateString, zone) / f"$hour%02d"

  /**
   * Get the location forecasts directory.
   *
   * @param baseOutputDir Base output directory
   * @param model Model name
   * @param initDateString Initialization date string
   * @param zone Zone identifier
   * @return Path like: baseOutputDir/<formatVersion>/<model>/<initDateString>/<zone>/location/
   */
  def locationDir(
    baseOutputDir: os.Path,
    model: String,
    initDateString: String,
    zone: String
  ): os.Path =
    zoneOutputDir(baseOutputDir, model, initDateString, zone) / "location"

  /**
   * Get the forecast.json metadata file path.
   *
   * @param baseOutputDir Base output directory
   * @param model Model name
   * @param initDateString Initialization date string
   * @return Path like: baseOutputDir/<formatVersion>/<model>/<initDateString>/forecast.json
   */
  def forecastMetadataFile(
    baseOutputDir: os.Path,
    model: String,
    initDateString: String
  ): os.Path =
    runOutputDir(baseOutputDir, model, initDateString) / "forecast.json"

  /**
   * Default base output directory.
   */
  val defaultBaseOutputDir: os.Path = os.Path("/home/user/soaringmeteo/output")

}
