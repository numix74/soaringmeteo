package org.soaringmeteo.parsing

import org.slf4j.LoggerFactory
import org.soaringmeteo.{Point, Wind, Temperatures}
import org.soaringmeteo.grib.Grib
import squants.energy.{Grays, SpecificEnergy}
import squants.motion.{MetersPerSecond, Pascals, Pressure}
import squants.radio.{Irradiance, WattsPerSquareMeter}
import squants.space.{Length, Meters, Millimeters}
import squants.thermal.{Celsius, Kelvin, Temperature}

import scala.collection.SortedMap
import scala.util.Try

/**
 * Generic GRIB parser that works with any weather model.
 *
 * Uses ModelRegistry.ModelSpec to look up variable names and extract data
 * from GRIB/GRIB2 files in a model-agnostic way.
 *
 * Design:
 * - Accepts ParseableData (from FileHandler) with ModelSpec
 * - Extracts variables using names from ModelSpec.variables
 * - Returns RawData structures (SurfaceData, BoundaryLayerData, etc.)
 * - Handles optional variables (cape, cin) gracefully
 */
object GribParser {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Parse GRIB file(s) for a geographic grid.
   *
   * @param parseableData Data prepared by FileHandler
   * @param longitudes Exact longitude coordinates (west to east)
   * @param latitudes Exact latitude coordinates (north to south or south to north)
   * @return Grid of RawData (one per grid point)
   */
  def parseGrid(
    parseableData: handlers.ParseableData,
    longitudes: IndexedSeq[Double],
    latitudes: IndexedSeq[Double]
  ): IndexedSeq[IndexedSeq[RawData]] = {

    val modelSpec = parseableData.modelSpec

    logger.info(s"Parsing ${modelSpec.name} GRIB file: ${parseableData.primaryFile}")

    // Read actual grid from GRIB file instead of using preconfigured grid
    val (actualLongitudes, actualLatitudes) = Grib.bracket(parseableData.primaryFile) { grib =>
      // Use any variable to get the grid coordinates (they're all the same)
      val tempGrid = grib.findGridByShortName(modelSpec.variables.temperature2m)
      if (tempGrid == null) {
        throw new IllegalStateException(s"Cannot find temperature2m variable '${modelSpec.variables.temperature2m}' in GRIB file")
      }

      val coordSys = tempGrid.getCoordinateSystem
      val lonAxis = coordSys.getXHorizAxis.asInstanceOf[ucar.nc2.dataset.CoordinateAxis1D]
      val latAxis = coordSys.getYHorizAxis.asInstanceOf[ucar.nc2.dataset.CoordinateAxis1D]

      val lons = lonAxis.getCoordValues.toIndexedSeq  // Array[Double] -> IndexedSeq
      val lats = latAxis.getCoordValues.toIndexedSeq

      (lons, lats)
    }

    logger.info(s"  GRIB Grid: ${actualLongitudes.size} x ${actualLatitudes.size} points")
    logger.info(s"    Longitudes: ${actualLongitudes.head} to ${actualLongitudes.last} (step=${if (actualLongitudes.size > 1) actualLongitudes(1) - actualLongitudes(0) else 0})")
    logger.info(s"    Latitudes: ${actualLatitudes.head} to ${actualLatitudes.last} (step=${if (actualLatitudes.size > 1) actualLatitudes(1) - actualLatitudes(0) else 0})")
    logger.info(s"  Requested Grid: ${longitudes.size} x ${latitudes.size} points (IGNORED - using GRIB grid)")

    // Use actual grid from GRIB file
    val effectiveLongitudes = actualLongitudes
    val effectiveLatitudes = actualLatitudes

    // AROME uses grouped files (SP1/SP2/SP3), handle separately
    if (modelSpec.id == "arome") {
      logger.info("  Using AROME grouped file parser (SP1/SP2/SP3)")
      return parseAromeGrid(parseableData, longitudes, latitudes)
    }

    // Standard parsing for GFS, WRF, etc.
    val vars = modelSpec.variables
    Grib.bracket(parseableData.primaryFile) { grib =>
      import grib.Feature

      // === Surface Variables ===
      val temperature2m = Feature(vars.temperature2m)
      val uWind10m = Feature(vars.uWind10m)
      val vWind10m = Feature(vars.vWind10m)
      val pressure = Feature(vars.pressure)
      val geopotentialSurface = Feature(vars.geopotentialSurface)

      // Dew point: either direct or computed from RH
      val dewPoint2m: Option[Feature] = if (vars.dewPoint2m.nonEmpty) {
        Some(Feature(vars.dewPoint2m))
      } else {
        None
      }
      val relativeHumidity2m: Option[Feature] = vars.relativeHumidity2m.flatMap { name =>
        Feature.maybe(name)
      }

      // Snow depth (optional)
      val snowDepth: Option[Feature] = vars.snowDepth.flatMap { name =>
        Feature.maybe(name)
      }

      // === Boundary Layer ===
      val pblHeight = Feature(vars.pblHeight)
      val uWindPBL = Feature(vars.uWindPBL)
      val vWindPBL = Feature(vars.vWindPBL)

      // Fluxes - GFS changes variable names (3h vs 6h avg), use fallback like production backend
      val sensibleHeatFlux = Feature.maybe(vars.sensibleHeatFlux)
        .getOrElse(Feature(vars.sensibleHeatFlux.replace("3_Hour", "6_Hour")))
      val latentHeatFlux = Feature.maybe(vars.latentHeatFlux)
        .getOrElse(Feature(vars.latentHeatFlux.replace("3_Hour", "6_Hour")))

      // === Atmosphere ===
      val totalCloudCover = Feature.maybe(vars.totalCloudCover)
        .getOrElse(Feature(vars.totalCloudCover.replace("3_Hour", "6_Hour")))
      val convectiveCloudCover: Option[Feature] = vars.convectiveCloudCover.flatMap { name =>
        Feature.maybe(name)
      }

      // Cloud layers (AROME specific)
      val lowCloudCover: Option[Feature] = vars.lowCloudCover.flatMap { name =>
        Feature.maybe(name)
      }
      val mediumCloudCover: Option[Feature] = vars.mediumCloudCover.flatMap { name =>
        Feature.maybe(name)
      }
      val highCloudCover: Option[Feature] = vars.highCloudCover.flatMap { name =>
        Feature.maybe(name)
      }

      val totalPrecipRate = Feature(vars.totalPrecipRate)
      val convectivePrecipRate = Feature(vars.convectivePrecipRate)
      val solarRadiation = Feature.maybe(vars.solarRadiation)
        .getOrElse(Feature(vars.solarRadiation.replace("3_Hour", "6_Hour")))

      // Stability (optional - WRF doesn't have)
      val cape: Option[Feature] = vars.cape.flatMap { name =>
        Feature.maybe(name)
      }
      val cin: Option[Feature] = vars.cin.flatMap { name =>
        Feature.maybe(name)
      }

      // Isotherm (zero degree)
      val geopotentialZeroDegC = Feature(vars.geopotentialZeroDegC)

      // === Isobaric Levels ===
      // Build features for each pressure level
      type IsobaricTuple = (Feature, Feature, Feature, Feature, Feature, Feature)
      val isobaricFeatures: Map[Pressure, IsobaricTuple] = if (modelSpec.pressureLevels.nonEmpty) {
        modelSpec.pressureLevels.map { pressureMb =>
          val pressure = Pascals(pressureMb * 100.0)  // Convert hPa to Pa
          val tempFeature = Feature(vars.temperatureIsobaric)
          val rhFeature = Feature(vars.relativeHumidityIsobaric)
          val uWindFeature = Feature(vars.uWindIsobaric)
          val vWindFeature = Feature(vars.vWindIsobaric)
          val geopFeature = Feature(vars.geopotentialIsobaric)
          val cloudFeature = Feature(vars.cloudCoverIsobaric)
          pressure -> (tempFeature, rhFeature, uWindFeature, vWindFeature, geopFeature, cloudFeature)
        }.toMap
      } else {
        Map.empty
      }

      // === Extract Grid ===
      for (longitude <- effectiveLongitudes) yield {
        for (latitude <- effectiveLatitudes) yield {
          val location = Point(latitude, longitude)

          // Helper to read value at location
          def readXY(feature: Feature): Double = feature.read(location)
          def readXYOpt(feature: Option[Feature]): Option[Double] = feature.map(_.read(location))

          // Helper to read value at location and pressure
          def readXYZ(feature: Feature, pressure: Pressure): Double =
            feature.read(location, pressure.toPascals)

          // === Surface Data ===
          val surfaceTemperature = Kelvin(readXY(temperature2m))

          val surfaceDewPoint: Temperature = dewPoint2m match {
            case Some(feature) =>
              // Direct dew point available (AROME)
              Kelvin(readXY(feature))
            case None =>
              // Compute from temperature + RH (GFS)
              relativeHumidity2m match {
                case Some(rhFeature) =>
                  Temperatures.dewPoint(
                    surfaceTemperature,
                    readXY(rhFeature)
                  )
                case None =>
                  throw new IllegalStateException(
                    s"Model ${modelSpec.id} has neither dewPoint2m nor relativeHumidity2m"
                  )
              }
          }

          val surfaceData = SurfaceData(
            elevation = Meters(readXY(geopotentialSurface)),
            temperature = surfaceTemperature,
            dewPoint = surfaceDewPoint,
            wind = Wind(
              MetersPerSecond(readXY(uWind10m)),
              MetersPerSecond(readXY(vWind10m))
            ),
            pressure = Pascals(readXY(pressure)),
            snowDepth = snowDepth.map(f => Millimeters(readXY(f))).getOrElse(Millimeters(0))
          )

          // === Boundary Layer Data ===
          val boundaryLayerData = BoundaryLayerData(
            pblDepth = Meters(readXY(pblHeight)),
            wind = Wind(
              MetersPerSecond(readXY(uWindPBL)),
              MetersPerSecond(readXY(vWindPBL))
            ),
            sensibleHeatFlux = WattsPerSquareMeter(readXY(sensibleHeatFlux)),
            latentHeatFlux = WattsPerSquareMeter(readXY(latentHeatFlux))
          )

          // === Atmosphere Data ===
          // Convert precipitation rate to accumulated (assumes 3-hour average for GFS, 1-hour for AROME)
          val timeStepHours = modelSpec.nativeTimeStep
          val totalRain = Millimeters(readXY(totalPrecipRate) * timeStepHours * 3600)
          val convectiveRain = Millimeters(readXY(convectivePrecipRate) * timeStepHours * 3600)

          val atmosphereData = AtmosphereData(
            totalCloudCover = readXY(totalCloudCover).round.intValue(),
            convectiveCloudCover = convectiveCloudCover.map(f => readXY(f).round.intValue()).getOrElse(0),
            lowCloudCover = lowCloudCover.map(f => readXY(f).round.intValue()),
            mediumCloudCover = mediumCloudCover.map(f => readXY(f).round.intValue()),
            highCloudCover = highCloudCover.map(f => readXY(f).round.intValue()),
            totalRain = totalRain,
            convectiveRain = convectiveRain,
            solarRadiation = WattsPerSquareMeter(readXY(solarRadiation)),
            cape = cape.flatMap { f =>
              Try(Grays(readXY(f))).toOption
            },
            cin = cin.flatMap { f =>
              Try(Grays(readXY(f))).toOption
            }
          )

          // === Vertical Profiles ===
          // Extract isobaric levels and convert to altitude-based profiles
          val (temperatureProfile, dewPointProfile, windProfile, cloudCoverProfile, airDataByAltitude) =
            if (isobaricFeatures.nonEmpty) {
              val tempMap = scala.collection.mutable.SortedMap.empty[Length, Temperature]
              val dewMap = scala.collection.mutable.SortedMap.empty[Length, Temperature]
              val windMap = scala.collection.mutable.SortedMap.empty[Length, Wind]
              val cloudMap = scala.collection.mutable.SortedMap.empty[Length, Int]
              val airDataMap = scala.collection.mutable.SortedMap.empty[Length, org.soaringmeteo.AirData]

              isobaricFeatures.foreach { case (pressure, (tempF, rhF, uWindF, vWindF, geopF, cloudF)) =>
                val altitude = Meters(readXYZ(geopF, pressure))
                val temp = Kelvin(readXYZ(tempF, pressure))
                val rh = readXYZ(rhF, pressure)
                val dewPt = Temperatures.dewPoint(temp, rh)
                val wind = Wind(
                  MetersPerSecond(readXYZ(uWindF, pressure)),
                  MetersPerSecond(readXYZ(vWindF, pressure))
                )
                val cloudCov = readXYZ(cloudF, pressure).round.intValue()

                tempMap(altitude) = temp
                dewMap(altitude) = dewPt
                windMap(altitude) = wind
                cloudMap(altitude) = cloudCov
                airDataMap(altitude) = org.soaringmeteo.AirData(
                  wind = wind,
                  temperature = temp,
                  dewPoint = dewPt,
                  cloudCover = cloudCov
                )
              }

              (
                SortedMap.from(tempMap),
                SortedMap.from(dewMap),
                SortedMap.from(windMap),
                SortedMap.from(cloudMap),
                SortedMap.from(airDataMap)
              )
            } else {
              (
                SortedMap.empty[Length, Temperature],
                SortedMap.empty[Length, Temperature],
                SortedMap.empty[Length, Wind],
                SortedMap.empty[Length, Int],
                SortedMap.empty[Length, org.soaringmeteo.AirData]
              )
            }

          val isothermZero = Try(Meters(readXY(geopotentialZeroDegC))).toOption

          val verticalProfiles = VerticalProfiles(
            temperatureProfile = temperatureProfile,
            dewPointProfile = dewPointProfile,
            windProfile = windProfile,
            cloudCoverProfile = cloudCoverProfile,
            isothermZero = isothermZero,
            airDataByAltitude = airDataByAltitude
          )

          // === Combine into RawData ===
          RawData(
            surface = surfaceData,
            boundary = boundaryLayerData,
            atmosphere = atmosphereData,
            profiles = verticalProfiles
          )
        }
      }
    }
  }

  /**
   * Parse AROME grouped GRIB files (SP1/SP2/SP3) for a geographic grid.
   *
   * AROME stores data in 3 grouped files per time range:
   * - SP1: Primary surface fields (T2M, U/V10m, pressure, rain)
   * - SP2: Complementary surface fields (CAPE, PBL height, clouds, dew point, terrain)
   * - SP3: Surface fluxes (sensible/latent heat flux, solar radiation)
   *
   * @param parseableData Data prepared by AromeFileHandler
   * @param longitudes Exact longitude coordinates
   * @param latitudes Exact latitude coordinates
   * @return Grid of RawData
   */
  private def parseAromeGrid(
    parseableData: handlers.ParseableData,
    longitudes: IndexedSeq[Double],
    latitudes: IndexedSeq[Double]
  ): IndexedSeq[IndexedSeq[RawData]] = {

    val modelSpec = parseableData.modelSpec
    val vars = modelSpec.variables
    val hourOffset = parseableData.hourOffset

    // Determine SP2, SP3, and HP1 file paths (same directory, same naming pattern)
    val sp1File = parseableData.primaryFile
    val sp2File = os.Path(sp1File.toString.replace("SP1_", "SP2_"))
    val sp3File = os.Path(sp1File.toString.replace("SP1_", "SP3_"))
    val hp1File = os.Path(sp1File.toString.replace("SP1_", "HP1_"))

    logger.info(s"  SP1 file: ${sp1File.last}")
    logger.info(s"  SP2 file: ${sp2File.last}")
    logger.info(s"  SP3 file: ${sp3File.last}")
    logger.info(s"  HP1 file: ${hp1File.last}")
    logger.info(s"  Hour offset within group: $hourOffset")

    // Verify files exist
    require(os.exists(sp2File), s"AROME SP2 file not found: $sp2File")
    require(os.exists(sp3File), s"AROME SP3 file not found: $sp3File")
    require(os.exists(hp1File), s"AROME HP1 file not found: $hp1File")

    // Open all 4 files (SP1, SP2, SP3, HP1) and extract data
    Grib.bracket(sp1File) { sp1 =>
      Grib.bracket(sp2File) { sp2 =>
        Grib.bracket(sp3File) { sp3 =>
          Grib.bracket(hp1File) { hp1 =>
            // Load only ONE time slice per Feature (memory optimization for grouped files)
            logger.debug(s"  Loading time slice $hourOffset from grouped files")

          // === SP1: Primary Surface Variables ===
          val temperature2m = sp1.Feature.withTimeSlice(vars.temperature2m, hourOffset)
          val uWind10m = sp1.Feature.withTimeSlice(vars.uWind10m, hourOffset)
          val vWind10m = sp1.Feature.withTimeSlice(vars.vWind10m, hourOffset)
          val pressure = sp1.Feature.maybeWithTimeSlice(vars.pressure, hourOffset)
            .orElse(sp1.Feature.maybeWithTimeSlice("Mean_sea-level_pressure_MSL", hourOffset))
            .getOrElse(sp1.Feature.withTimeSlice("Pressure_reduced_to_MSL_msl", hourOffset))

          // === SP2: Complementary Surface Variables ===
          // Note: SP2 static variables (elevation) use timeIdx=0, others use hourOffset
          val geopotentialSurface: Option[sp2.Feature] = sp2.Feature.maybeWithTimeSlice(vars.geopotentialSurface, 0)
            .orElse(sp2.Feature.maybeWithTimeSlice("Geopotential_height_surface", 0))
          val dewPoint2m = sp2.Feature.withTimeSlice(vars.dewPoint2m, hourOffset)
          val pblHeight = sp2.Feature.withTimeSlice(vars.pblHeight, hourOffset)

          // Total precipitation (accumulated, use hour-1 for flux variables except hour 0)
          // Located in SP2, not SP1
          val precipTimeIdx = if (hourOffset == 0) 0 else hourOffset - 1
          val totalPrecipRate = sp2.Feature.maybeWithTimeSlice(vars.totalPrecipRate, precipTimeIdx)

          // Clouds (flux variable, use hour-1 index)
          val cloudTimeIdx = if (hourOffset == 0) 0 else hourOffset - 1
          val totalCloudCover: Option[sp2.Feature] = sp2.Feature.maybeWithTimeSlice(vars.totalCloudCover, cloudTimeIdx)
            .orElse(sp2.Feature.maybeWithTimeSlice("Low_cloud_cover_surface", cloudTimeIdx))

          // Optional SP2 variables
          val cape: Option[sp2.Feature] = vars.cape.flatMap { name =>
            sp2.Feature.maybeWithTimeSlice(name, hourOffset)
          }
          val cin: Option[sp2.Feature] = vars.cin.flatMap { name =>
            sp2.Feature.maybeWithTimeSlice(name, hourOffset)
          }
          val snowDepth: Option[sp2.Feature] = vars.snowDepth.flatMap { name =>
            sp2.Feature.maybeWithTimeSlice(name, hourOffset)
          }
          val geopotentialZeroDegC: Option[sp2.Feature] = sp2.Feature.maybeWithTimeSlice(vars.geopotentialZeroDegC, hourOffset)

          // === SP3: Surface Fluxes ===
          // Fluxes use hour-1 index (except hour 0)
          val fluxTimeIdx = if (hourOffset == 0) 0 else hourOffset - 1
          val sensibleHeatFlux = sp3.Feature.withTimeSlice(vars.sensibleHeatFlux, fluxTimeIdx)
          val latentHeatFlux = sp3.Feature.withTimeSlice(vars.latentHeatFlux, fluxTimeIdx)
          val solarRadiation = sp3.Feature.withTimeSlice(vars.solarRadiation, fluxTimeIdx)

          // === Boundary Layer Wind ===
          // AROME may use surface wind or specific level for PBL wind
          // Use 10m wind as fallback
          val uWindPBL = sp1.Feature.maybeWithTimeSlice(vars.uWindPBL, hourOffset).getOrElse(uWind10m)
          val vWindPBL = sp1.Feature.maybeWithTimeSlice(vars.vWindPBL, hourOffset).getOrElse(vWind10m)

          // === Convective precipitation ===
          // AROME may not have convective precip, located in SP2
          val convectivePrecipRate = sp2.Feature.maybeWithTimeSlice(vars.convectivePrecipRate, precipTimeIdx)

          // === Convective cloud cover ===
          // AROME may not have convective cloud cover
          val convectiveCloudCover = vars.convectiveCloudCover.flatMap(name => sp2.Feature.maybeWithTimeSlice(name, cloudTimeIdx))

          // === Cloud layers (AROME specific) ===
          // Located in SP2
          val lowCloudCover: Option[sp2.Feature] = vars.lowCloudCover.flatMap { name =>
            sp2.Feature.maybeWithTimeSlice(name, cloudTimeIdx)
          }
          val mediumCloudCover: Option[sp2.Feature] = vars.mediumCloudCover.flatMap { name =>
            sp2.Feature.maybeWithTimeSlice(name, cloudTimeIdx)
          }
          val highCloudCover: Option[sp2.Feature] = vars.highCloudCover.flatMap { name =>
            sp2.Feature.maybeWithTimeSlice(name, cloudTimeIdx)
          }

          // === HP1: Vertical Profiles ===
          // HP1 contains 25 height levels above ground with Temperature, U/V wind, and Relative Humidity
          val hp1Temp = hp1.Feature.withTimeSlice("Temperature_height_above_ground", hourOffset)
          val hp1UWind = hp1.Feature.withTimeSlice("u-component_of_wind_height_above_ground", hourOffset)
          val hp1VWind = hp1.Feature.withTimeSlice("v-component_of_wind_height_above_ground", hourOffset)
          val hp1RH = hp1.Feature.withTimeSlice("Relative_humidity_height_above_ground", hourOffset)

          // HP1 vertical levels (meters AGL) - 25 levels
          val hp1Levels = Seq(
            10, 20, 35, 50, 75, 100, 150, 200, 250, 375, 500, 625, 750, 875, 1000,
            1125, 1250, 1375, 1500, 1750, 2000, 2250, 2500, 2750, 3000
          ).map(Meters(_))

          // === Extract Grid ===
          for (longitude <- longitudes) yield {
            for (latitude <- latitudes) yield {
              val location = Point(latitude, longitude)

              // Helper to read value at location using correct time index
              // For AROME grouped files, readAtTime() uses the hourOffset to read the correct timestep
              def readXY(feature: sp1.Feature, timeIdx: Int = hourOffset): Double = {
                feature.readAtTime(location, timeIdx)
              }
              def readXY2(feature: sp2.Feature, timeIdx: Int = hourOffset): Double = {
                feature.readAtTime(location, timeIdx)
              }
              def readXY3(feature: sp3.Feature, timeIdx: Int = hourOffset): Double = {
                feature.readAtTime(location, timeIdx)
              }
              def readXYOpt2(feature: Option[sp2.Feature], timeIdx: Int = hourOffset): Option[Double] =
                feature.map(_.readAtTime(location, timeIdx))

              // === Surface Data ===
              val surfaceTemperature = Kelvin(readXY(temperature2m))
              val surfaceDewPoint = Kelvin(readXY2(dewPoint2m))

              val surfaceData = SurfaceData(
                elevation = geopotentialSurface.map(f => Meters(readXY2(f, 0))).getOrElse(Meters(0.0)),  // Static terrain: timeIdx=0
                temperature = surfaceTemperature,
                dewPoint = surfaceDewPoint,
                wind = Wind(
                  MetersPerSecond(readXY(uWind10m)),
                  MetersPerSecond(readXY(vWind10m))
                ),
                pressure = Pascals(readXY(pressure)),
                snowDepth = snowDepth.map(f => Millimeters(readXY2(f))).getOrElse(Millimeters(0))
              )

              // === Boundary Layer Data ===
              val boundaryLayerData = BoundaryLayerData(
                pblDepth = Meters(readXY2(pblHeight)),
                wind = Wind(
                  MetersPerSecond(readXY(uWindPBL)),
                  MetersPerSecond(readXY(vWindPBL))
                ),
                sensibleHeatFlux = WattsPerSquareMeter(readXY3(sensibleHeatFlux, fluxTimeIdx)),
                latentHeatFlux = WattsPerSquareMeter(readXY3(latentHeatFlux, fluxTimeIdx))
              )

              // === Atmosphere Data ===
              // AROME native timestep is 1 hour
              val timeStepHours = modelSpec.nativeTimeStep
              val totalRain = totalPrecipRate.map(f =>
                Millimeters(readXY2(f, precipTimeIdx) * timeStepHours * 3600)
              ).getOrElse(Millimeters(0))
              val convectiveRain = convectivePrecipRate.map(f =>
                Millimeters(readXY2(f, precipTimeIdx) * timeStepHours * 3600)
              ).getOrElse(totalRain)  // Fallback to total rain

              val atmosphereData = AtmosphereData(
                totalCloudCover = readXYOpt2(totalCloudCover, cloudTimeIdx).map(_.round.intValue()).getOrElse(0),
                convectiveCloudCover = readXYOpt2(convectiveCloudCover, cloudTimeIdx).map(_.round.intValue()).getOrElse(0),
                lowCloudCover = readXYOpt2(lowCloudCover, cloudTimeIdx).map(_.round.intValue()),
                mediumCloudCover = readXYOpt2(mediumCloudCover, cloudTimeIdx).map(_.round.intValue()),
                highCloudCover = readXYOpt2(highCloudCover, cloudTimeIdx).map(_.round.intValue()),
                totalRain = totalRain,
                convectiveRain = convectiveRain,
                solarRadiation = WattsPerSquareMeter(readXY3(solarRadiation, fluxTimeIdx)),
                cape = cape.flatMap { f =>
                  Try(Grays(readXY2(f))).toOption
                },
                cin = cin.flatMap { f =>
                  Try(Grays(readXY2(f))).toOption
                }
              )

              // === Vertical Profiles ===
              val isothermZero = geopotentialZeroDegC.flatMap { f =>
                Try(Meters(readXY2(f))).toOption
              }

              val surfaceElevation = surfaceData.elevation
              val wind10m = surfaceData.wind

              // Helper to read HP1 data at specific vertical level
              def readHP1(feature: hp1.Feature, zIdx: Int): Double = {
                feature.readAtTime(location, hourOffset, zIdx)
              }

              // Build airDataByAltitude from HP1 vertical profiles (25 levels)
              val airDataByAltitude: SortedMap[Length, org.soaringmeteo.AirData] = SortedMap.from {
                hp1Levels.zipWithIndex.map { case (levelAGL, zIdx) =>
                  val altitudeMSL: Length = surfaceElevation + levelAGL
                  val tempK = Kelvin(readHP1(hp1Temp, zIdx))
                  val rhPercent = readHP1(hp1RH, zIdx)

                  // Calculate dew point from temperature and relative humidity
                  // Magnus formula: Td = (b * α) / (a - α) where α = ln(RH/100) + (a*T)/(b+T)
                  // a = 17.27, b = 237.7°C for over water
                  val tempC = tempK.toCelsiusScale
                  val a = 17.27
                  val b = 237.7
                  val alpha = math.log(rhPercent / 100.0) + (a * tempC) / (b + tempC)
                  val dewPointC = (b * alpha) / (a - alpha)
                  val dewPoint = Celsius(dewPointC)

                  altitudeMSL -> org.soaringmeteo.AirData(
                    wind = Wind(
                      MetersPerSecond(readHP1(hp1UWind, zIdx)),
                      MetersPerSecond(readHP1(hp1VWind, zIdx))
                    ),
                    temperature = tempK,
                    dewPoint = dewPoint,
                    cloudCover = 0  // HP1 doesn't provide cloud cover by level, use 0 for now
                  )
                }
              }

              val verticalProfiles = VerticalProfiles(
                temperatureProfile = SortedMap(surfaceElevation -> surfaceTemperature),
                dewPointProfile = SortedMap(surfaceElevation -> surfaceDewPoint),
                windProfile = SortedMap(surfaceElevation -> wind10m),
                cloudCoverProfile = SortedMap.empty[Length, Int],
                isothermZero = isothermZero,
                airDataByAltitude = airDataByAltitude
              )

              // === Combine into RawData ===
              RawData(
                surface = surfaceData,
                boundary = boundaryLayerData,
                atmosphere = atmosphereData,
                profiles = verticalProfiles
              )
            }
          }
        }
      }
    }
    }
  }
}
