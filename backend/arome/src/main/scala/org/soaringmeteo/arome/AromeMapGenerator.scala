package org.soaringmeteo.arome

import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.awt.{Color, Font, Graphics2D, RenderingHints}
import javax.imageio.ImageIO
import java.io.File

object AromeMapGenerator {
  
  private val logger = LoggerFactory.getLogger(getClass)
  
  case class MapConfig(
    title: String,
    unit: String,
    colorScale: Seq[(Double, Color)],
    valueExtractor: AromeData => Double
  )
  
  val configs = Map(
    "thermals" -> MapConfig(
      "Thermiques (W*)",
      "m/s",
      Seq(
        (0.0, new Color(200, 200, 255)),
        (1.0, new Color(150, 200, 255)),
        (2.0, new Color(100, 255, 100)),
        (3.0, new Color(255, 255, 100)),
        (4.0, new Color(255, 150, 50)),
        (5.0, new Color(255, 50, 50)),
        (8.0, new Color(150, 0, 0))
      ),
      _.thermalVelocity
    ),
    
    "pblh" -> MapConfig(
      "Hauteur plafond",
      "m",
      Seq(
        (0.0, new Color(150, 150, 200)),
        (500.0, new Color(150, 200, 255)),
        (1000.0, new Color(100, 255, 200)),
        (1500.0, new Color(255, 255, 150)),
        (2000.0, new Color(255, 200, 100)),
        (3000.0, new Color(255, 100, 50))
      ),
      _.pblh
    ),
    
    "wind" -> MapConfig(
      "Vent 10m",
      "km/h",
      Seq(
        (0.0, new Color(200, 255, 200)),
        (10.0, new Color(150, 255, 150)),
        (20.0, new Color(255, 255, 100)),
        (30.0, new Color(255, 200, 100)),
        (40.0, new Color(255, 100, 50)),
        (50.0, new Color(200, 50, 50))
      ),
      data => data.windSpeed * 3.6 // m/s → km/h
    ),
    
    "cape" -> MapConfig(
      "CAPE",
      "J/kg",
      Seq(
        (0.0, new Color(240, 240, 255)),
        (500.0, new Color(150, 200, 255)),
        (1000.0, new Color(100, 255, 150)),
        (1500.0, new Color(255, 255, 100)),
        (2000.0, new Color(255, 150, 50)),
        (3000.0, new Color(200, 50, 50))
      ),
      _.cape
    )
  )
  
  def generateMap(
    data: IndexedSeq[IndexedSeq[AromeData]],
    zone: AromeZone,
    paramName: String,
    outputFile: os.Path,
    hour: Int
  ): Unit = {
    
    val config = configs.getOrElse(paramName, throw new Exception(s"Unknown param: $paramName"))
    logger.info(s"Génération carte $paramName pour heure $hour...")
    
    val width = 1200
    val height = 800
    val img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    
    // Anti-aliasing
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    
    // Fond blanc
    g.setColor(Color.WHITE)
    g.fillRect(0, 0, width, height)
    
    // Marges
    val marginLeft = 80
    val marginRight = 150
    val marginTop = 80
    val marginBottom = 60
    
    val plotWidth = width - marginLeft - marginRight
    val plotHeight = height - marginTop - marginBottom
    
    // Dessiner les données
    val lonStep = plotWidth.toDouble / data.length
    val latStep = plotHeight.toDouble / data.head.length
    
    data.zipWithIndex.foreach { case (lonSlice, lonIdx) =>
      lonSlice.zipWithIndex.foreach { case (point, latIdx) =>
        val value = config.valueExtractor(point)
        val color = interpolateColor(value, config.colorScale)
        
        val x = marginLeft + (lonIdx * lonStep).toInt
        val y = marginTop + plotHeight - (latIdx * latStep).toInt
        
        g.setColor(color)
        g.fillRect(x, y, Math.ceil(lonStep).toInt + 1, Math.ceil(latStep).toInt + 1)
      }
    }
    
    // Titre
    g.setColor(Color.BLACK)
    g.setFont(new Font("Arial", Font.BOLD, 24))
    g.drawString(s"${config.title} - Heure +$hour", marginLeft, 40)
    
    // Légende
    drawLegend(g, config, width - marginRight + 20, marginTop, 100, plotHeight)
    
    g.dispose()
    
    // Sauvegarder
    os.makeDir.all(outputFile / os.up)
    ImageIO.write(img, "png", outputFile.toIO)
    logger.info(s"  Carte sauvegardée: $outputFile")
  }
  
  private def interpolateColor(value: Double, scale: Seq[(Double, Color)]): Color = {
    if (value <= scale.head._1) return scale.head._2
    if (value >= scale.last._1) return scale.last._2
    
    val idx = scale.indexWhere(_._1 > value)
    val (v1, c1) = scale(idx - 1)
    val (v2, c2) = scale(idx)
    
    val ratio = (value - v1) / (v2 - v1)
    
    new Color(
      (c1.getRed + ratio * (c2.getRed - c1.getRed)).toInt,
      (c1.getGreen + ratio * (c2.getGreen - c1.getGreen)).toInt,
      (c1.getBlue + ratio * (c2.getBlue - c1.getBlue)).toInt
    )
  }
  
  private def drawLegend(g: Graphics2D, config: MapConfig, x: Int, y: Int, w: Int, h: Int): Unit = {
    val steps = 50
    val stepHeight = h / steps
    
    config.colorScale.sliding(2).foreach { case Seq((v1, c1), (v2, c2)) =>
      val y1 = y + h - ((v1 / config.colorScale.last._1) * h).toInt
      val y2 = y + h - ((v2 / config.colorScale.last._1) * h).toInt
      
      for (i <- 0 until (y1 - y2)) {
        val ratio = i.toDouble / (y1 - y2)
        val color = new Color(
          (c1.getRed + ratio * (c2.getRed - c1.getRed)).toInt,
          (c1.getGreen + ratio * (c2.getGreen - c1.getGreen)).toInt,
          (c1.getBlue + ratio * (c2.getBlue - c1.getBlue)).toInt
        )
        g.setColor(color)
        g.fillRect(x, y2 + i, w, 2)
      }
    }
    
    // Bordure légende
    g.setColor(Color.BLACK)
    g.drawRect(x, y, w, h)
    
    // Labels
    g.setFont(new Font("Arial", Font.PLAIN, 12))
    config.colorScale.foreach { case (value, _) =>
      val yPos = y + h - ((value / config.colorScale.last._1) * h).toInt
      g.drawString(f"$value%.0f", x + w + 5, yPos + 4)
    }
    
    // Unité
    g.setFont(new Font("Arial", Font.BOLD, 14))
    g.drawString(config.unit, x, y - 10)
  }
}
