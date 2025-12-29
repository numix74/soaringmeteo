package org.soaringmeteo.out

import geotrellis.raster._
import geotrellis.raster.io.geotiff.GeoTiff
import geotrellis.raster.render.{ColorMap, ColorRamp, Png, RGB, RGBA}
import geotrellis.raster.render.png.{PngColorEncoding, RgbPngEncoding, RgbaPngEncoding}
import org.slf4j.LoggerFactory
import org.soaringmeteo.UnifiedModelData
import squants.thermal.Celsius

import scala.annotation.tailrec

/**
 * PNG raster generation for unified backend.
 *
 * Adapted from backend/common to work with UnifiedModelData instead of MeteoData.
 * Generates PNG maps for all meteorological layers.
 */
trait Raster {
  def toPng(width: Int, height: Int, unifiedData: IndexedSeq[IndexedSeq[UnifiedModelData]]): Png
  def path: String
}

object Raster {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Write all PNG files for a given hour offset.
   *
   * @param width Grid width
   * @param height Grid height
   * @param targetDir Output directory (hour-specific)
   * @param hourOffset Hour offset from initialization
   * @param unifiedData Grid of unified model data
   */
  def writeAllPngFiles(
    width: Int,
    height: Int,
    targetDir: os.Path,
    hourOffset: Int,
    unifiedData: IndexedSeq[IndexedSeq[UnifiedModelData]]
  ): Unit = {
    logger.info(s"Generating PNG rasters for hour $hourOffset in $targetDir")
    os.makeDir.all(targetDir)

    unifiedRasters.foreach { raster =>
      val pngPath = targetDir / s"${raster.path}.png"
      try {
        val png = raster.toPng(width, height, unifiedData)
        os.write.over(pngPath, png.bytes)
        logger.debug(s"  Generated ${raster.path}.png")
      } catch {
        case e: Exception =>
          logger.error(s"  Failed to generate ${raster.path}.png", e)
      }
    }
  }

  /**
   * All rasters to generate for unified backend.
   */
  val unifiedRasters: List[Raster] = List(
    Raster(
      "xc-flying-potential",
      intData(_.xcFlyingPotential),
      ColorMap(
        10  -> 0x333333,
        20  -> 0x990099,
        30  -> 0xff0000,
        40  -> 0xff9900,
        50  -> 0xffcc00,
        60  -> 0xffff00,
        70  -> 0x66ff00,
        80  -> 0x00ffff,
        90  -> 0x99ffff,
        100 -> 0xffffff
      ),
      RgbEncoding
    ),
    Raster(
      "boundary-layer-depth",
      doubleData(d => d.boundaryLayerDepth.toMeters),  // Keep .toMeters for Length → Double
      ColorMap(
        250  -> 0x333333,
        500  -> 0x990099,
        750  -> 0xff0000,
        1000 -> 0xff9900,
        1250 -> 0xffcc00,
        1500 -> 0xffff00,
        1750 -> 0x66ff00,
        2000 -> 0x00ffff,
        2250 -> 0x99ffff,
        2500 -> 0xffffff
      ).withFallbackColor(0xffffff),
      RgbEncoding
    ),
    Raster(
      "thermal-velocity",
      doubleData(d => d.thermalVelocity.toMetersPerSecond),
      ColorMap(
        0.25 -> 0x333333,
        0.50 -> 0x990099,
        0.75 -> 0xff0000,
        1.00 -> 0xff9900,
        1.25 -> 0xffcc00,
        1.50 -> 0xffff00,
        1.75 -> 0x66ff00,
        2.00 -> 0x00ffff,
        2.50 -> 0x99ffff,
        3.00 -> 0xffffff
      ).withFallbackColor(0xffffff),
      RgbEncoding
    ),
    Raster(
      "soaring-layer-depth",
      doubleData(d => d.soaringLayerDepth.toMeters),
      ColorMap(
        250  -> 0x333333,
        500  -> 0x990099,
        750  -> 0xff0000,
        1000 -> 0xff9900,
        1250 -> 0xffcc00,
        1500 -> 0xffff00,
        1750 -> 0x66ff00,
        2000 -> 0x00ffff,
        2250 -> 0x99ffff,
        2500 -> 0xffffff
      ).withFallbackColor(0xffffff),
      RgbEncoding
    ),
    Raster(
      "clouds-rain",
      doubleData(d => {
        val rain = d.totalRain.toMillimeters
        if (rain >= 0.2) {
          rain + 100
        } else {
          d.totalCloudCover.toDouble
        }
      }),
      ColorMap(
        5.0    -> 0xffffff00,
        20.0   -> 0xffffffff,
        40.0   -> 0xbdbdbdff,
        60.0   -> 0x888888ff,
        80.0   -> 0x4d4d4dff,
        100.2  -> 0x111111ff,
        101.0  -> 0x9df8f6ff,
        102.0  -> 0x0000ffff,
        104.0  -> 0x2a933bff,
        106.0  -> 0x49ff36ff,
        110.0  -> 0xfcff2dff,
        120.0  -> 0xfaca1eff,
        130.0  -> 0xf87c00ff,
        150.0  -> 0xf70c00ff,
        200.0  -> 0xac00dbff
      ).withFallbackColor(0xac00dbff),
      RgbaEncoding
    ),
    Raster(
      "temperature-2m",
      doubleData(d => d.surfaceTemperature.toCelsiusScale),
      ColorMap(
        -20.0 -> 0x00008bff,
        -10.0 -> 0x4169e1ff,
        0.0 -> 0x87cebaff,
        10.0 -> 0xffffe0ff,
        20.0 -> 0xffd700ff,
        30.0 -> 0xff8c00ff,
        40.0 -> 0xdc143cff
      ),
      RgbaEncoding
    ),
    Raster(
      "dew-point-2m",
      doubleData(d => d.surfaceDewPoint.toCelsiusScale),
      ColorMap(
        -20.0 -> 0x8b008bff,
        -10.0 -> 0x9370dbff,
        0.0 -> 0xb0c4deff,
        10.0 -> 0x90ee90ff,
        20.0 -> 0x228b22ff,
        30.0 -> 0x006400ff
      ),
      RgbaEncoding
    )
  )

  /**
   * Create a raster with specified parameters.
   */
  def apply[D](
    pathValue: String,
    dataExtractor: DataExtractor[D],
    colorMap: ColorMap,
    pngEncoding: PngEncoding
  ): Raster = new Raster {
    override val path: String = pathValue

    override def toPng(width: Int, height: Int, unifiedData: IndexedSeq[IndexedSeq[UnifiedModelData]]): Png = {
      val arrayData = unifiedData.flatten.map(dataExtractor.extract)
      val tile = dataExtractor.makeTile(arrayData, width, height)
      pngEncoding.encode(tile, colorMap)
    }

    override def toString: String = path
  }

  /**
   * Data extractor trait for converting UnifiedModelData to tile values.
   */
  trait DataExtractor[D] {
    type Data = D
    def extract(unifiedData: UnifiedModelData): Data
    def makeTile(arrayData: Seq[Data], width: Int, height: Int): Tile
  }

  /**
   * Integer data extractor.
   */
  def intData(f: UnifiedModelData => Int): DataExtractor[Int] = new DataExtractor[Int] {
    override def extract(unifiedData: UnifiedModelData): Int = f(unifiedData)
    override def makeTile(arrayData: Seq[Int], width: Int, height: Int): Tile =
      IntArrayTile(arrayData.toArray, width, height)
  }

  /**
   * Double data extractor.
   */
  def doubleData(f: UnifiedModelData => Double): DataExtractor[Double] = new DataExtractor[Double] {
    override def extract(unifiedData: UnifiedModelData): Double = f(unifiedData)
    override def makeTile(arrayData: Seq[Double], width: Int, height: Int): Tile =
      DoubleArrayTile(arrayData.toArray, width, height)
  }

  /**
   * PNG encoding strategies.
   */
  sealed trait PngEncoding {
    def encode(tile: Tile, colorMap: ColorMap): Png
    def pngColorEncoding: PngColorEncoding
  }

  case object RgbEncoding extends PngEncoding {
    override val pngColorEncoding: PngColorEncoding = RgbPngEncoding
    override def encode(tile: Tile, colorMap: ColorMap): Png =
      colorMap.render(tile).renderPng(pngColorEncoding)
  }

  case object RgbaEncoding extends PngEncoding {
    override val pngColorEncoding: PngColorEncoding = RgbaPngEncoding
    override def encode(tile: Tile, colorMap: ColorMap): Png =
      colorMap.render(tile).renderPng(pngColorEncoding)
  }
}
