import org.soaringmeteo.build.Dependencies

// Build settings
inThisBuild(Seq(
  scalaVersion := "2.13.12",
  scalacOptions += "-deprecation",
  libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % "always",
  testFrameworks += new TestFramework("verify.runner.Framework"),
  resolvers ++= Seq(
    "Unidata All" at "https://artifacts.unidata.ucar.edu/repository/unidata-all",
    "SciJava Public" at "https://maven.scijava.org/content/groups/public"
  ),
))

// Common module - Unified data structures and parsers
// NOTE: Some type files (Wind, Winds, ConvectiveClouds, etc.) are copied from backend/common
// to make backend-v2 autonomous. When backend/ is removed, these become the canonical versions.
val common =
  project.in(file("common"))
    .settings(
      name := "soaringmeteo-unified-common",
      libraryDependencies ++= Seq(
        // Files manipulation
        "com.lihaoyi" %% "os-lib" % "0.9.1",
        // Command-line arguments processing
        Dependencies.decline,
        // Image generation
        Dependencies.geotrellisRaster,
        Dependencies.geotrellisVectorTile,
        // GRIB2 and NetCDF files manipulation
        "edu.ucar" % "grib" % "5.8.0",
        // N5/Zarr for efficient data caching
        "org.janelia.saalfeldlab" % "n5" % "3.1.2",
        "org.janelia.saalfeldlab" % "n5-zarr" % "1.3.5",
        // Quantities
        Dependencies.squants,
        // Logging
        Dependencies.logback,
        // Configuration
        Dependencies.config,
        // JSON
        Dependencies.circeParser,
        // Persistence
        "com.typesafe.slick" %% "slick" % "3.4.1",
        "com.h2database" % "h2" % "2.2.224",
        // Testing
        Dependencies.verify % Test,
      )
    )

// Application - Single CLI for all weather models
val app =
  project.in(file("app"))
    .enablePlugins(JavaAppPackaging)
    .settings(
      name := "soaringmeteo-unified",
      Universal / packageName := "soaringmeteo-unified",
      run / fork := true,
      javaOptions ++= Seq(
        "-Xmx7g",  // Max 7GB heap (leave ~4GB for native memory and OS)
        "-Xms4g",  // Initial 4GB heap
        "-XX:+UseG1GC",  // G1 handles large objects better
        "-XX:+ExitOnOutOfMemoryError"  // Exit cleanly on OOM
      ),
      Universal / javaOptions ++= javaOptions.value.map(opt => s"-J$opt"),
      Compile / mainClass := Some("org.soaringmeteo.Main"),
      maintainer := "equipe@soaringmeteo.org",
      libraryDependencies ++= Seq(
        Dependencies.config,
        Dependencies.decline,
      ),
    )
    .dependsOn(common)
