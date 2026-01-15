package org.soaringmeteo

import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory
import org.soaringmeteo.parsing._
import org.soaringmeteo.parsing.handlers._

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Success, Try}

/**
 * Main entry point for unified backend v2.
 *
 * Usage:
 * {{{
 *   # Run with default config (unified.conf)
 *   sbt "app/run"
 *
 *   # Run with specific model
 *   sbt "app/run --model=arome"
 *
 *   # Run with specific init time
 *   sbt "app/run --init-time=2025-12-28T00:00Z"
 *
 *   # Run with dev config
 *   sbt "app/run --env=dev"
 * }}}
 */
object Main {

  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    logger.info("=" * 80)
    logger.info("SoaringMeteo Backend v2 - Unified Pipeline")
    logger.info("=" * 80)

    val result = for {
      cliArgs <- parseArguments(args)
      config <- loadConfig(cliArgs.env)
      _ <- run(config, cliArgs)
    } yield ()

    result match {
      case Success(_) =>
        logger.info("=" * 80)
        logger.info("Pipeline completed successfully")
        logger.info("=" * 80)
        System.exit(0)

      case Failure(ex) =>
        logger.error("=" * 80)
        logger.error("Pipeline failed with error:", ex)
        logger.error("=" * 80)
        System.exit(1)
    }
  }

  /**
   * Parse command-line arguments.
   */
  private def parseArguments(args: Array[String]): Try[CliArguments] = Try {
    val argMap = args.flatMap { arg =>
      if (arg.startsWith("--")) {
        val parts = arg.substring(2).split("=", 2)
        if (parts.length == 2) Some(parts(0) -> parts(1))
        else None
      } else None
    }.toMap

    CliArguments(
      model = argMap.get("model"),
      initTime = argMap.get("init-time"),
      env = argMap.getOrElse("env", "default")
    )
  }

  /**
   * Load configuration with environment-specific overrides.
   */
  private def loadConfig(env: String): Try[Config] = Try {
    val base = ConfigFactory.load("unified")

    val config = env match {
      case "dev" =>
        logger.info("Loading development configuration")
        ConfigFactory.load("unified").getConfig("dev").withFallback(base)

      case "prod" =>
        logger.info("Loading production configuration")
        ConfigFactory.load("unified").getConfig("prod").withFallback(base)

      case _ =>
        logger.info("Loading default configuration")
        base
    }

    logger.info(s"Configuration loaded successfully")
    logger.debug(s"Config: ${config.getConfig("unified").root().render()}")
    config
  }

  /**
   * Main processing logic.
   */
  private def run(config: Config, cliArgs: CliArguments): Try[Unit] = Try {
    val unifiedConfig = config.getConfig("unified")

    // Load geographic zone
    val zone = loadZone(unifiedConfig)
    logger.info(s"Geographic zone: $zone")
    logger.info(s"  Zone details:")
    logger.info(s"    ID: ${zone.id}")
    logger.info(s"    Label: ${zone.label}")
    logger.info(s"    Dimensions: ${zone.width} x ${zone.height} points")
    logger.info(s"    Extent: ${zone.extent}")
    logger.info(s"    Longitudes: ${zone.longitudes.head} to ${zone.longitudes.last}")
    logger.info(s"    Latitudes: ${zone.latitudes.head} to ${zone.latitudes.last}")
    if (zone.longitudes.size > 1) {
      logger.info(s"    Lon step: ${zone.longitudes(1) - zone.longitudes(0)} degrees")
    }
    if (zone.latitudes.size > 1) {
      logger.info(s"    Lat step: ${zone.latitudes(1) - zone.latitudes(0)} degrees")
    }
    logger.info(s"    Approximate resolution: ${zone.approximateResolutionKm} km")

    // Determine which model to use
    val modelId = selectModel(unifiedConfig, cliArgs.model)
    logger.info(s"Selected model: $modelId")

    val modelSpec = ModelRegistry.forModel(modelId)
    logger.info(s"Model spec: ${modelSpec.name}")
    logger.info(s"  Format: ${modelSpec.format}")
    logger.info(s"  Native timestep: ${modelSpec.nativeTimeStep}h")
    logger.info(s"  Max hour offset: ${modelSpec.maxHourOffset}h")

    // Create file handler
    val handler = createFileHandler(modelId, zone, unifiedConfig)
    logger.info(s"File handler created: ${handler.getClass.getSimpleName}")

    // Determine initialization time
    val initTime = parseInitTime(cliArgs.initTime)
    logger.info(s"Initialization time: $initTime")

    // Create output directory
    val outputBaseDir = os.Path(unifiedConfig.getString("output.base-directory"))
    val formatVersion = unifiedConfig.getInt("output.format-version")
    logger.info(s"Output directory: $outputBaseDir/$formatVersion")

    // H2 database URL
    val h2DbUrl = unifiedConfig.getString("h2db.url")
    logger.info(s"H2 database: $h2DbUrl")

    // Create and run pipeline
    val pipeline = UnifiedPipeline(
      modelSpec = modelSpec,
      fileHandler = handler,
      longitudes = zone.longitudes,
      latitudes = zone.latitudes,
      extent = zone.extent,
      outputDirectory = outputBaseDir / formatVersion.toString,
      zoneId = zone.id,
      h2DbUrl = h2DbUrl,
      zone = zone
    )

    logger.info("Starting pipeline processing...")
    val summary = pipeline.process(initTime).get

    // Log summary
    logger.info("Processing summary:")
    logger.info(s"  Model: ${summary.modelId}")
    logger.info(s"  Init time: ${summary.initTime}")
    logger.info(s"  Hours processed: ${summary.hoursProcessed}")
    logger.info(s"  Hours interpolated: ${summary.hoursInterpolated}")
    logger.info(s"  Hours failed: ${summary.hoursFailed}")
    logger.info(s"  Success rate: ${summary.successRate * 100}%.1f%%")
  }

  /**
   * Load geographic zone from config.
   * Priority: zone section > predefined regions
   */
  private def loadZone(config: Config): GeographicZone = {
    // Always try to load from zone section first
    if (config.hasPath("zone") &&
        config.getConfig("zone").hasPath("id") &&
        config.getConfig("zone").hasPath("step")) {
      // Load from config zone section
      val zone = GeographicZone.fromConfig(config.getConfig("zone"))
      logger.info(s"Loaded zone from config: $zone")
      zone
    } else if (config.hasPath("region")) {
      // Fallback to predefined region
      val region = config.getString("region")
      logger.info(s"Loading predefined region: '$region'")
      region.toLowerCase match {
        case "pyrenees" =>
          logger.info("Matched 'pyrenees' -> GeographicZone.Pyrenees")
          GeographicZone.Pyrenees
        case "pyrenees-gfs" =>
          logger.info("Matched 'pyrenees-gfs' -> GeographicZone.PyreneesGFS")
          GeographicZone.PyreneesGFS
        case "pays-basque" =>
          logger.info("Matched 'pays-basque' -> GeographicZone.PaysBasque")
          GeographicZone.PaysBasque
        case _ =>
          logger.warn(s"Unknown region: '$region', using Pyrenees")
          GeographicZone.Pyrenees
      }
    } else {
      logger.warn("No zone or region configured, using default Pyrenees")
      GeographicZone.Pyrenees
    }
  }

  /**
   * Select which model to use based on config and CLI args.
   */
  private def selectModel(config: Config, cliModel: Option[String]): String = {
    cliModel match {
      case Some(model) =>
        // CLI argument takes precedence
        logger.info(s"Using model from CLI argument: $model")
        model

      case None =>
        // Use priority order from config
        val sourcesConfig = config.getConfig("sources")
        val priorityOrder = config.getStringList("model-fusion.priority-order")

        import scala.jdk.CollectionConverters._
        val availableModel = priorityOrder.asScala.find { model =>
          sourcesConfig.hasPath(model) &&
          sourcesConfig.getConfig(model).getBoolean("enabled")
        }

        availableModel match {
          case Some(model) =>
            logger.info(s"Selected model from priority order: $model")
            model
          case None =>
            logger.warn("No model enabled in config, defaulting to GFS")
            "gfs"
        }
    }
  }

  /**
   * Create appropriate FileHandler for the model.
   */
  private def createFileHandler(modelId: String, zone: GeographicZone, config: Config): FileHandler = {
    val sourceConfig = config.getConfig(s"sources.$modelId")

    modelId.toLowerCase match {
      case "gfs" =>
        val gribDir = os.Path(sourceConfig.getString("grib-directory"))
        logger.debug(s"GFS GRIB directory: $gribDir")
        logger.debug(s"GFS zone: ${zone.id}")
        handlers.GfsFileHandler(gribDir, zone.id)

      case "arome" =>
        val gribDir = os.Path(sourceConfig.getString("grib-directory"))
        logger.debug(s"AROME GRIB directory: $gribDir")
        handlers.AromeFileHandler(gribDir)

      case "wrf" =>
        val netcdfDir = os.Path(sourceConfig.getString("netcdf-directory"))
        logger.debug(s"WRF NetCDF directory: $netcdfDir")
        // TODO: Implement WrfFileHandler
        throw new UnsupportedOperationException("WRF support not yet implemented")

      case _ =>
        throw new IllegalArgumentException(s"Unsupported model: $modelId")
    }
  }

  /**
   * Parse initialization time from CLI or use current time.
   */
  private def parseInitTime(cliInitTime: Option[String]): OffsetDateTime = {
    cliInitTime match {
      case Some(timeStr) =>
        OffsetDateTime.parse(timeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      case None =>
        // Use current time rounded to nearest hour
        val now = OffsetDateTime.now()
        now.withMinute(0).withSecond(0).withNano(0)
    }
  }
}

/**
 * CLI arguments.
 */
case class CliArguments(
  model: Option[String],
  initTime: Option[String],
  env: String
)
