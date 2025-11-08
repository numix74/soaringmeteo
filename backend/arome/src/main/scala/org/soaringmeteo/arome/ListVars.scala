package org.soaringmeteo.arome

import ucar.nc2.dt.grid.GridDataset

object ListVars {
  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      println("Usage: listVars <grib2file>")
      System.exit(1)
    }
    
    val file = args(0)
    println(s"=== Variables dans $file ===")
    
    val dataset = GridDataset.open(file)
    try {
      val grids = dataset.getGrids
      val it = grids.iterator()
      while (it.hasNext) {
        val grid = it.next()
        println(s"  ${grid.getFullName}")
      }
    } finally {
      dataset.close()
    }
  }
}
