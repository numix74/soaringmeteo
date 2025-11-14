# CLAUDE.md - SoaringMeteo/HaizeHegoa

## Project Overview

**SoaringMeteo** is a weather forecast platform designed specifically for soaring pilots (paragliding, hang-gliding, sailplane). The system processes meteorological data from multiple numerical weather prediction models and generates interactive web visualizations.

**Website**: https://soaringmeteo.org/v2
**License**: GPL-3.0-or-later
**Repository**: HaizeHegoa (Basque name meaning "Ocean Wind")

### Project Mission
Provide accurate, specialized weather forecasts for soaring flight safety and planning, with focus on:
- Thermal updraft strength and coverage
- Boundary layer depth and soaring altitude
- Cross-country flying potential
- Convective cloud development
- Wind conditions at multiple altitudes

---

## Architecture

### High-Level Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    DATA PRODUCTION (Backend)                 │
│  Scala/SBT - Processes GRIB/NetCDF → PNG/MVT/JSON Assets   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                  DATA CONSUMPTION (Frontend)                 │
│    Solid.js/TypeScript - Interactive Map Visualization      │
└─────────────────────────────────────────────────────────────┘
```

### Two-Tier Architecture

1. **Backend (Scala)**: Three independent pipelines that produce forecast data
   - `gfs` - Global Forecast System (NOAA, ~0.25° resolution, 5-day forecast)
   - `wrf` - Weather Research & Forecasting (High-resolution local model, 2km)
   - `arome` - AROME model (Météo-France, 2.5km resolution, 25-hour forecast)

2. **Frontend (TypeScript/Solid.js)**: Single-page web application
   - Consumes PNG rasters and MVT vector tiles
   - Interactive OpenLayers map with multiple meteorological layers
   - Meteograms and Skew-T diagrams for location-specific forecasts

---

## Technology Stack

### Backend

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Scala | 2.13.12 |
| Build Tool | SBT | 1.9.7 |
| JDK | OpenJDK/Temurin | 17+ |
| Weather Data | GRIB/NetCDF (ucar.edu) | 5.8.0 |
| Raster/Tiles | GeoTrellis | 3.7.1 |
| Database | H2 (embedded) | 2.2.224 |
| ORM | Slick | 3.4.1 |
| JSON | Circe | 0.14.5 |
| Units | Squants | 1.8.3 |
| Config | Typesafe Config | 1.4.2 |
| Logging | Logback | 1.4.7 |
| Testing | Verify | 0.2.0 |

### Frontend

| Component | Technology | Version |
|-----------|-----------|---------|
| Framework | Solid.js | 1.8.12 |
| Language | TypeScript | 5.3.3 |
| Build Tool | Vite | 4.5.5 |
| Mapping | OpenLayers | 8.1.0 |
| i18n | Inlang Paraglide | 1.2.5 |
| PWA | Vite PWA Plugin | 0.20.0 |
| Styling | CSS + CSS Hooks | 2.0.4 |
| Node | Node.js | 18+ |

---

## Directory Structure

```
/
├── backend/                    # Scala meteorological data processing
│   ├── build.sbt              # SBT build configuration (4 sub-projects)
│   ├── project/               # SBT plugins and dependencies
│   │   ├── Dependencies.scala # Centralized dependency management
│   │   ├── Deploy.scala       # Deployment helpers
│   │   └── plugins.sbt        # SBT native packager
│   │
│   ├── common/                # Shared utilities for all pipelines
│   │   └── src/main/scala/org/soaringmeteo/
│   │       ├── MeteoData.scala           # Core trait (unified interface)
│   │       ├── MeteoDataAdapter.scala    # GFS adapter
│   │       ├── Forecast.scala            # GFS forecast domain model
│   │       ├── Wind.scala, Thermals.scala, XCFlyingPotential.scala
│   │       ├── ConvectiveClouds.scala    # Cloud analysis
│   │       ├── Extent.scala, Point.scala # Geospatial primitives
│   │       ├── grib/Grib.scala           # GRIB utilities
│   │       └── out/
│   │           ├── Raster.scala          # PNG generation
│   │           ├── VectorTiles.scala     # MVT generation
│   │           ├── JsonData.scala        # JSON output
│   │           └── ForecastMetadata.scala
│   │
│   ├── gfs/                   # GFS pipeline (5-day global forecast)
│   │   ├── dev.conf           # Development configuration
│   │   └── src/main/scala/org/soaringmeteo/gfs/
│   │       ├── Main.scala              # CLI entry point
│   │       ├── Settings.scala          # Configuration loading
│   │       ├── GribDownloader.scala    # NOAA GRIB download
│   │       ├── DataPipeline.scala      # Processing pipeline
│   │       ├── in/GfsGrib.scala        # GRIB parsing
│   │       └── out/Store.scala         # H2 database persistence
│   │
│   ├── wrf/                   # WRF pipeline (high-res local)
│   │   └── src/main/scala/org/soaringmeteo/wrf/
│   │       ├── Main.scala              # CLI entry point
│   │       ├── NetCdf.scala            # NetCDF file reading
│   │       ├── DataPipeline.scala      # Processing
│   │       └── Settings.scala
│   │
│   ├── arome/                 # AROME pipeline (Météo-France model)
│   │   └── src/main/scala/org/soaringmeteo/arome/
│   │       ├── Main.scala                    # Entry point (grouped GRIB)
│   │       ├── Settings.scala                # Multi-zone configuration
│   │       ├── AromeGrib.scala               # GRIB parsing
│   │       ├── AromeData.scala               # Data structures
│   │       ├── AromeMeteoDataAdapter.scala   # MeteoData adapter
│   │       ├── AromeMapGenerator.scala       # Map generation
│   │       └── out/Store.scala               # Persistence
│   │
│   └── logback.prod.xml       # Production logging configuration
│
├── frontend/                  # Solid.js web application
│   ├── package.json           # NPM dependencies
│   ├── tsconfig.json          # TypeScript configuration
│   ├── vite.config.ts         # Build configuration
│   ├── index.html             # Entry point
│   ├── public/                # Static assets (favicons)
│   ├── messages/              # i18n translations (8 languages)
│   │   └── en.json, fr.json, de.json, es.json, it.json, pl.json, sk.json, pt.json
│   │
│   └── src/
│       ├── index.ts           # App initialization
│       ├── App.tsx            # Main component with layout
│       ├── State.tsx          # Global state (Solid.js store)
│       ├── i18n.tsx           # Internationalization
│       │
│       ├── data/              # Data models
│       │   ├── Model.ts                  # Zone, Model types
│       │   ├── ForecastMetadata.ts       # Forecast run metadata
│       │   └── LocationForecasts.ts      # Location-specific data
│       │
│       ├── layers/            # Visualization layers
│       │   ├── Layer.tsx, Layers.tsx
│       │   ├── ThQ.tsx                   # XC Flying Potential
│       │   ├── ThermalVelocity.tsx       # Updraft velocity
│       │   ├── Wind.tsx                  # Wind overlay
│       │   ├── CumuliDepth.tsx           # Cloud depth
│       │   ├── SoaringLayerDepth.tsx     # Soaring altitude
│       │   └── CloudsRain.tsx            # Cloud & rain
│       │
│       ├── map/               # OpenLayers map
│       │   ├── Map.ts                    # Map initialization
│       │   └── Overlay.tsx               # Map overlays
│       │
│       ├── diagrams/          # Meteorological diagrams
│       │   ├── Meteogram.tsx             # Time series
│       │   ├── Sounding.tsx              # Skew-T diagrams
│       │   └── Clouds.ts                 # Cloud analysis
│       │
│       ├── styles/            # Styling
│       │   ├── main.css                  # Global styles
│       │   ├── Styles.tsx                # CSS-in-JS
│       │   └── Forms.tsx                 # Form styling
│       │
│       └── [40+ UI components]
│
├── scripts/                   # Deployment automation
│   ├── arome_daily_pipeline_fixed.sh
│   ├── deploy_arome_to_vps.sh
│   ├── monitor_arome.sh
│   └── test_arome_availability.sh
│
├── docs/                      # Additional documentation
│   ├── arome-pipeline-analysis.md
│   ├── arome-vps-analysis.md
│   └── decisions/             # Architecture decision records
│
├── .github/workflows/         # CI/CD pipelines
│   ├── backend.yml            # Scala tests
│   └── frontend.yml           # TypeScript build
│
├── CONTRIBUTING.md            # Development setup guide
├── PHASE2_DOCUMENTATION.md    # MeteoData architecture details
├── REFACTOR_METEODATA.md      # Refactoring notes
├── TRANSLATING.md             # Translation guide
└── README.md                  # Project overview
```

---

## Development Workflows

### Backend Development

#### Setup

1. **Install JDK 17**:
   ```bash
   sudo apt install openjdk-17-jdk
   java --version  # Verify
   ```

2. **Install SBT**:
   ```bash
   echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
   curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
   sudo apt update && sudo apt install sbt
   ```

#### Common SBT Commands

From the `backend/` directory, start the SBT shell with `sbt`:

```scala
// Compile all projects
compile

// Compile specific project
gfs/compile
wrf/compile
arome/compile

// Run tests
test                  // All tests
gfs/test             // GFS tests only
common/test          // Common tests only

// Build production binaries
gfs/Universal/packageZipTarball
wrf/Universal/packageZipTarball
arome/Universal/packageZipTarball

// Development tasks (from SBT shell root)
makeGfsAssets         // Download & process sample GFS data
makeGfsAssets 00      // Use 00Z GFS run
makeWrfAssets         // Process sample WRF data
```

#### Running Pipelines Locally

**GFS Pipeline**:
```bash
cd backend
sbt "gfs/run -r target/grib target/forecast/data"
# -r: reuse downloaded GRIB files
# -t 00|06|12|18: specify GFS run time
```

**WRF Pipeline**:
```bash
cd backend
sbt "wrf/run <output-dir> <init-time> <first-timestep> <nc-files...>"
# Example:
# sbt "wrf/run target/forecast/data 2023-10-29T18:00Z 2023-10-30T06:00Z wrfout_*.nc"
```

**AROME Pipeline**:
```bash
cd backend
sbt "arome/run"
# Reads configuration from reference.conf
# Processes grouped GRIB files (SP1_00H06H, SP2_00H06H, etc.)
```

#### Backend Configuration

Configuration files use HOCON format (Typesafe Config):

**GFS**: `backend/gfs/dev.conf`
```hocon
soargfs {
  forecast_length = 5          # Download 5 days
  forecast_history = 2         # Keep 2 previous runs
  download_rate_limit = 100    # Requests/second
  subgrids = [
    {
      id = "pyrenees"
      extent = ["-4.0", "41.25", "3.75", "44.25"]  # [lon_min, lat_min, lon_max, lat_max]
      label = "Pyrénées"
      vectorTileSize = 300
    }
  ]
}
```

**AROME**: `backend/arome/src/main/resources/reference.conf`
```hocon
arome {
  zones = [
    {
      name = "pays-basque"
      zone {
        gridWidth = 120
        gridHeight = 120
        gridResolutionKm = 2.5
        centerLat = 43.0
        centerLon = -1.0
      }
      gribDirectory = "/path/to/grib"
      outputDirectory = "/path/to/output"
    }
  ]
}
```

### Frontend Development

#### Setup

1. **Install Node 18 (via nvm)**:
   ```bash
   curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.5/install.sh | bash
   nvm install 18
   nvm use 18
   ```

2. **Install dependencies**:
   ```bash
   cd frontend
   npm ci  # Use exact versions from package-lock.json
   ```

#### Common NPM Commands

```bash
npm start              # Dev server (port 3000)
npm run build          # Production build
npm run compile        # i18n compilation + TypeScript check
npm run preview        # Preview production build
npm run deploy         # Build + deploy to production server
SERVER=soarwrf3.soaringmeteo.org npm run deploy  # Deploy to specific server
```

#### Frontend Development Server

The dev server serves both the app and forecast data:
```bash
npm start
# Opens http://localhost:3000
# Serves forecast data from ../backend/target/forecast/data
```

**Important**: Backend must generate assets first (run `makeGfsAssets` or `makeWrfAssets`).

---

## Testing

### Backend Testing

**Framework**: Verify (custom Scala test framework)

```bash
cd backend
sbt test                 # All tests
sbt gfs/test            # GFS tests only
sbt common/test         # Common tests only
```

**Test Files**:
- `backend/common/src/test/scala/org/soaringmeteo/out/ForecastMetadataTest.scala`
- `backend/gfs/src/test/scala/org/soaringmeteo/LocationForecastsTestSuite.scala`
- `backend/gfs/src/test/scala/org/soaringmeteo/InterpolationTestSuite.scala`

### Frontend Testing

**Status**: No automated testing infrastructure currently exists.

**Recommendation**: Add Vitest or Jest for component and unit testing.

---

## Key Architectural Patterns

### MeteoData Trait (Unified Interface)

**Purpose**: Decouple meteorological data sources from output generation.

**Location**: `backend/common/src/main/scala/org/soaringmeteo/MeteoData.scala`

```scala
trait MeteoData {
  def thermalVelocity: Velocity
  def boundaryLayerDepth: Double  // meters AGL
  def wind10m: Wind
  def windAtHeight(heightMeters: Int): Option[Wind]
  def temperature2m: Temperature
  def cape: Option[SpecificEnergy]
  def totalCloudCover: Int  // 0-100
  def convectiveClouds: Option[ConvectiveClouds]
  def xcFlyingPotential: Int  // 0-100
  def time: OffsetDateTime
  // ... additional fields
}
```

**Implementations**:
- `GfsMeteoDataAdapter` - Wraps `Forecast` (GFS model)
- `AromeMeteoDataAdapter` - Wraps `AromeData` (AROME model)
- Future: `IconMeteoDataAdapter`, `EcmwfMeteoDataAdapter`, etc.

**Benefits**:
1. **No code duplication**: Raster/VectorTiles/Store work with all models
2. **Extensible**: Add new model = create one adapter, reuse everything
3. **Type-safe**: Adaptation at compile-time, not runtime
4. **Clean separation**: Business logic separated from model specifics

### Data Flow Pipeline

```
Input: GRIB/NetCDF files
    ↓
Parse Format (GribDownloader, NetCdf, AromeGrib)
    ↓
Extract Variables (temperature, wind, pressure, etc.)
    ↓
Calculate Derived Values (thermals, XC potential, cloud depth)
    ↓
Wrap in MeteoData adapter
    ↓
Generate Outputs (parallel):
    ├─→ PNG Rasters (Raster.scala)
    ├─→ Vector Tiles MVT (VectorTiles.scala)
    ├─→ JSON Metadata (JsonData.scala)
    └─→ Database Storage (Store.scala)
    ↓
Frontend Consumption
```

### Frontend State Management

**Pattern**: Solid.js reactive store with computed properties

**Location**: `frontend/src/State.tsx`

```typescript
type State = {
  model: Model                        // GFS, WRF, or AROME
  forecastMetadata: ForecastMetadata  // Current forecast run
  selectedZone: Zone                  // Geographic zone
  hourOffset: number                  // Time delta from init
  primaryLayer: Layer                 // XC potential, thermals, etc.
  windLayer: Layer                    // Wind overlay
  detailedView: DetailedView          // Location-specific data
  // Settings (persisted to localStorage)
  windNumericValuesShown: boolean
  utcTimeShown: boolean
  mapKeyShown: boolean
}
```

**Persistence**: User preferences saved to `localStorage`

**Reactive Updates**: Map layers update automatically when state changes

---

## Code Conventions

### Scala Conventions

1. **Immutability**: Prefer `val` over `var`, use immutable collections
2. **Case Classes**: Use for data structures (automatic equality, copy, toString)
3. **Pattern Matching**: Prefer over if/else chains
4. **Options**: Use `Option[T]` instead of nulls
5. **Type Aliases**: Define in `package.scala` for common types
6. **Units**: Always use Squants for physical quantities (Temperature, Velocity, etc.)

**Example**:
```scala
// Good
case class Wind(u: Velocity, v: Velocity) {
  def speed: Velocity = MetersPerSecond(math.sqrt(u.value * u.value + v.value * v.value))
}

// Bad - no units, mutable
class Wind(var u: Double, var v: Double) {
  def speed = math.sqrt(u * u + v * v)  // What unit? Undefined!
}
```

### TypeScript Conventions

1. **Strict Mode**: Always enabled (`strict: true` in tsconfig.json)
2. **No `any`**: Avoid type `any`, use proper types or `unknown`
3. **Solid.js Reactivity**: Use `createMemo()` for derived state, `createEffect()` for side effects
4. **Components**: Prefer functional components with TypeScript
5. **Lazy Loading**: Use `lazy()` for code splitting large components

**Example**:
```typescript
// Good - proper types
const selectedZoneName = createMemo((): string =>
  state.selectedZone.name
);

// Bad - implicit any
const selectedZoneName = createMemo(() =>
  state.selectedZone.name
);
```

### File Naming

**Backend (Scala)**:
- Classes: `PascalCase.scala` (e.g., `GribDownloader.scala`)
- Packages: `lowercase` (e.g., `org.soaringmeteo.gfs`)
- Test files: `*TestSuite.scala` or `*Test.scala`

**Frontend (TypeScript)**:
- Components: `PascalCase.tsx` (e.g., `Meteogram.tsx`)
- Utilities: `camelCase.ts` (e.g., `shared.ts`)
- Styles: `PascalCase.tsx` or `.css` (e.g., `Styles.tsx`, `main.css`)

---

## Git Workflow

### Branching Strategy

- **Main branch**: `main` (or unspecified default branch)
- **Feature branches**: `claude/<description>-<session-id>`
- **Pull requests**: Required for merging to main

### Commit Message Format

**Pattern**: `<type>(<scope>): <subject>`

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `refactor`: Code refactoring
- `test`: Adding tests
- `chore`: Maintenance tasks

**Examples**:
```
feat(arome): Add deployment script for VPS
fix(arome): Complete VPS production analysis and repair scripts
docs: Add comprehensive AROME pipeline analysis
refactor(common): Extract MeteoData trait for multi-model support
```

### Commit Best Practices

1. **One commit per logical change** (not per file)
2. **Clear, descriptive messages** (explain WHY, not just WHAT)
3. **Test before committing**: Run `sbt compile` and `sbt test`
4. **Small, focused commits**: Easier to review and revert if needed

---

## Deployment

### Backend Deployment (Production Servers)

**Servers**: `soarwrf1.soaringmeteo.org`, `soarwrf3.soaringmeteo.org`

**From SBT console**:
```scala
gfs/deploy soarwrf1      // Deploy GFS to soarwrf1
gfs/deploy soarwrf3      // Deploy GFS to soarwrf3
wrf/deploy soarwrf1      // Deploy WRF
arome/deploy soarwrf1    // Deploy AROME
```

**What happens**:
1. Builds native package (tarball)
2. Uploads to server via rsync
3. Replaces previous version

**Manual Deployment**:
```bash
cd backend
sbt arome/Universal/packageZipTarball
# Creates target/universal/soaringmeteo-arome.tgz
# Then manually copy to server
```

### Frontend Deployment

**From frontend/ directory**:
```bash
npm run deploy                                      # Default server
SERVER=soarwrf3.soaringmeteo.org npm run deploy    # Custom server
```

**What happens**:
1. TypeScript type checking
2. i18n compilation
3. Production build (minified bundles)
4. rsync to server at `/v2/` path

**Deployed URL**: https://soarwrf1.soaringmeteo.org/v2

### CI/CD

**GitHub Actions** automatically run on push/PR:

1. **Backend** (`.github/workflows/backend.yml`):
   - Setup JDK 17
   - Run `sbt +test`
   - Cache SBT artifacts

2. **Frontend** (`.github/workflows/frontend.yml`):
   - Setup Node 18
   - Run `npm ci`
   - Run `npm run compile && npm run build`
   - Cache npm dependencies

---

## Important Technical Details

### GeoTrellis 3.7.1 Specifics

**Vector Tiles (MVT)**:
```scala
// Type inference requires explicit types for empty sequences
val layer = StrictLayer(
  name = "features",
  tileWidth = 4096,  // MVT standard
  version = 2,
  tileExtent = extent,
  points = features.toSeq,
  lines = Seq.empty[MVTFeature[LineString]],    // ✓ Correct
  polygons = Seq.empty[MVTFeature[Polygon]]     // ✓ Correct
  // NOT: lines = Seq.empty  // ✗ Infers Seq[Nothing]
)

// Serialization (new API)
val encoded: Array[Byte] = vectorTile.toBytes
```

### Squants Unit Handling

```scala
// SpecificEnergy returns Try[SpecificEnergy]
val cape: Double = 1200.0
val result: Option[SpecificEnergy] = SpecificEnergy(cape).toOption

// Always specify units explicitly
val velocity = MetersPerSecond(2.5)
val temp = Kelvin(288.15)
```

### Proj4j Coordinate Transforms

```scala
// Use factory, not constructor
val ctFactory = new CoordinateTransformFactory()
val transform = ctFactory.createTransform(srcCrs, dstCrs)
val result = new ProjCoordinate()
transform.transform(source, result)  // 2 params: source, result
```

### H2 Database Configuration

**In-memory** (development):
```hocon
h2db {
  url = "jdbc:h2:mem:arome"
  driver = "org.h2.Driver"
}
```

**File-based** (production):
```hocon
h2db {
  url = "jdbc:h2:file:./arome"
  driver = "org.h2.Driver"
}
```

**Usage**:
```scala
val db = Database.forConfig("h2db")
val result = Await.result(db.run(query), Duration.Inf)
```

---

## Common Tasks for AI Assistants

### Adding a New Weather Model

**Example**: Add ICON (DWD) model support

1. **Create adapter** in `backend/icon/`:
   ```scala
   class IconMeteoDataAdapter(data: IconData) extends MeteoData {
     def thermalVelocity = // map from IconData
     def boundaryLayerDepth = // map from IconData
     // ... implement all MeteoData methods
   }
   ```

2. **Add SBT project** in `build.sbt`:
   ```scala
   val icon = project.in(file("icon"))
     .enablePlugins(JavaAppPackaging)
     .settings(/* similar to gfs/wrf/arome */)
     .dependsOn(common)
   ```

3. **Create Main.scala** to parse ICON GRIB files

4. **Reuse existing infrastructure**:
   - `Raster.scala` - PNG generation
   - `VectorTiles.scala` - MVT generation
   - `Store.scala` - Database persistence

### Adding a New Frontend Layer

**Example**: Add "Wind Shear" layer

1. **Create layer definition** in `frontend/src/layers/WindShear.tsx`:
   ```typescript
   export const windShearLayer: Layer = {
     key: 'wind-shear',
     labelId: 'wind_shear',
     domain: [0, 10],  // m/s per 100m
     colorScale: windShearColors,
     makeReactiveComponents: (state) => ({
       raster: () => makeRasterLayer(state, 'wind-shear'),
     })
   };
   ```

2. **Register in** `frontend/src/layers/Layers.tsx`:
   ```typescript
   export const layers = [
     // ... existing layers
     windShearLayer
   ];
   ```

3. **Backend**: Add PNG/MVT generation in `Raster.scala` and `VectorTiles.scala`

### Fixing Compilation Errors

**Common issues**:

1. **Type mismatch with Squants**:
   ```scala
   // Error: SpecificEnergy(value) returns Try
   val cape: SpecificEnergy = SpecificEnergy(1200.0)  // ✗

   // Fix: unwrap Try
   val cape: Option[SpecificEnergy] = SpecificEnergy(1200.0).toOption  // ✓
   ```

2. **GeoTrellis type inference**:
   ```scala
   // Error: type mismatch
   lines = Seq.empty  // ✗

   // Fix: explicit type
   lines = Seq.empty[MVTFeature[LineString]]  // ✓
   ```

3. **Missing imports**:
   ```scala
   import squants.motion.{MetersPerSecond, Velocity}
   import squants.thermal.{Kelvin, Temperature}
   import squants.energy.SpecificEnergy
   ```

### Adding Translations

1. **Add language to Inlang config** (`frontend/project.inlang/settings.json`)

2. **Translate messages** in `frontend/messages/<lang>.json`

3. **Enable in UI** (`frontend/src/i18n.tsx`):
   ```typescript
   const supportedLangsAndLabels: [string, string][] = [
     ['en', 'English'],
     ['fr', 'Français'],
     ['de', 'Deutsch'],
     ['xx', 'NewLanguage'],  // Add here
   ];
   ```

4. **Test**: `npm run compile` to validate all translations exist

---

## Critical Safety Notes

### Aviation Safety

This application is used for **flight safety planning**. Code changes affecting meteorological calculations, data accuracy, or visualizations must be:

1. **Thoroughly tested** with real-world data
2. **Validated** against known good forecasts
3. **Reviewed** by domain experts (meteorology/aviation)
4. **Documented** with clear change logs

**Never** introduce changes that could:
- Silently corrupt data
- Misrepresent forecast confidence
- Hide critical weather hazards
- Introduce unit conversion errors

### Data Version Compatibility

The frontend reads **past forecast runs** (not just latest). When evolving data format:

**Safe changes**:
- Add optional fields
- Remove non-optional fields (frontend ignores)

**Requires two-stage deployment**:
1. Deploy backend, wait for new data generation
2. Deploy frontend only after backend has produced data

**Bump version** in:
- `backend/common/src/main/scala/org/soaringmeteo/out/package.scala`
- `frontend/src/data/ForecastMetadata.ts`

---

## Performance Considerations

### Backend

**Memory allocation** (configured in `build.sbt`):
- GFS: 5-6GB heap (`-Xmx6g -Xms5g`)
- WRF: 5GB heap (`-Xmx5g -Xms5g`)
- AROME: 4GB heap (`-Xmx4g -Xms4g`)

**Parallel processing**:
- Use `Future` for concurrent operations
- Example: AROME processes 25 hours in parallel

**Rate limiting**:
- GFS downloads rate-limited to avoid NOAA blocking
- Configured in `dev.conf`: `download_rate_limit = 100`

### Frontend

**Code splitting**:
```typescript
// Vite config manual chunks
manualChunks: {
  'ol': ['ol'],        // Separate OpenLayers bundle
  'solid': ['solid-js']
}
```

**Lazy loading**:
```typescript
const Meteogram = lazy(() => import('./diagrams/Meteogram'));
```

**PWA caching**: Service worker caches app shell and forecast data

---

## Debugging Tips

### Backend Debugging

**Enable verbose logging**:
```scala
// In logback.xml
<root level="DEBUG">
  <appender-ref ref="STDOUT" />
</root>
```

**Run in debug mode** (IntelliJ IDEA):
1. Import `build.sbt` as SBT project
2. Set breakpoints in Scala files
3. Debug configuration: run `Main.scala` with arguments

**Common issues**:
- GRIB file not found: Check path and permissions
- Out of memory: Increase heap size in `javaOptions`
- H2 database locked: Close other connections

### Frontend Debugging

**Browser DevTools**:
- Console: Check for JavaScript errors
- Network: Verify forecast data loads correctly
- Application: Check localStorage for persisted state

**Solid.js DevTools** (browser extension):
- Inspect reactive state
- Track component renders
- Debug signal updates

**Vite HMR**: Hot module replacement for fast development

---

## External Resources

### Official Documentation

- [Scala Documentation](https://docs.scala-lang.org/)
- [SBT Reference](https://www.scala-sbt.org/1.x/docs/)
- [Solid.js Guide](https://www.solidjs.com/docs/latest)
- [TypeScript Handbook](https://www.typescriptlang.org/docs/)
- [OpenLayers API](https://openlayers.org/en/latest/apidoc/)

### Libraries

- [GeoTrellis Wiki](https://github.com/locationtech/geotrellis/wiki)
- [Slick Documentation](https://scala-slick.org/docs/)
- [Squants](https://www.squants.com/)
- [Circe JSON](https://circe.github.io/circe/)
- [Proj4j](https://github.com/locationtech/proj4j)

### Specifications

- [GRIB Format](https://www.wmo.int/pages/prog/wis/2010np13_en.html)
- [NetCDF](https://www.unidata.ucar.edu/software/netcdf/)
- [MVT Specification](https://github.com/mapbox/vector-tile-spec)

### Weather Models

- [GFS Documentation](https://www.ncdc.noaa.gov/data-access/model-data/model-datasets/global-forcast-system-gfs)
- [WRF User Guide](https://www2.mmm.ucar.edu/wrf/users/)
- [AROME Model](https://www.umr-cnrm.fr/spip.php?article120&lang=en)

---

## Project History & Evolution

### Recent Major Changes

1. **MeteoData Refactoring** (October 2025):
   - Extracted unified `MeteoData` trait
   - Created `GfsMeteoDataAdapter` and `AromeMeteoDataAdapter`
   - Decoupled Raster/VectorTiles from specific forecast types
   - See: `REFACTOR_METEODATA.md`, `PHASE2_DOCUMENTATION.md`

2. **AROME Integration** (October 2025):
   - Added AROME pipeline for high-resolution French forecasts
   - Grouped GRIB file support (SP1_00H06H format)
   - Multi-zone configuration system
   - VPS deployment scripts

3. **UI Improvements** (2023-2024):
   - Mobile-responsive layout
   - PWA support for offline access
   - 8 language translations
   - Improved sounding diagrams

### Technical Debt & Future Work

**High Priority**:
- [ ] Add frontend testing (Vitest/Jest)
- [ ] Improve error handling in AROME pipeline
- [ ] Document AROME zone configuration process

**Medium Priority**:
- [ ] Add ICON model support (German DWD)
- [ ] Multi-model comparison view
- [ ] Performance optimization for large grids

**Low Priority**:
- [ ] Machine learning bias correction
- [ ] Ensemble forecast visualization
- [ ] Mobile native app (Capacitor)

---

## Contact & Support

**Project Maintainer**: equipe@soaringmeteo.org

**Contributing**: See `CONTRIBUTING.md` for detailed setup instructions

**Issues**: Open issues on GitHub for bugs or feature requests

**Donations**: https://www.paypal.com/donate (helps cover server costs ~1500 CHF/year)

---

## License

This project is licensed under **GPL-3.0-or-later**.

See `COPYING` file for full license text.

**Key Points**:
- Free to use, modify, and distribute
- Must share modifications under same license
- No warranty provided
- Commercial use allowed with GPL compliance

---

## Quick Reference Cheat Sheet

### SBT Commands
```bash
sbt compile          # Compile all
sbt test             # Run all tests
sbt gfs/run          # Run GFS pipeline
makeGfsAssets        # Dev: process sample data
gfs/deploy soarwrf1  # Deploy to production
```

### NPM Commands
```bash
npm start            # Dev server
npm run build        # Production build
npm run deploy       # Deploy to production
```

### File Locations
```
Backend entry:  backend/{gfs,wrf,arome}/src/main/scala/org/soaringmeteo/{gfs,wrf,arome}/Main.scala
Frontend entry: frontend/src/index.ts
Config:         backend/{module}/src/main/resources/reference.conf
Tests:          backend/{module}/src/test/scala/
```

### Key Types
```scala
// Backend
MeteoData         // Unified weather data interface
Forecast          // GFS forecast data
AromeData         // AROME model data
Wind, Velocity    // Squants types
OffsetDateTime    // Java time

// Frontend
State             // Global application state
Layer             // Map visualization layer
ForecastMetadata  // Forecast run metadata
```

---

**Last Updated**: November 2025
**Document Version**: 1.0
**Maintained By**: AI Assistant Context
