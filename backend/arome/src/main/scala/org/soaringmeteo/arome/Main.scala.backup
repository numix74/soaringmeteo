package org.soaringmeteo.arome

import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration.Duration
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import os.Path
import org.soaringmeteo.MeteoData
import org.soaringmeteo.arome.out.Store

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
    
    logger.info("=== AROME Production Pipeline ===")
    logger.info(s"Initialization time: $initTime")
    logger.info(s"Zones to process: ${settings.zones.map(_.name).mkString(", ")}")

    Await.result(Store.ensureSchemaExists(), Duration.Inf)

    for (setting <- settings.zones) {
      logger.info(s"\nProcessing zone: ${setting.name}")
      
      val outputBaseDir = os.Path(setting.outputDirectory)
      os.makeDir.all(outputBaseDir)

      processZone(initTime, setting, outputBaseDir)
    }

    Store.close()
    logger.info("\n=== AROME Pipeline Complete ===")
  }

  private def processZone(initTime: OffsetDateTime, setting: AromeSetting, outputBaseDir: os.Path): Unit = {
    val gribDir = os.Path(setting.gribDirectory)
    
    if (!os.exists(gribDir)) {
      logger.warn(s"GRIB directory not found: $gribDir")
      return
    }

    val futures = scala.collection.mutable.ListBuffer[Future[Unit]]()

    for (hourOffset <- 0 until 25) {
      val future = Future {
        try {
          logger.info(s"  Processing hour $hourOffset...")
          
          val data = AromeGrib.fromGroupFiles(
            sp1File = gribDir / "sp1" / s"hour_$hourOffset.grib2",
            sp2File = gribDir / "sp2" / s"hour_$hourOffset.grib2",
            sp3File = gribDir / "sp3" / s"hour_$hourOffset.grib2",
            windsDir = gribDir / "winds",
            hourOffset = hourOffset,
            zone = setting.zone
          )

          val meteoData: IndexedSeq[IndexedSeq[MeteoData]] = data.map { row =>
            row.map { aromeData =>
              new AromeMeteoDataAdapter(aromeData, initTime.plusHours(hourOffset))
            }
          }

          val mapsDir = outputBaseDir / "maps" / f"${hourOffset}%02d"
          os.makeDir.all(mapsDir)

          logger.debug(s"    Generating PNG...")
          org.soaringmeteo.out.Raster.writeAllPngFiles(
            setting.zone.longitudes.size,
            setting.zone.latitudes.size,
            mapsDir,
            hourOffset,
            meteoData
          )

          logger.debug(s"    Generating MVT...")
          org.soaringmeteo.out.VectorTiles.writeAllVectorTiles(
            AromeVectorTilesParameters(setting.zone),
            mapsDir,
            hourOffset,
            meteoData
          )

          logger.debug(s"    Saving to database...")
          Await.result(
            Store.save(initTime, setting.name, hourOffset, data),
            Duration.Inf
          )

          logger.info(s"  Hour $hourOffset completed")
        } catch {
          case e: Exception =>
            logger.error(s"Error processing hour $hourOffset", e)
        }
      }
      futures += future
    }

    Await.result(Future.sequence(futures), Duration.Inf)
    logger.info(s"Zone ${setting.name} completed")
  }
}
