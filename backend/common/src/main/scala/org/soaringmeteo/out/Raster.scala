package org.soaringmeteo.out

import geotrellis.raster.render.png.{GreyaPngEncoding, PngColorEncoding, RgbPngEncoding, RgbaPngEncoding}
import geotrellis.raster.{DoubleArrayTile, IntArrayTile, Tile}
import geotrellis.raster.render.{ColorMap, LessThan, Png}
import org.slf4j.LoggerFactory
import org.soaringmeteo.{Forecast, MeteoData}

trait Raster {
  def toPng(width: Int, height: Int, meteoData: IndexedSeq[IndexedSeq[MeteoData]]): Png
  def path: String
}

object Raster {

  private val logger = LoggerFactory.getLogger(getClass)

  def apply(path: String, extractor: DataExtractor, colorMap: ColorMap, pngColorEncoding: PngColorEncoding): Raster = {
    val pathArgument = path
    new Raster {
      def path: String = pathArgument
      def toPng(width: Int, height: Int, meteoData: IndexedSeq[IndexedSeq[MeteoData]]): Png = {
        val pixels =
          for {
            y <- 0 until height
            x <- 0 until width
          } yield {
            extractor.extract(meteoData(x)(y))
          }
        val tile = extractor.makeTile(pixels, width, height)
        colorMap
          .withBoundaryType(LessThan)
          .render(tile)
          .renderPng(pngColorEncoding)
      }
    }
  }

  def writeAllPngFiles(
    width: Int,
    height: Int,
    targetDir: os.Path,
    hourOffset: Int,
    meteoData: IndexedSeq[IndexedSeq[MeteoData]]
  ): Unit = {
    logger.debug(s"Generating images for hour offset n°${hourOffset}")
    for (raster <- gfsRasters) {
      val fileName = s"${hourOffset}.png"
      val path = targetDir / raster.path / fileName
      logger.trace(s"Generating image ${path}")
      os.write.over(
        path,
        raster.toPng(width, height, meteoData).bytes,
        createFolders = true
      )
    }
  }

  val gfsRasters: List[Raster] = List(
    Raster(
      "xc-potential",
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
      RgbPngEncoding
    ),
    Raster(
      "boundary-layer-depth",
      doubleData(_.boundaryLayerDepth),
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
      RgbPngEncoding
    ),
    Raster(
      "thermal-velocity",
      doubleData(_.thermalVelocity.toMetersPerSecond),
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
      RgbPngEncoding
    ),
    Raster(
      "soaring-layer-depth",
      doubleData(_.soaringLayerDepth),
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
      RgbPngEncoding
    ),
    Raster(
      "cumulus-depth",
      intData { meteoData =>
        meteoData.convectiveClouds.fold(0) { clouds =>
          (clouds.top - clouds.bottom).toMeters.round.toInt
        }
      },
      ColorMap(
        50   -> 0xffffff00,
        400  -> 0xffffff7f,
        800  -> 0xffffffff,
        1500 -> 0xffff00ff,
        3000 -> 0xff0000ff
      ).withFallbackColor(0xff0000ff),
      RgbaPngEncoding
    ),
    Raster(
      "clouds-rain",
      doubleData { meteoData =>
        val rain = meteoData.totalRain
        if (rain >= 0.2) {
          rain + 100
        } else {
          meteoData.totalCloudCover.toDouble
        }
      },
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
      RgbaPngEncoding
    ),
    Raster(
      "cape",
      doubleData { meteoData =>
        meteoData.cape.fold(0.0)(_.toGrays)
      },
      ColorMap(
        0    -> 0xf0f0ff,
        500  -> 0x96c8ff,
        1000 -> 0x64ff96,
        1500 -> 0xffff64,
        2000 -> 0xff9632,
        3000 -> 0xc83232
      ).withFallbackColor(0xc83232),
      RgbPngEncoding
    )
  )

  trait DataExtractor {
    type Data
    def extract(meteoData: MeteoData): Data
    def makeTile(arrayData: Seq[Data], width: Int, height: Int): Tile
  }

  def intData(extract: MeteoData => Int): DataExtractor = {
    val extractArgument = extract
    new DataExtractor {
      type Data = Int
      def extract(meteoData: MeteoData): Int = extractArgument(meteoData)
      def makeTile(arrayData: Seq[Int], width: Int, height: Int): Tile =
        IntArrayTile(arrayData.toArray, width, height)
    }
  }

  def doubleData(extract: MeteoData => Double): DataExtractor = {
    val extractArgument = extract
    new DataExtractor {
      type Data = Double
      def extract(meteoData: MeteoData): Double = extractArgument(meteoData)
      def makeTile(arrayData: Seq[Double], width: Int, height: Int): Tile =
        DoubleArrayTile(arrayData.toArray, width, height)
    }
  }
}
