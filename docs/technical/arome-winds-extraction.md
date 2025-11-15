# AROME Winds Extraction - Technical Guide

**Date**: Novembre 2025
**Status**: Production-Ready

---

## Overview

This document describes the technical implementation for extracting multi-level wind data from AROME GRIB2 files.
AROME provides wind data at multiple height levels which are essential for soaring flight planning.

---

## Problem Statement

### Challenge: Multi-Height Wind Data

**What AROME provides**:
- Wind components (U, V) at surface (10m)
- Wind components at multiple height levels above ground (AGL)
- Wind components at pressure levels (AMSL)

**What the application needs**:
- `wind-surface` (10m)
- `wind-300m-agl`
- `wind-boundary-layer` (at PBLH height)
- `wind-2000m-amsl`, `wind-3000m-amsl`, `wind-4000m-amsl`

### Original Problem: Out of Memory (OOM)

**Initial approach**: Load complete HP1 file (~470 MB) into memory → OutOfMemoryError

**Solution implemented**: Extract only needed heights with wgrib2, load separately

---

## AROME GRIB2 Files Structure

### SP Files (Surface Parameters)

**SP1**: Basic meteorological data
- Temperature at 2m (T2M)
- Wind components U10/V10 at 10m
- Surface pressure

**SP2**: Atmospheric stability
- CAPE (Convective Available Potential Energy)
- PBLH (Planetary Boundary Layer Height)
- Cloud cover
- Terrain elevation

**SP3**: Heat flux data
- Sensible heat flux
- Latent heat flux
- Solar radiation

### HP1 File (Height Above Ground - AGL)

**Contains**: Wind components at fixed height levels above ground

**Available levels**: 20, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1200, 1500, 1800, 2000, 2200, 2500, 2700, 3000m

**Parameters**:
- `UGRD:heightAboveGround` - U component (east-west)
- `VGRD:heightAboveGround` - V component (north-south)

### HP3 File (Pressure Levels - AMSL)

**Contains**: Wind components at isobaric surfaces

**Available levels**: 1000, 950, 925, 900, 850, 800, 700, 600, 500, 400, 300, 250, 200 hPa

**Altitude correspondence**:
- 800 hPa ≈ 2000m AMSL
- 700 hPa ≈ 3000m AMSL
- 600 hPa ≈ 4000m AMSL

**Parameters**:
- `UGRD:ISBL` - U component at pressure level
- `VGRD:ISBL` - V component at pressure level

---

## Extraction Strategy

### Heights Extracted

To minimize memory usage while covering all necessary wind levels, we extract **12 levels every 250m**:

```
250m, 500m, 750m, 1000m, 1250m, 1500m, 1750m, 2000m, 2250m, 2500m, 2750m, 3000m
```

This provides:
- Direct match for 300m AGL (use 250m or interpolate)
- Full coverage up to 3000m for interpolation
- ~50 MB total vs 470 MB for complete HP1

### Extraction with wgrib2

**Command pattern**:
```bash
wgrib2 HP1_file.grib2 -match "UGRD:250 m above ground" -grib u_250m.grib2
wgrib2 HP1_file.grib2 -match "VGRD:250 m above ground" -grib v_250m.grib2
```

**For all heights**:
```bash
#!/bin/bash
# extract_arome_winds.sh

HEIGHTS="250 500 750 1000 1250 1500 1750 2000 2250 2500 2750 3000"
HP1_FILE="HP1_00H06H.grib2"
OUTPUT_DIR="winds"

mkdir -p "$OUTPUT_DIR"

for height in $HEIGHTS; do
  echo "Extracting U/V at ${height}m..."

  wgrib2 "$HP1_FILE" \
    -match "UGRD:${height} m above ground" \
    -grib "${OUTPUT_DIR}/u_${height}m.grib2"

  wgrib2 "$HP1_FILE" \
    -match "VGRD:${height} m above ground" \
    -grib "${OUTPUT_DIR}/v_${height}m.grib2"
done

echo "Wind extraction complete: $(ls -lh $OUTPUT_DIR)"
```

---

## Implementation in AromeGrib.scala

### Wind Data Structure

```scala
case class AromeData(
  // ... other fields
  windsAtHeights: Map[Int, (Double, Double)]  // height_m → (u, v)
)
```

### Reading Winds at Multiple Heights

```scala
object AromeGrib {

  def extractWindsAtHeights(
    windsDir: os.Path,
    location: (Int, Int),  // (lon_index, lat_index)
    hourOffset: Int
  ): Map[Int, (Double, Double)] = {

    val heights = Seq(250, 500, 750, 1000, 1250, 1500, 1750, 2000, 2250, 2500, 2750, 3000)

    heights.flatMap { height =>
      val uFile = windsDir / s"u_${height}m.grib2"
      val vFile = windsDir / s"v_${height}m.grib2"

      if (os.exists(uFile) && os.exists(vFile)) {
        try {
          val u = readGribValue(uFile, location, hourOffset)
          val v = readGribValue(vFile, location, hourOffset)
          Some(height -> (u, v))
        } catch {
          case e: Exception =>
            logger.warn(s"Failed to read wind at ${height}m: ${e.getMessage}")
            None
        }
      } else {
        logger.warn(s"Wind files not found for ${height}m")
        None
      }
    }.toMap
  }

  private def readGribValue(
    gribFile: os.Path,
    location: (Int, Int),
    hourOffset: Int
  ): Double = {
    val ncDataset = NetcdfDatasets.openDataset(gribFile.toString)
    try {
      // Read grid and extract value at location
      // Implementation details...
    } finally {
      ncDataset.close()
    }
  }
}
```

### Usage in Main.scala

```scala
val data = AromeGrib.fromGroupFiles(
  sp1File = sp1File,
  sp2File = sp2File,
  sp3File = sp3File,
  windsDir = windsDir,  // Directory with u_XXXm.grib2, v_XXXm.grib2
  hourOffset = hourOffsetInGroup,
  zone = setting.zone
)

// data.windsAtHeights now contains:
// Map(250 -> (u250, v250), 500 -> (u500, v500), ...)
```

---

## Wind Interpolation

### For Specific Heights

To get wind at arbitrary heights (e.g., boundary layer top), interpolate:

```scala
def interpolateWind(
  height: Double,
  windsAtHeights: Map[Int, (Double, Double)]
): (Double, Double) = {

  val sortedHeights = windsAtHeights.keys.toSeq.sorted

  // Find bounding heights
  val lowerHeight = sortedHeights.filter(_ <= height).lastOption
  val upperHeight = sortedHeights.filter(_ > height).headOption

  (lowerHeight, upperHeight) match {
    case (Some(h1), Some(h2)) =>
      val (u1, v1) = windsAtHeights(h1)
      val (u2, v2) = windsAtHeights(h2)

      // Linear interpolation
      val ratio = (height - h1) / (h2 - h1)
      val u = u1 + ratio * (u2 - u1)
      val v = v1 + ratio * (v2 - v1)

      (u, v)

    case (Some(h), None) => windsAtHeights(h)  // Use highest available
    case (None, Some(h)) => windsAtHeights(h)  // Use lowest available
    case _ => (0.0, 0.0)  // No data
  }
}
```

### Mapping to MeteoData Requirements

```scala
class AromeMeteoDataAdapter(data: AromeData, time: OffsetDateTime) extends MeteoData {

  def windAtHeight(heightMeters: Int): Option[Wind] = {
    val (u, v) = interpolateWind(heightMeters.toDouble, data.windsAtHeights)
    if (u != 0.0 || v != 0.0) {
      Some(Wind(MetersPerSecond(u), MetersPerSecond(v)))
    } else {
      None
    }
  }
}
```

---

## Height Conversion: AGL ↔ AMSL

### AGL to AMSL

```scala
val altitudeAMSL = heightAGL + terrainElevation
```

**Terrain elevation** comes from SP2 file (`ALTITUDE` field).

### AMSL to AGL

```scala
val heightAGL = altitudeAMSL - terrainElevation
```

---

## Vector Tiles Generation

### Wind Layers for Frontend

With multi-height wind data available, generate MVT tiles for:

```scala
val windLayers = Seq(
  ("wind-surface", 10),           // 10m AGL
  ("wind-300m-agl", 300),         // 300m AGL
  ("wind-boundary-layer", pblh),  // At PBLH (interpolated)
  ("wind-2000m-amsl", 2000 + terrainElevation),
  ("wind-3000m-amsl", 3000 + terrainElevation),
  ("wind-4000m-amsl", 4000 + terrainElevation)
)

windLayers.foreach { case (layerName, heightAGL) =>
  val wind = interpolateWind(heightAGL, data.windsAtHeights)
  // Generate MVT for this layer...
}
```

---

## File Structure in Production

```
/mnt/soaringmeteo-data/arome/grib/pays_basque/
├── SP1_00H06H.grib2     # Surface parameters
├── SP2_00H06H.grib2     # Stability data
├── SP3_00H06H.grib2     # Heat flux
└── winds/               # Extracted wind files (~50 MB total)
    ├── u_250m.grib2     (~4 MB each)
    ├── v_250m.grib2
    ├── u_500m.grib2
    ├── v_500m.grib2
    ├── ...
    ├── u_3000m.grib2
    └── v_3000m.grib2
```

---

## Performance Considerations

### Memory Usage

**Before optimization**:
- Load HP1 (~470 MB) → OOM crash on 4GB heap

**After optimization**:
- Load 12 × 2 small files (~4 MB each) = ~100 MB peak
- Process sequentially to minimize memory footprint

### Processing Time

- Extracting winds with wgrib2: ~30 seconds for 12 heights
- Loading in Scala: ~2 seconds per hour
- Total for 25 hours: ~50 seconds for wind data

---

## Testing

### Verify Extraction

```bash
# Check that all wind files exist
for h in 250 500 750 1000 1250 1500 1750 2000 2250 2500 2750 3000; do
  ls -lh winds/u_${h}m.grib2 winds/v_${h}m.grib2
done

# Verify file content
wgrib2 winds/u_250m.grib2
# Should show: UGRD:250 m above ground
```

### Verify in Application

```scala
// In AromeData
val winds = data.windsAtHeights
assert(winds.size == 12, s"Expected 12 heights, got ${winds.size}")
assert(winds.contains(250), "Missing 250m wind")
assert(winds.contains(3000), "Missing 3000m wind")
```

---

## Troubleshooting

### Missing Wind Files

**Problem**: `Wind files not found for XXXm`

**Solution**:
1. Check wgrib2 extraction script ran successfully
2. Verify HP1 file contains the height level
3. Check file permissions

### Incorrect Wind Values

**Problem**: Winds show 0 or unrealistic values

**Solution**:
1. Verify GRIB file time index matches hour offset
2. Check coordinate transformation (lat/lon → grid indices)
3. Validate interpolation logic

### High Memory Usage

**Problem**: Still getting OOM errors

**Solution**:
1. Reduce number of extracted heights
2. Process heights sequentially, not in parallel
3. Close GRIB datasets immediately after reading

---

## References

### Internal

- `backend/arome/src/main/scala/org/soaringmeteo/arome/AromeGrib.scala`
- `backend/arome/src/main/scala/org/soaringmeteo/arome/AromeData.scala`
- `backend/arome/src/main/scala/org/soaringmeteo/arome/AromeMeteoDataAdapter.scala`
- `scripts/extract_arome_winds.sh` (production script)

### External

- [AROME Model Documentation](http://www.umr-cnrm.fr/spip.php?article121)
- [GRIB2 Format Specification](https://www.nco.ncep.noaa.gov/pmb/docs/grib2/)
- [wgrib2 Documentation](https://www.cpc.ncep.noaa.gov/products/wesley/wgrib2/)
- [NetCDF Java Library](https://docs.unidata.ucar.edu/netcdf-java/current/)

---

**Document Version**: 1.0
**Last Updated**: November 15, 2025
