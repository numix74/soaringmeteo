package org.soaringmeteo

import com.typesafe.config.Config

/**
 * Geographic zone for weather data processing.
 *
 * Defines a rectangular grid with:
 * - Longitude/latitude ranges
 * - Grid resolution (km)
 * - Geographic extent for raster generation
 *
 * Example:
 * {{{
 *   val pyrenees = GeographicZone(
 *     id = "pyrenees",
 *     label = "Pyrénées",
 *     longitudes = (-4.0 to 3.75 by 0.025).toIndexedSeq,
 *     latitudes = (44.25 to 41.25 by -0.025).toIndexedSeq
 *   )
 * }}}
 */
case class GeographicZone(
  id: String,
  label: String,
  longitudes: IndexedSeq[Double],
  latitudes: IndexedSeq[Double],
  vectorTileSize: Int = 512
) {
  /** Number of grid points in X direction */
  def width: Int = longitudes.size

  /** Number of grid points in Y direction */
  def height: Int = latitudes.size

  /** Geographic extent for raster generation */
  lazy val extent: Extent = Extent(
    xMin = longitudes.head,
    yMin = latitudes.last,
    xMax = longitudes.last,
    yMax = latitudes.head
  )

  /** All grid coordinates as Points */
  lazy val coordinates: IndexedSeq[IndexedSeq[Point]] =
    for (lon <- longitudes) yield
      for (lat <- latitudes) yield Point(lat, lon)

  /** Approximate grid resolution in km (at mid-latitude) */
  def approximateResolutionKm: Double = {
    val midLat = (latitudes.head + latitudes.last) / 2.0
    val lonStep = math.abs(longitudes(1) - longitudes.head)
    val latStep = math.abs(latitudes(1) - latitudes.head)

    // 1 degree ≈ 111 km at equator, adjusted by cos(latitude)
    val lonKm = lonStep * 111.0 * math.cos(math.toRadians(midLat))
    val latKm = latStep * 111.0

    (lonKm + latKm) / 2.0  // Average
  }

  override def toString: String =
    s"GeographicZone($id: ${width}x$height points, ~${approximateResolutionKm}%.2f km)"
}

object GeographicZone {

  /**
   * Predefined zone: Pyrénées
   *
   * Coverage: Western to Eastern Pyrénées
   * Resolution: 2.5 km (AROME native)
   */
  val Pyrenees = GeographicZone(
    id = "pyrenees",
    label = "Pyrénées",
    longitudes = BigDecimal(-4.0).to(BigDecimal(3.75), BigDecimal(0.025))
      .map(_.toDouble).toIndexedSeq,
    latitudes = BigDecimal(44.25).to(BigDecimal(41.25), BigDecimal(-0.025))
      .map(_.toDouble).toIndexedSeq,
    vectorTileSize = 512
  )

  /**
   * Predefined zone: Pays Basque (subset of Pyrénées)
   *
   * Smaller zone for AROME testing
   * Resolution: 2.5 km
   */
  val PaysBasque = GeographicZone(
    id = "pays-basque",
    label = "Pays Basque",
    longitudes = BigDecimal(-2.0).to(BigDecimal(0.5), BigDecimal(0.025))
      .map(_.toDouble).toIndexedSeq,
    latitudes = BigDecimal(43.6).to(BigDecimal(42.8), BigDecimal(-0.025))
      .map(_.toDouble).toIndexedSeq,
    vectorTileSize = 300
  )

  /**
   * Predefined zone: Pyrénées at GFS resolution
   *
   * Matches GFS GRIB files resolution (0.25° ≈ 27 km)
   * Grid: 32×7 points
   */
  val PyreneesGFS = GeographicZone(
    id = "pyrenees-gfs",
    label = "Pyrénées (GFS)",
    longitudes = BigDecimal(-4.0).to(BigDecimal(3.75), BigDecimal(0.25))
      .map(_.toDouble).toIndexedSeq,
    latitudes = BigDecimal(43.5).to(BigDecimal(42.0), BigDecimal(-0.25))
      .map(_.toDouble).toIndexedSeq,
    vectorTileSize = 300
  )

  /**
   * Create GeographicZone from Typesafe Config.
   *
   * Expected config format:
   * {{{
   *   zone {
   *     id = "pyrenees"
   *     label = "Pyrénées"
   *     lon-min = -4.0
   *     lon-max = 3.75
   *     lat-min = 41.25
   *     lat-max = 44.25
   *     step = 0.025  # Grid resolution in degrees
   *     vector-tile-size = 512  # Optional, default 512
   *   }
   * }}}
   */
  def fromConfig(config: Config): GeographicZone = {
    val id = config.getString("id")
    val label = config.getString("label")
    val lonMin = config.getDouble("lon-min")
    val lonMax = config.getDouble("lon-max")
    val latMin = config.getDouble("lat-min")
    val latMax = config.getDouble("lat-max")
    val step = config.getDouble("step")
    val tileSize = if (config.hasPath("vector-tile-size")) {
      config.getInt("vector-tile-size")
    } else {
      512
    }

    val longitudes = BigDecimal(lonMin).to(BigDecimal(lonMax), BigDecimal(step))
      .map(_.toDouble).toIndexedSeq
    val latitudes = BigDecimal(latMax).to(BigDecimal(latMin), BigDecimal(-step))
      .map(_.toDouble).toIndexedSeq

    GeographicZone(id, label, longitudes, latitudes, tileSize)
  }

  /**
   * Create zone with explicit resolution in km.
   *
   * @param id Zone identifier
   * @param label Human-readable name
   * @param lonMin Western boundary (degrees)
   * @param lonMax Eastern boundary (degrees)
   * @param latMin Southern boundary (degrees)
   * @param latMax Northern boundary (degrees)
   * @param resolutionKm Target resolution in kilometers
   */
  def fromBoundsAndResolution(
    id: String,
    label: String,
    lonMin: Double,
    lonMax: Double,
    latMin: Double,
    latMax: Double,
    resolutionKm: Double
  ): GeographicZone = {
    // Convert km to degrees (approximate at mid-latitude)
    val midLat = (latMin + latMax) / 2.0
    val degreePerKm = 1.0 / 111.0  // 1 degree ≈ 111 km

    // Adjust longitude step by latitude
    val lonStep = degreePerKm * resolutionKm / math.cos(math.toRadians(midLat))
    val latStep = degreePerKm * resolutionKm

    val longitudes = BigDecimal(lonMin).to(BigDecimal(lonMax), BigDecimal(lonStep))
      .map(_.toDouble).toIndexedSeq
    val latitudes = BigDecimal(latMax).to(BigDecimal(latMin), BigDecimal(-latStep))
      .map(_.toDouble).toIndexedSeq

    GeographicZone(id, label, longitudes, latitudes)
  }
}
