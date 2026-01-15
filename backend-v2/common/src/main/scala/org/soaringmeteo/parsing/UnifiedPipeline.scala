package org.soaringmeteo.parsing

import org.slf4j.LoggerFactory
import org.soaringmeteo.{Extent, GeographicZone, OutputPaths, Point, UnifiedModelData, out}

import java.time.OffsetDateTime
import scala.util.{Failure, Success, Try}

/**
 * Unified processing pipeline for all weather models.
 *
 * This pipeline:
 * 1. Reads GRIB files using model-specific FileHandler
 * 2. Parses data using generic GribParser
 * 3. Performs temporal interpolation to generate hourly forecasts
 * 4. Converts RawData to UnifiedModelData with derived calculations
 * 5. Generates output assets (rasters, tiles, JSON)
 *
 * Example usage:
 * {{{
 *   val zone = GeographicZone.Pyrenees
 *   val pipeline = UnifiedPipeline(
 *     modelSpec = ModelRegistry.GFS,
 *     fileHandler = GfsFileHandler("/path/to/grib"),
 *     longitudes = zone.longitudes,
 *     latitudes = zone.latitudes,
 *     extent = zone.extent,
 *     outputDirectory = os.Path("/output")
 *   )
 *
 *   pipeline.process(initTime = OffsetDateTime.parse("2025-12-28T00:00Z"))
 * }}}
 */
case class UnifiedPipeline(
  modelSpec: ModelRegistry.ModelSpec,
  fileHandler: handlers.FileHandler,
  longitudes: IndexedSeq[Double],
  latitudes: IndexedSeq[Double],
  extent: Extent,
  outputDirectory: os.Path,
  zoneId: String,  // Zone identifier (e.g., "pays-basque", "pyrenees")
  h2DbUrl: String,  // H2 database URL for storing grids
  zone: GeographicZone  // Full zone object for metadata generation
) {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Process a complete forecast run.
   *
   * @param initTime Forecast initialization time
   * @return Success/Failure with processing summary
   */
  def process(initTime: OffsetDateTime): Try[ProcessingSummary] = {
    logger.info(s"Starting ${modelSpec.name} pipeline")
    logger.info(s"  Init time: $initTime")
    logger.info(s"  Grid: ${longitudes.size} x ${latitudes.size} points")
    logger.info(s"  Extent: $extent")
    logger.info(s"  Output: $outputDirectory")

    Try {
      // Initialize H2 Store for grid storage
      out.Store.initialize(h2DbUrl)
      import scala.concurrent.Await
      import scala.concurrent.duration._
      Await.result(out.Store.ensureSchemaExists(), 10.seconds)
      logger.info(s"  H2 Store initialized: $h2DbUrl")

      // Get native hour offsets available
      val nativeHours = fileHandler.availableHourOffsets(initTime)
      logger.info(s"  Native hours: ${nativeHours.size} timesteps (${nativeHours.head} to ${nativeHours.last})")

      // Generate all hours (first native hour to maxHourOffset, every hour)
      // Note: We start at the first available native hour to avoid extrapolation before data starts
      val firstAvailableHour = nativeHours.headOption.getOrElse(0)
      val lastHour = Math.min(modelSpec.maxHourOffset, nativeHours.lastOption.getOrElse(modelSpec.maxHourOffset))
      val allHours = firstAvailableHour to lastHour
      logger.info(s"  Target hours: ${allHours.size} (${firstAvailableHour} to ${lastHour})")
      if (firstAvailableHour > 0) {
        logger.warn(s"  WARNING: Starting at hour $firstAvailableHour (no data available before this)")
      }

      // Cache for native grids to avoid re-parsing
      val nativeGridCache = scala.collection.mutable.Map.empty[Int, IndexedSeq[IndexedSeq[RawData]]]

      // Track processed hours
      val processedHours = scala.collection.mutable.Set.empty[Int]

      var processedCount = 0
      var interpolatedCount = 0
      var failedCount = 0

      allHours.foreach { hourOffset =>
        try {
          logger.info(s"Processing hour $hourOffset...")

          // Get or interpolate grid
          val (rawDataGrid, isNative) = if (nativeHours.contains(hourOffset)) {
            // Native hour - parse directly (no caching for AROME-like models)
            logger.debug(s"  Hour $hourOffset: parsing native file")
            val grid = parseNativeHour(hourOffset, initTime)
            (grid, true)
          } else {
            // Interpolated hour - find surrounding native hours
            logger.debug(s"  Hour $hourOffset: interpolating")
            val (beforeHour, afterHour) = findSurroundingHours(hourOffset, nativeHours)
            logger.debug(s"    Between hours $beforeHour and $afterHour")

            // Get or parse grids for surrounding hours
            val gridBefore = nativeGridCache.getOrElseUpdate(beforeHour, parseNativeHour(beforeHour, initTime))
            val gridAfter = nativeGridCache.getOrElseUpdate(afterHour, parseNativeHour(afterHour, initTime))

            // Interpolate
            val fraction = (hourOffset - beforeHour).toDouble / (afterHour - beforeHour).toDouble
            logger.debug(s"    Interpolation fraction: $fraction")
            val interpolated = TemporalInterpolator.interpolateGrids(gridBefore, gridAfter, fraction)
            interpolatedCount += 1
            (interpolated, false)
          }

          // Convert RawData to UnifiedModelData
          val validTime = initTime.plusHours(hourOffset.toLong)
          val unifiedDataGrid = convertToUnifiedModelData(rawDataGrid, validTime)

          // Generate outputs (PNG, MVT)
          generateOutputs(unifiedDataGrid, hourOffset, initTime)

          // Save grid to H2 database (instead of keeping in memory)
          Await.result(
            out.Store.save(initTime, modelSpec.id, zoneId, hourOffset, unifiedDataGrid),
            30.seconds
          )
          processedHours += hourOffset

          processedCount += 1
          logger.info(s"  Hour $hourOffset: completed")

          // AGGRESSIVE cache cleanup to prevent OOM with high-resolution grids
          if (isNative) {
            // For native hours (AROME): no interpolation needed, clear cache immediately
            // This prevents accumulating large grids in memory
            nativeGridCache.clear()
            logger.debug(s"  Cache cleared after native hour $hourOffset (aggressive mode)")
          } else {
            // For interpolated hours: keep only the 2 most recent entries (before/after pair)
            if (nativeGridCache.size > 2) {
              val keysToRemove = nativeGridCache.keys.toSeq.sorted.dropRight(2)
              keysToRemove.foreach { key =>
                nativeGridCache.remove(key)
                logger.debug(s"  Removed cached hour $key (keeping max 2 entries)")
              }
            }
          }

        } catch {
          case e: Exception =>
            logger.error(s"  Hour $hourOffset: FAILED", e)
            failedCount += 1
        }
      }

      logger.info(s"Pipeline complete:")
      logger.info(s"  Processed: $processedCount hours")
      logger.info(s"  Interpolated: $interpolatedCount hours")
      logger.info(s"  Failed: $failedCount hours")

      // Generate JSON location forecasts and metadata
      if (processedCount > 0) {
        generateJsonData(processedHours.toSet, initTime, zone)
      }

      // Close H2 database
      out.Store.close()

      ProcessingSummary(
        modelId = modelSpec.id,
        initTime = initTime,
        hoursProcessed = processedCount,
        hoursInterpolated = interpolatedCount,
        hoursFailed = failedCount
      )
    }
  }

  /**
   * Parse a native hour from GRIB file.
   */
  private def parseNativeHour(
    hourOffset: Int,
    initTime: OffsetDateTime
  ): IndexedSeq[IndexedSeq[RawData]] = {
    val parseableData = fileHandler.prepareData(hourOffset, initTime)
    GribParser.parseGrid(parseableData, longitudes, latitudes)
  }

  /**
   * Find the two native hours surrounding a target hour.
   *
   * @param targetHour Hour to interpolate
   * @param nativeHours Available native hours
   * @return (beforeHour, afterHour)
   */
  private def findSurroundingHours(
    targetHour: Int,
    nativeHours: Seq[Int]
  ): (Int, Int) = {
    val before = nativeHours.filter(_ <= targetHour).lastOption.getOrElse(nativeHours.head)
    val after = nativeHours.filter(_ >= targetHour).headOption.getOrElse(nativeHours.last)
    (before, after)
  }

  /**
   * Convert RawData grid to UnifiedModelData grid.
   */
  private def convertToUnifiedModelData(
    rawDataGrid: IndexedSeq[IndexedSeq[RawData]],
    validTime: OffsetDateTime
  ): IndexedSeq[IndexedSeq[UnifiedModelData]] = {
    rawDataGrid.map { row =>
      row.map { rawData =>
        UnifiedModelData.fromRawData(
          time = validTime,
          surface = rawData.surface,
          boundary = rawData.boundary,
          atmosphere = rawData.atmosphere,
          profiles = rawData.profiles
        )
      }
    }
  }

  /**
   * Generate output assets (rasters, tiles, JSON).
   *
   * Creates:
   * - PNG raster maps for each layer
   * - Vector tiles (MVT)
   * - JSON metadata (generated separately after all hours are processed)
   */
  private def generateOutputs(
    unifiedDataGrid: IndexedSeq[IndexedSeq[UnifiedModelData]],
    hourOffset: Int,
    initTime: OffsetDateTime
  ): Unit = {
    // Use OutputPaths for consistent directory structure
    // outputDirectory is expected to be version-specific (e.g., /output-unified/7)
    val formatVersion = 7  // TODO: Get from config

    // Get base directory (remove format version)
    val baseDir = outputDirectory / os.up

    // Get zone directory (layer directories will be created by Raster.writeAllPngFiles)
    val zoneDir = OutputPaths.zoneOutputDir(
      baseDir,
      formatVersion,
      modelSpec.id,
      initTime,
      zoneId
    )
    os.makeDir.all(zoneDir)

    // Grid dimensions
    val width = unifiedDataGrid.size
    val height = if (width > 0) unifiedDataGrid.head.size else 0

    // Generate PNG rasters (frontend-compatible structure: layer/hourOffset.png)
    logger.debug(s"    Generating PNG rasters...")
    out.Raster.writeAllPngFiles(
      width,
      height,
      zoneDir,
      hourOffset,
      unifiedDataGrid
    )

    // Generate vector tiles (MVT)
    logger.debug(s"    Generating vector tiles...")
    generateVectorTiles(
      unifiedDataGrid,
      hourOffset,
      initTime,
      baseDir,
      formatVersion
    )

    logger.debug(s"    Output directory: $zoneDir")
  }

  /**
   * Generate JSON location forecasts and metadata.
   */
  private def generateJsonData(
    processedHours: Set[Int],
    initTime: OffsetDateTime,
    zone: GeographicZone
  ): Unit = {
    import geotrellis.proj4.{LatLng, WebMercator}
    import geotrellis.vector.Extent
    import geotrellis.vector.reproject.Reproject
    import java.time.Period
    import java.time.format.DateTimeFormatter
    import scala.concurrent.Await
    import scala.concurrent.duration._

    logger.info("Generating JSON location forecasts...")

    val formatVersion = 7  // TODO: Get from config
    val baseDir = outputDirectory / os.up

    // Grid dimensions
    val width = longitudes.size
    val height = latitudes.size

    if (width == 0 || height == 0) {
      logger.warn("Empty grid, skipping JSON generation")
      return
    }

    // Zone output directory
    val zoneOutputDir = OutputPaths.zoneOutputDir(
      baseDir,
      formatVersion,
      modelSpec.id,
      initTime,
      zoneId
    )

    // Write location forecasts (clustered JSON files)
    // Data is retrieved from H2 Store instead of memory
    out.JsonData.writeForecastsByLocation(
      zoneName = zoneId,
      width = width,
      height = height,
      targetDir = zoneOutputDir
    ) { (x, y) =>
      // Retrieve data for this location from H2 Store
      Await.result(
        out.Store.getDataForLocation(initTime, modelSpec.id, zoneId, x, y),
        60.seconds
      )
    }

    logger.info("Generating forecast metadata...")

    // Generate metadata.json
    val initDateString = OutputPaths.formatInitTime(initTime)  // Format: 2026-01-06T18
    val latestHourOffset = processedHours.max

    // Convert extent to Web Mercator for vector tiles
    val (xMinWM, yMinWM) = Reproject((extent.xMin, extent.yMin), LatLng, WebMercator)
    val (xMaxWM, yMaxWM) = Reproject((extent.xMax, extent.yMax), LatLng, WebMercator)
    val bufferX = (xMaxWM - xMinWM) * 0.01
    val bufferY = (yMaxWM - yMinWM) * 0.01
    val extentWM = Extent(
      xMinWM - bufferX,
      yMinWM - bufferY,
      xMaxWM + bufferX,
      yMaxWM + bufferY
    )

    // Create grid coordinates for VectorTiles.Parameters
    val gridCoordinates: IndexedSeq[IndexedSeq[Point]] =
      for (lon <- longitudes) yield
        for (lat <- latitudes) yield {
          Point(lat, lon)
        }

    // Create VectorTiles parameters (calculates zoomLevels and minViewZoom automatically)
    val vectorTilesParams = out.VectorTiles.Parameters(
      extent = extentWM,
      maxViewZoom = 8,  // Standard for most maps
      width = width,
      height = height,
      gridCoordinates = gridCoordinates
    )

    // Create zone metadata
    val zoneMetadata = out.ForecastMetadata.Zone(
      id = zoneId,
      label = zone.label,  // Get from zone config
      raster = out.ForecastMetadata.Raster(
        projection = "EPSG:4326",
        resolution = BigDecimal((extent.xMax - extent.xMin) / (width - 1)),
        extent = Extent(extent.xMin, extent.yMin, extent.xMax, extent.yMax)
      ),
      vectorTiles = out.ForecastMetadata.VectorTiles(vectorTilesParams, zone.vectorTileSize)
    )

    // Model target directory (e.g., /output/7/gfs)
    val modelDir = OutputPaths.modelOutputDir(baseDir, formatVersion, modelSpec.id)

    // Data path for frontend (relative path like "7/gfs/20251229_06")
    val dataPath = s"$formatVersion/${modelSpec.id}/$initDateString"

    // Write metadata with history retention
    out.ForecastMetadata.overwriteLatestForecastMetadata(
      targetDir = modelDir,
      history = Period.ofDays(2),  // Keep 2 days of history
      initDateString = dataPath,
      initDateTime = initTime,
      maybeFirstTimeStep = None,  // GFS starts at init time
      latestHourOffset = latestHourOffset,
      zones = Seq(zoneMetadata)
    )

    logger.info("JSON generation complete")
  }

  /**
   * Generate vector tiles for wind layers.
   */
  private def generateVectorTiles(
    unifiedDataGrid: IndexedSeq[IndexedSeq[UnifiedModelData]],
    hourOffset: Int,
    initTime: OffsetDateTime,
    baseDir: os.Path,
    formatVersion: Int
  ): Unit = {
    import geotrellis.proj4.{LatLng, WebMercator}
    import geotrellis.vector.Extent
    import geotrellis.vector.reproject.Reproject

    // Grid dimensions
    val width = unifiedDataGrid.size
    val height = if (width > 0) unifiedDataGrid.head.size else 0

    if (width == 0 || height == 0) {
      logger.warn("Empty grid, skipping vector tiles generation")
      return
    }

    // Convert extent to Web Mercator (EPSG:3857)
    val (xMinWM, yMinWM) = Reproject((extent.xMin, extent.yMin), LatLng, WebMercator)
    val (xMaxWM, yMaxWM) = Reproject((extent.xMax, extent.yMax), LatLng, WebMercator)

    // Add a small buffer to ensure all reprojected grid points are within the extent
    // WebMercator projection can cause slight distortions, so we add 1% margin
    val bufferX = (xMaxWM - xMinWM) * 0.01
    val bufferY = (yMaxWM - yMinWM) * 0.01
    val extentWM = Extent(
      xMinWM - bufferX,
      yMinWM - bufferY,
      xMaxWM + bufferX,
      yMaxWM + bufferY
    )

    // Create grid coordinates (EPSG:4326)
    val lonStep = (extent.xMax - extent.xMin) / (width - 1)
    val latStep = (extent.yMax - extent.yMin) / (height - 1)
    val gridCoordinates: IndexedSeq[IndexedSeq[Point]] =
      (0 until width).map { x =>
        (0 until height).map { y =>
          val lon = extent.xMin + x * lonStep
          val lat = extent.yMax - y * latStep  // Latitudes go from top to bottom
          Point(lat, lon)
        }
      }

    // Vector tile parameters
    val parameters = out.VectorTiles.Parameters(
      extent = extentWM,
      maxViewZoom = 8,  // TODO: Get from config
      width = width,
      height = height,
      gridCoordinates = gridCoordinates
    )

    // Output directory for this zone
    val zoneOutputDir = OutputPaths.zoneOutputDir(
      baseDir,
      formatVersion,
      modelSpec.id,
      initTime,
      zoneId
    )

    // Generate all vector tile layers
    out.VectorTiles.writeAllVectorTiles(
      parameters,
      zoneOutputDir,
      hourOffset,
      unifiedDataGrid
    )
  }
}

/**
 * Summary of pipeline processing.
 */
case class ProcessingSummary(
  modelId: String,
  initTime: OffsetDateTime,
  hoursProcessed: Int,
  hoursInterpolated: Int,
  hoursFailed: Int
) {
  def totalHours: Int = hoursProcessed + hoursFailed
  def successRate: Double = if (totalHours > 0) hoursProcessed.toDouble / totalHours else 0.0
}
