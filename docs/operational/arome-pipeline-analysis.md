# AROME Pipeline Analysis

**Date:** November 11, 2025
**Status:** Analysis of AROME scripts and production pipeline

## Executive Summary

The AROME (Application of Research to Operations at Mesoscale) weather forecasting pipeline for SoaringMeteo consists of two main components:
1. **Scala Processing Pipeline** (in repository) - Processes GRIB2 files and generates forecast data
2. **Shell Scripts** (NOT in repository) - Handle data download and orchestration on production servers

Analysis of production logs reveals missing shell scripts that are preventing AROME forecasts from running.

---

## 1. AROME Scala Pipeline (Repository Code)

### Location
`backend/arome/` - Complete Scala implementation similar to GFS and WRF pipelines

### Main Components

#### Main.scala
**Purpose:** Orchestrates the AROME data processing pipeline

**Key Features:**
- Processes GRIB2 files for 25-hour forecasts (hours 0-24)
- Multi-zone support (configurable geographic regions)
- Parallel processing of forecast hours
- Generates:
  - PNG raster maps
  - MVT (Mapbox Vector Tiles)
  - Database storage for location forecasts

**Entry Point:** `org.soaringmeteo.arome.Main`

**Usage:**
```bash
bin/arome <config-file>
```

#### Settings.scala
**Purpose:** Configuration management

**Configuration Structure:**
```hocon
arome {
  zones = [
    {
      name = "Pays Basque"
      lon-min = -2.0
      lon-max = 0.5
      lat-min = 42.8
      lat-max = 43.6
      step = 0.025
      grib-directory = "/mnt/soaringmeteo-data/arome/grib/pays_basque"
      output-directory = "/mnt/soaringmeteo-data/arome/output/pays_basque"
    }
  ]
}
```

#### AromeGrib.scala
**Purpose:** GRIB2 file reading and data extraction

**Reads from 3 GRIB file groups per hour:**
- **SP1 Files:** Basic meteorological data
  - Temperature at 2m (T2M)
  - Wind components U10/V10 at 10m

- **SP2 Files:** Atmospheric stability data
  - CAPE (Convective Available Potential Energy)
  - PBLH (Planetary Boundary Layer Height)
  - Cloud cover
  - Terrain elevation

- **SP3 Files:** Heat flux data
  - Sensible heat flux
  - Latent heat flux
  - Solar radiation

- **Winds Directory:** Wind profiles at multiple heights
  - Heights: 250m, 500m, 750m, 1000m, 1250m, 1500m, 1750m, 2000m, 2250m, 2500m, 2750m, 3000m
  - U and V components at each height

**Expected File Structure:**
```
{grib-directory}/
├── sp1/
│   ├── hour_0.grib2
│   ├── hour_1.grib2
│   └── ...hour_24.grib2
├── sp2/
│   ├── hour_0.grib2
│   └── ...
├── sp3/
│   ├── hour_0.grib2
│   └── ...
└── winds/
    ├── u_250m.grib2
    ├── v_250m.grib2
    ├── u_500m.grib2
    └── ...
```

#### AromeData.scala
**Purpose:** Weather data model and calculations

**Computed Meteorological Parameters:**
- Thermal velocity (w*) using convective heat flux formula
- Wind speed and direction
- Wind interpolation at arbitrary heights
- Key wind levels for soaring (300m AGL, 500m AGL, 1000m AGL, 2000m/3000m/4000m AMSL)

---

## 2. Production Shell Scripts (NOT in Repository)

### Current Status: MISSING

The following scripts are referenced in cron jobs but do not exist on the server:

### 2.1 download_arome_daily.sh (OLD - MISSING)
**Cron Schedule:** `30 5 * * *` (05:30 UTC daily)

**Expected Functionality:**
- Download AROME GRIB2 files from Météo-France servers
- Organize files into sp1/sp2/sp3/winds directories
- Handle file organization for 25-hour forecast horizon

**Status:** NOT FOUND - Script missing from `/home/ubuntu/`

### 2.2 generate_arome_daily.sh (OLD - MISSING)
**Cron Schedule:** `0 10 * * *` (10:00 UTC daily)

**Expected Functionality:**
- Invoke the Scala AROME pipeline: `bin/arome <config-file>`
- Process downloaded GRIB files
- Generate PNG maps and vector tiles
- Copy outputs to nginx directory for web serving
- Clean up old data

**Status:** NOT FOUND - Script missing from `/home/ubuntu/`

### 2.3 download_arome_robust.sh (NEW - REFERENCED IN UPDATED CRON)
**Cron Schedule:** `30 1 * * *` (01:30 UTC daily)

**Configuration:**
```bash
source /home/ubuntu/miniconda3/bin/activate wrf-env && /home/ubuntu/download_arome_robust.sh
```

**Status:** Unknown - Need to verify if this script exists and what it does

---

## 3. Cron Configuration Analysis

### Old Configuration (Not Working)
```cron
30 5 * * * /home/ubuntu/download_arome_daily.sh         # Download at 05:30 UTC
0 10 * * * /home/ubuntu/generate_arome_daily.sh          # Generate at 10:00 UTC
0 2 * * 0 find /home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME/ -type d -name "????????_??" -mtime +3 -exec rm -rf {} ; 2>/dev/null
```

**Issues:**
- Scripts missing from `/home/ubuntu/`
- References old WRF_BUILD path
- Uses older directory structure

### New Configuration (Current)
```cron
30 1 * * * source /home/ubuntu/miniconda3/bin/activate wrf-env && /home/ubuntu/download_arome_robust.sh
0 3 * * 0 find /mnt/soaringmeteo-data/arome -type d -mtime +7 -exec rm -rf {} +
```

**Changes:**
- Earlier download time (01:30 vs 05:30)
- New script name: `download_arome_robust.sh`
- Uses conda environment activation
- New data path: `/mnt/soaringmeteo-data/arome`
- Longer retention: 7 days vs 3 days
- **MISSING:** No generation/processing step!

---

## 4. Log File Analysis

### Recent Logs (from your output)

**AROME Download Logs:**
```
-rw-rw-r--  1 ubuntu ubuntu    1489 oct.  16 19:36 arome_download_20251016.log
-rw-rw-r--  1 ubuntu ubuntu 1629751 oct.  17 18:38 arome_download_20251017.log  # Large file - successful download
-rw-rw-r--  1 ubuntu ubuntu    1457 oct.  18 05:33 arome_download_20251018.log
```

**Pattern:**
- October 16-21 had regular downloads
- File size variation suggests Oct 17 was particularly successful (1.6MB vs ~1.5KB for others)
- Small file sizes (~1.5KB) likely indicate errors or minimal logging

**AROME Generate Logs:**
```
-rw-rw-r--  1 ubuntu ubuntu    3142 oct.  16 19:39 arome_generate_20251016.log
-rw-rw-r--  1 ubuntu ubuntu  699535 oct.  17 21:00 arome_generate_20251017.log  # Large - successful
-rw-rw-r--  1 ubuntu ubuntu  272631 oct.  18 10:04 arome_generate_20251018.log
```

**Pattern:**
- Processing happened after downloads
- October 17 shows the largest log (699KB) indicating successful processing
- Logs stop after October 21

**AROME Pipeline Log:**
```
-rw-rw-r--  1 ubuntu ubuntu     488 oct.  13 21:21 arome_pipeline_20251013.log
```

**Current Status (Nov 11):**
```
-rw-rw-r--  1 ubuntu ubuntu    1340 nov.  11 14:54 arome_20251111_1454.log
```
- Only 1.3KB - likely an error log

### Cron Log Shows Errors
```
/bin/sh: 1: /home/ubuntu/download_arome_daily.sh: not found
/bin/sh: 1: /home/ubuntu/generate_arome_daily.sh: not found
```

---

## 5. Data Flow Architecture

### Complete AROME Pipeline Flow

```
┌─────────────────────────────────────────────────────────────┐
│ 1. DATA DOWNLOAD (Missing Script)                           │
├─────────────────────────────────────────────────────────────┤
│ • Source: Météo-France AROME model servers                  │
│ • Frequency: Daily (01:30 UTC in new config)                │
│ • Output: GRIB2 files organized by type and hour            │
│ • Location: /mnt/soaringmeteo-data/arome/grib/pays_basque/  │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. DATA PROCESSING (Scala Pipeline)                         │
├─────────────────────────────────────────────────────────────┤
│ • Reads: GRIB2 files (sp1, sp2, sp3, winds)                 │
│ • Processes: 25 hours (0-24) in parallel                    │
│ • Computes: Thermal velocity, winds, cloud cover, etc.      │
│ • Generates:                                                 │
│   - PNG raster maps (thermal, wind, cloud, etc.)            │
│   - MVT vector tiles                                        │
│   - Database entries for location forecasts                 │
│ • Output: /mnt/soaringmeteo-data/arome/output/pays_basque/  │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. WEB SERVING (nginx)                                      │
├─────────────────────────────────────────────────────────────┤
│ • Copy assets to nginx directory                            │
│ • Serve to https://soaringmeteo.org                         │
└─────────────────────────────────────────────────────────────┘
```

---

## 6. Comparison with GFS Pipeline

### Similarities
- Both download GRIB files from external sources
- Both process data to generate PNG maps and vector tiles
- Both use Scala for data processing
- Both use similar build configuration (build.sbt)

### Differences

| Aspect | GFS | AROME |
|--------|-----|-------|
| **Spatial Coverage** | Global | Regional (France) |
| **Resolution** | 0.25° (~25km) | ~2.5km (0.025°) |
| **Forecast Horizon** | 120-384 hours | 25 hours |
| **Update Frequency** | 4x daily (00/06/12/18Z) | 1-2x daily |
| **Data Source** | NOAA (US) | Météo-France |
| **GRIB Organization** | Single files per forecast hour | Multiple files (sp1/sp2/sp3) + winds |
| **Download Method** | HTTP from NOAA servers | Custom (Météo-France protocol?) |

---

## 7. Issues and Recommendations

### Critical Issues

#### Issue 1: Missing Download Script
**Problem:** `download_arome_daily.sh` and `download_arome_robust.sh` not in repository

**Impact:** No AROME data being downloaded

**Recommendation:**
1. Locate the `download_arome_robust.sh` script on production server
2. Review its implementation
3. Add to version control (if appropriate)
4. Document download procedure

#### Issue 2: Missing Generation Script
**Problem:** `generate_arome_daily.sh` missing, no processing happening

**Impact:** Even if data is downloaded, it's not being processed

**Recommendation:**
Create a new generation script similar to GFS pipeline. Example structure:

```bash
#!/bin/bash
# generate_arome_daily.sh

set -euo pipefail

LOG_DIR="/var/log/soaringmeteo"
LOG_FILE="$LOG_DIR/arome_$(date +%Y%m%d_%H%M).log"
CONFIG_FILE="/home/ubuntu/arome-production.conf"
OUTPUT_DIR="/mnt/soaringmeteo-data/arome/output"
NGINX_DIR="/var/www/soaringmeteo/arome"

echo "=== AROME Generation Started at $(date) ===" | tee -a "$LOG_FILE"

# Activate environment if needed
source /home/ubuntu/miniconda3/bin/activate wrf-env

# Run Scala pipeline
cd /path/to/HaizeHegoa/backend
sbt "arome/run $CONFIG_FILE" 2>&1 | tee -a "$LOG_FILE"

# Copy to nginx
echo "=== Copying to nginx at $(date) ===" | tee -a "$LOG_FILE"
rsync -av "$OUTPUT_DIR/" "$NGINX_DIR/" 2>&1 | tee -a "$LOG_FILE"

echo "=== AROME Generation completed at $(date) ===" | tee -a "$LOG_FILE"
```

#### Issue 3: Inconsistent Paths
**Problem:** Old cron uses `/home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME/`, new uses `/mnt/soaringmeteo-data/arome`

**Recommendation:**
- Standardize on `/mnt/soaringmeteo-data/arome` (matches config file)
- Update all references
- Clean up old directories

#### Issue 4: No Error Monitoring
**Problem:** Scripts fail silently (logs show errors but no alerts)

**Recommendation:**
- Add email notifications on failure
- Implement health checks
- Monitor log file sizes (small files = errors)

### Configuration File Needed

The Scala pipeline requires a config file. Create `/home/ubuntu/arome-production.conf`:

```hocon
include "reference.conf"

arome {
  zones = [
    {
      name = "Pays Basque"
      lon-min = -2.0
      lon-max = 0.5
      lat-min = 42.8
      lat-max = 43.6
      step = 0.025
      grib-directory = "/mnt/soaringmeteo-data/arome/grib/pays_basque"
      output-directory = "/mnt/soaringmeteo-data/arome/output/pays_basque"
    }
  ]
}

h2db {
  url = "jdbc:h2:file:/mnt/soaringmeteo-data/arome/arome.h2"
  driver = "org.h2.Driver"
}
```

### Updated Cron Configuration

```cron
# AROME Pipeline - Download at 01:30 UTC daily
30 1 * * * source /home/ubuntu/miniconda3/bin/activate wrf-env && /home/ubuntu/download_arome_robust.sh >> /var/log/soaringmeteo/arome_download_$(date +\%Y\%m\%d).log 2>&1

# AROME Pipeline - Process at 04:00 UTC daily (after download completes)
0 4 * * * /home/ubuntu/generate_arome_daily.sh >> /var/log/soaringmeteo/arome_generate_$(date +\%Y\%m\%d).log 2>&1

# AROME Cleanup - Keep 7 days of data, run weekly on Sunday at 03:00 UTC
0 3 * * 0 find /mnt/soaringmeteo-data/arome -type d -mtime +7 -exec rm -rf {} + 2>&1 | logger -t arome-cleanup
```

---

## 8. Next Steps

### Immediate Actions (Priority Order)

1. **Verify current data locations**
   ```bash
   ls -la /home/ubuntu/*arome*.sh
   ls -la /mnt/soaringmeteo-data/arome/
   ls -la /home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME/ 2>/dev/null
   ```

2. **Review download_arome_robust.sh**
   - Understand download mechanism
   - Verify it's working
   - Check output directory matches config

3. **Create generate_arome_daily.sh**
   - Build Scala pipeline if needed: `cd backend && sbt arome/stage`
   - Create shell script to invoke pipeline
   - Test with existing data

4. **Update cron configuration**
   - Remove old entries
   - Add complete new pipeline
   - Test cron jobs manually first

5. **Verify Scala pipeline build**
   ```bash
   cd /path/to/HaizeHegoa/backend
   sbt arome/stage
   # Creates: backend/arome/target/universal/stage/bin/arome
   ```

6. **Test end-to-end**
   - Run download script manually
   - Verify GRIB files exist
   - Run generation script manually
   - Check output in nginx directory

### Long-term Improvements

1. **Add to version control**
   - Document shell scripts (or add to repo if appropriate)
   - Version control cron configuration
   - Document deployment procedure

2. **Monitoring**
   - Set up alerts for failed downloads/processing
   - Monitor disk usage
   - Track forecast data freshness

3. **Documentation**
   - Document AROME data sources
   - API documentation for Météo-France
   - Troubleshooting guide

4. **Testing**
   - Add unit tests for AromeGrib parsing
   - Integration tests for full pipeline
   - Validate output quality

---

## 9. Technical Details

### Build Configuration (build.sbt)

```scala
val arome =
  project.in(file("arome"))
    .enablePlugins(JavaAppPackaging)
    .settings(
      name := "arome",
      Universal / packageName := "soaringmeteo-arome",
      run / fork := true,
      javaOptions ++= Seq("-Xmx4g", "-Xms4g"),  // 4GB RAM
      Compile / mainClass := Some("org.soaringmeteo.arome.Main"),
      maintainer := "equipe@soaringmeteo.org",
      libraryDependencies ++= Seq(
        Dependencies.logback,
        "com.typesafe.slick" %% "slick" % "3.4.1",
        "com.h2database" % "h2" % "2.2.224",
        Dependencies.circeParser,
        Dependencies.config,
        Dependencies.geotrellisRaster,
      )
    )
    .dependsOn(common)
```

### Build Commands

```bash
# Compile
sbt arome/compile

# Run in dev mode
sbt "arome/run /path/to/config.conf"

# Create standalone distribution
sbt arome/stage
# Output: backend/arome/target/universal/stage/

# Create zip package
sbt arome/universal:packageBin
# Output: backend/arome/target/universal/soaringmeteo-arome-<version>.zip
```

---

## 10. References

### Internal References
- Repository: HaizeHegoa (SoaringMeteo)
- Backend README: `/backend/README.md`
- Build configuration: `/backend/build.sbt`
- AROME reference config: `/backend/arome/src/main/resources/reference.conf`

### External References
- AROME Model: http://www.umr-cnrm.fr/spip.php?article121
- Météo-France Data: https://donneespubliques.meteofrance.fr/
- GRIB2 Format: https://www.nco.ncep.noaa.gov/pmb/docs/grib2/
- NetCDF Java: https://docs.unidata.ucar.edu/netcdf-java/current/

---

## Appendix: File Structure

### Expected Production Directory Structure

```
/mnt/soaringmeteo-data/arome/
├── grib/
│   └── pays_basque/
│       ├── sp1/
│       │   ├── hour_0.grib2
│       │   ├── hour_1.grib2
│       │   └── ... hour_24.grib2
│       ├── sp2/
│       │   └── hour_*.grib2
│       ├── sp3/
│       │   └── hour_*.grib2
│       └── winds/
│           ├── u_250m.grib2
│           ├── v_250m.grib2
│           └── ... (all height levels)
├── output/
│   └── pays_basque/
│       ├── maps/
│       │   ├── 00/  # Hour 0
│       │   │   ├── thermal-velocity.png
│       │   │   ├── wind-speed.png
│       │   │   ├── wind-barbs.mvt
│       │   │   └── ...
│       │   ├── 01/  # Hour 1
│       │   └── ... 24/
│       └── locations/  # JSON forecasts for specific locations
└── arome.h2.db  # H2 database file
```

---

**Document Version:** 1.0
**Last Updated:** November 11, 2025
**Author:** Claude Code Analysis
