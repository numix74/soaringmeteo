package org.soaringmeteo.grib

import org.soaringmeteo.Point
import ucar.ma2.{Array => _, _}
import ucar.nc2.dt.grid.{GeoGrid, GridDataset}

import scala.util.Using

/**
 * Convenient class for manipulating GRIB files.
 *
 * Uses the `ucar` library under the hood.
 *
 * TODO Handle errors.
 */
class Grib(dataset: GridDataset) {

  /**
   * Find a grid variable by its short name.
   * Exposes controlled access to the underlying dataset.
   */
  def findGridByShortName(shortName: String): GeoGrid = {
    dataset.findGridByShortName(shortName)
  }

  /**
   * @param grid Forecast data such as boundary layer height, wind force, etc.
   * @param timeIdx Optional time index to load only one time slice (memory optimization for grouped files)
   */
  case class Feature(grid: GeoGrid, timeIdx: Option[Int] = None) {
    // Important: we pre-fetch all the data into memory, otherwise the execution is very slow due to file IOs
    // For grouped files (AROME), load only the requested time slice to save memory
    private val data = timeIdx match {
      case Some(t) => grid.readDataSlice(/* t = */ t, /* z = */ -1, /* y = */ -1, /* x = */ -1)
      case None => grid.readDataSlice(/* t = */ -1, /* z = */ -1, /* y = */ -1, /* x = */ -1)
    }

    // Offset to adjust timeIdx when only one slice is loaded
    private val timeOffset = timeIdx.getOrElse(0)

    def newIndex(): Index = data.getIndex()

    /** Read at a specific time index and vertical level (for HP1/HP2 vertical profiles) */
    def readAtTime(location: Point, timeIdx: Int, zIdx: Int = 0): Double = {
      val (x, y) = getXYCoordinates(location)

      // When we loaded a single time slice, data dimensions are reduced (no time dimension)
      if (this.timeIdx.isDefined) {
        // Single slice loaded: data is 2D (y,x) or 3D (z,y,x)
        data match {
          case d2: ArrayFloat.D2 =>
            d2.get(y, x)
          case d3: ArrayFloat.D3 =>
            val Array(zs, _, _) = data.getShape
            require(zIdx >= 0 && zIdx < zs, s"Level index $zIdx out of bounds [0, $zs)")
            d3.get(zIdx, y, x)
        }
      } else {
        // All slices loaded: data is 3D (t,y,x) or 4D (t,z,y,x)
        val adjustedTimeIdx = timeIdx - timeOffset
        data match {
          case d3: ArrayFloat.D3 =>
            val Array(ts, _, _) = data.getShape
            require(adjustedTimeIdx >= 0 && adjustedTimeIdx < ts, s"Time index $timeIdx (adjusted: $adjustedTimeIdx) out of bounds [0, $ts)")
            d3.get(adjustedTimeIdx, y, x)
          case d4: ArrayFloat.D4 =>
            val Array(ts, zs, _, _) = data.getShape
            require(adjustedTimeIdx >= 0 && adjustedTimeIdx < ts, s"Time index $timeIdx (adjusted: $adjustedTimeIdx) out of bounds [0, $ts)")
            require(zIdx >= 0 && zIdx < zs, s"Level index $zIdx out of bounds [0, $zs)")
            d4.get(adjustedTimeIdx, zIdx, y, x)
        }
      }
    }

    /** Assumes that the feature has 3 or 4 dimensions (where the time and elevation have only one possible value) */
    def read(location: Point): Double = {
      val (x, y) = getXYCoordinates(location)
      // Simplified logic matching v1 behavior
      data match {
        case d3: ArrayFloat.D3 =>
          val Array(ts, ys, xs) = d3.getShape
          if (x < 0 || x >= xs || y < 0 || y >= ys) {
            throw new IllegalArgumentException(
              s"Coordinates out of bounds: location=$location -> x=$x, y=$y, but grid dimensions are (t=$ts, y=$ys, x=$xs)"
            )
          }
          d3.get(/* t = */ 0, y, x)
        case d4: ArrayFloat.D4 =>
          val Array(ts, zs, ys, xs) = d4.getShape
          if (x < 0 || x >= xs || y < 0 || y >= ys) {
            throw new IllegalArgumentException(
              s"Coordinates out of bounds: location=$location -> x=$x, y=$y, but grid dimensions are (t=$ts, z=$zs, y=$ys, x=$xs)"
            )
          }
          d4.get(/* t = */ 0, /* z = */ 0, y, x)
        case _ =>
          // Fallback to complex logic for other cases
          readAtTime(location, 0, 0)
      }
    }

    /**
     * Assumes that the feature has 4 dimensions:
     *
     *   - 0: time
     *   - 1: z (elevation)
     *   - 2: y
     *   - 3: x
     *
     * @return At each point in time, the values of this feature at each elevation
     */
    def readSlices(x: Int, y: Int): Seq[Seq[Double]] = {
      val Array(ts, zs, _, _) = data.getShape
      val index = data.getIndex
      (0 until ts).map { t =>
        (0 until zs).map { z =>
          data.getDouble(index.set(t, z, y, x))
        }
      }
    }

    /**
     * Assumes that the feature has 3 dimensions:
     *
     *   - 0: time
     *   - 1: y
     *   - 2: x
     *
     * @return At each point in time, the values of this feature
     */
    def readSlice(x: Int, y: Int): Seq[Double] = {
      val Array(ts, _, _) = data.getShape
      val index = data.getIndex
      (0 until ts).map { t =>
        data.getDouble(index.set(t, y, x))
      }
    }

    def read(index: Index): Double = {
      data.getDouble(index)
    }

    /** Assumes that the feature has 4 dimensions (where the time has only one possible value) */
    def read(location: Point, elevation: Double): Double = {
      val (x, y) = getXYCoordinates(location)
      val z = grid.getCoordinateSystem.getVerticalAxis.findCoordElement(elevation)
      data.asInstanceOf[ArrayFloat.D4].get(/* t = */ 0, z, y, x)
    }

    /** Read at a specific time index and elevation (for isobaric/height levels) */
    def readAtTimeAndElevation(location: Point, timeIdx: Int, elevation: Double): Double = {
      val (x, y) = getXYCoordinates(location)
      val z = grid.getCoordinateSystem.getVerticalAxis.findCoordElement(elevation)

      if (this.timeIdx.isDefined) {
        // Single slice loaded: data is 3D (z,y,x)
        val d3 = data.asInstanceOf[ArrayFloat.D3]
        d3.get(z, y, x)
      } else {
        // All slices loaded: data is 4D (t,z,y,x)
        val d4 = data.asInstanceOf[ArrayFloat.D4]
        val Array(ts, _, _, _) = d4.getShape
        val adjustedTimeIdx = timeIdx - timeOffset
        require(adjustedTimeIdx >= 0 && adjustedTimeIdx < ts, s"Time index $timeIdx (adjusted: $adjustedTimeIdx) out of bounds [0, $ts)")
        d4.get(adjustedTimeIdx, z, y, x)
      }
    }

    private def getXYCoordinates(location: Point): (Int, Int) = {
      val Array(x, y) =
        grid
          .getCoordinateSystem
          .findXYindexFromLatLon(location.latitude.doubleValue, location.longitude.doubleValue, null)
      (x, y)
    }

  }

  object Feature {

    def apply(name: String): Feature = maybe(name).get

    def maybe(name: String): Option[Feature] =
      Option(dataset.findGridByShortName(name)).map(Feature(_))

    /** Create Feature loading only one time slice (memory optimization for grouped files) */
    def withTimeSlice(name: String, timeIdx: Int): Feature =
      maybeWithTimeSlice(name, timeIdx).get

    def maybeWithTimeSlice(name: String, timeIdx: Int): Option[Feature] =
      Option(dataset.findGridByShortName(name)).map(Feature(_, Some(timeIdx)))

  }


}

object Grib {

  /**
   * Open a GRIB file, do something with it, and close it.
   */
  def bracket[A](file: os.Path)(f: Grib => A): A =
    Using.resource(GridDataset.open(file.toIO.getAbsolutePath)) { data =>
      f(new Grib(data))
    }

}
