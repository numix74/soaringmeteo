package org.soaringmeteo.gfs

import org.soaringmeteo.out.OutputPaths

package object out {

  /** Directory to write the output of the GFS forecast and the
   * `forecast.json` metadata.
   *
   * @deprecated Use OutputPaths.modelOutputDir(basePath, "gfs") instead
   */
  def versionedTargetPath(basePath: os.Path): os.Path =
    OutputPaths.modelOutputDir(basePath, "gfs")

  /** Directory to write the output of a GFS run
   *
   * @deprecated Use OutputPaths.runOutputDir(basePath, "gfs", initDateString) instead
   */
  def runTargetPath(versionedTargetPath: os.Path, initDateString: String): os.Path =
    versionedTargetPath / initDateString

  /** Directory to write the output of a subgrid
   *
   * @deprecated Use OutputPaths.zoneOutputDir(basePath, "gfs", initDateString, subgrid.id) instead
   */
  def subgridTargetPath(runTargetPath: os.Path, subgrid: Subgrid): os.Path =
    runTargetPath / subgrid.id

}
