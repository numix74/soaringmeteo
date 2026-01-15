package org.soaringmeteo.out

import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.vector.{Extent, Point => GeotrellisPoint}
import geotrellis.vector.reproject.Reproject
import geotrellis.vectortile.{MVTFeature, MVTFeatures, StrictLayer, VectorTile, VInt64, VFloat}
import org.slf4j.LoggerFactory
import org.soaringmeteo.{UnifiedModelData, Point, Wind}

/**
 * Vector tile generation for unified backend.
 *
 * Adapted from backend/common to work with UnifiedModelData instead of MeteoData.
 * Generates MVT (Mapbox Vector Tiles) for wind layers at different altitudes.
 */
case class VectorTiles(
  path: String,
  feature: UnifiedModelData => Option[Wind],
  excluded: UnifiedModelData => Boolean = _ => false
)

object VectorTiles {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Parameters for generating the vector tiles.
   *
   * @param extent Bounding box of all the features. Coordinates must be in EPSG:3857 projection.
   * @param maxViewZoom The value of view zoom where all the points of the grids should be displayed
   * @param width Grid width
   * @param height Grid height
   * @param gridCoordinates Coordinates (EPSG:4326) of all the grid points
   */
  case class Parameters(
    extent: Extent,
    maxViewZoom: Int,
    width: Int,
    height: Int,
    gridCoordinates: IndexedSeq[IndexedSeq[Point]]
  ) {

    val (minViewZoom: Int, zoomLevels: Int) = {
      // Vector tiles partition the extent into multiple tiles. At the zoom level 0, there is
      // just one tile that covers the whole extent. At zoom level 1, there are 4 tiles, at
      // zoom level 2 there are 16 tiles, and so on.
      // By default, OpenLayers assumes that 1 tile covers an area of 512 px on the map. So, if the
      // projection of the extent is larger than 512 px, OpenLayers will try to "zoom" into the tiles
      // to find only the points that are visible in the current view.
      // We find that rendering at most 15 wind arrow per tile looks good, so we make sure that
      // our tiles don't contain more points than that. We down-sample the grid by removing every
      // other point from the previous zoom level.
      val threshold = 15
      var zoomLevelsValue = 1
      var maxPoints = math.max(width, height)
      while (maxPoints > threshold) {
        maxPoints = maxPoints / 2
        zoomLevelsValue = zoomLevelsValue + 1
      }
      if (maxPoints < 1) maxPoints = 1
      // The minimal zoom level of the tiles is always 0, but here we define the minimal
      // zoom level of the view. It means that there is no point in trying to show the
      // wind arrows when the view zoom level is below that value, because the information
      // would be too small.
      val minViewZoomValue = math.max(maxViewZoom - zoomLevelsValue + 1, 0)
      (minViewZoomValue, zoomLevelsValue)
    }

  }

  /**
   * All vector tile layers to generate for unified backend.
   */
  val unifiedVectorTiles = List(
    VectorTiles("wind-surface", d => Some(d.surfaceWind)),
    VectorTiles("wind-boundary-layer", d => Some(d.boundaryLayerWind)),
    VectorTiles("wind-soaring-layer-top", d => Some(d.winds.soaringLayerTop)),
    VectorTiles("wind-300m-agl", d => Some(d.winds.`300m AGL`)),
    VectorTiles("wind-2000m-amsl", d => Some(d.winds.`2000m AMSL`)),
    VectorTiles("wind-3000m-amsl", d => Some(d.winds.`3000m AMSL`)),
    VectorTiles("wind-4000m-amsl", d => Some(d.winds.`4000m AMSL`))
  )

  // Cache the projection of the coordinates from LatLng to WebMercator
  private val coordinatesCache =
    collection.concurrent.TrieMap.empty[Point, (Double, Double)]

  /**
   * Write vector tiles for a single layer.
   */
  def writeVectorTiles(
    vectorTiles: VectorTiles,
    targetDir: os.Path,
    parameters: Parameters,
    unifiedData: IndexedSeq[IndexedSeq[UnifiedModelData]]
  ): Unit = {
    val rootTileExtent = parameters.extent
    val zoomLevels = parameters.zoomLevels
    val maxZoom = zoomLevels - 1

    // Cache the computed features at their (longitude, latitude) coordinates
    val featuresCache = collection.concurrent.TrieMap.empty[(Double, Double), Option[MVTFeature[GeotrellisPoint]]]

    for (z <- 0 to maxZoom) {
      // Number of rows and columns in the zoom level 'z'
      val tilesCount = 1 << z
      // FIXME The root tile extent may not necessarily be square, however we _have to_ use the same tile
      // height and width in the following otherwise the position of the features is wrong.
      val tileSize = rootTileExtent.maxExtent / tilesCount
      // Show all the points at the highest zoom level only,
      // otherwise show every other point from the previous zoom level
      val step = 1 << (maxZoom - z)
      logger.trace(s"Generating tiles for zoom level ${z} (step = ${step}).")
      val visiblePoints = for {
        x <- 0 until parameters.width by step
        y <- 0 until parameters.height by step
      } yield {
        val lonLatPoint = parameters.gridCoordinates(x)(y)
        val webMercatorPoint = coordinatesCache.getOrElseUpdate(
          lonLatPoint,
          Reproject((lonLatPoint.longitude.doubleValue, lonLatPoint.latitude.doubleValue), LatLng, WebMercator)
            .ensuring(p => rootTileExtent.contains(p._1, p._2), "Features must be within the root tile extent")
        )
        (webMercatorPoint, unifiedData(x)(y))
      }

      val tiles =
        visiblePoints
          // Partition the visible points by tile
          .groupBy { case ((webMercatorX, webMercatorY), _) =>
            // Compute the (x, y) tile coordinates this point belongs too
            val x = ((webMercatorX - rootTileExtent.xmin) / tileSize).intValue
            val y = ((rootTileExtent.ymax - webMercatorY) / tileSize).intValue
            assert(x < tilesCount, s"Bad x value: ${x}.")
            assert(y < tilesCount, s"Bad y value: ${y}.")
            (x, y)
          }

      logger.trace(s"Found points in tiles ${tiles.keys.toSeq.sorted.mkString(",")}")

      for (((x, y), features) <- tiles) {
        var usedFeatures = features

        // SI LA LISTE EST VIDE : ON FORCE UN POINT MÉTÉO CENTRAL POUR LA TUILE
        if (usedFeatures.isEmpty) {
          // Coordonnées centrales (en indices de la grille)
          val centralLonIdx = (parameters.width / 2).min(parameters.width - 1)
          val centralLatIdx = (parameters.height / 2).min(parameters.height - 1)
          val lonLatPoint = parameters.gridCoordinates(centralLonIdx)(centralLatIdx)
          val webMercatorPoint = coordinatesCache.getOrElseUpdate(
            lonLatPoint,
            Reproject((lonLatPoint.longitude.doubleValue, lonLatPoint.latitude.doubleValue), LatLng, WebMercator)
          )
          // Prend la donnée météo centrale
          val data = unifiedData(centralLonIdx)(centralLatIdx)
          usedFeatures = IndexedSeq((webMercatorPoint, data))
        }

        val tileExtent = Extent(
          rootTileExtent.xmin + x * tileSize,
          rootTileExtent.ymax - (y + 1) * tileSize,
          rootTileExtent.xmin + (x + 1) * tileSize,
          rootTileExtent.ymax - y * tileSize,
        )
        val vectorTile = VectorTile(
          layers = Map(
            "points" -> StrictLayer(
              name = "points",
              tileWidth = 4096,
              version = 2,
              tileExtent = tileExtent,
              mvtFeatures = MVTFeatures(
                points = features
                  .filterNot { case (_, data) => vectorTiles.excluded(data) }
                  .flatMap { case (point @ (webMercatorX, webMercatorY), data) =>
                    featuresCache.getOrElseUpdate(point, {
                      vectorTiles.feature(data).map { wind =>
                        MVTFeature(
                          geom = GeotrellisPoint(webMercatorX, webMercatorY),
                          data = Map(
                            "speed" -> VInt64(wind.speed.toKilometersPerHour.round.intValue),
                            "direction" -> VFloat(wind.direction.toFloat)
                          )
                        )
                      }
                    })
                  },
                multiPoints = Nil,
                lines = Nil,
                multiLines = Nil,
                polygons = Nil,
                multiPolygons = Nil
              )
            )
          ),
          tileExtent = tileExtent
        )
        os.write.over(
          targetDir / s"${z}-${x}-${y}.mvt",
          vectorTile.toBytes,
          createFolders = true
        )
      }
    }
  }

  /**
   * Write all vector tile layers for a given hour offset.
   *
   * @param parameters Vector tile generation parameters
   * @param subgridTargetDir Base output directory
   * @param hourOffset Hour offset from initialization
   * @param unifiedData Grid of unified model data
   */
  def writeAllVectorTiles(
    parameters: Parameters,
    subgridTargetDir: os.Path,
    hourOffset: Int,
    unifiedData: IndexedSeq[IndexedSeq[UnifiedModelData]]
  ): Unit = {
    logger.debug(s"Generating vector tiles for hour offset n°${hourOffset}")

    for (vectorTiles <- unifiedVectorTiles) {
      logger.trace(s"Generating vector tiles for layer ${vectorTiles.path}")
      writeVectorTiles(
        vectorTiles,
        subgridTargetDir / vectorTiles.path / s"${hourOffset}",
        parameters,
        unifiedData
      )
    }
  }

}
