package org.soaringmeteo.arome

import org.slf4j.LoggerFactory
import org.soaringmeteo.Point
import org.soaringmeteo.grib.Grib
import ucar.ma2.{Array => UArray, ArrayFloat}
import ucar.nc2.dt.grid.GeoGrid
import scala.collection.mutable

case class AromeZone(
  longitudes: IndexedSeq[Double],
  latitudes: IndexedSeq[Double]
)

object AromeZone {
  val paysBasque = AromeZone(
    longitudes = BigDecimal(-2.0).to(BigDecimal(0.5), BigDecimal(0.025)).map(_.toDouble).toIndexedSeq,
    latitudes = BigDecimal(42.8).to(BigDecimal(43.6), BigDecimal(0.025)).map(_.toDouble).toIndexedSeq
  )
}

case class LoadedData(
  data: UArray,
  grid: GeoGrid,
  name: String,
  isFlux: Boolean = false
) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val shape = data.getShape
  private val rank = data.getRank
  logger.info(s"$name shape: ${shape.mkString("x")} (rank=$rank)")

  def readAtTime(location: Point, hourOffset: Int): Double = {
    // Special case: flux variables at hour 0 return 0
    if (isFlux && hourOffset == 0) {
      return 0.0
    }

    val Array(x, y) = grid.getCoordinateSystem.findXYindexFromLatLon(
      location.latitude.doubleValue,
      location.longitude.doubleValue,
      null
    )

    // Since we now read single timesteps, data should be 2D
    (rank, data) match {

      case (2, d2: ArrayFloat.D2) =>
        // Check bounds before accessing
        val Array(dimY, dimX) = shape

        if (y < 0 || y >= dimY) {
          logger.error(s"$name: Y index $y out of bounds [0, $dimY) at location $location")
          logger.error(s"$name: Grid dimensions: ${dimY}x$dimX")
          throw new ArrayIndexOutOfBoundsException(s"Y index $y out of bounds for dimension $dimY")
        }

        if (x < 0 || x >= dimX) {
          logger.error(s"$name: X index $x out of bounds [0, $dimX) at location $location")
          logger.error(s"$name: Grid dimensions: ${dimY}x$dimX")
          throw new ArrayIndexOutOfBoundsException(s"X index $x out of bounds for dimension $dimX")
        }
        d2.get(y, x).toDouble

      case _ =>
        logger.error(s"$name: Unexpected array rank $rank, expected 2")
        logger.error(s"$name: Array shape: ${shape.mkString("x")}")
        throw new Exception(s"Unexpected array structure: rank=$rank, expected 2D array. Shape: ${shape.mkString("x")}")
    }
  }
}

object AromeGrib {

  private val logger = LoggerFactory.getLogger(getClass)

  // Hauteurs extraites tous les 250m (250m à 3000m)
  val heightLevels = Seq(250, 500, 750, 1000, 1250, 1500, 1750, 2000, 2250, 2500, 2750, 3000)

  def fromGroupFiles(
    sp1File: os.Path,
    sp2File: os.Path,
    sp3File: os.Path,
    windsDir: os.Path,  // Répertoire contenant u_XXXm.grib2 / v_XXXm.grib2
    hourOffset: Int,
    zone: AromeZone
  ): IndexedSeq[IndexedSeq[AromeData]] = {

    logger.info(s"Lecture AROME heure $hourOffset depuis ${sp1File.last}")

    val sp1Data = Grib.bracket(sp1File) { grib =>
      val t2m = grib.Feature("Temperature_height_above_ground")
      val u10 = grib.Feature("u-component_of_wind_height_above_ground")
      val v10 = grib.Feature("v-component_of_wind_height_above_ground")

     // Read only the timestep we need (2D spatial slice)
      (
        LoadedData(t2m.grid.readDataSlice(hourOffset, 0, -1, -1), t2m.grid, "T2M"),
        LoadedData(u10.grid.readDataSlice(hourOffset, 0, -1, -1), u10.grid, "U10"),
        LoadedData(v10.grid.readDataSlice(hourOffset, 0, -1, -1), v10.grid, "V10")
      )
    }

    val sp2Data = Grib.bracket(sp2File) { grib =>
      val cape = grib.Feature.maybe("Convective_available_potential_energy_surface_layer")
      val pblh = grib.Feature.maybe("Planetary_boundary_layer_height_surface")
      val clouds = grib.Feature.maybe("Low_cloud_cover_surface")
      val terrain = grib.Feature.maybe("Geopotential_height_surface")

      // For flux variables, we need to handle the time index offset
      val cloudTimeIdx = if (hourOffset == 0) 0 else hourOffset - 1

      (
        cape.map { c => LoadedData(c.grid.readDataSlice(hourOffset, 0, -1, -1), c.grid, "CAPE") },
        pblh.map { p => LoadedData(p.grid.readDataSlice(hourOffset, 0, -1, -1), p.grid, "PBLH") },
        clouds.map { c => LoadedData(c.grid.readDataSlice(cloudTimeIdx, 0, -1, -1), c.grid, "CLOUDS", isFlux = true) },
        terrain.map { t => LoadedData(t.grid.readDataSlice(hourOffset, 0, -1, -1), t.grid, "TERRAIN") }
      )
    }

    val sp3Data = Grib.bracket(sp3File) { grib =>
      val sensible = grib.Feature("Sensible_heat_net_flux_surface_Mixed_intervals_Accumulation")
      val latent = grib.Feature("Latent_heat_net_flux_surface_Mixed_intervals_Accumulation")
      val solar = grib.Feature("Net_short_wave_radiation_flux_surface_Mixed_intervals_Accumulation")

      // Flux variables: skip hour 0, use hour-1 for other hours
      val fluxTimeIdx = if (hourOffset == 0) 0 else hourOffset - 1

      (
        LoadedData(sensible.grid.readDataSlice(fluxTimeIdx, 0, -1, -1), sensible.grid, "SHTFL", isFlux = true),
        LoadedData(latent.grid.readDataSlice(fluxTimeIdx, 0, -1, -1), latent.grid, "LHTFL", isFlux = true),
        LoadedData(solar.grid.readDataSlice(fluxTimeIdx, 0, -1, -1), solar.grid, "SOLAR", isFlux = true)
      )
    }

    logger.info(s"Extraction ${zone.longitudes.size}x${zone.latitudes.size} points...")
    logger.info(s"Reading winds from: $windsDir")

    for (lon <- zone.longitudes) yield {
      for (lat <- zone.latitudes) yield {
        val location = Point(lat, lon)
        val terrainElev = sp2Data._4.flatMap(t => Try(t.readAtTime(location, hourOffset)).toOption).getOrElse(0.0)

        AromeData(
          t2m = sp1Data._1.readAtTime(location, hourOffset),
          u10 = sp1Data._2.readAtTime(location, hourOffset),
          v10 = sp1Data._3.readAtTime(location, hourOffset),
          pblh = sp2Data._2.map(_.readAtTime(location, hourOffset)).getOrElse(0.0),
          cape = sp2Data._1.map(_.readAtTime(location, hourOffset)).getOrElse(0.0),
          sensibleHeatFlux = sp3Data._1.readAtTime(location, hourOffset),
          latentHeatFlux = sp3Data._2.readAtTime(location, hourOffset),
          solarRadiation = sp3Data._3.readAtTime(location, hourOffset),
          cloudCover = sp2Data._3.map(_.readAtTime(location, hourOffset)).getOrElse(0.0),
          terrainElevation = terrainElev,
          windsAtHeights = extractWindsAtHeights(windsDir, location, hourOffset)
        )
      }
    }
  }

  private def extractWindsAtHeights(
    windsDir: os.Path,
    location: Point,
    hourOffset: Int
  ): Map[Int, (Double, Double)] = {

    try {
      val winds = mutable.Map.empty[Int, (Double, Double)]

      for (height <- heightLevels) {
        val uFile = windsDir / s"u_${height}m.grib2"
        val vFile = windsDir / s"v_${height}m.grib2"

        if (os.exists(uFile) && os.exists(vFile)) {
          try {
            val uVal = Grib.bracket(uFile) { grib =>
              val u = grib.Feature("u-component_of_wind_height_above_ground")
              val data = u.grid.readDataSlice(hourOffset, 0, -1, -1)
              val Array(x, y) = u.grid.getCoordinateSystem.findXYindexFromLatLon(
                location.latitude.doubleValue,
                location.longitude.doubleValue,
                null
              )
              data.asInstanceOf[ArrayFloat.D2].get(y, x).toDouble
            }
            val vVal = Grib.bracket(vFile) { grib =>
              val v = grib.Feature("v-component_of_wind_height_above_ground")
              val data = v.grid.readDataSlice(hourOffset, 0, -1, -1)
              val Array(x, y) = v.grid.getCoordinateSystem.findXYindexFromLatLon(
                location.latitude.doubleValue,
                location.longitude.doubleValue,
                null
              )
              data.asInstanceOf[ArrayFloat.D2].get(y, x).toDouble
            }
            winds(height) = (uVal, vVal)
          } catch {

            case e: Exception =>
              logger.warn(s"Error reading winds at ${height}m: ${e.getMessage}")
          }
        }
      }
      winds.toMap
    } catch {
      case e: Exception =>
        logger.warn(s"Error extracting winds: ${e.getMessage}")
        Map.empty
    }
  }

  private def Try[T](fn: => T): scala.util.Try[T] = scala.util.Try(fn)
}

