package org.soaringmeteo.arome

import org.soaringmeteo.out.VectorTiles
import org.soaringmeteo.Point
import org.locationtech.proj4j.{CRSFactory, CoordinateTransformFactory, ProjCoordinate}
import geotrellis.vector.Extent

object AromeVectorTilesParameters {
  private val crsFactory = new CRSFactory()
  private val ctFactory = new CoordinateTransformFactory()
  private val latLngCRS = crsFactory.createFromName("EPSG:4326")
  private val webMercatorCRS = crsFactory.createFromName("EPSG:3857")

  def apply(zone: AromeZone): VectorTiles.Parameters = {
    val transform = ctFactory.createTransform(latLngCRS, webMercatorCRS)
    
    val minLon = zone.longitudes.head
    val maxLon = zone.longitudes.last
    val minLat = zone.latitudes.last
    val maxLat = zone.latitudes.head
    
    val pMin = new ProjCoordinate(minLon, minLat)
    val pMax = new ProjCoordinate(maxLon, maxLat)
    
    transform.transform(pMin, pMin)  // ✅ 2 params: source, result
    transform.transform(pMax, pMax)
    
    val extent = Extent(pMin.x, pMin.y, pMax.x, pMax.y).buffer(5000)
    
    val coordinates =
      for (longitude <- zone.longitudes) yield
        for (latitude <- zone.latitudes) yield
          Point(latitude, longitude)
    
    VectorTiles.Parameters(extent, 8, zone.longitudes.size, zone.latitudes.size, coordinates)
  }
}
