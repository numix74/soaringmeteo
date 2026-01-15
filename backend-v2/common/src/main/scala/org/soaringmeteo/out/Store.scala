package org.soaringmeteo.out

import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject, parser}
import org.soaringmeteo.{AirData, UnifiedModelData, Winds}
import org.soaringmeteo.parsing.{AtmosphereData, BoundaryLayerData, SurfaceData}
import slick.jdbc.H2Profile.api._
import squants.energy.{Grays, SpecificEnergy}
import squants.motion.{MetersPerSecond, Pascals, Pressure, Velocity}
import squants.radio.WattsPerSquareMeter
import squants.space.{Meters, Length}
import squants.thermal.{Kelvin, Temperature}

import java.time.OffsetDateTime
import scala.collection.immutable.SortedMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * H2 database store for UnifiedModelData grids.
 *
 * Saves memory by storing processed grids in H2 instead of keeping
 * everything in memory. Similar to backend v1 Store but works with
 * UnifiedModelData instead of model-specific types.
 */
object Store {

  // Database connection - configured via unified.conf
  private var dbInstance: Option[Database] = None

  def initialize(jdbcUrl: String): Unit = {
    dbInstance = Some(Database.forURL(
      jdbcUrl,
      driver = "org.h2.Driver"
    ))
  }

  private def db: Database = dbInstance.getOrElse {
    // Fallback to default if not initialized
    Database.forURL(
      "jdbc:h2:file:./unified-data",
      driver = "org.h2.Driver"
    )
  }

  private class UnifiedGrids(tag: Tag) extends Table[(OffsetDateTime, String, String, Int, Int, Int, String)](tag, "unified_grids") {
    def initTime = column[OffsetDateTime]("init_time")
    def modelId = column[String]("model_id")  // "gfs", "arome", "wrf"
    def zone = column[String]("zone")
    def hourOffset = column[Int]("hour_offset")
    def x = column[Int]("x")
    def y = column[Int]("y")
    def data = column[String]("data")  // JSON
    def * = (initTime, modelId, zone, hourOffset, x, y, data)
    def pk = primaryKey("pk_unified", (initTime, modelId, zone, hourOffset, x, y))
    def spatialAccess = index("idx_unified_spatial", (initTime, modelId, zone, x, y))
  }

  private val unifiedGrids = TableQuery(tag => new UnifiedGrids(tag))

  def ensureSchemaExists(): Future[Unit] = {
    // Try to create schema, ignore error if table already exists
    db.run(unifiedGrids.schema.createIfNotExists)
      .recover {
        case e: org.h2.jdbc.JdbcSQLSyntaxErrorException if e.getMessage.contains("already exists") =>
          // Table already exists, this is fine
          ()
        case e: Exception =>
          // Log other errors but don't fail
          scala.Console.err.println(s"Warning: Schema check returned error: ${e.getMessage}")
          ()
      }
  }

  def close(): Unit = dbInstance.foreach(_.close())

  /**
   * Save a complete grid for a specific hour.
   * Replaces any existing data for the same (initTime, modelId, zone, hourOffset).
   */
  def save(
    initTime: OffsetDateTime,
    modelId: String,
    zone: String,
    hourOffset: Int,
    data: IndexedSeq[IndexedSeq[UnifiedModelData]]
  ): Future[Unit] = {
    val cleanupAction = unifiedGrids
      .filter(g =>
        g.initTime === initTime &&
        g.modelId === modelId &&
        g.zone === zone &&
        g.hourOffset === hourOffset
      )
      .delete

    val newRows = for {
      (row, x) <- data.zipWithIndex
      (unifiedData, y) <- row.zipWithIndex
    } yield (initTime, modelId, zone, hourOffset, x, y, encodeUnifiedModelData(unifiedData).noSpaces)

    val insertAction = unifiedGrids ++= newRows
    db.run(cleanupAction >> insertAction).map(_ => ())
  }

  /**
   * Check if a complete grid exists for a specific hour.
   */
  def exists(
    initTime: OffsetDateTime,
    modelId: String,
    zone: String,
    hourOffset: Int,
    width: Int,
    height: Int
  ): Future[Boolean] = {
    val action = unifiedGrids
      .filter(g =>
        g.initTime === initTime &&
        g.modelId === modelId &&
        g.zone === zone &&
        g.hourOffset === hourOffset
      )
      .length
      .result
    db.run(action).map(_ == width * height)
  }

  /**
   * Retrieve all data for a specific location (x, y) across all hour offsets.
   * Returns a Map of hourOffset -> UnifiedModelData.
   *
   * This is used for generating location JSON files.
   */
  def getDataForLocation(
    initTime: OffsetDateTime,
    modelId: String,
    zone: String,
    x: Int,
    y: Int
  ): Future[Map[Int, UnifiedModelData]] = {
    val action = unifiedGrids
      .filter(g =>
        g.initTime === initTime &&
        g.modelId === modelId &&
        g.zone === zone &&
        g.x === x &&
        g.y === y
      )
      .result

    db.run(action).map { rows =>
      rows.map { case (_, _, _, hourOffset, _, _, dataJson) =>
        val data = parser.parse(dataJson).flatMap(decodeUnifiedModelData).toOption
        hourOffset -> data
      }.collect { case (hour, Some(data)) => hour -> data }.toMap
    }
  }

  /**
   * Encode UnifiedModelData to JSON for storage.
   * All 28 fields encoded flat (no nested objects).
   */
  private def encodeUnifiedModelData(data: UnifiedModelData): Json = {
    Json.obj(
      // Temporal and spatial metadata
      "time" -> Json.fromString(data.time.toString),
      "elevation" -> encodeLength(data.elevation),

      // Boundary layer and thermals
      "boundaryLayerDepth" -> encodeLength(data.boundaryLayerDepth),
      "thermalVelocity" -> encodeVelocity(data.thermalVelocity),
      "soaringLayerDepth" -> encodeLength(data.soaringLayerDepth),

      // Surface and boundary layer winds
      "surfaceWind" -> encodeWind(data.surfaceWind),
      "boundaryLayerWind" -> encodeWind(data.boundaryLayerWind),
      // Note: winds (Winds type) - need to implement encoding if used

      // Temperature and humidity
      "surfaceTemperature" -> encodeTemperature(data.surfaceTemperature),
      "surfaceDewPoint" -> encodeTemperature(data.surfaceDewPoint),

      // Pressure
      "mslet" -> encodePressure(data.mslet),

      // Clouds and precipitation
      "totalCloudCover" -> Json.fromInt(data.totalCloudCover),
      "convectiveCloudCover" -> Json.fromInt(data.convectiveCloudCover),
      "lowCloudCover" -> encodeOptionalInt(data.lowCloudCover),
      "mediumCloudCover" -> encodeOptionalInt(data.mediumCloudCover),
      "highCloudCover" -> encodeOptionalInt(data.highCloudCover),
      // Note: convectiveClouds (ConvectiveClouds type) - need to implement encoding if used
      "totalRain" -> encodeLength(data.totalRain),
      "convectiveRain" -> encodeLength(data.convectiveRain),
      "snowDepth" -> encodeLength(data.snowDepth),

      // Energy fluxes (Irradiance type)
      "sensibleHeatNetFlux" -> Json.fromDoubleOrNull(data.sensibleHeatNetFlux.value),
      "latentHeatNetFlux" -> Json.fromDoubleOrNull(data.latentHeatNetFlux.value),
      "downwardShortWaveRadiationFlux" -> Json.fromDoubleOrNull(data.downwardShortWaveRadiationFlux.value),

      // Atmospheric stability
      "cape" -> encodeSpecificEnergy(data.cape),
      "cin" -> encodeSpecificEnergy(data.cin),

      // Isotherms
      "isothermZero" -> encodeOptionalLength(data.isothermZero),

      // Vertical profiles
      "airDataByAltitude" -> encodeAirDataByAltitude(data.airDataByAltitude.to(scala.collection.immutable.SortedMap)),

      // Derived products
      "xcFlyingPotential" -> Json.fromInt(data.xcFlyingPotential)
    )
  }

  /**
   * Decode UnifiedModelData from JSON.
   * Decodes all 24 flat fields that are encoded, then reconstructs winds from airDataByAltitude.
   */
  private def decodeUnifiedModelData(json: Json): Decoder.Result[UnifiedModelData] = {
    val cursor = json.hcursor

    for {
      // Temporal and spatial metadata
      timeStr <- cursor.downField("time").as[String]
      time <- scala.util.Try(OffsetDateTime.parse(timeStr)).toOption.toRight(
        DecodingFailure("Invalid OffsetDateTime", cursor.downField("time").history)
      )
      elevation <- cursor.downField("elevation").as[Double].map(Meters(_))

      // Boundary layer and thermals
      boundaryLayerDepth <- cursor.downField("boundaryLayerDepth").as[Double].map(Meters(_))
      thermalVelocity <- cursor.downField("thermalVelocity").as[Double].map(MetersPerSecond(_))
      soaringLayerDepth <- cursor.downField("soaringLayerDepth").as[Double].map(Meters(_))

      // Surface and boundary layer winds
      surfaceWind <- cursor.downField("surfaceWind").as[JsonObject].flatMap(decodeWind)
      boundaryLayerWind <- cursor.downField("boundaryLayerWind").as[JsonObject].flatMap(decodeWind)

      // Temperature and humidity
      surfaceTemperature <- cursor.downField("surfaceTemperature").as[Double].map(Kelvin(_))
      surfaceDewPoint <- cursor.downField("surfaceDewPoint").as[Double].map(Kelvin(_))

      // Pressure
      mslet <- cursor.downField("mslet").as[Double].map(Pascals(_))

      // Clouds and precipitation
      totalCloudCover <- cursor.downField("totalCloudCover").as[Int]
      convectiveCloudCover <- cursor.downField("convectiveCloudCover").as[Int]
      lowCloudCover <- cursor.downField("lowCloudCover").as[Option[Int]]
      mediumCloudCover <- cursor.downField("mediumCloudCover").as[Option[Int]]
      highCloudCover <- cursor.downField("highCloudCover").as[Option[Int]]
      totalRain <- cursor.downField("totalRain").as[Double].map(Meters(_))
      convectiveRain <- cursor.downField("convectiveRain").as[Double].map(Meters(_))
      snowDepth <- cursor.downField("snowDepth").as[Double].map(Meters(_))

      // Energy fluxes
      sensibleHeatNetFlux <- cursor.downField("sensibleHeatNetFlux").as[Double].map(WattsPerSquareMeter(_))
      latentHeatNetFlux <- cursor.downField("latentHeatNetFlux").as[Double].map(WattsPerSquareMeter(_))
      downwardShortWaveRadiationFlux <- cursor.downField("downwardShortWaveRadiationFlux").as[Double].map(WattsPerSquareMeter(_))

      // Atmospheric stability
      capeOpt <- cursor.downField("cape").as[Option[Double]]
      cape: Option[SpecificEnergy] = capeOpt.map(Grays(_))
      cinOpt <- cursor.downField("cin").as[Option[Double]]
      cin: Option[SpecificEnergy] = cinOpt.map(Grays(_))

      // Isotherms
      isothermZeroOpt <- cursor.downField("isothermZero").as[Option[Double]]
      isothermZero = isothermZeroOpt.map(Meters(_))

      // Vertical profiles
      airDataByAltitude <- cursor.downField("airDataByAltitude").as[JsonObject].flatMap(decodeAirDataByAltitude)

      // Derived products
      xcFlyingPotential <- cursor.downField("xcFlyingPotential").as[Int]

    } yield {
      // Reconstruct Winds from airDataByAltitude + elevation + soaringLayerDepth
      val winds = Winds(airDataByAltitude, elevation, soaringLayerDepth)

      UnifiedModelData(
        time = time,
        elevation = elevation,
        boundaryLayerDepth = boundaryLayerDepth,
        thermalVelocity = thermalVelocity,
        soaringLayerDepth = soaringLayerDepth,
        surfaceWind = surfaceWind,
        boundaryLayerWind = boundaryLayerWind,
        winds = winds,
        surfaceTemperature = surfaceTemperature,
        surfaceDewPoint = surfaceDewPoint,
        mslet = mslet,
        totalCloudCover = totalCloudCover,
        convectiveCloudCover = convectiveCloudCover,
        lowCloudCover = lowCloudCover,
        mediumCloudCover = mediumCloudCover,
        highCloudCover = highCloudCover,
        convectiveClouds = None,  // Not encoded/decoded (can be reconstructed if needed)
        totalRain = totalRain,
        convectiveRain = convectiveRain,
        snowDepth = snowDepth,
        sensibleHeatNetFlux = sensibleHeatNetFlux,
        latentHeatNetFlux = latentHeatNetFlux,
        downwardShortWaveRadiationFlux = downwardShortWaveRadiationFlux,
        cape = cape,
        cin = cin,
        isothermZero = isothermZero,
        airDataByAltitude = airDataByAltitude,
        xcFlyingPotential = xcFlyingPotential
      )
    }
  }

  // Encoding helpers (use .value for all Squants types - returns value in SI base units)
  private def encodeTemperature(t: Temperature): Json =
    Json.fromDoubleOrNull(t.value)  // Kelvin

  private def encodeVelocity(v: Velocity): Json =
    Json.fromDoubleOrNull(v.value)  // MetersPerSecond

  private def encodeLength(l: Length): Json =
    Json.fromDoubleOrNull(l.value)  // Meters

  private def encodeOptionalLength(ol: Option[Length]): Json =
    ol.fold(Json.Null)(l => Json.fromDoubleOrNull(l.value))

  private def encodeOptionalInt(oi: Option[Int]): Json =
    oi.fold(Json.Null)(Json.fromInt)

  private def encodeSpecificEnergy(se: Option[SpecificEnergy]): Json =
    se.fold(Json.Null)(e => Json.fromDoubleOrNull(e.value))  // JoulesPerKilogram

  private def encodePressure(p: Pressure): Json =
    Json.fromDoubleOrNull(p.value)  // Pascals

  private def encodeWind(wind: org.soaringmeteo.Wind): Json = Json.obj(
    "u" -> Json.fromDoubleOrNull(wind.u.value),
    "v" -> Json.fromDoubleOrNull(wind.v.value)
  )

  private def encodeAirDataByAltitude(airData: SortedMap[Length, AirData]): Json = {
    Json.obj(
      airData.map { case (altitude, ad) =>
        altitude.value.toString -> Json.obj(
          "wind" -> encodeWind(ad.wind),
          "temperature" -> encodeTemperature(ad.temperature),
          "dewPoint" -> encodeTemperature(ad.dewPoint),
          "cloudCover" -> Json.fromInt(ad.cloudCover)
        )
      }.toSeq: _*
    )
  }

  // Decoding helpers
  private def decodeWind(windObj: JsonObject): Decoder.Result[org.soaringmeteo.Wind] = {
    for {
      uNum <- windObj("u").flatMap(_.asNumber)
        .toRight(DecodingFailure("Missing u in wind", Nil))
      vNum <- windObj("v").flatMap(_.asNumber)
        .toRight(DecodingFailure("Missing v in wind", Nil))
    } yield org.soaringmeteo.Wind(MetersPerSecond(uNum.toDouble), MetersPerSecond(vNum.toDouble))
  }

  private def decodeAirDataByAltitude(obj: JsonObject): Decoder.Result[SortedMap[Length, AirData]] = {
    // Convert JsonObject to Map[String, JsonObject]
    val altitudesMap: Map[String, JsonObject] = obj.toMap.collect {
      case (key, value) if value.isObject => key -> value.asObject.get
    }

    val decoded = altitudesMap.iterator.map { case (altStr, airDataObj) =>
      for {
        altitude <- altStr.toDoubleOption
          .toRight(DecodingFailure(s"Invalid altitude: $altStr", Nil))

        windObj <- airDataObj("wind").flatMap(_.asObject)
          .toRight(DecodingFailure("Missing wind", Nil))
        uNum <- windObj("u").flatMap(_.asNumber)
          .toRight(DecodingFailure("Missing u", Nil))
        vNum <- windObj("v").flatMap(_.asNumber)
          .toRight(DecodingFailure("Missing v", Nil))

        tempNum <- airDataObj("temperature").flatMap(_.asNumber)
          .toRight(DecodingFailure("Missing temperature", Nil))
        dewPointNum <- airDataObj("dewPoint").flatMap(_.asNumber)
          .toRight(DecodingFailure("Missing dewPoint", Nil))
        cloudCoverNum <- airDataObj("cloudCover").flatMap(_.asNumber)
          .toRight(DecodingFailure("Missing cloudCover", Nil))
        cloudCover <- cloudCoverNum.toInt
          .toRight(DecodingFailure("Cannot convert cloudCover to int", Nil))
      } yield {
        val airData = AirData(
          wind = org.soaringmeteo.Wind(MetersPerSecond(uNum.toDouble), MetersPerSecond(vNum.toDouble)),
          temperature = Kelvin(tempNum.toDouble),
          dewPoint = Kelvin(dewPointNum.toDouble),
          cloudCover = cloudCover
        )
        Meters(altitude) -> airData
      }
    }.toSeq

    // Check if any decoding failed
    decoded.collectFirst { case Left(err) => Left(err) }
      .getOrElse(Right(SortedMap(decoded.collect { case Right(pair) => pair }: _*)))
  }

  // Old decoding helpers - can be removed later if not used elsewhere
  private def decodeSurfaceTemp(obj: JsonObject, key: String): Decoder.Result[Temperature] =
    obj(key).toRight(DecodingFailure(s"Missing $key", Nil))
      .flatMap(_.asNumber.toRight(DecodingFailure(s"Not a number: $key", Nil)))
      .map(n => Kelvin(n.toDouble))

  private def decodeSurfaceWind(obj: JsonObject, key: String): Decoder.Result[org.soaringmeteo.Wind] = {
    obj(key).toRight(DecodingFailure(s"Missing $key", Nil))
      .flatMap(_.asObject.toRight(DecodingFailure(s"Not an object: $key", Nil)))
      .flatMap { windObj =>
        for {
          uNum <- windObj("u").flatMap(_.asNumber)
            .toRight(DecodingFailure("Missing u", Nil))
          vNum <- windObj("v").flatMap(_.asNumber)
            .toRight(DecodingFailure("Missing v", Nil))
        } yield org.soaringmeteo.Wind(MetersPerSecond(uNum.toDouble), MetersPerSecond(vNum.toDouble))
      }
  }

  private def decodeSurfaceDouble(obj: JsonObject, key: String): Decoder.Result[Double] =
    obj(key).toRight(DecodingFailure(s"Missing $key", Nil))
      .flatMap(_.asNumber.toRight(DecodingFailure(s"Not a number: $key", Nil)))
      .map(n => n.toDouble)

  private def decodeSurfaceLength(obj: JsonObject, key: String): Decoder.Result[Length] =
    decodeSurfaceDouble(obj, key).map(Meters(_))

  private def decodeBoundaryLength(obj: JsonObject, key: String): Decoder.Result[Length] =
    obj(key).toRight(DecodingFailure(s"Missing $key", Nil))
      .flatMap(_.asNumber.toRight(DecodingFailure(s"Not a number: $key", Nil)))
      .map(n => Meters(n.toDouble))

  private def decodeBoundaryCape(obj: JsonObject, key: String): Decoder.Result[Option[SpecificEnergy]] =
    obj(key).toRight(DecodingFailure(s"Missing $key", Nil))
      .flatMap {
        case json if json.isNull => Right(None)
        case json => json.asNumber
          .toRight(DecodingFailure(s"Not a number: $key", Nil))
          .map(n => SpecificEnergy(n.toDouble).toOption)
      }

  private def decodeBoundaryDouble(obj: JsonObject, key: String): Decoder.Result[Double] =
    obj(key).toRight(DecodingFailure(s"Missing $key", Nil))
      .flatMap(_.asNumber.toRight(DecodingFailure(s"Not a number: $key", Nil)))
      .map(n => n.toDouble)

  private def decodeAtmosphereOptLength(obj: JsonObject, key: String): Decoder.Result[Option[Length]] =
    obj(key).toRight(DecodingFailure(s"Missing $key", Nil))
      .flatMap {
        case json if json.isNull => Right(None)
        case json => json.asNumber
          .toRight(DecodingFailure(s"Not a number: $key", Nil))
          .map(n => Some(Meters(n.toDouble)))
      }

  private def decodeAtmosphereOptInt(obj: JsonObject, key: String): Decoder.Result[Option[Int]] =
    obj(key).toRight(DecodingFailure(s"Missing $key", Nil))
      .flatMap {
        case json if json.isNull => Right(None)
        case json => json.asNumber
          .toRight(DecodingFailure(s"Not a number: $key", Nil))
          .map(n => n.toInt)
      }
}
