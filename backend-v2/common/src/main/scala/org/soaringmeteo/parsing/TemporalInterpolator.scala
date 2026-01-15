package org.soaringmeteo.parsing

import org.soaringmeteo.{AirData, Wind}
import squants.energy.SpecificEnergy
import squants.motion.{MetersPerSecond, Pascals, Pressure}
import squants.radio.WattsPerSquareMeter
import squants.space.{Length, Meters}
import squants.thermal.{Kelvin, Temperature}

import scala.collection.SortedMap

/**
 * Temporal interpolation for weather data.
 *
 * Weather models produce forecasts at discrete time steps (e.g., GFS every 3 hours).
 * This interpolator generates intermediate hours by linear interpolation.
 *
 * Example:
 *   - GFS native: 0h, 3h, 6h, 9h, ...
 *   - After interpolation: 0h, 1h, 2h, 3h, 4h, 5h, 6h, ...
 *
 * Design:
 * - Takes two grids (before/after) at native timesteps
 * - Returns interpolated grid for intermediate hour
 * - Uses simple linear interpolation for all variables
 */
object TemporalInterpolator {

  /**
   * Interpolate between two grids of RawData.
   *
   * @param gridBefore Grid at earlier timestep
   * @param gridAfter Grid at later timestep
   * @param fraction Interpolation fraction (0.0 = before, 1.0 = after)
   * @return Interpolated grid
   */
  def interpolateGrids(
    gridBefore: IndexedSeq[IndexedSeq[RawData]],
    gridAfter: IndexedSeq[IndexedSeq[RawData]],
    fraction: Double
  ): IndexedSeq[IndexedSeq[RawData]] = {
    require(
      gridBefore.size == gridAfter.size,
      s"Grid sizes must match: ${gridBefore.size} != ${gridAfter.size}"
    )
    require(
      fraction >= 0.0 && fraction <= 1.0,
      s"Fraction must be in [0, 1]: $fraction"
    )

    gridBefore.zip(gridAfter).map { case (rowBefore, rowAfter) =>
      require(
        rowBefore.size == rowAfter.size,
        s"Row sizes must match: ${rowBefore.size} != ${rowAfter.size}"
      )
      rowBefore.zip(rowAfter).map { case (dataBefore, dataAfter) =>
        interpolateRawData(dataBefore, dataAfter, fraction)
      }
    }
  }

  /**
   * Interpolate between two RawData points.
   */
  private def interpolateRawData(
    before: RawData,
    after: RawData,
    fraction: Double
  ): RawData = {
    RawData(
      surface = interpolateSurface(before.surface, after.surface, fraction),
      boundary = interpolateBoundary(before.boundary, after.boundary, fraction),
      atmosphere = interpolateAtmosphere(before.atmosphere, after.atmosphere, fraction),
      profiles = interpolateProfiles(before.profiles, after.profiles, fraction)
    )
  }

  /**
   * Interpolate surface data.
   */
  private def interpolateSurface(
    before: SurfaceData,
    after: SurfaceData,
    fraction: Double
  ): SurfaceData = {
    SurfaceData(
      elevation = before.elevation,  // Elevation doesn't change
      temperature = interpolateTemperature(before.temperature, after.temperature, fraction),
      dewPoint = interpolateTemperature(before.dewPoint, after.dewPoint, fraction),
      wind = interpolateWind(before.wind, after.wind, fraction),
      pressure = interpolatePressure(before.pressure, after.pressure, fraction),
      snowDepth = interpolateLength(before.snowDepth, after.snowDepth, fraction)
    )
  }

  /**
   * Interpolate boundary layer data.
   */
  private def interpolateBoundary(
    before: BoundaryLayerData,
    after: BoundaryLayerData,
    fraction: Double
  ): BoundaryLayerData = {
    BoundaryLayerData(
      pblDepth = interpolateLength(before.pblDepth, after.pblDepth, fraction),
      wind = interpolateWind(before.wind, after.wind, fraction),
      sensibleHeatFlux = WattsPerSquareMeter(
        interpolateDouble(before.sensibleHeatFlux.toWattsPerSquareMeter, after.sensibleHeatFlux.toWattsPerSquareMeter, fraction)
      ),
      latentHeatFlux = WattsPerSquareMeter(
        interpolateDouble(before.latentHeatFlux.toWattsPerSquareMeter, after.latentHeatFlux.toWattsPerSquareMeter, fraction)
      )
    )
  }

  /**
   * Interpolate atmosphere data.
   */
  private def interpolateAtmosphere(
    before: AtmosphereData,
    after: AtmosphereData,
    fraction: Double
  ): AtmosphereData = {
    AtmosphereData(
      totalCloudCover = interpolateInt(before.totalCloudCover, after.totalCloudCover, fraction),
      convectiveCloudCover = interpolateInt(before.convectiveCloudCover, after.convectiveCloudCover, fraction),
      lowCloudCover = interpolateOptionalInt(before.lowCloudCover, after.lowCloudCover, fraction),
      mediumCloudCover = interpolateOptionalInt(before.mediumCloudCover, after.mediumCloudCover, fraction),
      highCloudCover = interpolateOptionalInt(before.highCloudCover, after.highCloudCover, fraction),
      totalRain = interpolateLength(before.totalRain, after.totalRain, fraction),
      convectiveRain = interpolateLength(before.convectiveRain, after.convectiveRain, fraction),
      solarRadiation = WattsPerSquareMeter(
        interpolateDouble(before.solarRadiation.toWattsPerSquareMeter, after.solarRadiation.toWattsPerSquareMeter, fraction)
      ),
      cape = interpolateOptionalEnergy(before.cape, after.cape, fraction),
      cin = interpolateOptionalEnergy(before.cin, after.cin, fraction)
    )
  }

  /**
   * Interpolate vertical profiles.
   */
  private def interpolateProfiles(
    before: VerticalProfiles,
    after: VerticalProfiles,
    fraction: Double
  ): VerticalProfiles = {
    // For profiles, we need to align altitudes and interpolate values
    // For simplicity, we'll use the altitude levels from 'before' and interpolate values
    val tempProfile = interpolateAltitudeMap(
      before.temperatureProfile,
      after.temperatureProfile,
      fraction,
      interpolateTemperature
    )

    val dewProfile = interpolateAltitudeMap(
      before.dewPointProfile,
      after.dewPointProfile,
      fraction,
      interpolateTemperature
    )

    val windProfile = interpolateAltitudeMap(
      before.windProfile,
      after.windProfile,
      fraction,
      interpolateWind
    )

    val cloudProfile = interpolateAltitudeMap(
      before.cloudCoverProfile,
      after.cloudCoverProfile,
      fraction,
      interpolateInt
    )

    val airDataProfile = interpolateAltitudeMap(
      before.airDataByAltitude,
      after.airDataByAltitude,
      fraction,
      interpolateAirData
    )

    VerticalProfiles(
      temperatureProfile = tempProfile,
      dewPointProfile = dewProfile,
      windProfile = windProfile,
      cloudCoverProfile = cloudProfile,
      isothermZero = interpolateOptionalLength(before.isothermZero, after.isothermZero, fraction),
      airDataByAltitude = airDataProfile
    )
  }

  /**
   * Interpolate a sorted map indexed by altitude.
   */
  private def interpolateAltitudeMap[T](
    before: SortedMap[Length, T],
    after: SortedMap[Length, T],
    fraction: Double,
    interpolateValue: (T, T, Double) => T
  ): SortedMap[Length, T] = {
    // Get union of altitude keys
    val allAltitudes = (before.keySet ++ after.keySet).toSeq.sorted(Ordering.by[Length, Double](_.toMeters))

    SortedMap.from(
      allAltitudes.map { altitude =>
        val valueBefore = before.get(altitude)
        val valueAfter = after.get(altitude)

        val interpolatedValue = (valueBefore, valueAfter) match {
          case (Some(vb), Some(va)) =>
            // Both have data at this altitude, interpolate
            interpolateValue(vb, va, fraction)
          case (Some(vb), None) =>
            // Only before has data, use it
            vb
          case (None, Some(va)) =>
            // Only after has data, use it
            va
          case (None, None) =>
            // Neither has data (shouldn't happen due to union)
            throw new IllegalStateException(s"No data at altitude $altitude")
        }

        altitude -> interpolatedValue
      }
    )(Ordering.by(_.toMeters))
  }

  // === Primitive interpolation functions ===

  private def interpolateDouble(before: Double, after: Double, fraction: Double): Double =
    before + (after - before) * fraction

  private def interpolateInt(before: Int, after: Int, fraction: Double): Int =
    (before + (after - before) * fraction).round.toInt

  private def interpolateTemperature(before: Temperature, after: Temperature, fraction: Double): Temperature =
    Kelvin(interpolateDouble(before.toKelvinDegrees, after.toKelvinDegrees, fraction))

  private def interpolateLength(before: Length, after: Length, fraction: Double): Length =
    Meters(interpolateDouble(before.toMeters, after.toMeters, fraction))

  private def interpolatePressure(before: Pressure, after: Pressure, fraction: Double): Pressure =
    Pascals(interpolateDouble(before.toPascals, after.toPascals, fraction))

  private def interpolateWind(before: Wind, after: Wind, fraction: Double): Wind =
    Wind(
      MetersPerSecond(interpolateDouble(before.u.toMetersPerSecond, after.u.toMetersPerSecond, fraction)),
      MetersPerSecond(interpolateDouble(before.v.toMetersPerSecond, after.v.toMetersPerSecond, fraction))
    )

  private def interpolateAirData(before: AirData, after: AirData, fraction: Double): AirData =
    AirData(
      wind = interpolateWind(before.wind, after.wind, fraction),
      temperature = interpolateTemperature(before.temperature, after.temperature, fraction),
      dewPoint = interpolateTemperature(before.dewPoint, after.dewPoint, fraction),
      cloudCover = interpolateInt(before.cloudCover, after.cloudCover, fraction)
    )

  private def interpolateOptionalLength(
    before: Option[Length],
    after: Option[Length],
    fraction: Double
  ): Option[Length] = {
    (before, after) match {
      case (Some(b), Some(a)) => Some(interpolateLength(b, a, fraction))
      case (Some(b), None) => Some(b)
      case (None, Some(a)) => Some(a)
      case (None, None) => None
    }
  }

  private def interpolateOptionalEnergy(
    before: Option[SpecificEnergy],
    after: Option[SpecificEnergy],
    fraction: Double
  ): Option[SpecificEnergy] = {
    (before, after) match {
      case (Some(b), Some(a)) =>
        Some(squants.energy.Grays(interpolateDouble(b.toGrays, a.toGrays, fraction)))
      case (Some(b), None) => Some(b)
      case (None, Some(a)) => Some(a)
      case (None, None) => None
    }
  }

  private def interpolateOptionalInt(
    before: Option[Int],
    after: Option[Int],
    fraction: Double
  ): Option[Int] = {
    (before, after) match {
      case (Some(b), Some(a)) => Some(interpolateInt(b, a, fraction))
      case (Some(b), None) => Some(b)
      case (None, Some(a)) => Some(a)
      case (None, None) => None
    }
  }
}
