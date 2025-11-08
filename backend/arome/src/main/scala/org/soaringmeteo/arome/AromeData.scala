package org.soaringmeteo.arome

case class AromeData(
  t2m: Double,
  u10: Double,
  v10: Double,
  pblh: Double,
  cape: Double,
  sensibleHeatFlux: Double,
  latentHeatFlux: Double,
  solarRadiation: Double,
  cloudCover: Double,
  terrainElevation: Double = 0.0,
  windsAtHeights: Map[Int, (Double, Double)] = Map.empty
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
