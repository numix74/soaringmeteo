package org.soaringmeteo.arome

import org.soaringmeteo.grib.Grib

object ListVariables {
  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      println("Usage: ListVariables <grib-file>")
      System.exit(1)
    }
    
    val gribFile = os.Path(args(0))
    println(s"=== Analyse de: $gribFile ===\n")
    
    Grib.bracket(gribFile) { grib =>
      println("Tentative de lecture des variables...")
      
      // Utiliser la réflexion pour lister toutes les variables disponibles
      try {
        val dataset = grib.asInstanceOf[{ def dataset: ucar.nc2.dt.GridDataset }].dataset
        val grids = dataset.getGrids
        
        println(s"\nNombre de grilles trouvées: ${grids.size()}\n")
        
        import scala.jdk.CollectionConverters._
        grids.asScala.foreach { grid =>
          println(s"  - ${grid.getName}")
          println(s"    Description: ${grid.getDescription}")
          println(s"    Unité: ${grid.getUnitsString}")
          println()
        }
      } catch {
        case e: Exception =>
          println(s"Erreur: ${e.getMessage}")
          e.printStackTrace()
      }
    }
  }
}
