package org.soaringmeteo.parsing.handlers

import org.soaringmeteo.parsing.ModelRegistry

import java.time.OffsetDateTime

/**
 * FileHandler for Météo-France AROME model.
 *
 * AROME file structure (grouped GRIB2 files):
 * - Files are grouped by time ranges (00H06H, 07H12H, etc.)
 * - Each group contains:
 *   - SP1_{group}.grib2: Primary surface fields
 *   - SP2_{group}.grib2: Secondary surface fields
 *   - SP3_{group}.grib2: Surface fluxes
 *   - HP1_{group}.grib2: Isobaric/height levels (optional)
 *   - HP2_{group}.grib2: Additional levels (optional)
 *
 * - Native timestep: 1 hour (hourly forecasts)
 * - Groups: 00H06H (hours 0-6), 07H12H (7-12), 13H18H (13-18),
 *           19H24H (19-24), 25H30H (25-30), 31H36H (31-36), 37H42H (37-42)
 *
 * Example:
 *   hourOffset = 8
 *   → Group: 07H12H
 *   → Offset within group: 1 (8 - 7 = 1)
 *   → Files: SP1_07H12H.grib2, SP2_07H12H.grib2, SP3_07H12H.grib2, HP1_07H12H.grib2
 */
class AromeFileHandler(gribDirectory: os.Path) extends FileHandler {

  private val modelSpec = ModelRegistry.AROME

  /**
   * AROME groups: (groupName, firstHour, lastHour)
   * AROME provides 24h forecasts in 4 groups of 6-7 hours
   */
  private val groups = Seq(
    ("00H06H", 0, 6),
    ("07H12H", 7, 12),
    ("13H18H", 13, 18),
    ("19H24H", 19, 24)
  )

  override def prepareData(hourOffset: Int, initTime: OffsetDateTime): ParseableData = {
    require(
      hourOffset >= 0 && hourOffset <= modelSpec.maxHourOffset,
      s"AROME hourOffset must be 0-${modelSpec.maxHourOffset}, got: $hourOffset"
    )

    // Find the group containing this hour
    val (groupName, firstHour, lastHour) = groups.find { case (_, first, last) =>
      hourOffset >= first && hourOffset <= last
    }.getOrElse {
      throw new IllegalArgumentException(
        s"Hour offset $hourOffset not found in any AROME group"
      )
    }

    // Calculate offset within the group file
    val hourOffsetInGroup = hourOffset - firstHour

    // Build file paths
    val sp1File = gribDirectory / s"SP1_${groupName}.grib2"
    val sp2File = gribDirectory / s"SP2_${groupName}.grib2"
    val sp3File = gribDirectory / s"SP3_${groupName}.grib2"
    val hp1File = gribDirectory / s"HP1_${groupName}.grib2"

    // Validate SP files exist (required)
    require(
      os.exists(sp1File),
      s"AROME SP1 file not found: $sp1File"
    )
    require(
      os.exists(sp2File),
      s"AROME SP2 file not found: $sp2File"
    )
    require(
      os.exists(sp3File),
      s"AROME SP3 file not found: $sp3File"
    )

    // HP1 file is optional (for isobaric levels)
    val secondaryFile = if (os.exists(hp1File)) Some(hp1File) else None

    // Calculate valid time
    val validTime = initTime.plusHours(hourOffset.toLong)

    // For AROME, we need to pass metadata about the group structure
    // We use the primaryFile to represent the SP1 file, but the parser
    // will need access to SP2 and SP3 as well
    // TODO: Consider extending ParseableData to support multiple primary files
    //       for now, we'll handle this in the parser by convention:
    //       - primaryFile = SP1
    //       - Parser knows to also read SP2 and SP3 from same directory

    ParseableData(
      primaryFile = sp1File,
      secondaryFile = secondaryFile,  // HP1 file
      initTime = initTime,
      hourOffset = hourOffsetInGroup,  // IMPORTANT: offset WITHIN the group file
      validTime = validTime,
      modelSpec = modelSpec
    )
  }

  override def availableHourOffsets(initTime: OffsetDateTime): Seq[Int] = {
    // AROME native timesteps: every hour from 0 to 42
    (0 to modelSpec.maxHourOffset by modelSpec.nativeTimeStep)
  }

  /**
   * Get SP2 file path for a given hour offset.
   * Used by parser to locate the complementary surface file.
   */
  def getSp2File(hourOffset: Int): os.Path = {
    val (groupName, firstHour, _) = findGroup(hourOffset)
    gribDirectory / s"SP2_${groupName}.grib2"
  }

  /**
   * Get SP3 file path for a given hour offset.
   * Used by parser to locate the flux file.
   */
  def getSp3File(hourOffset: Int): os.Path = {
    val (groupName, firstHour, _) = findGroup(hourOffset)
    gribDirectory / s"SP3_${groupName}.grib2"
  }

  /**
   * Get winds directory path.
   * AROME stores pre-extracted wind files in winds/ subdirectory.
   */
  def getWindsDir: os.Path = {
    gribDirectory / "winds"
  }

  /**
   * Get profiles directory path for a given hour offset.
   * AROME stores pre-extracted vertical profiles in profiles/{groupName}/.
   */
  def getProfilesDir(hourOffset: Int): os.Path = {
    val (groupName, _, _) = findGroup(hourOffset)
    gribDirectory / "profiles" / groupName
  }

  private def findGroup(hourOffset: Int): (String, Int, Int) = {
    groups.find { case (_, first, last) =>
      hourOffset >= first && hourOffset <= last
    }.getOrElse {
      throw new IllegalArgumentException(
        s"Hour offset $hourOffset not found in any AROME group"
      )
    }
  }
}

object AromeFileHandler {
  /**
   * Create AROME FileHandler from configuration.
   *
   * @param gribDirectory Directory containing AROME grouped GRIB files
   */
  def apply(gribDirectory: os.Path): AromeFileHandler = {
    require(
      os.exists(gribDirectory) && os.isDir(gribDirectory),
      s"AROME GRIB directory not found or not a directory: $gribDirectory"
    )
    new AromeFileHandler(gribDirectory)
  }
}
