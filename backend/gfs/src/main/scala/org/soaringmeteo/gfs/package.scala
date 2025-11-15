package org.soaringmeteo

import org.soaringmeteo.{Forecast => GfsDataInternal}

/**
 * GFS-specific types and aliases.
 */
package object gfs {

  /**
   * GFS-specific forecast data.
   *
   * This is an alias for the `Forecast` case class, which represents
   * the result of processing GFS GRIB data. The name `GfsData` clarifies
   * that this is GFS-specific data, as opposed to the generic `MeteoData` trait
   * used across all weather models.
   *
   * @note In the future, `Forecast` will be renamed to `GfsData` completely
   *       and moved from `common/` to `gfs/` module for better code organization.
   *       This alias serves as a transition step.
   *
   * @see [[org.soaringmeteo.Forecast]] for the actual implementation
   * @see [[org.soaringmeteo.MeteoData]] for the unified interface
   * @see [[org.soaringmeteo.arome.AromeData]] for AROME equivalent
   */
  type GfsData = GfsDataInternal

}
