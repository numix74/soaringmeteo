package org.soaringmeteo.parsing.handlers

import verify.BasicTestSuite

import java.time.{OffsetDateTime, ZoneOffset}

object GfsFileHandlerTestSuite extends BasicTestSuite {

  test("GfsFileHandler validates cycle hour (must be 0, 6, 12, or 18)") {
    // Create a temporary directory structure for testing
    val tempDir = os.temp.dir(prefix = "gfs-test-")

    try {
      // Valid cycle hours: 00, 06, 12, 18
      val validInitTime = OffsetDateTime.of(2025, 11, 8, 18, 0, 0, 0, ZoneOffset.UTC)

      // Create necessary directory structure
      val dateDir = "251108"  // 2025-11-08
      val hourDir = "18"
      val runDir = tempDir / dateDir / hourDir
      os.makeDir.all(runDir)

      // Create a test GRIB file
      os.write(runDir / "GFS-pyrenees-003.grib2", "test content")

      val handler = new GfsFileHandler(tempDir, "pyrenees")

      // Should succeed with valid cycle hour
      val data = handler.prepareData(3, validInitTime)
      assert(data.hourOffset == 3)
      assert(data.initTime == validInitTime)

      // Test with invalid cycle hour (should fail)
      val invalidInitTime = OffsetDateTime.of(2025, 11, 8, 15, 0, 0, 0, ZoneOffset.UTC)
      try {
        handler.prepareData(3, invalidInitTime)
        assert(false, "Should have thrown IllegalArgumentException for invalid cycle hour")
      } catch {
        case _: IllegalArgumentException => assert(true)
      }
    } finally {
      // Cleanup
      os.remove.all(tempDir)
    }
  }

  test("GfsFileHandler constructs correct file paths") {
    val tempDir = os.temp.dir(prefix = "gfs-test-")

    try {
      val initTime = OffsetDateTime.of(2025, 11, 8, 18, 0, 0, 0, ZoneOffset.UTC)

      // Create directory structure: 251108/18/
      val dateDir = "251108"
      val hourDir = "18"
      val runDir = tempDir / dateDir / hourDir
      os.makeDir.all(runDir)

      // Create test files for different hour offsets
      os.write(runDir / "GFS-pyrenees-000.grib2", "H+0")
      os.write(runDir / "GFS-pyrenees-003.grib2", "H+3")
      os.write(runDir / "GFS-pyrenees-006.grib2", "H+6")
      os.write(runDir / "GFS-pyrenees-120.grib2", "H+120")

      val handler = new GfsFileHandler(tempDir, "pyrenees")

      // Test H+0
      val data0 = handler.prepareData(0, initTime)
      assert(data0.primaryFile.last == "GFS-pyrenees-000.grib2")

      // Test H+3
      val data3 = handler.prepareData(3, initTime)
      assert(data3.primaryFile.last == "GFS-pyrenees-003.grib2")

      // Test H+120
      val data120 = handler.prepareData(120, initTime)
      assert(data120.primaryFile.last == "GFS-pyrenees-120.grib2")
    } finally {
      os.remove.all(tempDir)
    }
  }

  test("GfsFileHandler throws error if file does not exist") {
    val tempDir = os.temp.dir(prefix = "gfs-test-")

    try {
      val initTime = OffsetDateTime.of(2025, 11, 8, 12, 0, 0, 0, ZoneOffset.UTC)

      // Create directory but no files
      val dateDir = "251108"
      val hourDir = "12"
      val runDir = tempDir / dateDir / hourDir
      os.makeDir.all(runDir)

      val handler = new GfsFileHandler(tempDir, "pyrenees")

      // Should throw error for missing file
      try {
        handler.prepareData(3, initTime)
        assert(false, "Should have thrown IllegalArgumentException for missing file")
      } catch {
        case _: IllegalArgumentException => assert(true)
      }
    } finally {
      os.remove.all(tempDir)
    }
  }

  test("GfsFileHandler availableHourOffsets returns sorted list") {
    val tempDir = os.temp.dir(prefix = "gfs-test-")

    try {
      val initTime = OffsetDateTime.of(2025, 12, 31, 0, 0, 0, 0, ZoneOffset.UTC)

      // Create directory structure: 251231/00/
      val dateDir = "251231"
      val hourDir = "00"
      val runDir = tempDir / dateDir / hourDir
      os.makeDir.all(runDir)

      // Create files in non-sorted order
      os.write(runDir / "GFS-pyrenees-012.grib2", "H+12")
      os.write(runDir / "GFS-pyrenees-003.grib2", "H+3")
      os.write(runDir / "GFS-pyrenees-000.grib2", "H+0")
      os.write(runDir / "GFS-pyrenees-009.grib2", "H+9")
      os.write(runDir / "GFS-pyrenees-006.grib2", "H+6")

      val handler = new GfsFileHandler(tempDir, "pyrenees")
      val available = handler.availableHourOffsets(initTime)

      // Should return sorted list
      assert(available == Seq(0, 3, 6, 9, 12))
    } finally {
      os.remove.all(tempDir)
    }
  }

  test("GfsFileHandler availableHourOffsets filters by zone") {
    val tempDir = os.temp.dir(prefix = "gfs-test-")

    try {
      val initTime = OffsetDateTime.of(2025, 12, 31, 6, 0, 0, 0, ZoneOffset.UTC)

      // Create directory structure
      val dateDir = "251231"
      val hourDir = "06"
      val runDir = tempDir / dateDir / hourDir
      os.makeDir.all(runDir)

      // Create files for different zones
      os.write(runDir / "GFS-pyrenees-000.grib2", "pyrenees H+0")
      os.write(runDir / "GFS-pyrenees-003.grib2", "pyrenees H+3")
      os.write(runDir / "GFS-pays-basque-000.grib2", "pays-basque H+0")
      os.write(runDir / "GFS-pays-basque-003.grib2", "pays-basque H+3")

      // Test with "pyrenees" zone
      val handlerPyrenees = new GfsFileHandler(tempDir, "pyrenees")
      val availablePyrenees = handlerPyrenees.availableHourOffsets(initTime)
      assert(availablePyrenees == Seq(0, 3))

      // Test with "pays-basque" zone
      val handlerPaysBasque = new GfsFileHandler(tempDir, "pays-basque")
      val availablePaysBasque = handlerPaysBasque.availableHourOffsets(initTime)
      assert(availablePaysBasque == Seq(0, 3))
    } finally {
      os.remove.all(tempDir)
    }
  }

  test("GfsFileHandler calculates correct validTime") {
    val tempDir = os.temp.dir(prefix = "gfs-test-")

    try {
      val initTime = OffsetDateTime.of(2025, 11, 8, 12, 0, 0, 0, ZoneOffset.UTC)

      // Create directory structure
      val dateDir = "251108"
      val hourDir = "12"
      val runDir = tempDir / dateDir / hourDir
      os.makeDir.all(runDir)

      os.write(runDir / "GFS-pyrenees-006.grib2", "H+6")

      val handler = new GfsFileHandler(tempDir, "pyrenees")
      val data = handler.prepareData(6, initTime)

      // Valid time should be initTime + hourOffset
      val expectedValidTime = initTime.plusHours(6)
      assert(data.validTime == expectedValidTime)
      assert(data.validTime.getHour == 18)
    } finally {
      os.remove.all(tempDir)
    }
  }

  test("GfsFileHandler throws error if directory does not exist") {
    val tempDir = os.temp.dir(prefix = "gfs-test-")

    try {
      val initTime = OffsetDateTime.of(2025, 11, 8, 0, 0, 0, 0, ZoneOffset.UTC)

      // Don't create the directory structure
      val handler = new GfsFileHandler(tempDir, "pyrenees")

      // Should throw error for missing directory
      try {
        handler.availableHourOffsets(initTime)
        assert(false, "Should have thrown IllegalStateException for missing directory")
      } catch {
        case _: IllegalStateException => assert(true)
      }
    } finally {
      os.remove.all(tempDir)
    }
  }

  test("GfsFileHandler.apply validates directory exists") {
    val nonExistentDir = os.pwd / "non-existent-gfs-dir-12345"

    // Should throw error for non-existent directory
    try {
      GfsFileHandler(nonExistentDir, "pyrenees")
      assert(false, "Should have thrown IllegalArgumentException for non-existent directory")
    } catch {
      case _: IllegalArgumentException => assert(true)
    }
  }

  test("GfsFileHandler handles different years correctly") {
    val tempDir = os.temp.dir(prefix = "gfs-test-")

    try {
      // Test year 2025 (25 prefix)
      val initTime2025 = OffsetDateTime.of(2025, 3, 15, 0, 0, 0, 0, ZoneOffset.UTC)
      val dateDir2025 = "250315"
      val hourDir = "00"
      val runDir2025 = tempDir / dateDir2025 / hourDir
      os.makeDir.all(runDir2025)
      os.write(runDir2025 / "GFS-pyrenees-000.grib2", "2025")

      // Test year 2026 (26 prefix)
      val initTime2026 = OffsetDateTime.of(2026, 1, 12, 0, 0, 0, 0, ZoneOffset.UTC)
      val dateDir2026 = "260112"
      val runDir2026 = tempDir / dateDir2026 / hourDir
      os.makeDir.all(runDir2026)
      os.write(runDir2026 / "GFS-pyrenees-000.grib2", "2026")

      val handler = new GfsFileHandler(tempDir, "pyrenees")

      val data2025 = handler.prepareData(0, initTime2025)
      assert(data2025.primaryFile.toString.contains("250315"))

      val data2026 = handler.prepareData(0, initTime2026)
      assert(data2026.primaryFile.toString.contains("260112"))
    } finally {
      os.remove.all(tempDir)
    }
  }
}
