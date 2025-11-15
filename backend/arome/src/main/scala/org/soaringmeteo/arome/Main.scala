package org.soaringmeteo.arome

import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration.Duration
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import os.Path
import org.soaringmeteo.{InitDateString, MeteoData}
import org.soaringmeteo.arome.out.Store
import org.soaringmeteo.out.{JsonData, OutputPaths, touchMarkerFile}

object Main {
  private val logger = LoggerFactory.getLogger(getClass)
  implicit val ec: ExecutionContext = ExecutionContext.global

  def main(args: Array[String]): Unit = {
    try {
      val settings = Settings.fromCommandLineArguments(args)
      run(settings)
      System.exit(0)
    } catch {
      case e: Exception =>
        logger.error("AROME processing failed", e)
        System.exit(1)
    }
  }

  def run(settings: Settings): Unit = {
    val initTime = OffsetDateTime.now()
    val initDateString = InitDateString(initTime)

    logger.info("=== AROME Production Pipeline ===")
    logger.info(s"Initialization time: $initTime")
    logger.info(s"Init date string: $initDateString")
    logger.info(s"Zones to process: ${settings.zones.map(_.name).mkString(", ")}")

    Await.result(Store.ensureSchemaExists(), Duration.Inf)

    for (setting <- settings.zones) {
      logger.info(s"\nProcessing zone: ${setting.name}")
      val outputBaseDir = os.Path(setting.outputDirectory)

      // Use harmonized output paths
      processZone(initTime, initDateString, setting, outputBaseDir)
    }

    Store.close()
    touchMarkerFile(outputBaseDir)
    logger.info("\n=== AROME Pipeline Complete ===")
  }

  private def processZone(
    initTime: OffsetDateTime,
    initDateString: String,
    setting: AromeSetting,
    outputBaseDir: os.Path
  ): Unit = {
    val gribDir = os.Path(setting.gribDirectory)

    if (!os.exists(gribDir)) {
      logger.warn(s"GRIB directory not found: $gribDir")
      return
    }

    // Use harmonized output paths
    val zoneId = setting.name.toLowerCase.replace(" ", "-")
    val zoneOutputDir = OutputPaths.zoneOutputDir(outputBaseDir, "arome", initDateString, zoneId)
    val mapsBaseDir = OutputPaths.mapsDir(outputBaseDir, "arome", initDateString, zoneId)
    val locationDir = OutputPaths.locationDir(outputBaseDir, "arome", initDateString, zoneId)

    // Create directories
    os.makeDir.all(zoneOutputDir)
    os.makeDir.all(mapsBaseDir)
    os.makeDir.all(locationDir)

    // Définir les groupes de fichiers GRIB
    val groups = Seq(
      ("00H06H", 0 to 6),
      ("07H12H", 7 to 12),
      ("13H18H", 13 to 18),
      ("19H24H", 19 to 24)
      // Ajoutez les autres groupes si vous les avez :
      // ("25H30H", 25 to 30),
      // ("31H36H", 31 to 36),
      // ("37H42H", 37 to 42)
    )

    val futures = scala.collection.mutable.ListBuffer[Future[Unit]]()

    // Traiter chaque groupe de fichiers
    groups.foreach { case (groupName, hours) =>
      logger.info(s"Processing group $groupName (hours ${hours.head}-${hours.last})...")

      val sp1File = gribDir / s"SP1_${groupName}.grib2"
      val sp2File = gribDir / s"SP2_${groupName}.grib2"
      val sp3File = gribDir / s"SP3_${groupName}.grib2"
      val windsDir = gribDir / "winds"

      // Vérifier que tous les fichiers existent
      if (!os.exists(sp1File)) {
        logger.warn(s"Missing file: $sp1File")
        return
      }
      if (!os.exists(sp2File)) {
        logger.warn(s"Missing file: $sp2File")
        return
      }
      if (!os.exists(sp3File)) {
        logger.warn(s"Missing file: $sp3File")
        return
      }

      // Traiter chaque heure du groupe
      hours.foreach { hour =>
        val hourOffsetInGroup = hour - hours.head  // 0, 1, 2, 3, 4, 5, 6 pour le premier groupe

        val future = Future {
          try {
            logger.info(s"  Processing hour $hour (offset $hourOffsetInGroup in file $groupName)...")

            val data = AromeGrib.fromGroupFiles(
              sp1File = sp1File,
              sp2File = sp2File,
              sp3File = sp3File,
              windsDir = windsDir,
              hourOffset = hourOffsetInGroup,  // Offset DANS le fichier groupe
              zone = setting.zone
            )

            val meteoData: IndexedSeq[IndexedSeq[MeteoData]] = data.map { row =>
              row.map { aromeData =>
                new AromeMeteoDataAdapter(aromeData, initTime.plusHours(hour))
              }
            }

            // Use harmonized hour directory path
            val hourMapsDir = OutputPaths.hourMapsDir(outputBaseDir, "arome", initDateString, zoneId, hour)
            os.makeDir.all(hourMapsDir)

            logger.debug(s"    Generating PNG to $hourMapsDir...")
            org.soaringmeteo.out.Raster.writeAllPngFiles(
              setting.zone.longitudes.size,
              setting.zone.latitudes.size,
              zoneOutputDir,  // Base directory for zone
              hour,
              meteoData
            )
            logger.debug(s"    Generating MVT...")
            org.soaringmeteo.out.VectorTiles.writeAllVectorTiles(
              AromeVectorTilesParameters(setting.zone),
              zoneOutputDir,  // Base directory for zone
              hour,
              meteoData
            )
            logger.debug(s"    Saving to database...")
            Await.result(
              Store.save(initTime, setting.name, hour, data),
              Duration.Inf
            )

            logger.info(s"  Hour $hour completed")
          } catch {
            case e: Exception =>
              logger.error(s"Error processing hour $hour", e)
          }
        }
        futures += future
      }
    }

    Await.result(Future.sequence(futures), Duration.Inf)

    // TODO: Generate location JSON files
    logger.info(s"Generating location forecasts for zone ${setting.name}...")
    // JsonData.writeForecastsByLocation(...) will be added next

    logger.info(s"Zone ${setting.name} completed")
  }
}
