package org.soaringmeteo.wrf

import cats.data.NonEmptyList
import org.slf4j.LoggerFactory
import org.soaringmeteo.InitDateString
import org.soaringmeteo.out.{ForecastMetadata, JsonData, OutputPaths, Raster, VectorTiles, deleteOldData, touchMarkerFile}

import java.time.OffsetDateTime

object DataPipeline {

  private val logger = LoggerFactory.getLogger(getClass)

  def run(
    inputFiles: NonEmptyList[os.Path],
    outputDir: os.Path,
    initDateTime: OffsetDateTime,
    firstTimeStep: OffsetDateTime,
  ): Unit = {
    val initDateString = InitDateString(initDateTime)

    // Use harmonized output paths
    val modelTargetDir = OutputPaths.modelOutputDir(outputDir, "wrf")
    val runTargetDir = OutputPaths.runOutputDir(outputDir, "wrf", initDateString)

    // Process all the input files (grids) one by one
    val results =
      for (inputFile <- inputFiles) yield {
        logger.info(s"Processing file ${inputFile}")
        val grid = Grid.find(inputFile)

        // Create zone directories
        val zoneOutputDir = OutputPaths.zoneOutputDir(outputDir, "wrf", initDateString, grid.outputPath)
        os.makeDir.all(zoneOutputDir / "maps")
        os.makeDir.all(zoneOutputDir / "location")

        val metadata = processFile(inputFile, grid, zoneOutputDir)
        (grid, metadata)
      }

    // Ultimately, write the forecast metadata for the run and delete old data
    val currentForecasts =
      overwriteLatestForecastMetadata(
        modelTargetDir,
        initDateString,
        initDateTime,
        firstTimeStep,
        results.toList
      )

    deleteOldData(modelTargetDir, currentForecasts)

    touchMarkerFile(outputDir)
  }

  private def processFile(inputFile: os.Path, grid: Grid, zoneOutputDir: os.Path): NetCdf.Metadata = {
    val result = NetCdf.read(inputFile)
    generateRasterImagesAndVectorTiles(zoneOutputDir, result)
    generateLocationForecasts(zoneOutputDir, grid.outputPath, result)
    result.metadata
  }

  private def generateRasterImagesAndVectorTiles(
    outputDir: os.Path,
    netCdfResult: NetCdf.Result,
  ): Unit = {
    logger.info(s"Generating raster images and vector tiles")
    val forecastsByHour = netCdfResult.forecastsByHour
    for ((forecasts, t) <- forecastsByHour.zipWithIndex) {
      Raster.writeAllPngFiles(netCdfResult.metadata.width, netCdfResult.metadata.height, outputDir, t, forecasts)
      VectorTiles.writeAllVectorTiles(netCdfResult.metadata.vectorTilesParameters, outputDir, t, forecasts)
    }
  }

  private def generateLocationForecasts(
    outputDir: os.Path,
    gridName: String,
    result: NetCdf.Result,
  ): Unit = {
    JsonData.writeForecastsByLocation(
      gridName,
      result.metadata.width,
      result.metadata.height,
      outputDir
    ) { (x, y) =>
      result.forecastsByHour.zipWithIndex
        .map { case (forecasts, timeStep) => (timeStep, forecasts(x)(y)) }
        .toMap
    }
  }

  /** @return the exposed forecast runs */
  private def overwriteLatestForecastMetadata(
    outputDir: os.Path,
    initDateString: String,
    initializationDate: OffsetDateTime,
    firstTimeStep: OffsetDateTime,
    results: Seq[(Grid, NetCdf.Metadata)]
  ): Seq[ForecastMetadata] = {
    logger.info(s"Writing forecast metadata in ${outputDir}")
    val latestHourOffset = results.head._2.latestHourOffset // Assume all the grids have the same time-steps
    val zones =
      for ((grid, metadata) <- results) yield {
        val (rasterResolution, rasterExtent) = metadata.raster
        ForecastMetadata.Zone(
          grid.outputPath,
          grid.label,
          ForecastMetadata.Raster(
            projection = "WRF",
            rasterResolution,
            rasterExtent
          ),
          ForecastMetadata.VectorTiles(metadata.vectorTilesParameters, grid.vectorTileSize)
        )
      }
    ForecastMetadata.overwriteLatestForecastMetadata(
      outputDir,
      Settings.forecastHistory,
      initDateString,
      initializationDate,
      Some(firstTimeStep),
      latestHourOffset,
      zones
    )
  }

}
