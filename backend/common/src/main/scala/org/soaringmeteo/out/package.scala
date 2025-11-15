package org.soaringmeteo

package object out {
  /**
   * Version of the format of the forecast data we produce.
   * We need to bump this number everytime we introduce incompatibilities (e.g. adding a non-optional field).
   * Make sure to also bump the `formatVersion` in the frontend (see frontend/src/data/ForecastMetadata.ts).
   */
  val formatVersion = 7

  /**
   * Delete the target directories older than the `oldestForecastToKeep`.
   *
   * @param targetDir Directory that contains the `forecast.json` file and the subdirectories
   *                  named according to the initialization time of the forecast.
   */
  def deleteOldData(targetDir: os.Path, currentForecasts: Seq[ForecastMetadata]): Unit = {
    val currentForecastPaths =
      currentForecasts.map(_.dataPath).toSet
    def isExpired(path: String): Boolean =
      !currentForecastPaths.contains(path)
    for {
      path <- os.list(targetDir)
      if os.isDir(path)
      if isExpired(path.last)
    } os.remove.all(path)
  }

  /**
   * Update the last modification date of the marker file used by both GFS and WRF.
   *
   * External programs can use this information to know whether new forecast results
   * have been published.
   *
   * @param outputDir Root output directory (without [[formatVersion]]).
   */
  def touchMarkerFile(outputDir: os.Path): Unit = {
    try {
      os.write.over(outputDir / "marker", "")
    } catch {
      case exception: Exception => () // Ignore exceptions
    }
  }

  /**
   * Create all necessary output directories for a model run.
   *
   * @param baseOutputDir Base output directory
   * @param model Model name ("gfs", "wrf", "arome")
   * @param initDateString Initialization date string
   * @param zones List of zone identifiers
   */
  def createOutputDirectories(
    baseOutputDir: os.Path,
    model: String,
    initDateString: String,
    zones: Seq[String]
  ): Unit = {
    // Create run directory and forecast.json location
    val runDir = OutputPaths.runOutputDir(baseOutputDir, model, initDateString)
    os.makeDir.all(runDir)

    // Create zone-specific directories
    for (zone <- zones) {
      val mapsDir = OutputPaths.mapsDir(baseOutputDir, model, initDateString, zone)
      val locationDir = OutputPaths.locationDir(baseOutputDir, model, initDateString, zone)
      os.makeDir.all(mapsDir)
      os.makeDir.all(locationDir)
    }
  }

}
