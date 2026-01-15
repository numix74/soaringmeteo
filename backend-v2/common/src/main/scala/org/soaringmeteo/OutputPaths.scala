package org.soaringmeteo

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Helper object for consistent output path generation.
 *
 * Provides standardized directory structure for all weather models,
 * compatible with the existing frontend:
 * {{{
 *   output-unified/
 *     7/                              # Format version
 *       gfs/
 *         2025-12-28T00/              # Init date + hour (ISO-8601 format)
 *           pyrenees/                 # Zone
 *             thermal-velocity/       # Layer
 *               0.png                 # Hour offset
 *               1.png
 *               ...
 *             wind-surface/
 *               0.png
 *               ...
 *             location/
 *               forecast.json
 *       arome/
 *         2025-12-28T06/
 *           pays-basque/
 *             ...
 * }}}
 *
 * This structure is compatible with the existing frontend which expects:
 * - Model-specific directories (gfs/, arome/, wrf/)
 * - Init-date directories (YYYY-MM-DDTHH/ - ISO-8601 format)
 * - Zone-specific subdirectories
 * - Layer-specific subdirectories (thermal-velocity/, wind-surface/, ...)
 * - Hour offset PNG files (0.png, 1.png, ..., 120.png)
 */
object OutputPaths {

  /**
   * Format initialization time as directory name.
   *
   * Format: YYYY-MM-DDTHH (ISO-8601 compatible, frontend-compatible)
   * Example: 2025-12-28T00:00Z → "2025-12-28T00"
   */
  def formatInitTime(initTime: OffsetDateTime): String = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH")
    initTime.format(formatter)
  }

  /**
   * Get base model output directory.
   *
   * Example: /output-unified/7/gfs
   *
   * @param baseDir Base output directory (e.g., /output-unified)
   * @param formatVersion Format version (e.g., 7)
   * @param modelId Model identifier (e.g., "gfs", "arome")
   */
  def modelOutputDir(
    baseDir: os.Path,
    formatVersion: Int,
    modelId: String
  ): os.Path = {
    baseDir / formatVersion.toString / modelId
  }

  /**
   * Get forecast run output directory.
   *
   * Example: /output-unified/7/gfs/20251228_00
   *
   * @param baseDir Base output directory
   * @param formatVersion Format version
   * @param modelId Model identifier
   * @param initTime Forecast initialization time
   */
  def runOutputDir(
    baseDir: os.Path,
    formatVersion: Int,
    modelId: String,
    initTime: OffsetDateTime
  ): os.Path = {
    modelOutputDir(baseDir, formatVersion, modelId) / formatInitTime(initTime)
  }

  /**
   * Get zone-specific output directory.
   *
   * Example: /output-unified/7/gfs/20251228_00/pyrenees
   *
   * @param baseDir Base output directory
   * @param formatVersion Format version
   * @param modelId Model identifier
   * @param initTime Forecast initialization time
   * @param zoneId Zone identifier (e.g., "pyrenees")
   */
  def zoneOutputDir(
    baseDir: os.Path,
    formatVersion: Int,
    modelId: String,
    initTime: OffsetDateTime,
    zoneId: String
  ): os.Path = {
    runOutputDir(baseDir, formatVersion, modelId, initTime) / zoneId
  }

  /**
   * Get layer-specific maps directory (frontend-compatible structure).
   *
   * Example: /output-unified/7/gfs/2025-12-28T00/pyrenees/thermal-velocity
   *
   * @param baseDir Base output directory
   * @param formatVersion Format version
   * @param modelId Model identifier
   * @param initTime Forecast initialization time
   * @param zoneId Zone identifier
   * @param layerName Layer name (e.g., "thermal-velocity", "wind-surface")
   */
  def layerMapsDir(
    baseDir: os.Path,
    formatVersion: Int,
    modelId: String,
    initTime: OffsetDateTime,
    zoneId: String,
    layerName: String
  ): os.Path = {
    zoneOutputDir(baseDir, formatVersion, modelId, initTime, zoneId) / layerName
  }

  /**
   * Get location forecasts directory.
   *
   * Example: /output-unified/7/gfs/20251228_00/pyrenees/location
   *
   * @param baseDir Base output directory
   * @param formatVersion Format version
   * @param modelId Model identifier
   * @param initTime Forecast initialization time
   * @param zoneId Zone identifier
   */
  def locationDir(
    baseDir: os.Path,
    formatVersion: Int,
    modelId: String,
    initTime: OffsetDateTime,
    zoneId: String
  ): os.Path = {
    zoneOutputDir(baseDir, formatVersion, modelId, initTime, zoneId) / "location"
  }

  /**
   * Get path for a specific map PNG file (frontend-compatible structure).
   *
   * Example: /output-unified/7/gfs/2025-12-28T00/pyrenees/thermal-velocity/6.png
   *
   * @param baseDir Base output directory
   * @param formatVersion Format version
   * @param modelId Model identifier
   * @param initTime Forecast initialization time
   * @param zoneId Zone identifier
   * @param hourOffset Hour offset
   * @param layerName Layer name (e.g., "thermal-velocity", "wind-surface")
   */
  def mapFilePath(
    baseDir: os.Path,
    formatVersion: Int,
    modelId: String,
    initTime: OffsetDateTime,
    zoneId: String,
    hourOffset: Int,
    layerName: String
  ): os.Path = {
    layerMapsDir(baseDir, formatVersion, modelId, initTime, zoneId, layerName) /
      s"$hourOffset.png"
  }

  /**
   * Get path for forecast metadata JSON.
   *
   * Example: /output-unified/7/gfs/20251228_00/pyrenees/forecast.json
   *
   * @param baseDir Base output directory
   * @param formatVersion Format version
   * @param modelId Model identifier
   * @param initTime Forecast initialization time
   * @param zoneId Zone identifier
   */
  def forecastMetadataPath(
    baseDir: os.Path,
    formatVersion: Int,
    modelId: String,
    initTime: OffsetDateTime,
    zoneId: String
  ): os.Path = {
    zoneOutputDir(baseDir, formatVersion, modelId, initTime, zoneId) / "forecast.json"
  }

  /**
   * Create all necessary output directories for a forecast run.
   * Creates layer directories (frontend-compatible structure).
   *
   * @param baseDir Base output directory
   * @param formatVersion Format version
   * @param modelId Model identifier
   * @param initTime Forecast initialization time
   * @param zoneId Zone identifier
   */
  def createAllDirectories(
    baseDir: os.Path,
    formatVersion: Int,
    modelId: String,
    initTime: OffsetDateTime,
    zoneId: String
  ): Unit = {
    // Create base directories
    val zoneDir = zoneOutputDir(baseDir, formatVersion, modelId, initTime, zoneId)
    os.makeDir.all(zoneDir)

    // Create location directory
    os.makeDir.all(locationDir(baseDir, formatVersion, modelId, initTime, zoneId))

    // Create layer directories (one per standard layer)
    val standardLayers = Seq(
      "thermal-velocity",
      "soaring-layer-depth",
      "wind-surface",
      "clouds-rain",
      "xc-flying-potential",
      "boundary-layer-depth",
      "temperature-2m",
      "dew-point-2m"
    )

    standardLayers.foreach { layer =>
      os.makeDir.all(layerMapsDir(baseDir, formatVersion, modelId, initTime, zoneId, layer))
    }
  }

  /**
   * Get all map files for a specific hour.
   *
   * Returns paths for all standard map layers.
   */
  def allMapFilesForHour(
    baseDir: os.Path,
    formatVersion: Int,
    modelId: String,
    initTime: OffsetDateTime,
    zoneId: String,
    hourOffset: Int
  ): Map[String, os.Path] = {
    val standardLayers = Seq(
      "thermal-velocity",
      "soaring-layer-depth",
      "wind-surface",
      "clouds-rain",
      "xc-flying-potential",
      "boundary-layer-depth",
      "temperature-2m",
      "dew-point-2m"
    )

    standardLayers.map { layer =>
      layer -> mapFilePath(
        baseDir,
        formatVersion,
        modelId,
        initTime,
        zoneId,
        hourOffset,
        layer
      )
    }.toMap
  }
}
