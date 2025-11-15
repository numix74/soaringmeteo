package org.soaringmeteo.arome

/**
 * Atmospheric data at a specific altitude level for AROME vertical profiles.
 * @param u Wind u-component (m/s)
 * @param v Wind v-component (m/s)
 * @param temperature Temperature (K)
 * @param dewPoint Dew point temperature (K)
 * @param cloudCover Cloud cover (0-1 fraction)
 */
case class AromeAirData(
  u: Double,
  v: Double,
  temperature: Double,
  dewPoint: Double,
  cloudCover: Double
)

/**
 * Complete AROME meteorological data for a single grid point and timestep.
 * Includes surface fields, vertical profiles, and derived parameters.
 */
case class AromeData(
  // Surface temperature and winds
  t2m: Double,
  u10: Double,
  v10: Double,

  // Boundary layer and convection
  pblh: Double,
  cape: Double,

  // Surface fluxes
  sensibleHeatFlux: Double,
  latentHeatFlux: Double,
  solarRadiation: Double,

  // Cloud and precipitation
  cloudCover: Double,
  totalRain: Double = 0.0,  // mm
  snowDepth: Double = 0.0,  // m

  // Surface humidity and pressure
  dewPoint2m: Double = 273.15,  // K (default to 0°C if missing)
  mslet: Double = 101325.0,  // Pa (default to 1013.25 hPa if missing)

  // Altitude markers
  isothermZero: Option[Double] = None,  // m AMSL
  terrainElevation: Double = 0.0,  // m AMSL

  // Winds at discrete heights (for wind barbs)
  windsAtHeights: Map[Int, (Double, Double)] = Map.empty,  // height (m AGL) -> (u, v) in m/s

  // Vertical profiles (for soundings)
  airDataByAltitude: Map[Int, AromeAirData] = Map.empty  // altitude (m AMSL) -> AromeAirData
) {
  def thermalVelocity: Double = {
    val g = 9.81
    val rho = 1.2
    val cp = 1004.0
    val heatFluxInstant = Math.max(sensibleHeatFlux / 3600.0, 0.0)
    if (pblh > 0 && heatFluxInstant > 0 && t2m > 0) {
      val wStar = Math.pow((g / t2m) * (heatFluxInstant / (rho * cp)) * pblh, 1.0 / 3.0)
      Math.max(0.0, Math.min(wStar, 8.0))
    } else 0.0
  }
  def windSpeed: Double = Math.sqrt(u10 * u10 + v10 * v10)
  def windDirection: Double = {
    val dir = Math.toDegrees(Math.atan2(v10, u10))
    (270 - dir) % 360
  }
  def t2mCelsius: Double = t2m - 273.15
  def dewPoint2mCelsius: Double = dewPoint2m - 273.15
  def msletHPa: Double = mslet / 100.0  // Pa -> hPa
  def windAtHeight(heightAGL: Int): Option[(Double, Double)] = windsAtHeights.get(heightAGL)
  def windAtHeightInterpolated(targetHeightAGL: Double): Option[(Double, Double)] = {
    if (windsAtHeights.isEmpty) return None
    val heights = windsAtHeights.keys.toSeq.sorted
    if (targetHeightAGL <= heights.head) {
      windsAtHeights.get(heights.head)
    } else if (targetHeightAGL >= heights.last) {
      windsAtHeights.get(heights.last)
    } else {
      val idx = heights.indexWhere(_ > targetHeightAGL)
      val h1 = heights(idx - 1)
      val h2 = heights(idx)
      val (u1, v1) = windsAtHeights(h1)
      val (u2, v2) = windsAtHeights(h2)
      val ratio = (targetHeightAGL - h1) / (h2 - h1)
      val u = u1 + ratio * (u2 - u1)
      val v = v1 + ratio * (v2 - v1)
      Some((u, v))
    }
  }
  def windAtAltitudeAMSL(altitudeAMSL: Double): Option[(Double, Double)] = {
    val heightAGL = altitudeAMSL - terrainElevation
    if (heightAGL < 0) None else windAtHeightInterpolated(heightAGL)
  }
  def windSpeedAndDirectionAtHeight(heightAGL: Int): Option[(Double, Double)] = {
    windAtHeight(heightAGL).map { case (u, v) =>
      val speed = Math.sqrt(u * u + v * v)
      val direction = { val dir = Math.toDegrees(Math.atan2(v, u)); (270 - dir) % 360 }
      (speed, direction)
    }
  }
  def keyWinds: Map[String, Option[(Double, Double)]] = Map(
    "300m_AGL" -> windAtHeightInterpolated(300.0),
    "500m_AGL" -> windAtHeightInterpolated(500.0),
    "1000m_AGL" -> windAtHeight(1000),
    "2000m_AMSL" -> windAtAltitudeAMSL(2000),
    "3000m_AMSL" -> windAtAltitudeAMSL(3000),
    "4000m_AMSL" -> windAtAltitudeAMSL(4000)
  )
}
