package org.soaringmeteo.tools

import ucar.nc2.dt.grid.GridDataset
import scala.jdk.CollectionConverters._

object ListGribVariables {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      println("Usage: ListGribVariables <grib-file>")
      System.exit(1)
    }

    val file = args(0)
    println(s"Listing variables in: $file")
    println("=" * 80)

    val dataset = GridDataset.open(file)
    try {
      val grids = dataset.getGrids.asScala
      println(s"Found ${grids.size} variables:\n")

      grids.foreach { grid =>
        println(s"Short name:  ${grid.getShortName}")
        println(s"Full name:   ${grid.getFullName}")
        println(s"Description: ${grid.getDescription}")
        println(s"Units:       ${grid.getUnitsString}")
        println(s"Dimensions:  ${grid.getDimensions.asScala.map(_.getLength).mkString(" x ")}")
        println("-" * 80)
      }
    } finally {
      dataset.close()
    }
  }
}
