package org.soaringmeteo.parsing.handlers

import org.soaringmeteo.parsing.ModelRegistry

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * FileHandler for NOAA GFS model.
 *
 * GFS file structure (backend/gfs format):
 * - Single file per forecast hour per zone
 * - Directory structure: gribDirectory/YYMMDD/HH/
 *   - YYMMDD = year-month-day (e.g., 251108 for 2025-11-08)
 *   - HH = cycle hour (00, 06, 12, 18)
 * - Naming: GFS-{zone}-{HHH}.grib2
 *   - zone = geographic zone (e.g., "pyrenees", "pays-basque")
 *   - HHH = hour offset (000, 003, 006, ..., 120)
 * - Native timestep: 3 hours (0, 3, 6, 9, ...)
 * - All variables in one file (no separate isobaric file)
 *
 * Example:
 *   gribDirectory = /home/ubuntu/soaringmeteo/backend/gfs/target/grib
 *   zone = "pyrenees"
 *   initTime = 2025-11-08T18:00Z
 *   hourOffset = 6
 *   → /home/ubuntu/soaringmeteo/backend/gfs/target/grib/251108/18/GFS-pyrenees-006.grib2
 */
class GfsFileHandler(gribDirectory: os.Path, zone: String = "pyrenees") extends FileHandler {

  private val modelSpec = ModelRegistry.GFS

  override def prepareData(hourOffset: Int, initTime: OffsetDateTime): ParseableData = {
    // GFS cycle hour (00, 06, 12, 18)
    val cycleHour = initTime.getHour
    require(
      cycleHour % 6 == 0,
      s"GFS runs every 6 hours. Invalid initTime: $initTime (hour=$cycleHour)"
    )

    // Build file path following backend/gfs structure
    val dateDir = formatDateDirectory(initTime)
    val hourDir = f"$cycleHour%02d"
    val fileName = formatGfsFileName(zone, hourOffset)
    val filePath = gribDirectory / dateDir / hourDir / fileName

    // Validate file exists
    require(
      os.exists(filePath),
      s"GFS GRIB file not found: $filePath\n" +
      s"  Expected structure: {gribDirectory}/{YYMMDD}/{HH}/GFS-{zone}-{HHH}.grib2\n" +
      s"  Make sure backend/gfs has downloaded data for this run."
    )

    // Calculate valid time
    val validTime = initTime.plusHours(hourOffset.toLong)

    ParseableData(
      primaryFile = filePath,
      secondaryFile = None,  // GFS has all data in one file
      initTime = initTime,
      hourOffset = hourOffset,
      validTime = validTime,
      modelSpec = modelSpec
    )
  }

  override def availableHourOffsets(initTime: OffsetDateTime): Seq[Int] = {
    // Scan directory to find actually available files
    val dateDir = formatDateDirectory(initTime)
    val hourDir = f"${initTime.getHour}%02d"
    val runDirectory = gribDirectory / dateDir / hourDir

    if (!os.exists(runDirectory)) {
      throw new IllegalStateException(
        s"GFS run directory not found: $runDirectory\n" +
        s"  Expected structure: {gribDirectory}/{YYMMDD}/{HH}/\n" +
        s"  Make sure backend/gfs has downloaded data for init time: $initTime"
      )
    }

    // List all GFS-{zone}-*.grib2 files and extract hour offsets
    val availableFiles = os.list(runDirectory)
      .filter(f => os.isFile(f) && f.last.startsWith(s"GFS-$zone-") && f.last.endsWith(".grib2"))
      .map { file =>
        // Extract hour from filename: GFS-pyrenees-006.grib2 -> 6
        val hourStr = file.last.stripPrefix(s"GFS-$zone-").stripSuffix(".grib2")
        hourStr.toInt
      }
      .sorted

    if (availableFiles.isEmpty) {
      throw new IllegalStateException(
        s"No GFS GRIB files found in $runDirectory\n" +
        s"  Looking for files matching: GFS-$zone-*.grib2"
      )
    }

    availableFiles
  }

  /**
   * Format date directory name.
   *
   * @param initTime Forecast initialization time
   * @return Directory name (e.g., "251108" for 2025-11-08)
   */
  private def formatDateDirectory(initTime: OffsetDateTime): String = {
    val year = initTime.getYear % 100  // Last 2 digits of year
    val month = initTime.getMonthValue
    val day = initTime.getDayOfMonth
    f"$year%02d$month%02d$day%02d"
  }

  /**
   * Format GFS GRIB file name.
   *
   * @param zone Geographic zone (e.g., "pyrenees")
   * @param hourOffset Forecast hour offset (0, 3, 6, ...)
   * @return File name (e.g., "GFS-pyrenees-006.grib2")
   */
  private def formatGfsFileName(zone: String, hourOffset: Int): String = {
    val hour = f"$hourOffset%03d"
    s"GFS-$zone-$hour.grib2"
  }
}

object GfsFileHandler {
  /**
   * Create GFS FileHandler from configuration.
   *
   * @param gribDirectory Directory containing downloaded GFS GRIB files
   * @param zone Geographic zone (e.g., "pyrenees", "pays-basque")
   */
  def apply(gribDirectory: os.Path, zone: String = "pyrenees"): GfsFileHandler = {
    require(
      os.exists(gribDirectory) && os.isDir(gribDirectory),
      s"GFS GRIB directory not found or not a directory: $gribDirectory"
    )
    new GfsFileHandler(gribDirectory, zone)
  }
}
