package org.soaringmeteo.arome.out

import io.circe.{Codec, Decoder, Encoder, Json, JsonObject, parser}
import org.soaringmeteo.{Wind, MeteoData}
import org.soaringmeteo.arome.AromeData
import slick.jdbc.H2Profile.api._
import squants.motion.MetersPerSecond
import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Store {
  // Fix: Use forURL instead of forConfig to avoid HikariCP dependency
  private val db = Database.forURL(
    "jdbc:h2:file:/tmp/arome.h2",
    driver = "org.h2.Driver"
  )

  private class AromeGrids(tag: Tag) extends Table[(OffsetDateTime, String, Int, Int, Int, String)](tag, "arome_grids") {
    def initTime = column[OffsetDateTime]("init_time")
    def zone = column[String]("zone")
    def hourOffset = column[Int]("hour_offset")
    def x = column[Int]("x")
    def y = column[Int]("y")
    def data = column[String]("data")
    def * = (initTime, zone, hourOffset, x, y, data)
    def pk = primaryKey("pk_arome", (initTime, zone, hourOffset, x, y))
    def spatialAccess = index("idx_arome_spatial", (initTime, zone, x, y))
  }

  private val aromeGrids = TableQuery(tag => new AromeGrids(tag))

  def ensureSchemaExists(): Future[Unit] = {
    for {
      exists <- db.run(
        sql"""
          SELECT EXISTS (
            SELECT * FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'arome_grids'
          )
        """.as[Boolean].head)
      _ <- if (exists) Future.successful(()) else db.run(aromeGrids.schema.create)
    } yield ()
  }

  def close(): Unit = db.close()

  def save(initTime: OffsetDateTime, zone: String, hourOffset: Int, data: IndexedSeq[IndexedSeq[AromeData]]): Future[Unit] = {
    val cleanupAction = aromeGrids
      .filter(g => g.initTime === initTime && g.zone === zone && g.hourOffset === hourOffset)
      .delete
    val newRows = for {
      (row, x) <- data.zipWithIndex
      (aromeData, y) <- row.zipWithIndex
    } yield (initTime, zone, hourOffset, x, y, encodeAromeData(aromeData).noSpaces)
    val insertAction = aromeGrids ++= newRows
    db.run(cleanupAction >> insertAction).map(_ => ())
  }

  def exists(initTime: OffsetDateTime, zone: String, hourOffset: Int, width: Int, height: Int): Future[Boolean] = {
    val action = aromeGrids
      .filter(g => g.initTime === initTime && g.zone === zone && g.hourOffset === hourOffset)
      .length
      .result
    db.run(action).map(_ == width * height)
  }

  private def encodeAromeData(data: AromeData): Json = {
    Json.obj(
      "t2m" -> Json.fromDoubleOrNull(data.t2m),
      "u10" -> Json.fromDoubleOrNull(data.u10),
      "v10" -> Json.fromDoubleOrNull(data.v10),
      "pblh" -> Json.fromDoubleOrNull(data.pblh),
      "cape" -> Json.fromDoubleOrNull(data.cape),
      "sensibleHeatFlux" -> Json.fromDoubleOrNull(data.sensibleHeatFlux),
      "latentHeatFlux" -> Json.fromDoubleOrNull(data.latentHeatFlux),
      "solarRadiation" -> Json.fromDoubleOrNull(data.solarRadiation),
      "cloudCover" -> Json.fromDoubleOrNull(data.cloudCover),
      "terrainElevation" -> Json.fromDoubleOrNull(data.terrainElevation),
      "thermalVelocity" -> Json.fromDoubleOrNull(data.thermalVelocity),
      "windSpeed10m" -> Json.fromDoubleOrNull(data.windSpeed),
      "windDirection10m" -> Json.fromDoubleOrNull(data.windDirection),
      "windsAtHeights" -> Json.obj(
        data.windsAtHeights.map { case (height, (u, v)) =>
          height.toString -> Json.obj(
            "u" -> Json.fromDoubleOrNull(u),
            "v" -> Json.fromDoubleOrNull(v)
          )
        }.toSeq: _*
      )
    )
  }
}
