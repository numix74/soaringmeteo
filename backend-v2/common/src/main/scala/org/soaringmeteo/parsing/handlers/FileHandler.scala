package org.soaringmeteo.parsing.handlers

import java.time.OffsetDateTime

/**
 * Handles model-specific file access patterns.
 *
 * Different weather models organize their data files differently:
 * - GFS: One file per hour (gfs.t00z.pgrb2.0p25.f000, f003, f006, ...)
 * - AROME: Grouped files (SP1_00H06H, SP2_06H12H, SP3_12H24H, HP1_00H42H)
 * - WRF: Timestamped files (wrfout_d01_2023-10-29_18:00:00)
 *
 * FileHandler isolates these differences from the generic parser.
 */
trait FileHandler {

  /**
   * Prepare parseable data for a specific forecast hour.
   *
   * @param hourOffset Hours from forecast initialization (0, 1, 2, ...)
   * @param initTime Forecast initialization time
   * @return ParseableData containing file paths and metadata
   */
  def prepareData(hourOffset: Int, initTime: OffsetDateTime): ParseableData

  /**
   * Get the list of all available hour offsets for this forecast run.
   * Used for temporal interpolation to know what native hours exist.
   *
   * @param initTime Forecast initialization time
   * @return Sequence of hour offsets (e.g., Seq(0, 3, 6, 9, ...) for GFS)
   */
  def availableHourOffsets(initTime: OffsetDateTime): Seq[Int]
}

/**
 * Data prepared by a FileHandler for parsing.
 *
 * Contains all information needed by the generic parser:
 * - File paths to read
 * - Time metadata
 * - Model specification
 */
case class ParseableData(
  /**
   * Primary file containing most variables (surface, boundary layer, atmosphere).
   * For GFS: single file (e.g., gfs.t00z.pgrb2.0p25.f006)
   * For AROME: SP file (e.g., SP1_00H06H.grib2)
   */
  primaryFile: os.Path,

  /**
   * Optional secondary file for additional variables.
   * For AROME: HP1 file containing isobaric levels
   * For GFS/WRF: None (all data in primary file)
   */
  secondaryFile: Option[os.Path],

  /**
   * Forecast initialization time (model run time).
   */
  initTime: OffsetDateTime,

  /**
   * Hour offset from initialization (0, 1, 2, ...).
   */
  hourOffset: Int,

  /**
   * Valid time for this forecast hour (initTime + hourOffset).
   */
  validTime: OffsetDateTime,

  /**
   * Model specification from ModelRegistry.
   */
  modelSpec: org.soaringmeteo.parsing.ModelRegistry.ModelSpec
)
