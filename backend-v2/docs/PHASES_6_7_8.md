# Backend-v2 Unifié - Phases 6, 7 et 8

**Projet** : SoaringMeteo - Architecture Backend Unifiée v2
**Date** : 28 décembre 2025
**Status Phases 1-5** : ✅ Phases 1-4 complètes, 🔄 Phase 5 en cours

---

## 📊 Vue d'Ensemble du Projet

### Phases Complétées (Phases 1-4)

- ✅ **Phase 1** (3j) : Structures de données unifiées (UnifiedModelData, RawData)
- ✅ **Phase 2** (5j) : Parseurs génériques + FileHandlers (GFS, AROME, WRF)
- ✅ **Phase 3** (5j) : Pipeline unifiée + interpolation temporelle
- ✅ **Phase 4** (2j) : Configuration HOCON + CLI Main.scala

### Phase en Cours (Phase 5)

🔄 **Phase 5** (1 semaine) : Validation avec données réelles

**Progrès** :
- ✅ GfsFileHandler adapté au format réel (`GFS-{zone}-{hour}.grib2`)
- ✅ Détection automatique fichiers disponibles
- ✅ Gestion heures manquantes (démarre à H+3 au lieu de H+0)
- ✅ Premier test réussi : parsing GRIB + génération 7 PNG rasters
- ⚠️ Bug identifié : mismatch grille (311×121 vs 358×139) → erreur vector tiles

**À faire** :
- Fix GribParser grid generation
- Validation complète GFS + AROME
- Comparaison outputs production (±1% tolérance)
- Benchmarks performance

---

## Phase 6 : Phenomena Engine (8-9 mois) - ⏳ FUTUR

### Objectif

Implémenter **82 phénomènes météorologiques** spécifiques au vol libre pour améliorer l'analyse et la prévision.

### Approche

Chaque phénomène est un module indépendant qui prend en entrée des `UnifiedModelData` et produit des valeurs analysées spécialisées.

### Structure Architecturale

```
backend-v2/phenomena/
├── thermals/
│   ├── ThermalStrength.scala         # Force des thermiques
│   ├── ThermalCoverage.scala         # Couverture thermique
│   ├── ThermalHeight.scala           # Hauteur des thermiques
│   └── ThermalTurbulence.scala       # Turbulence dans thermiques
│
├── clouds/
│   ├── CumulusDevelopment.scala      # Développement des cumulus
│   ├── Overdevelopment.scala         # Sur-développement
│   ├── CloudStreets.scala            # Rues de nuages
│   └── CloudbaseHeight.scala         # Hauteur de base des nuages
│
├── winds/
│   ├── WindShear.scala               # Cisaillement de vent
│   ├── ValleyWinds.scala             # Vents de vallée
│   ├── SeaBreeze.scala               # Brise de mer
│   ├── MountainWaves.scala           # Ondes orographiques
│   └── Convergence.scala             # Zones de convergence
│
├── stability/
│   ├── InversionLayer.scala          # Couche d'inversion
│   ├── AtmosphericStability.scala    # Stabilité atmosphérique
│   └── LapseRate.scala               # Gradient thermique vertical
│
├── hazards/
│   ├── Thunderstorms.scala           # Risque d'orages
│   ├── Icing.scala                   # Givrage
│   ├── DowndraftRisk.scala           # Risque de descendance
│   └── TurbulenceRisk.scala          # Risque de turbulence
│
└── xcsoaring/
    ├── TaskOptimization.scala        # Optimisation de parcours
    ├── GlideRatio.scala              # Finesse optimale
    └── ArrivalHeight.scala           # Hauteur d'arrivée
```

### Catégories de Phénomènes (82 total)

#### 1. Thermiques (15 phénomènes)
- Thermal strength index
- Thermal coverage percentage
- Thermal spacing
- Thermal lifetime
- Blue thermal probability
- Dust devil risk
- Thermal organization patterns
- Morning thermal development
- Evening thermal decay
- Thermal trigger points
- Thermal streets orientation
- Thermal top height variation
- Thermal climb rate distribution
- Thermal core diameter
- Thermal edge turbulence

#### 2. Nuages (18 phénomènes)
- Cumulus base/top height
- Cloud street orientation
- Overdevelopment risk
- Cumulonimbus probability
- Cloud shadow impact
- Cloud lifecycle stage
- Cloud spacing patterns
- Convective cloud percentage
- Cloud base temperature
- Cloud base humidity
- Altocumulus castellanus presence
- Lenticular cloud zones
- Cap cloud indicators
- Cloud street spacing
- Cloud dissipation timing
- Cloud merging probability
- Cirrus overspread timing
- Stratus burnoff time

#### 3. Vents (20 phénomènes)
- Wind shear strength
- Valley wind channeling
- Sea breeze front location
- Sea breeze front timing
- Mountain wave amplitude
- Rotor zones
- Convergence lines
- Anabatic wind strength
- Katabatic wind strength
- Föhn wind probability
- Wind shadow zones
- Wind acceleration zones
- Crosswind component
- Headwind component
- Tailwind component
- Wind gradient (surface to altitude)
- Wind direction change with height
- Gusts factor
- Sustained wind variability
- Wind shift timing

#### 4. Stabilité Atmosphérique (12 phénomènes)
- Inversion layer height
- Inversion strength
- Atmospheric stability index
- Lapse rate (dry/wet)
- Lifted Index (LI)
- K-Index
- Showalter Index
- Total Totals Index
- Convective Available Potential Energy (CAPE)
- Convective Inhibition (CIN)
- Bulk Richardson Number
- Helicity index

#### 5. Sécurité (12 phénomènes)
- Thunderstorm proximity (km)
- Thunderstorm development timing
- Lightning risk
- Icing level (altitude)
- Icing severity
- Downdraft strength
- Turbulence intensity (light/moderate/severe)
- Wind gust factor
- Visibility reduction
- Precipitation intensity
- Hail probability
- Microburst risk

#### 6. XC Flying (17 phénomènes)
- Cross-country distance potential (km)
- Task speed estimate (km/h)
- Optimal cruise altitude (m AMSL)
- Glide ratio requirements
- Arrival height margin
- Turnpoint reachability
- Alternative landing options
- Energy line altitude
- MacCready setting recommendation
- Transition timing (thermal to glide)
- Convergence exploitation zones
- Cloud street alignment with task
- Wave exploitation potential
- Ridge soaring sections
- Glide range at altitude
- Safe glide altitude
- Final glide margin

### Timeline Phase 6

| Mois | Activité | Phénomènes |
|------|----------|------------|
| **M1-M2** | Phénomènes thermiques | 15 |
| **M3-M4** | Phénomènes nuages | 18 |
| **M5-M6** | Phénomènes vents | 20 |
| **M7** | Stabilité atmosphérique + Sécurité | 12 + 12 = 24 |
| **M8** | XC Flying | 17 |
| **M9** | Tests, validation, optimisation | - |

**Total** : 8-9 mois

### Pattern d'Implémentation

**Interface générique** :

```scala
package org.soaringmeteo.phenomena

trait Phenomenon[T] {
  /** Nom du phénomène */
  def name: String

  /** Description courte */
  def description: String

  /** Calcule le phénomène à partir des données unifiées */
  def compute(data: UnifiedModelData): T

  /** Niveau de confiance (0.0 à 1.0) */
  def confidence(data: UnifiedModelData): Double
}
```

**Exemple d'implémentation** :

```scala
package org.soaringmeteo.phenomena.thermals

import org.soaringmeteo.UnifiedModelData
import squants.motion.Velocity

object ThermalStrength extends Phenomenon[Velocity] {
  override def name = "thermal-strength"
  override def description = "Thermal updraft strength index"

  override def compute(data: UnifiedModelData): Velocity = {
    // Utilise W*, CAPE, BL depth, etc.
    val baseStrength = data.thermalVelocity
    val capeBoost = data.cape.map(c => MetersPerSecond(c.toGrays * 0.01)).getOrElse(MetersPerSecond(0))
    val stabilityFactor = computeStabilityFactor(data)

    baseStrength * stabilityFactor + capeBoost
  }

  override def confidence(data: UnifiedModelData): Double = {
    // Confiance basée sur disponibilité des données
    val hasCApe = data.cape.isDefined
    val hasBLDepth = data.boundaryLayerDepth.toMeters > 100

    if (hasCApe && hasBLDepth) 0.9
    else if (hasBLDepth) 0.7
    else 0.5
  }

  private def computeStabilityFactor(data: UnifiedModelData): Double = {
    // Analyse gradient thermique vertical
    ???
  }
}
```

### Intégration dans UnifiedModelData

**Option 1 : Extension de UnifiedModelData**

```scala
case class UnifiedModelData(
  // ... champs existants ...

  // Phénomènes calculés (optionnels)
  phenomena: Map[String, Any] = Map.empty
)

// Usage
val data = UnifiedModelData(...)
val enrichedData = data.copy(
  phenomena = Map(
    "thermal-strength" -> ThermalStrength.compute(data),
    "overdevelopment-risk" -> Overdevelopment.compute(data),
    // ... autres phénomènes
  )
)
```

**Option 2 : Structure séparée**

```scala
case class PhenomenaAnalysis(
  baseData: UnifiedModelData,
  thermals: ThermalsPhenomena,
  clouds: CloudsPhenomena,
  winds: WindsPhenomena,
  stability: StabilityPhenomena,
  hazards: HazardsPhenomena,
  xcsoaring: XCSoaringPhenomena
)

case class ThermalsPhenomena(
  strength: Velocity,
  coverage: Double,
  height: Length,
  turbulence: Double,
  // ... 11 autres
)
```

### Outputs Phase 6

**Nouveaux layers PNG** :
- `phenomena-thermal-strength.png`
- `phenomena-overdevelopment-risk.png`
- `phenomena-wind-shear.png`
- etc.

**JSON enrichi** :
```json
{
  "time": "2025-12-28T12:00Z",
  "location": {"lat": 43.0, "lon": -1.0},
  "base_data": {
    "thermal_velocity": 2.5,
    "boundary_layer_depth": 2000
  },
  "phenomena": {
    "thermals": {
      "strength": {"value": 3.2, "unit": "m/s", "confidence": 0.9},
      "coverage": {"value": 65, "unit": "%", "confidence": 0.85}
    },
    "clouds": {
      "overdevelopment_risk": {"value": 0.3, "unit": "0-1", "confidence": 0.7}
    }
  }
}
```

### Note Important

**Phase 6 est optionnelle pour Phase 5** : Le backend-v2 fonctionne déjà avec les données de base (thermal velocity, boundary layer, winds, etc.) sans nécessiter tous les 82 phénomènes. Les phénomènes peuvent être ajoutés progressivement après déploiement.

---

## Phase 7 : Downscaling 1km (2-3 semaines) - ⏳ FUTUR

### 7.1 Règle de Downscaling

📌 **RÈGLE CRITIQUE** : Le downscaling n'a lieu QUE SI le modèle étudié présente des variables météo au-delà de la résolution de 1,1 km.

**Conditions** :
- Si résolution modèle ≤ 1,0 km → **Pas de downscaling** (données natives utilisées telles quelles)
- Si résolution modèle > 1,1 km → **Downscaling vers 1,0 km**

**Exemples concrets** :

| Modèle | Résolution Native | Action | Facteur |
|--------|-------------------|--------|---------|
| GFS | ~27 km | ✅ Downscale 27km → 1km | 27× |
| AROME | 2.5 km | ✅ Downscale 2.5km → 1km | 2.5× |
| ICON-D2 | 2.2 km | ✅ Downscale 2.2km → 1km | 2.2× |
| WRF (hypothétique) | 0.8 km | ❌ Pas de downscaling | - |
| AROME-HD (futur) | 0.5 km | ❌ Pas de downscaling | - |

### 7.2 Objectif

Fournir une résolution finale **uniforme de 1 km** sur toute la zone Pyrénées, indépendamment du modèle source utilisé.

**Bénéfices** :
- Résolution homogène pour tous les modèles
- Détails terrain améliorés (vallées, crêtes, exposition)
- Prévisions locales plus précises
- Compatibilité avec topographie SRTM 90m

### 7.3 Architecture Downscaling

#### Interface Générique

```scala
package org.soaringmeteo.downscaling

import org.soaringmeteo.UnifiedModelData
import squants.space.Length

/**
 * Interface pour algorithmes de downscaling.
 */
trait Downscaler {
  /**
   * Downscale une grille de données météo.
   *
   * @param inputGrid Grille native du modèle
   * @param inputResolution Résolution native (ex: 2.5km)
   * @param targetResolution Résolution cible (1km)
   * @param dem Modèle numérique de terrain
   * @return Grille downscalée
   */
  def downscale(
    inputGrid: IndexedSeq[IndexedSeq[UnifiedModelData]],
    inputResolution: Length,
    targetResolution: Length,
    dem: DigitalElevationModel
  ): IndexedSeq[IndexedSeq[UnifiedModelData]]
}
```

#### BilinearDownscaler (Simple, rapide)

**Usage** : Validation initiale, benchmarks

```scala
package org.soaringmeteo.downscaling

import squants.space.{Kilometers, Length}

/**
 * Downscaling par interpolation bilinéaire simple.
 * Rapide mais ne tient pas compte du terrain.
 */
class BilinearDownscaler extends Downscaler {

  override def downscale(
    inputGrid: IndexedSeq[IndexedSeq[UnifiedModelData]],
    inputResolution: Length,
    targetResolution: Length,
    dem: DigitalElevationModel
  ): IndexedSeq[IndexedSeq[UnifiedModelData]] = {

    val factor = (inputResolution / targetResolution).toEach
    val outputWidth = (inputGrid.size * factor).toInt
    val outputHeight = (inputGrid.head.size * factor).toInt

    // Interpolation bilinéaire classique
    (0 until outputWidth).map { x =>
      (0 until outputHeight).map { y =>
        val srcX = x.toDouble / factor
        val srcY = y.toDouble / factor

        bilinearInterpolate(inputGrid, srcX, srcY)
      }
    }
  }

  private def bilinearInterpolate(
    grid: IndexedSeq[IndexedSeq[UnifiedModelData]],
    x: Double,
    y: Double
  ): UnifiedModelData = {
    val x0 = x.floor.toInt
    val x1 = (x0 + 1).min(grid.size - 1)
    val y0 = y.floor.toInt
    val y1 = (y0 + 1).min(grid.head.size - 1)

    val wx = x - x0
    val wy = y - y0

    // Interpolation de tous les champs
    interpolateData(
      grid(x0)(y0), grid(x1)(y0),
      grid(x0)(y1), grid(x1)(y1),
      wx, wy
    )
  }
}
```

#### TerrainAwareDownscaler (Qualité production)

**Usage** : Production, résultats optimaux

```scala
package org.soaringmeteo.downscaling

import squants.motion.MetersPerSecond
import squants.space.Meters
import squants.thermal.Celsius

/**
 * Downscaling tenant compte du terrain.
 * Applique des corrections orographiques pour améliorer la précision.
 */
class TerrainAwareDownscaler extends Downscaler {

  private val bilinear = new BilinearDownscaler()

  override def downscale(
    inputGrid: IndexedSeq[IndexedSeq[UnifiedModelData]],
    inputResolution: Length,
    targetResolution: Length,
    dem: DigitalElevationModel
  ): IndexedSeq[IndexedSeq[UnifiedModelData]] = {

    // 1. Interpolation bilinéaire de base
    val baseGrid = bilinear.downscale(inputGrid, inputResolution, targetResolution, dem)

    // 2. Appliquer corrections terrain
    baseGrid.zipWithIndex.map { case (row, x) =>
      row.zipWithIndex.map { case (data, y) =>
        val lat = /* calculer depuis grille */
        val lon = /* calculer depuis grille */

        applyTerrainCorrections(data, lat, lon, dem)
      }
    }
  }

  /**
   * Applique corrections orographiques.
   */
  private def applyTerrainCorrections(
    data: UnifiedModelData,
    lat: Double,
    lon: Double,
    dem: DigitalElevationModel
  ): UnifiedModelData = {

    val elevation = dem.elevationAt(lat, lon)
    val slope = dem.slopeAt(lat, lon)
    val aspect = dem.aspectAt(lat, lon)

    data.copy(
      // Ajustement thermiques selon exposition solaire
      thermalVelocity = adjustThermalVelocity(
        data.thermalVelocity,
        slope,
        aspect,
        data.time.getHour
      ),

      // Ajustement vents selon channeling/accélération
      surfaceWind = adjustWindForTerrain(
        data.surfaceWind,
        slope,
        aspect,
        dem
      ),

      // Ajustement température selon altitude
      surfaceTemperature = adjustTemperatureForAltitude(
        data.surfaceTemperature,
        elevation,
        data.elevation  // Altitude originale
      )
    )
  }

  /**
   * Ajuste vitesse thermique selon exposition solaire.
   * - Pentes sud : +thermiques (exposition solaire maximale)
   * - Pentes nord : -thermiques (ombrage)
   * - Pentes raides : +turbulence
   */
  private def adjustThermalVelocity(
    baseVelocity: Velocity,
    slope: Double,  // radians
    aspect: Double, // 0-360° (0=nord, 180=sud)
    hourOfDay: Int
  ): Velocity = {

    val solarExposure = computeSolarExposure(slope, aspect, hourOfDay)
    val slopeFactor = 1.0 + (slope.toDegrees / 45.0) * 0.15  // +15% max pour pentes 45°

    MetersPerSecond(
      baseVelocity.toMetersPerSecond * solarExposure * slopeFactor
    )
  }

  /**
   * Exposition solaire (0.7 à 1.3).
   */
  private def computeSolarExposure(
    slope: Double,
    aspect: Double,
    hourOfDay: Int
  ): Double = {
    // Azimuth soleil (simplifié)
    val sunAzimuth = (hourOfDay - 12) * 15  // -180 à +180

    // Différence orientation pente vs soleil
    val angleDiff = Math.abs(aspect - (180 + sunAzimuth))

    // Facteur exposition (cos de l'angle)
    val exposure = Math.cos(Math.toRadians(angleDiff))

    // Normaliser entre 0.7 et 1.3
    0.7 + 0.6 * ((exposure + 1) / 2.0)
  }

  /**
   * Ajuste vent pour channeling/accélération.
   * - Vallées alignées avec vent : channeling (+vitesse)
   * - Crêtes perpendiculaires : accélération (+30-50%)
   * - Vallées perpendiculaires : blocage (-vitesse)
   */
  private def adjustWindForTerrain(
    baseWind: Wind,
    slope: Double,
    aspect: Double,
    dem: DigitalElevationModel
  ): Wind = {

    val windDirection = baseWind.direction
    val windSpeed = baseWind.speed

    // Analyser topographie locale (simplifié)
    val isValley = slope < 0.1  // Pente faible
    val isRidge = slope > 0.3   // Pente forte

    val speedFactor = if (isValley) {
      // Valley channeling
      val alignment = Math.cos(Math.toRadians(windDirection - aspect))
      if (alignment > 0.7) 1.2  // Aligné : +20%
      else if (alignment < -0.7) 0.7  // Perpendiculaire : -30%
      else 1.0
    } else if (isRidge) {
      // Ridge acceleration
      1.4  // +40% sur crêtes
    } else {
      1.0
    }

    Wind(
      u = MetersPerSecond(baseWind.u.toMetersPerSecond * speedFactor),
      v = MetersPerSecond(baseWind.v.toMetersPerSecond * speedFactor)
    )
  }

  /**
   * Ajuste température selon altitude.
   * Gradient adiabatique : -6.5°C/km
   */
  private def adjustTemperatureForAltitude(
    baseTemperature: Temperature,
    targetElevation: Length,
    sourceElevation: Length
  ): Temperature = {

    val elevationDiff = (targetElevation - sourceElevation).toKilometers
    val adiabaticLapseRate = -6.5  // °C/km
    val tempAdjustment = elevationDiff * adiabaticLapseRate

    Celsius(baseTemperature.toCelsiusDegrees + tempAdjustment)
  }
}
```

### 7.4 Digital Elevation Model (DEM)

#### Source de Données

**SRTM (Shuttle Radar Topography Mission)** :
- **Résolution** : 90m (SRTM3), peut être affiné à 30m (SRTM1)
- **Couverture** : Pyrénées complètes (56°N à 60°S)
- **Format** : GeoTIFF
- **Téléchargement** : https://earthexplorer.usgs.gov/

**Fichier cible** : `/data/dem/srtm_pyrenees_1km.tif`

#### Implémentation

```scala
package org.soaringmeteo.downscaling

import geotrellis.raster._
import geotrellis.raster.io.geotiff.GeoTiff
import squants.space.{Length, Meters}

/**
 * Modèle numérique de terrain (DEM).
 * Fournit altitude, pente, et orientation du terrain.
 */
class DigitalElevationModel(demFile: os.Path) {

  private val raster: MultibandGeoTiff = GeoTiff.readMultiband(demFile.toString)
  private val tile: Tile = raster.tile.band(0)
  private val extent = raster.extent
  private val cols = raster.cols
  private val rows = raster.rows

  /**
   * Altitude à une position donnée.
   */
  def elevationAt(lat: Double, lon: Double): Length = {
    val (col, row) = latLonToColRow(lat, lon)
    val elevation = tile.getDouble(col, row)
    Meters(elevation)
  }

  /**
   * Pente en radians (0 = plat, π/2 = vertical).
   */
  def slopeAt(lat: Double, lon: Double): Double = {
    val (col, row) = latLonToColRow(lat, lon)

    // Gradient horizontal et vertical
    val dzdx = (elevationAtColRow(col + 1, row) - elevationAtColRow(col - 1, row)) / 2.0
    val dzdy = (elevationAtColRow(col, row + 1) - elevationAtColRow(col, row - 1)) / 2.0

    Math.atan(Math.sqrt(dzdx * dzdx + dzdy * dzdy))
  }

  /**
   * Orientation pente en degrés (0 = nord, 90 = est, 180 = sud, 270 = ouest).
   */
  def aspectAt(lat: Double, lon: Double): Double = {
    val (col, row) = latLonToColRow(lat, lon)

    val dzdx = (elevationAtColRow(col + 1, row) - elevationAtColRow(col - 1, row)) / 2.0
    val dzdy = (elevationAtColRow(col, row + 1) - elevationAtColRow(col, row - 1)) / 2.0

    val aspect = Math.atan2(dzdy, dzdx).toDegrees

    // Convertir en orientation géographique (0 = nord)
    (90 - aspect + 360) % 360
  }

  private def latLonToColRow(lat: Double, lon: Double): (Int, Int) = {
    val col = ((lon - extent.xmin) / (extent.xmax - extent.xmin) * cols).toInt
    val row = ((extent.ymax - lat) / (extent.ymax - extent.ymin) * rows).toInt
    (col.max(0).min(cols - 1), row.max(0).min(rows - 1))
  }

  private def elevationAtColRow(col: Int, row: Int): Double = {
    val c = col.max(0).min(cols - 1)
    val r = row.max(0).min(rows - 1)
    tile.getDouble(c, r)
  }
}

object DigitalElevationModel {
  def load(demFile: os.Path): DigitalElevationModel = {
    require(os.exists(demFile), s"DEM file not found: $demFile")
    new DigitalElevationModel(demFile)
  }
}
```

### 7.5 Intégration dans UnifiedPipeline

**Configuration** (`unified.conf`) :

```hocon
unified {
  downscaling {
    enabled = true
    target-resolution = 1.0  # km
    threshold = 1.1          # Downscale si résolution > 1.1km
    method = "terrain-aware" # "bilinear" | "terrain-aware"
    dem-file = "/data/dem/srtm_pyrenees_1km.tif"
    cache-dem = true         # Cache DEM en mémoire
  }
}
```

**Code pipeline** :

```scala
def processRun(initTime: OffsetDateTime): Try[ProcessingSummary] = {
  // ... parsing + interpolation temporelle ...

  // Charger DEM si downscaling activé
  val demCache: Option[DigitalElevationModel] =
    if (config.getBoolean("unified.downscaling.enabled")) {
      Some(DigitalElevationModel.load(
        os.Path(config.getString("unified.downscaling.dem-file"))
      ))
    } else {
      None
    }

  for (hour <- firstAvailableHour to lastHour) {
    var grid = // ... parse ou interpole ...

    // Downscaling si nécessaire
    grid = applyDownscalingIfNeeded(grid, demCache)

    generateOutputs(grid, hour)
  }
}

private def applyDownscalingIfNeeded(
  grid: IndexedSeq[IndexedSeq[UnifiedModelData]],
  demCache: Option[DigitalElevationModel]
): IndexedSeq[IndexedSeq[UnifiedModelData]] = {

  demCache match {
    case Some(dem) =>
      val nativeResolution = Kilometers(gridResolutionKm)
      val targetResolution = Kilometers(config.getDouble("unified.downscaling.target-resolution"))
      val threshold = Kilometers(config.getDouble("unified.downscaling.threshold"))

      if (nativeResolution > threshold) {
        logger.info(s"Downscaling from $nativeResolution to $targetResolution")

        val downscaler = createDownscaler(config.getString("unified.downscaling.method"))
        downscaler.downscale(grid, nativeResolution, targetResolution, dem)
      } else {
        logger.info(s"Skipping downscaling (native resolution $nativeResolution ≤ threshold $threshold)")
        grid
      }

    case None =>
      grid  // Downscaling désactivé
  }
}

private def createDownscaler(method: String): Downscaler = method match {
  case "bilinear" => new BilinearDownscaler()
  case "terrain-aware" => new TerrainAwareDownscaler()
  case _ => throw new IllegalArgumentException(s"Unknown downscaling method: $method")
}
```

### 7.6 Timeline Phase 7

| Sous-phase | Durée | Tâches | Livrables |
|-----------|-------|--------|-----------|
| **7.1** | 1 sem | DEM SRTM + DigitalElevationModel.scala | DEM chargeable, méthodes elevation/slope/aspect |
| **7.2** | 1 sem | BilinearDownscaler + tests unitaires | Downscaling simple fonctionnel |
| **7.3** | 1 sem | TerrainAwareDownscaler (corrections orographiques) | Downscaling terrain-aware |
| **7.4** | 3j | Validation terrain (comparaison stations météo) | Rapport validation ±15% |
| **7.5** | 2j | Intégration UnifiedPipeline + benchmarks | Pipeline complet avec downscaling |

**Total** : 2-3 semaines

### 7.7 Validation

**Méthode** :
1. Comparer prévisions downscalées avec mesures réelles :
   - Stations météo altitude (température, vent)
   - Observations pilotes (thermiques, vents)
2. Benchmarks qualité :
   - Bilinear vs Terrain-Aware
   - Erreur absolue moyenne (MAE)
   - Erreur quadratique moyenne (RMSE)

**Critères de succès** :
- MAE température : ±2°C
- MAE vent : ±3 km/h
- MAE thermiques : ±15% (acceptable pour downscaling)
- Amélioration vs bilinear : ≥10%

---

## Phase 8 : Déploiement Production (1 semaine) - ⏳ FUTUR

### 8.1 Pré-requis Déploiement

✅ **Checklist complète avant production** :

1. ✅ **Phase 5 validée**
   - Outputs identiques à ±1% vs production actuelle
   - Tests GFS + AROME réussis
   - Performance acceptable (temps ≤ 120% ancien)

2. ✅ **Phase 6 complète** (ou partiellement)
   - Phénomènes prioritaires implémentés
   - Ou déploiement Phase 6 différé (phénomènes ajoutés progressivement)

3. ✅ **Phase 7 testée** (si activée)
   - Downscaling validé (±15% tolérance)
   - Performance acceptable
   - Ou downscaling désactivé initialement

4. ✅ **Documentation complète**
   - CLI usage guide
   - API documentation
   - Tutorial adding new model
   - Troubleshooting guide

5. ✅ **Tests automatisés**
   - Tests unitaires (parsing, interpolation)
   - Tests intégration (pipeline complète)
   - Tests validation (outputs vs attendus)

6. ✅ **Monitoring prêt**
   - Métriques définies
   - Alertes configurées
   - Logs structurés

### 8.2 Stratégie de Déploiement (3 semaines)

#### Semaine 1 : Shadow Mode (Parallèle)

**Objectif** : Pipeline v2 tourne en parallèle sans affecter utilisateurs.

**Configuration cron** :

```bash
# Production actuelle (backend/ - inchangé)
0 */6 * * * /home/ubuntu/soaringmeteo/backend/gfs/bin/soaringmeteo-gfs
10 */6 * * * /home/ubuntu/soaringmeteo/backend/arome/bin/soaringmeteo-arome

# Nouveau v2 (backend-v2/ - en test parallèle)
5 */6 * * * /home/ubuntu/soaringmeteo/backend-v2/app/bin/soaringmeteo-unified --model=gfs --env=prod 2>&1 | logger -t soaringmeteo-v2-gfs
15 */6 * * * /home/ubuntu/soaringmeteo/backend-v2/app/bin/soaringmeteo-unified --model=arome --env=prod 2>&1 | logger -t soaringmeteo-v2-arome
```

**Actions quotidiennes** :
- Comparer outputs v1 vs v2 automatiquement
  ```bash
  #!/bin/bash
  # scripts/compare_outputs.sh
  diff -r /home/ubuntu/soaringmeteo/output/7/gfs/ \
          /home/ubuntu/soaringmeteo/output-unified/7/gfs/ \
    | grep -c "differ" > /tmp/diff_count.txt
  ```
- Monitoring erreurs (journalctl)
- Vérifier performance (CPU, mémoire, disque)
- Collecter métriques temps exécution

**Critères passage Semaine 2** :
- ✅ Outputs identiques ≥ 99%
- ✅ Pas d'erreurs critiques sur 7 jours
- ✅ Performance acceptable (temps ≤ 120% ancien, mémoire ≤ 150%)

#### Semaine 2 : Bascule Progressive

**Jour 1-2 : 10% utilisateurs** (Test A/B)

```nginx
# /etc/nginx/sites-available/soaringmeteo
location /v2/forecast-data/ {
    # 10% traffic sur backend-v2
    split_clients "${remote_addr}${time_iso8601}" $backend_version {
        10%     v2;
        *       v1;
    }

    if ($backend_version = "v2") {
        alias /home/ubuntu/soaringmeteo/output-unified/7/;
    }
    if ($backend_version = "v1") {
        alias /home/ubuntu/soaringmeteo/output/7/;
    }
}
```

**Monitoring** :
- Erreurs frontend JavaScript (console.errors)
- Feedback utilisateurs (bugs visuels, données manquantes)
- Métriques API (temps réponse, taux erreur)

**Jour 3-4 : 50% utilisateurs**

Augmenter progressivement :
```nginx
split_clients "${remote_addr}${time_iso8601}" $backend_version {
    50%     v2;
    *       v1;
}
```

**Jour 5-7 : 100% utilisateurs**

Bascule complète :
```nginx
location /v2/forecast-data/ {
    alias /home/ubuntu/soaringmeteo/output-unified/7/;
}
```

**Critères passage Semaine 3** :
- ✅ Taux erreur frontend < 0.1%
- ✅ Feedback utilisateurs positifs
- ✅ Métriques stables

#### Semaine 3 : Consolidation

**Actions** :
- Surveillance 24/7
- Corriger bugs mineurs rapidement
- Documentation retours utilisateurs
- Optimisations mineures (cache, parallélisation)

**Plan de rollback** (si problème critique) :
```bash
#!/bin/bash
# scripts/rollback.sh

echo "🚨 ROLLBACK TO v1"

# 1. Arrêter pipeline v2
systemctl stop soaringmeteo-unified

# 2. Pointer frontend sur ancien output
ln -sf /home/ubuntu/soaringmeteo/output /var/www/forecast-data

# 3. Redémarrer pipeline v1 (si arrêté)
systemctl start soaringmeteo-gfs
systemctl start soaringmeteo-arome

# 4. Notifier équipe
echo "Rollback completed at $(date)" | mail -s "SoaringMeteo ROLLBACK" equipe@soaringmeteo.org

# 5. Investigation logs
journalctl -u soaringmeteo-unified -n 1000 > /tmp/v2_logs.txt
```

**Critères rollback** :
- ❌ Erreurs > 5% des runs
- ❌ Performance > 200% ancien temps
- ❌ Bugs frontend critiques (carte blanche, données manquantes)
- ❌ Perte de données (forecasts incomplets)

**Après 1 mois stable** :
- 🎉 Succès confirmé
- Archiver `backend/` (tag git `v1-final`)
- Nettoyer ancien code (après 3 mois)
- Célébration équipe !

### 8.3 Monitoring Production

#### Métriques à Surveiller

**Configuration** (`unified.conf`) :

```hocon
monitoring {
  enabled = true

  metrics {
    pipeline-duration-seconds      # Temps exécution total
    hours-processed                # Heures traitées avec succès
    hours-failed                   # Heures échouées
    hours-interpolated             # Heures interpolées
    memory-peak-mb                 # Pic mémoire
    disk-usage-gb                  # Espace disque utilisé
    error-rate-percent             # Taux d'erreur
    grib-parse-duration-ms         # Temps parsing GRIB
    output-generation-duration-ms  # Temps génération outputs
  }

  alerts {
    pipeline-duration-seconds > 3600       # Alerte si > 1h
    hours-failed > 5                       # Alerte si > 5 heures échouées
    error-rate-percent > 5.0               # Alerte si > 5%
    memory-peak-mb > 8192                  # Alerte si > 8GB
    disk-usage-gb > 50                     # Alerte si > 50GB
  }

  # Export Prometheus
  prometheus {
    enabled = true
    port = 9090
  }
}
```

**Logs structurés** :

```scala
import org.slf4j.LoggerFactory
import com.fasterxml.jackson.databind.ObjectMapper

val logger = LoggerFactory.getLogger(getClass)
val mapper = new ObjectMapper()

// Log JSON structuré
def logPipelineSummary(summary: ProcessingSummary): Unit = {
  val logData = Map(
    "event" -> "pipeline_completed",
    "model" -> summary.modelId,
    "init_time" -> summary.initTime.toString,
    "duration_seconds" -> summary.durationSeconds,
    "hours_processed" -> summary.hoursProcessed,
    "hours_failed" -> summary.hoursFailed,
    "hours_interpolated" -> summary.hoursInterpolated,
    "success_rate" -> summary.successRate,
    "memory_peak_mb" -> summary.memoryPeakMb
  )

  logger.info(mapper.writeValueAsString(logData))
}
```

**Dashboard Grafana** :

Visualiser métriques :
- Temps exécution par run
- Taux de succès (%)
- Utilisation mémoire
- Heures échouées par jour
- Comparaison v1 vs v2

#### Alertes Email/Slack

```scala
import com.typesafe.config.Config

class AlertManager(config: Config) {

  def sendAlert(alert: Alert): Unit = {
    alert.severity match {
      case Severity.Critical =>
        sendEmail(alert)
        sendSlack(alert)

      case Severity.Warning =>
        sendSlack(alert)

      case Severity.Info =>
        logger.info(alert.message)
    }
  }

  private def sendEmail(alert: Alert): Unit = {
    val recipients = config.getStringList("monitoring.email.recipients")
    // Utiliser javax.mail
    ???
  }

  private def sendSlack(alert: Alert): Unit = {
    val webhookUrl = config.getString("monitoring.slack.webhook")
    // POST to Slack webhook
    ???
  }
}

case class Alert(
  severity: Severity,
  message: String,
  details: Map[String, Any]
)

sealed trait Severity
object Severity {
  case object Critical extends Severity
  case object Warning extends Severity
  case object Info extends Severity
}
```

### 8.4 Documentation Déploiement

#### À Créer

**1. `docs/deployment/PRODUCTION_DEPLOYMENT.md`**

```markdown
# Déploiement Production Backend-v2

## Checklist Pré-Déploiement

- [ ] Phase 5 validée (outputs ±1%)
- [ ] Tests performance OK (≤ 120% temps v1)
- [ ] Documentation complète
- [ ] Monitoring configuré
- [ ] Alertes testées
- [ ] Plan rollback vérifié
- [ ] Backup v1 complet

## Procédure Bascule

### Semaine 1 : Shadow Mode
...

### Semaine 2 : Bascule Progressive
...

### Semaine 3 : Consolidation
...

## Plan Rollback

En cas de problème critique :
```bash
./scripts/rollback.sh
```

## Contacts Urgence

- Équipe technique : equipe@soaringmeteo.org
- Hotline : +33 X XX XX XX XX
```

**2. `docs/deployment/MONITORING.md`**

```markdown
# Monitoring Backend-v2

## Métriques Critiques

### Pipeline
- `pipeline_duration_seconds` : Temps exécution
- `hours_processed` : Succès
- `hours_failed` : Échecs
- `error_rate_percent` : Taux erreur

### Seuils d'Alerte
- Duration > 3600s → Warning
- Errors > 5% → Critical
- Memory > 8GB → Warning

## Dashboard Grafana

URL : http://monitoring.soaringmeteo.org:3000

## Procédure Incident

1. Vérifier dashboard
2. Consulter logs : `journalctl -u soaringmeteo-unified`
3. Si critique : rollback
4. Investigation post-mortem
```

**3. `docs/deployment/TROUBLESHOOTING.md`**

```markdown
# Troubleshooting Backend-v2

## Erreurs Communes

### "GFS GRIB file not found"

**Cause** : Fichiers GFS pas téléchargés

**Solution** :
```bash
cd backend/gfs
./bin/soaringmeteo-gfs
```

### "Features must be within root tile extent"

**Cause** : Mismatch grille parsing vs zone

**Solution** : Vérifier GeographicZone et GribParser utilisent mêmes coordonnées

### Pipeline très lent (> 2h)

**Cause** : Cache GRIB désactivé ou mémoire insuffisante

**Solution** : Vérifier JVM heap (-Xmx), activer cache
```

### 8.5 Outils de Déploiement

**Script de déploiement automatisé** :

```bash
#!/bin/bash
# scripts/deploy_production.sh

set -e

echo "🚀 Déploiement Backend-v2 Production"

# 1. Backup v1
echo "📦 Backup v1..."
tar czf /backup/backend-v1-$(date +%Y%m%d).tar.gz /home/ubuntu/soaringmeteo/backend/

# 2. Build v2
echo "🔨 Build v2..."
cd /home/ubuntu/soaringmeteo/backend-v2
sbt clean compile test

# 3. Package
echo "📦 Package..."
sbt "app/Universal/packageZipTarball"

# 4. Deploy
echo "🚢 Deploy to /opt/soaringmeteo-v2..."
sudo tar xzf app/target/universal/soaringmeteo-unified.tgz -C /opt/

# 5. Configure systemd
echo "⚙️  Configure systemd..."
sudo cp scripts/systemd/soaringmeteo-unified.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable soaringmeteo-unified

# 6. Start (shadow mode)
echo "▶️  Start shadow mode..."
sudo systemctl start soaringmeteo-unified

# 7. Verify
echo "✅ Verify..."
sleep 10
sudo systemctl status soaringmeteo-unified

echo "✅ Déploiement terminé !"
echo "📊 Monitor : http://monitoring.soaringmeteo.org"
```

**Systemd service** :

```ini
# scripts/systemd/soaringmeteo-unified.service
[Unit]
Description=SoaringMeteo Unified Pipeline v2
After=network.target

[Service]
Type=simple
User=ubuntu
Group=ubuntu
WorkingDirectory=/opt/soaringmeteo-v2
ExecStart=/opt/soaringmeteo-v2/bin/soaringmeteo-unified --env=prod
Restart=on-failure
RestartSec=60

# Limites ressources
MemoryLimit=10G
CPUQuota=400%

# Logs
StandardOutput=journal
StandardError=journal
SyslogIdentifier=soaringmeteo-v2

[Install]
WantedBy=multi-user.target
```

---

## 🎯 Timeline Globale Complète

| Phase | Durée | Status | Description | Livrables |
|-------|-------|--------|-------------|-----------|
| **Phase 1** | 3j | ✅ **COMPLÈTE** | Structures données unifiées | UnifiedModelData, RawData |
| **Phase 2** | 5j | ✅ **COMPLÈTE** | Parseurs + FileHandlers | GribParser, GfsFileHandler, AromeFileHandler |
| **Phase 3** | 5j | ✅ **COMPLÈTE** | Pipeline + interpolation | UnifiedPipeline, TemporalInterpolator |
| **Phase 4** | 2j | ✅ **COMPLÈTE** | Configuration + CLI | unified.conf, Main.scala |
| **Phase 5** | 1 sem | 🔄 **EN COURS** | Validation GFS + AROME | Tests, comparaisons, benchmarks |
| **Phase 6** | 8-9 mois | ⏳ **FUTUR** | Phenomena Engine | 82 phénomènes météo |
| **Phase 7** | 2-3 sem | ⏳ **FUTUR** | Downscaling 1km | BilinearDownscaler, TerrainAwareDownscaler |
| **Phase 8** | 1 sem | ⏳ **FUTUR** | Déploiement production | Shadow mode → Bascule → Consolidation |
| **Total** | **~10 mois** | | Architecture complète | Production ready |

---

## 📝 Notes Importantes

### Phase 6 - Optionnelle pour Déploiement Initial

La Phase 6 (Phenomena Engine) peut être **déployée progressivement** :
- Déployer backend-v2 **sans** les 82 phénomènes (Phases 1-5 + 7)
- Ajouter phénomènes par **versions incrémentielles** (v2.1, v2.2, etc.)
- Prioriser phénomènes selon feedback utilisateurs

### Phase 7 - Configurable

Le downscaling peut être :
- **Activé** en production (`downscaling.enabled = true`)
- **Désactivé** initialement pour réduire complexité/temps
- **Activé progressivement** par zone ou modèle

### Flexibilité Architecture

L'architecture v2 permet :
- Ajout facile nouveaux modèles (ICON, ECMWF, etc.) en 1 jour
- Extension phénomènes sans toucher pipeline
- Configuration par environnement (dev/prod)
- Tests A/B (anciennes vs nouvelles implémentations)

---

**Document créé le** : 28 décembre 2025
**Dernière mise à jour** : 28 décembre 2025
**Version** : 1.0
**Statut** : Phases 6-7-8 spécifiées, Phase 5 en cours
