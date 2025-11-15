package org.soaringmeteo.arome

import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration.Duration
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import os.Path
import org.soaringmeteo.MeteoData
import org.soaringmeteo.arome.out.{Store, AromeLocationJson}
import org.soaringmeteo.out.ForecastMetadata
import org.soaringmeteo.InitDateString

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
    logger.info(s"Zones to process: ${settings.zones.map(_.name).mkString(", ")}")

    Await.result(Store.ensureSchemaExists(), Duration.Inf)

    // Process each zone
    for (setting <- settings.zones) {
      logger.info(s"\nProcessing zone: ${setting.name}")
      val outputBaseDir = os.Path(setting.outputDirectory)
      processZone(initTime, initDateString, setting, outputBaseDir)
    }

    // Generate forecast.json for AROME
    val firstZoneSetting = settings.zones.head
    val outputBaseDir = os.Path(firstZoneSetting.outputDirectory)
    generateForecastMetadata(settings, outputBaseDir, initTime, initDateString)

    Store.close()
    logger.info("\n=== AROME Pipeline Complete ===")
  }

  private def generateForecastMetadata(
    settings: Settings,
    outputBaseDir: os.Path,
    initTime: OffsetDateTime,
    initDateString: String
  ): Unit = {
    val versionedDir = out.versionedTargetPath(outputBaseDir)
    val forecastJsonPath = versionedDir / "forecast.json"
    
    val zones = settings.zones.map { zoneSetting =>
      val zoneId = zoneSetting.name.toLowerCase.replace(" ", "-")
      val zone = zoneSetting.zone
      val lonMin = zone.longitudes.min
      val lonMax = zone.longitudes.max
      val latMin = zone.latitudes.min
      val latMax = zone.latitudes.max
      val step = if (zone.longitudes.size > 1) {
        BigDecimal((zone.longitudes(1) - zone.longitudes(0)).abs)
      } else BigDecimal(0.025)
      
      ForecastMetadata.Zone(
        id = zoneId,
        label = zoneSetting.name,
        raster = ForecastMetadata.Raster(
          projection = "EPSG:4326",
          resolution = step,
          extent = geotrellis.vector.Extent(lonMin, latMin, lonMax, latMax).buffer((step / 2).toDouble)
        ),
        vectorTiles = ForecastMetadata.VectorTiles(
          AromeVectorTilesParameters(zone),
          300
        )
      )
    }
    
    val metadata = ForecastMetadata(
      dataPath = initDateString,
      initDateTime = initTime,
      maybeFirstTimeStep = None,
      latestHourOffset = 24,
      zones = zones
    )
    
    logger.info(s"Writing forecast metadata to $forecastJsonPath")
    // jsonCodec works on Seq[ForecastMetadata], so wrap in a Seq
    val jsonString = ForecastMetadata.jsonCodec.apply(Seq(metadata)).spaces2
    os.write.over(forecastJsonPath, jsonString, createFolders = true)
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

    val versionedDir = out.versionedTargetPath(outputBaseDir)
    val runDir = out.runTargetPath(versionedDir, initDateString)
    val zoneId = setting.name.toLowerCase.replace(" ", "-")
    val zoneDir = out.zoneTargetPath(runDir, zoneId)
    val mapsBaseDir = zoneDir / "maps"
    val locationDir = zoneDir / "locations"
    
    os.makeDir.all(mapsBaseDir)
    os.makeDir.all(locationDir)

    val groups = Seq(
      ("00H06H", 0 to 6),
      ("07H12H", 7 to 12),
      ("13H18H", 13 to 18),
      ("19H24H", 19 to 24)
    )

    val futures = scala.collection.mutable.ListBuffer[Future[Unit]]()
    val allData = scala.collection.mutable.Map[Int, IndexedSeq[IndexedSeq[AromeData]]]()

    groups.foreach { case (groupName, hours) =>
      logger.info(s"Processing group $groupName (hours ${hours.head}-${hours.last})...")

      val sp1File = gribDir / s"SP1_${groupName}.grib2"
      val sp2File = gribDir / s"SP2_${groupName}.grib2"
      val sp3File = gribDir / s"SP3_${groupName}.grib2"
      val windsDir = gribDir / "winds"

      if (!os.exists(sp1File) || !os.exists(sp2File) || !os.exists(sp3File)) {
        logger.warn(s"Missing files for group $groupName")
        return
      }

      hours.foreach { hour =>
        val hourOffsetInGroup = hour - hours.head

        val future = Future {
          try {
            logger.info(s"  Processing hour $hour...")

            val data = AromeGrib.fromGroupFiles(
              sp1File = sp1File,
              sp2File = sp2File,
              sp3File = sp3File,
              windsDir = windsDir,
              hourOffset = hourOffsetInGroup,
              zone = setting.zone
            )

            synchronized { allData(hour) = data }

            val meteoData: IndexedSeq[IndexedSeq[MeteoData]] = data.map { row =>
              row.map { aromeData =>
                new AromeMeteoDataAdapter(aromeData, initTime.plusHours(hour))
              }
            }
            
            val hourMapsDir = mapsBaseDir / f"${hour}%02d"
            os.makeDir.all(hourMapsDir)
            
            logger.debug(s"    Generating PNG...")
            org.soaringmeteo.out.Raster.writeAllPngFiles(
              setting.zone.longitudes.size,
              setting.zone.latitudes.size,
              hourMapsDir,
              hour,
              meteoData
            )
            
            logger.debug(s"    Generating MVT...")
            org.soaringmeteo.out.VectorTiles.writeAllVectorTiles(
              AromeVectorTilesParameters(setting.zone),
              hourMapsDir,
              hour,
              meteoData
            )
            
            logger.debug(s"    Saving to database...")
            Await.result(Store.save(initTime, setting.name, hour, data), Duration.Inf)

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
    
    logger.info(s"Generating location JSON files for zone ${setting.name}...")
    AromeLocationJson.writeLocationForecasts(
      zone = setting.zone,
      initTime = initTime,
      allData = allData.toMap,
      outputDir = locationDir
    )
    
    logger.info(s"Zone ${setting.name} completed")
  }
}
