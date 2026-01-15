package org.soaringmeteo.out

import geotrellis.vector.Extent
import verify.BasicTestSuite

import java.time.{OffsetDateTime, Period}

object ForecastMetadataTestSuite extends BasicTestSuite {

  private def createZone(id: String): ForecastMetadata.Zone = {
    ForecastMetadata.Zone(
      id = id,
      label = s"Zone $id",
      raster = ForecastMetadata.Raster(
        projection = "EPSG:3857",
        resolution = BigDecimal(2000.0),
        extent = Extent(0, 0, 100, 100)
      ),
      vectorTiles = ForecastMetadata.VectorTiles(
        extent = Extent(0, 0, 100, 100),
        zoomLevels = 8,
        minZoom = 6,
        tileSize = 512
      )
    )
  }

  test("updateForecasts with no previous forecasts") {
    val forecast = ForecastMetadata("foo", OffsetDateTime.now(), None, 42, Seq(createZone("z1")))
    val forecasts = ForecastMetadata.updateForecasts(forecast, Nil, Period.ofDays(1))
    assert(forecasts == Seq(forecast))
  }

  test("updateForecast with older previous forecasts") {
    val forecast = ForecastMetadata("foo", OffsetDateTime.now(), None, 42, Seq(createZone("z1")))
    val previousForecasts = Seq(
      ForecastMetadata("bar", forecast.initDateTime.minusHours(12), None, 42, Seq(createZone("z1")))
    )
    val forecasts = ForecastMetadata.updateForecasts(forecast, previousForecasts, Period.ofDays(1))
    assert(forecasts == (previousForecasts ++ Seq(forecast)))
  }

  test("updateForecast with previous forecasts going further in the future than the latest forecast") {
    val forecast = ForecastMetadata("foo", OffsetDateTime.now(), None, 42, Seq(createZone("z1")))
    val previousForecasts = Seq(
      ForecastMetadata("bar", forecast.initDateTime.minusHours(12), Some(OffsetDateTime.now().plusDays(1)), 42, Seq(createZone("z1")))
    )
    val forecasts = ForecastMetadata.updateForecasts(forecast, previousForecasts, Period.ofDays(1))
    assert(forecasts == (Seq(forecast) ++ previousForecasts))
  }

  test("updateForecast with expired previous forecasts") {
    val forecast = ForecastMetadata("foo", OffsetDateTime.now(), None, 42, Seq(createZone("z1")))
    val previousForecasts = Seq(
      ForecastMetadata("bar", forecast.initDateTime.minusDays(2), None, 42, Seq(createZone("z1")))
    )
    val forecasts = ForecastMetadata.updateForecasts(forecast, previousForecasts, Period.ofDays(1))
    assert(forecasts == Seq(forecast))
  }

  test("updateForecast with previous forecast having the same start time as the latest forecast") {
    val forecast = ForecastMetadata("foo", OffsetDateTime.now(), None, 42, Seq(createZone("z1")))
    val previousForecasts = Seq(
      ForecastMetadata("bar", forecast.initDateTime.minusDays(1), Some(forecast.firstDateTime), 42, Seq(createZone("z1")))
    )
    val forecasts = ForecastMetadata.updateForecasts(forecast, previousForecasts, Period.ofDays(2))
    assert(forecasts == Seq(forecast))
  }

  test("updateForecast with multiple previous forecasts") {
    val now = OffsetDateTime.now()
    val forecast = ForecastMetadata("latest", now, None, 24, Seq(createZone("z1")))
    val previousForecasts = Seq(
      ForecastMetadata("old1", now.minusHours(24), None, 24, Seq(createZone("z1"))),
      ForecastMetadata("old2", now.minusHours(18), None, 24, Seq(createZone("z1"))),
      ForecastMetadata("old3", now.minusHours(12), None, 24, Seq(createZone("z1"))),
      ForecastMetadata("old4", now.minusHours(6), None, 24, Seq(createZone("z1")))
    )
    val forecasts = ForecastMetadata.updateForecasts(forecast, previousForecasts, Period.ofDays(1))

    // All previous forecasts should be kept (within retention period), and latest added
    assert(forecasts.size == 5)
    assert(forecasts.last == forecast)
  }

  test("updateForecast removes forecasts outside retention period") {
    val now = OffsetDateTime.now()
    val forecast = ForecastMetadata("latest", now, None, 24, Seq(createZone("z1")))
    val previousForecasts = Seq(
      ForecastMetadata("expired", now.minusDays(3), None, 24, Seq(createZone("z1"))),
      ForecastMetadata("kept1", now.minusHours(12), None, 24, Seq(createZone("z1"))),
      ForecastMetadata("kept2", now.minusHours(6), None, 24, Seq(createZone("z1")))
    )
    val forecasts = ForecastMetadata.updateForecasts(forecast, previousForecasts, Period.ofDays(1))

    // Only the forecasts within retention period should be kept
    assert(forecasts.size == 3)
    assert(!forecasts.exists(_.dataPath == "expired"))
    assert(forecasts.exists(_.dataPath == "kept1"))
    assert(forecasts.exists(_.dataPath == "kept2"))
    assert(forecasts.exists(_.dataPath == "latest"))
  }

  test("updateForecast maintains sorted order by firstDateTime") {
    val now = OffsetDateTime.now()
    val forecast = ForecastMetadata("latest", now, None, 24, Seq(createZone("z1")))
    val previousForecasts = Seq(
      ForecastMetadata("old1", now.minusHours(18), None, 24, Seq(createZone("z1"))),
      ForecastMetadata("old2", now.minusHours(12), None, 24, Seq(createZone("z1"))),
      ForecastMetadata("old3", now.minusHours(6), None, 24, Seq(createZone("z1")))
    )
    val forecasts = ForecastMetadata.updateForecasts(forecast, previousForecasts, Period.ofDays(1))

    // Check that forecasts are sorted by firstDateTime
    val sortedForecasts = forecasts.sortBy(_.firstDateTime)
    assert(forecasts == sortedForecasts)
  }

  test("updateForecast replaces forecast with same firstDateTime") {
    val now = OffsetDateTime.now()
    val sameFirstDateTime = now.minusHours(6)

    val forecast = ForecastMetadata("latest", now, Some(sameFirstDateTime), 24, Seq(createZone("z1")))
    val previousForecasts = Seq(
      ForecastMetadata("old1", now.minusHours(12), None, 24, Seq(createZone("z1"))),
      ForecastMetadata("duplicate", now.minusHours(10), Some(sameFirstDateTime), 24, Seq(createZone("z1"))),
      ForecastMetadata("old2", now.minusHours(3), None, 24, Seq(createZone("z1")))
    )
    val forecasts = ForecastMetadata.updateForecasts(forecast, previousForecasts, Period.ofDays(1))

    // The 'duplicate' should be replaced by 'latest'
    assert(forecasts.size == 3)
    assert(!forecasts.exists(_.dataPath == "duplicate"))
    assert(forecasts.exists(_.dataPath == "latest"))
    assert(forecasts.count(f => f.firstDateTime == sameFirstDateTime) == 1)
  }

  test("firstDateTime returns initDateTime when maybeFirstTimeStep is None") {
    val initTime = OffsetDateTime.now()
    val forecast = ForecastMetadata("test", initTime, None, 24, Seq(createZone("z1")))
    assert(forecast.firstDateTime == initTime)
  }

  test("firstDateTime returns maybeFirstTimeStep when defined") {
    val initTime = OffsetDateTime.now()
    val firstTimeStep = initTime.plusHours(3)
    val forecast = ForecastMetadata("test", initTime, Some(firstTimeStep), 24, Seq(createZone("z1")))
    assert(forecast.firstDateTime == firstTimeStep)
  }
}
