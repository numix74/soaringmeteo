package org.soaringmeteo

case class Extent(xMin: Double, yMin: Double, xMax: Double, yMax: Double) {
  def buffer(distance: Double): Extent =
    Extent(xMin - distance, yMin - distance, xMax + distance, yMax + distance)
  
  def width: Double = xMax - xMin
  def height: Double = yMax - yMin
}
