package org.soaringmeteo.parsing

import org.soaringmeteo.{AirData, Wind}
import squants.motion.{MetersPerSecond, Pascals}
import squants.radio.WattsPerSquareMeter
import squants.space.Meters
import squants.thermal.{Celsius, Kelvin}
import verify.BasicTestSuite

import scala.collection.SortedMap

object TemporalInterpolatorTestSuite extends BasicTestSuite {

  // === Tests for basic interpolation ===

  test("interpolateGrids with fraction 0.0 returns first grid") {
    val surface1 = SurfaceData(
      elevation = Meters(100),
      temperature = Celsius(10),
      dewPoint = Celsius(5),
      wind = Wind(MetersPerSecond(1), MetersPerSecond(0)),
      pressure = Pascals(101325),
      snowDepth = Meters(0)
    )
    val surface2 = SurfaceData(
      elevation = Meters(100),
      temperature = Celsius(20),
      dewPoint = Celsius(15),
      wind = Wind(MetersPerSecond(5), MetersPerSecond(0)),
      pressure = Pascals(101325),
      snowDepth = Meters(0)
    )

    val boundary1 = BoundaryLayerData(
      pblDepth = Meters(1000),
      wind = Wind(MetersPerSecond(2), MetersPerSecond(0)),
      sensibleHeatFlux = WattsPerSquareMeter(100),
      latentHeatFlux = WattsPerSquareMeter(50)
    )
    val boundary2 = BoundaryLayerData(
      pblDepth = Meters(2000),
      wind = Wind(MetersPerSecond(4), MetersPerSecond(0)),
      sensibleHeatFlux = WattsPerSquareMeter(200),
      latentHeatFlux = WattsPerSquareMeter(100)
    )

    val atmosphere1 = AtmosphereData(
      totalCloudCover = 50,
      convectiveCloudCover = 30,
      lowCloudCover = Some(20),
      mediumCloudCover = Some(15),
      highCloudCover = Some(15),
      totalRain = Meters(0.001),
      convectiveRain = Meters(0.0005),
      solarRadiation = WattsPerSquareMeter(500),
      cape = None,
      cin = None
    )
    val atmosphere2 = AtmosphereData(
      totalCloudCover = 80,
      convectiveCloudCover = 60,
      lowCloudCover = Some(40),
      mediumCloudCover = Some(30),
      highCloudCover = Some(10),
      totalRain = Meters(0.005),
      convectiveRain = Meters(0.003),
      solarRadiation = WattsPerSquareMeter(300),
      cape = None,
      cin = None
    )

    val profiles1 = VerticalProfiles(
      temperatureProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      dewPointProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      windProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      cloudCoverProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      isothermZero = Some(Meters(3000)),
      airDataByAltitude = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters))
    )
    val profiles2 = VerticalProfiles(
      temperatureProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      dewPointProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      windProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      cloudCoverProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      isothermZero = Some(Meters(2500)),
      airDataByAltitude = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters))
    )

    val raw1 = RawData(surface1, boundary1, atmosphere1, profiles1)
    val raw2 = RawData(surface2, boundary2, atmosphere2, profiles2)

    val gridBefore = IndexedSeq(IndexedSeq(raw1))
    val gridAfter = IndexedSeq(IndexedSeq(raw2))

    val result = TemporalInterpolator.interpolateGrids(gridBefore, gridAfter, 0.0)

    // With fraction 0.0, should get the first grid
    assert(result(0)(0).surface.temperature.toCelsiusDegrees == 10.0)
    assert(result(0)(0).boundary.pblDepth.toMeters == 1000.0)
  }

  test("interpolateGrids with fraction 1.0 returns second grid") {
    val surface1 = SurfaceData(
      elevation = Meters(100),
      temperature = Celsius(10),
      dewPoint = Celsius(5),
      wind = Wind(MetersPerSecond(1), MetersPerSecond(0)),
      pressure = Pascals(101325),
      snowDepth = Meters(0)
    )
    val surface2 = SurfaceData(
      elevation = Meters(100),
      temperature = Celsius(20),
      dewPoint = Celsius(15),
      wind = Wind(MetersPerSecond(5), MetersPerSecond(0)),
      pressure = Pascals(101325),
      snowDepth = Meters(0)
    )

    val boundary1 = BoundaryLayerData(
      pblDepth = Meters(1000),
      wind = Wind(MetersPerSecond(2), MetersPerSecond(0)),
      sensibleHeatFlux = WattsPerSquareMeter(100),
      latentHeatFlux = WattsPerSquareMeter(50)
    )
    val boundary2 = BoundaryLayerData(
      pblDepth = Meters(2000),
      wind = Wind(MetersPerSecond(4), MetersPerSecond(0)),
      sensibleHeatFlux = WattsPerSquareMeter(200),
      latentHeatFlux = WattsPerSquareMeter(100)
    )

    val atmosphere1 = AtmosphereData(
      totalCloudCover = 50,
      convectiveCloudCover = 30,
      lowCloudCover = Some(20),
      mediumCloudCover = Some(15),
      highCloudCover = Some(15),
      totalRain = Meters(0.001),
      convectiveRain = Meters(0.0005),
      solarRadiation = WattsPerSquareMeter(500),
      cape = None,
      cin = None
    )
    val atmosphere2 = AtmosphereData(
      totalCloudCover = 80,
      convectiveCloudCover = 60,
      lowCloudCover = Some(40),
      mediumCloudCover = Some(30),
      highCloudCover = Some(10),
      totalRain = Meters(0.005),
      convectiveRain = Meters(0.003),
      solarRadiation = WattsPerSquareMeter(300),
      cape = None,
      cin = None
    )

    val profiles1 = VerticalProfiles(
      temperatureProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      dewPointProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      windProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      cloudCoverProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      isothermZero = Some(Meters(3000)),
      airDataByAltitude = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters))
    )
    val profiles2 = VerticalProfiles(
      temperatureProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      dewPointProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      windProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      cloudCoverProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      isothermZero = Some(Meters(2500)),
      airDataByAltitude = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters))
    )

    val raw1 = RawData(surface1, boundary1, atmosphere1, profiles1)
    val raw2 = RawData(surface2, boundary2, atmosphere2, profiles2)

    val gridBefore = IndexedSeq(IndexedSeq(raw1))
    val gridAfter = IndexedSeq(IndexedSeq(raw2))

    val result = TemporalInterpolator.interpolateGrids(gridBefore, gridAfter, 1.0)

    // With fraction 1.0, should get the second grid
    assert(result(0)(0).surface.temperature.toCelsiusDegrees == 20.0)
    assert(result(0)(0).boundary.pblDepth.toMeters == 2000.0)
  }

  test("interpolateGrids with fraction 0.5 returns midpoint") {
    val surface1 = SurfaceData(
      elevation = Meters(100),
      temperature = Celsius(10),
      dewPoint = Celsius(5),
      wind = Wind(MetersPerSecond(2), MetersPerSecond(0)),
      pressure = Pascals(100000),
      snowDepth = Meters(0)
    )
    val surface2 = SurfaceData(
      elevation = Meters(100),
      temperature = Celsius(20),
      dewPoint = Celsius(15),
      wind = Wind(MetersPerSecond(6), MetersPerSecond(4)),
      pressure = Pascals(102000),
      snowDepth = Meters(0.1)
    )

    val boundary1 = BoundaryLayerData(
      pblDepth = Meters(1000),
      wind = Wind(MetersPerSecond(2), MetersPerSecond(0)),
      sensibleHeatFlux = WattsPerSquareMeter(100),
      latentHeatFlux = WattsPerSquareMeter(50)
    )
    val boundary2 = BoundaryLayerData(
      pblDepth = Meters(2000),
      wind = Wind(MetersPerSecond(4), MetersPerSecond(2)),
      sensibleHeatFlux = WattsPerSquareMeter(200),
      latentHeatFlux = WattsPerSquareMeter(100)
    )

    val atmosphere1 = AtmosphereData(
      totalCloudCover = 40,
      convectiveCloudCover = 20,
      lowCloudCover = Some(10),
      mediumCloudCover = Some(20),
      highCloudCover = Some(10),
      totalRain = Meters(0.002),
      convectiveRain = Meters(0.001),
      solarRadiation = WattsPerSquareMeter(400),
      cape = None,
      cin = None
    )
    val atmosphere2 = AtmosphereData(
      totalCloudCover = 60,
      convectiveCloudCover = 40,
      lowCloudCover = Some(30),
      mediumCloudCover = Some(20),
      highCloudCover = Some(10),
      totalRain = Meters(0.006),
      convectiveRain = Meters(0.003),
      solarRadiation = WattsPerSquareMeter(200),
      cape = None,
      cin = None
    )

    val profiles1 = VerticalProfiles(
      temperatureProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      dewPointProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      windProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      cloudCoverProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      isothermZero = Some(Meters(2000)),
      airDataByAltitude = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters))
    )
    val profiles2 = VerticalProfiles(
      temperatureProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      dewPointProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      windProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      cloudCoverProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      isothermZero = Some(Meters(4000)),
      airDataByAltitude = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters))
    )

    val raw1 = RawData(surface1, boundary1, atmosphere1, profiles1)
    val raw2 = RawData(surface2, boundary2, atmosphere2, profiles2)

    val gridBefore = IndexedSeq(IndexedSeq(raw1))
    val gridAfter = IndexedSeq(IndexedSeq(raw2))

    val result = TemporalInterpolator.interpolateGrids(gridBefore, gridAfter, 0.5)

    // With fraction 0.5, should get midpoints
    assert(result(0)(0).surface.temperature.toCelsiusDegrees == 15.0)
    assert(result(0)(0).surface.dewPoint.toCelsiusDegrees == 10.0)
    assert(result(0)(0).surface.wind.u.toMetersPerSecond == 4.0)
    assert(result(0)(0).surface.wind.v.toMetersPerSecond == 2.0)
    assert(result(0)(0).surface.pressure.toPascals == 101000.0)
    assert(result(0)(0).surface.snowDepth.toMeters == 0.05)

    assert(result(0)(0).boundary.pblDepth.toMeters == 1500.0)
    assert(result(0)(0).boundary.wind.u.toMetersPerSecond == 3.0)
    assert(result(0)(0).boundary.sensibleHeatFlux.toWattsPerSquareMeter == 150.0)
    assert(result(0)(0).boundary.latentHeatFlux.toWattsPerSquareMeter == 75.0)

    assert(result(0)(0).atmosphere.totalCloudCover == 50)
    assert(result(0)(0).atmosphere.convectiveCloudCover == 30)
    assert(result(0)(0).atmosphere.lowCloudCover == Some(20))
    assert(result(0)(0).atmosphere.solarRadiation.toWattsPerSquareMeter == 300.0)

    assert(result(0)(0).profiles.isothermZero == Some(Meters(3000)))
  }

  test("interpolateGrids validates fraction range") {
    val surface = SurfaceData(
      elevation = Meters(100),
      temperature = Celsius(10),
      dewPoint = Celsius(5),
      wind = Wind(MetersPerSecond(1), MetersPerSecond(0)),
      pressure = Pascals(101325),
      snowDepth = Meters(0)
    )
    val boundary = BoundaryLayerData(
      pblDepth = Meters(1000),
      wind = Wind(MetersPerSecond(2), MetersPerSecond(0)),
      sensibleHeatFlux = WattsPerSquareMeter(100),
      latentHeatFlux = WattsPerSquareMeter(50)
    )
    val atmosphere = AtmosphereData(
      totalCloudCover = 50,
      convectiveCloudCover = 30,
      lowCloudCover = None,
      mediumCloudCover = None,
      highCloudCover = None,
      totalRain = Meters(0.001),
      convectiveRain = Meters(0.0005),
      solarRadiation = WattsPerSquareMeter(500),
      cape = None,
      cin = None
    )
    val profiles = VerticalProfiles(
      temperatureProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      dewPointProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      windProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      cloudCoverProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      isothermZero = None,
      airDataByAltitude = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters))
    )

    val raw = RawData(surface, boundary, atmosphere, profiles)
    val grid = IndexedSeq(IndexedSeq(raw))

    // Test fraction < 0
    try {
      TemporalInterpolator.interpolateGrids(grid, grid, -0.1)
      assert(false, "Should have thrown IllegalArgumentException")
    } catch {
      case _: IllegalArgumentException => assert(true)
    }

    // Test fraction > 1
    try {
      TemporalInterpolator.interpolateGrids(grid, grid, 1.1)
      assert(false, "Should have thrown IllegalArgumentException")
    } catch {
      case _: IllegalArgumentException => assert(true)
    }
  }

  test("interpolateGrids validates grid sizes match") {
    val surface = SurfaceData(
      elevation = Meters(100),
      temperature = Celsius(10),
      dewPoint = Celsius(5),
      wind = Wind(MetersPerSecond(1), MetersPerSecond(0)),
      pressure = Pascals(101325),
      snowDepth = Meters(0)
    )
    val boundary = BoundaryLayerData(
      pblDepth = Meters(1000),
      wind = Wind(MetersPerSecond(2), MetersPerSecond(0)),
      sensibleHeatFlux = WattsPerSquareMeter(100),
      latentHeatFlux = WattsPerSquareMeter(50)
    )
    val atmosphere = AtmosphereData(
      totalCloudCover = 50,
      convectiveCloudCover = 30,
      lowCloudCover = None,
      mediumCloudCover = None,
      highCloudCover = None,
      totalRain = Meters(0.001),
      convectiveRain = Meters(0.0005),
      solarRadiation = WattsPerSquareMeter(500),
      cape = None,
      cin = None
    )
    val profiles = VerticalProfiles(
      temperatureProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      dewPointProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      windProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      cloudCoverProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      isothermZero = None,
      airDataByAltitude = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters))
    )

    val raw = RawData(surface, boundary, atmosphere, profiles)
    val grid1 = IndexedSeq(IndexedSeq(raw))
    val grid2 = IndexedSeq(IndexedSeq(raw), IndexedSeq(raw))

    // Test mismatched grid sizes
    try {
      TemporalInterpolator.interpolateGrids(grid1, grid2, 0.5)
      assert(false, "Should have thrown IllegalArgumentException")
    } catch {
      case _: IllegalArgumentException => assert(true)
    }
  }

  test("interpolateGrids with 2x2 grid") {
    val surface1 = SurfaceData(
      elevation = Meters(100),
      temperature = Celsius(10),
      dewPoint = Celsius(5),
      wind = Wind(MetersPerSecond(2), MetersPerSecond(0)),
      pressure = Pascals(101325),
      snowDepth = Meters(0)
    )
    val surface2 = SurfaceData(
      elevation = Meters(100),
      temperature = Celsius(20),
      dewPoint = Celsius(15),
      wind = Wind(MetersPerSecond(6), MetersPerSecond(0)),
      pressure = Pascals(101325),
      snowDepth = Meters(0)
    )

    val boundary1 = BoundaryLayerData(
      pblDepth = Meters(1000),
      wind = Wind(MetersPerSecond(2), MetersPerSecond(0)),
      sensibleHeatFlux = WattsPerSquareMeter(100),
      latentHeatFlux = WattsPerSquareMeter(50)
    )
    val boundary2 = BoundaryLayerData(
      pblDepth = Meters(2000),
      wind = Wind(MetersPerSecond(4), MetersPerSecond(0)),
      sensibleHeatFlux = WattsPerSquareMeter(200),
      latentHeatFlux = WattsPerSquareMeter(100)
    )

    val atmosphere1 = AtmosphereData(
      totalCloudCover = 50,
      convectiveCloudCover = 30,
      lowCloudCover = None,
      mediumCloudCover = None,
      highCloudCover = None,
      totalRain = Meters(0.001),
      convectiveRain = Meters(0.0005),
      solarRadiation = WattsPerSquareMeter(500),
      cape = None,
      cin = None
    )
    val atmosphere2 = AtmosphereData(
      totalCloudCover = 80,
      convectiveCloudCover = 60,
      lowCloudCover = None,
      mediumCloudCover = None,
      highCloudCover = None,
      totalRain = Meters(0.005),
      convectiveRain = Meters(0.003),
      solarRadiation = WattsPerSquareMeter(300),
      cape = None,
      cin = None
    )

    val profiles1 = VerticalProfiles(
      temperatureProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      dewPointProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      windProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      cloudCoverProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      isothermZero = None,
      airDataByAltitude = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters))
    )
    val profiles2 = VerticalProfiles(
      temperatureProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      dewPointProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      windProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      cloudCoverProfile = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters)),
      isothermZero = None,
      airDataByAltitude = SortedMap.empty(Ordering.by[squants.space.Length, Double](_.toMeters))
    )

    val raw1 = RawData(surface1, boundary1, atmosphere1, profiles1)
    val raw2 = RawData(surface2, boundary2, atmosphere2, profiles2)

    val gridBefore = IndexedSeq(
      IndexedSeq(raw1, raw1),
      IndexedSeq(raw1, raw1)
    )
    val gridAfter = IndexedSeq(
      IndexedSeq(raw2, raw2),
      IndexedSeq(raw2, raw2)
    )

    val result = TemporalInterpolator.interpolateGrids(gridBefore, gridAfter, 0.5)

    // Verify all cells are interpolated
    assert(result.size == 2)
    assert(result(0).size == 2)
    assert(result(1).size == 2)

    assert(result(0)(0).surface.temperature.toCelsiusDegrees == 15.0)
    assert(result(0)(1).surface.temperature.toCelsiusDegrees == 15.0)
    assert(result(1)(0).surface.temperature.toCelsiusDegrees == 15.0)
    assert(result(1)(1).surface.temperature.toCelsiusDegrees == 15.0)
  }
}
