# Analyse du dépôt SoaringMeteo

## 📋 Vue d'ensemble du projet

**SoaringMeteo** (https://soaringmeteo.org) est un site web de prévisions météorologiques spécialement conçu pour les pilotes de vol libre (parapente, deltaplane et planeur).

### Objectif
Fournir des données météorologiques adaptées au vol à voile à partir de deux sources :
- **GFS** (Global Forecast System) - Données globales de NOAA (prévisions jusqu'à 8 jours)
- **WRF** (Weather Research and Forecasting) - Prévisions régionales haute résolution (2km et 6km)

---

## 🏗️ Architecture générale

Le projet suit une architecture **backend/frontend séparés** :

### Backend (Scala 2.13.12)
Pipeline de traitement des données météorologiques :
- Téléchargement des fichiers GRIB depuis NOAA
- Traitement des données WRF depuis serveurs dédiés
- Calculs météorologiques (thermiques, nuages, vent)
- Génération d'assets (PNG rasters + tuiles vectorielles MVT)
- Stockage sur disque avec base H2

### Frontend (SolidJS 1.8.12 + TypeScript 5.3.3)
Application web réactive :
- Carte interactive OpenLayers avec projection Lambert
- Gestion d'état centralisée (Domain class)
- Couches météo multiples (rasters + vecteurs)
- Diagrammes canvas (météogrammes, émagrammes)
- 8 langues supportées (EN, DE, ES, FR, IT, PL, PT, SK)
- Progressive Web App (PWA) avec offline support

---

## 📁 Structure détaillée des dossiers et fichiers

### Backend (`/backend`)

```
backend/
├── build.sbt                         # Configuration build principale (3 modules)
├── logback.prod.xml                  # Configuration logging production
│
├── project/                          # Configuration SBT
│   ├── build.properties             # Version SBT (1.9.7)
│   ├── plugins.sbt                  # Plugins (sbt-native-packager)
│   ├── Dependencies.scala           # Dépendances centralisées
│   └── Deploy.scala                 # Configuration déploiement serveurs
│
├── common/                           # Module partagé
│   ├── build.sbt
│   └── src/
│       ├── main/scala/org/soaringmeteo/
│       │   ├── ConvectiveClouds.scala      # Calcul cumulus (formule Hennig)
│       │   ├── Forecast.scala              # Classe principale des prévisions
│       │   ├── LocationForecasts.scala     # Prévisions par localisation
│       │   ├── Point.scala                 # Coordonnées géographiques
│       │   ├── Wind.scala                  # Modèle vent (u, v)
│       │   ├── Winds.scala                 # Collection vents altitude
│       │   ├── Thermals.scala              # Calculs thermiques
│       │   ├── Temperatures.scala          # Calculs températures
│       │   ├── XCFlyingPotential.scala     # Potentiel vol distance
│       │   ├── Interpolation.scala         # Interpolation grilles
│       │   ├── InitDateString.scala        # Parsing dates runs
│       │   ├── PathArgument.scala          # Arguments CLI
│       │   │
│       │   ├── grib/
│       │   │   └── Grib.scala             # Parsing fichiers GRIB
│       │   │
│       │   ├── out/                       # Génération outputs
│       │   │   ├── Raster.scala           # PNG avec ColorMaps
│       │   │   ├── VectorTiles.scala      # Tuiles MVT (vent)
│       │   │   ├── ForecastMetadata.scala # Métadonnées JSON
│       │   │   ├── JsonData.scala         # Export données JSON
│       │   │   └── package.scala          # Types partagés
│       │   │
│       │   └── util/
│       │       ├── WorkReporter.scala     # Suivi progression
│       │       └── package.scala
│       │
│       └── test/scala/org/soaringmeteo/
│           ├── out/ForecastMetadataTest.scala
│           └── util/WorkReporterTestSuite.scala
│
├── gfs/                              # Module GFS
│   ├── build.sbt
│   ├── dev.conf                     # Config développement
│   └── src/
│       ├── main/
│       │   ├── scala/org/soaringmeteo/gfs/
│       │   │   ├── Main.scala              # Point d'entrée CLI
│       │   │   ├── Settings.scala          # Configuration GFS
│       │   │   ├── Subgrid.scala           # Zones géographiques
│       │   │   ├── DataPipeline.scala      # Pipeline traitement
│       │   │   ├── JsonWriter.scala        # Export JSON locations
│       │   │   ├── GribDownloader.scala    # Téléchargement NOAA
│       │   │   ├── GfsInitializationTime.scala
│       │   │   │
│       │   │   ├── in/                    # Lecture données
│       │   │   │   ├── GfsGrib.scala      # Parsing GRIB GFS
│       │   │   │   ├── ForecastRun.scala  # Gestion run
│       │   │   │   └── IsobaricVariables.scala
│       │   │   │
│       │   │   ├── out/                   # Export données
│       │   │   │   ├── Store.scala        # Stockage H2
│       │   │   │   └── package.scala
│       │   │   │
│       │   │   └── util/
│       │   │       └── RateLimiter.scala  # Limite requêtes NOAA
│       │   │
│       │   └── resources/
│       │       └── reference.conf         # Configuration zones
│       │
│       └── test/scala/org/soaringmeteo/
│           ├── InterpolationTestSuite.scala
│           ├── LocationForecastsTestSuite.scala
│           └── util/RateLimiterSuite.scala
│
└── wrf/                              # Module WRF
    ├── build.sbt
    └── src/
        ├── main/scala/org/soaringmeteo/wrf/
        │   ├── Main.scala                  # Point d'entrée CLI
        │   ├── Settings.scala              # Configuration WRF
        │   ├── NetCdf.scala                # Parsing fichiers NetCDF
        │   ├── DataPipeline.scala          # Pipeline traitement
        │   └── Grid.scala                  # Gestion grilles
        │
        └── test/scala/
```

**Fonction des fichiers clés backend :**

| Fichier | Rôle principal |
|---------|---------------|
| `common/ConvectiveClouds.scala` | Calcul base/sommet cumulus via formule Hennig |
| `common/out/Raster.scala` | Génération PNG avec ColorMaps pour 5 couches |
| `common/out/VectorTiles.scala` | Génération tuiles MVT avec down-sampling |
| `common/out/ForecastMetadata.scala` | Export métadonnées forecast.json |
| `common/out/JsonData.scala` | Export données locations/*.json |
| `gfs/Subgrid.scala` | Définition zones (Europe, Amériques, etc.) |
| `gfs/GribDownloader.scala` | Téléchargement GRIB depuis NOAA avec rate limiting |
| `gfs/DataPipeline.scala` | Orchestration: download → parse → calculate → export |
| `gfs/out/Store.scala` | Stockage temporaire base H2 |
| `wrf/NetCdf.scala` | Lecture fichiers NetCDF WRF |
| `wrf/DataPipeline.scala` | Traitement WRF: parse → calculate → export |

---

### Frontend (`/frontend`)

```
frontend/
├── package.json                      # Dépendances npm
├── vite.config.ts                    # Configuration Vite
├── tsconfig.json                     # Configuration TypeScript
├── index.html                        # Point d'entrée HTML
│
├── public/                           # Assets statiques
│   ├── manifest.json                # PWA manifest
│   ├── robots.txt
│   └── *.png                        # Icônes PWA
│
├── messages/                         # Traductions i18n
│   ├── en.json
│   ├── de.json
│   ├── es.json
│   ├── fr.json
│   ├── it.json
│   ├── pl.json
│   ├── pt.json
│   └── sk.json
│
├── project.inlang/
│   └── settings.json                # Configuration Inlang
│
└── src/
    ├── index.ts                      # Point d'entrée JS (start() + PWA)
    ├── App.tsx                       # Composant racine + layout
    ├── State.tsx                     # ⭐ Gestion état (Domain class)
    ├── css-hooks.ts                  # Configuration CSS Hooks
    ├── i18n.tsx                      # Configuration i18n Paraglide
    ├── shared.ts                     # Utilitaires partagés
    ├── Plausible.tsx                 # Analytics
    │
    ├── data/                         # Modèles de données
    │   ├── Model.ts                  # Types Model, Zone, ModelName
    │   ├── ForecastMetadata.ts       # ⭐ Gestion métadonnées + fetch
    │   └── LocationForecasts.ts      # ⭐ Données prévisions détaillées
    │
    ├── map/                          # Intégration OpenLayers
    │   ├── Map.ts                    # ⭐ Initialisation carte + MapHooks
    │   └── Overlay.tsx               # Overlays sur carte
    │
    ├── layers/                       # Système de couches
    │   ├── Layer.tsx                 # ⭐ Interface Layer + helpers
    │   ├── Layers.tsx                # Registre toutes les couches
    │   ├── ThQ.tsx                   # Couche potentiel XC
    │   ├── ThermalVelocity.tsx       # Couche vitesse thermique
    │   ├── SoaringLayerDepth.tsx     # Couche profondeur ascendances
    │   ├── CumuliDepth.tsx           # Couche profondeur cumulus
    │   ├── CloudsRain.tsx            # Couche nuages/pluie
    │   └── Wind.tsx                  # ⭐ 7 couches vent (altitudes)
    │
    ├── diagrams/                     # Diagrammes canvas
    │   ├── Diagram.ts                # ⭐ Classe transformation coordonnées
    │   ├── Meteogram.tsx             # ⭐ Diagramme 24-72h (5 strates)
    │   ├── Sounding.tsx              # ⭐ Profil vertical atmosphère
    │   └── Clouds.ts                 # Utilitaires dessins nuages
    │
    ├── help/                         # Système aide
    │   ├── Help.tsx                  # Modal aide avec documentation
    │   ├── HelpButton.tsx            # Bouton ouverture aide
    │   └── data.ts                   # Contenu aide
    │
    ├── styles/                       # Composants de style
    │   ├── Styles.tsx                # Constantes style partagées
    │   └── Forms.tsx                 # Styles formulaires
    │
    ├── images/                       # Images
    │   ├── wind-0.png à wind-9.png  # Icônes flèches vent
    │   ├── marker-icon.png           # Marqueur localisation
    │   └── *.png                     # Autres assets
    │
    ├── ColorScale.ts                 # Classe échelles couleurs
    ├── shapes.tsx                    # Fonctions dessin (flèches, nuages)
    │
    ├── PeriodSelector.tsx            # ⭐ Sélecteur heure + météogramme
    ├── DaySelector.tsx               # Sélecteur jour précédent/suivant
    ├── LayersSelector.tsx            # ⭐ Menu sélection couches
    ├── LayerKeys.tsx                 # Légendes couleurs
    ├── LocationDetails.tsx           # ⭐ Popup détails clic carte
    ├── DetailedView.tsx              # Types vue détaillée
    ├── Settings.tsx                  # ⭐ Modal paramètres utilisateur
    ├── Burger.tsx                    # Menu burger overlay
    └── BurgerButton.tsx              # Bouton ouverture menu
```

**Fonction des fichiers clés frontend :**

| Fichier | Rôle principal |
|---------|---------------|
| `State.tsx` | **Classe Domain** - État centralisé, persistence localStorage/URL |
| `data/ForecastMetadata.ts` | Fetch forecast.json, gestion métadonnées runs |
| `data/LocationForecasts.ts` | Types données prévisions détaillées par localisation |
| `map/Map.ts` | Init OpenLayers, MapHooks pour contrôle carte |
| `layers/Layer.tsx` | Interface Layer, patterns summarizer/mapKey |
| `layers/Wind.tsx` | 7 couches vent (surface, 300m, 2000m, 3000m, 4000m, BL, SLT) |
| `diagrams/Meteogram.tsx` | Canvas 5 strates: ThQ, Thermal, High Air, Main Air, Rain |
| `diagrams/Sounding.tsx` | Profil vertical: température, vent, nuages par altitude |
| `diagrams/Diagram.ts` | Transformations coord locales → canvas |
| `PeriodSelector.tsx` | Sélecteur heure + météogramme intégré |
| `LocationDetails.tsx` | Popup clic carte: summary/meteogram/sounding |
| `LayersSelector.tsx` | Menu: modèle, zone, couches primaires/vent |
| `Settings.tsx` | Paramètres: langue, vent numérique, UTC, légende |
| `App.tsx` | Composant racine, layout 3 zones, effects sync état→carte |

---

## 🔍 Découvertes techniques importantes

### 1. Base de données H2

**Rôle :** Stockage temporaire sur disque pendant le traitement des données météo.

**Problème résolu :**
- Ancien système : 8 GB RAM pour 20 000 points
- Nouveau avec H2 : 150 000 points avec moins de RAM
- Compromis : 3× plus lent mais coût infrastructure réduit

**Fichier de référence :** `docs/decisions/0001-on-disk-storage.md`

---

### 2. Génération des hauteurs de cumulus

#### Backend - Calcul
**Fichier :** `backend/common/src/main/scala/org/soaringmeteo/ConvectiveClouds.scala`

Formule de Hennig pour calculer la base :
```scala
val convectiveCloudsBottom: Length =
  Meters(122.6 * (surfaceTemperature - surfaceDewPoint).toCelsiusScale) + groundLevel
```

Profondeur = `top - bottom` (en mètres)

#### Backend - Génération PNG
**Fichier :** `backend/common/src/main/scala/org/soaringmeteo/out/Raster.scala:151-161`

```scala
Raster(
  "cumulus-depth",
  intData(_.convectiveClouds.fold(0)(clouds =>
    (clouds.top - clouds.bottom).toMeters.round.toInt
  )),
  ColorMap(
    50   -> 0xffffff00,  // Transparent
    400  -> 0xffffff7f,  // Blanc semi-transparent
    800  -> 0xffffffff,  // Blanc opaque
    1500 -> 0xffff00ff,  // Jaune
    3000 -> 0xff0000ff   // Rouge
  ),
  RgbaPngEncoding
)
```

**Important :** Les PNG contiennent uniquement des couleurs, pas de texte numérique.

#### Frontend - Affichage
**Fichier :** `frontend/src/layers/CumuliDepth.tsx`

Les valeurs numériques apparaissent uniquement :
- Dans les **popups** lors d'un clic sur la carte
- Dans la **légende** avec les paliers de couleur

**Pas d'affichage numérique direct sur la carte** (contrairement au vent).

---

### 3. Couche clouds-rain : Anomalie détectée

**Fichier :** `backend/common/src/main/scala/org/soaringmeteo/out/Raster.scala:119-149`

#### Logique d'encodage
```scala
doubleData { forecast =>
  val rain = forecast.totalRain.toMillimeters
  if (rain >= 0.2) {
    rain + 100  // Offset de +100 pour distinguer pluie/nuages
  } else {
    forecast.totalCloudCover.toDouble  // 0-100%
  }
}
```

#### ⚠️ Problème potentiel dans la ColorMap

Les valeurs actuelles :
```scala
1010.0 -> 0xfcff2dff,  // Jaune
1020.0 -> 0xfaca1eff,  // Orange
1030.0 -> 0xf87c00ff,  // Orange foncé
1050.0 -> 0xf70c00ff,  // Rouge
1100.0 -> 0xac00dbff,  // Violet
```

**Interprétation actuelle :** 910, 920, 930, 950, 1000 mm de pluie (absurde !)

**Devrait probablement être :**
```scala
110.0 -> ...  // 10 mm (pluie modérée)
120.0 -> ...  // 20 mm (pluie forte)
130.0 -> ...  // 30 mm (pluie très forte)
150.0 -> ...  // 50 mm (pluie torrentielle)
200.0 -> ...  // 100 mm (déluge)
```

---

### 4. Système d'affichage du vent selon le zoom

#### Algorithme de down-sampling
**Fichier :** `backend/common/src/main/scala/org/soaringmeteo/out/VectorTiles.scala:34-57`

**Principe :** Maximum 15 flèches de vent par tuile

```scala
val threshold = 15
var zoomLevelsValue = 1
var maxPoints = math.max(width, height)
while (maxPoints > threshold) {
  maxPoints = maxPoints / 2
  zoomLevelsValue = zoomLevelsValue + 1
}
val minViewZoomValue = math.max(maxViewZoom - zoomLevelsValue + 1, 0)
```

#### Sélection des points pour chaque niveau de zoom
**Fichier :** `backend/common/src/main/scala/org/soaringmeteo/out/VectorTiles.scala:88-109`

```scala
for (z <- 0 to maxZoom) {
  // Calcul du step pour down-sampling
  val step = 1 << (maxZoom - z)  // 2^(maxZoom - z)

  // Sélection 1 point sur 'step'
  val visiblePoints = for {
    x <- 0 until parameters.width by step
    y <- 0 until parameters.height by step
  } yield {
    // ... génération des features MVT ...
  }

  // Partition par tuile et écriture fichiers .mvt
}
```

**Exemple avec grille 64×64 et maxZoom = 3 :**

| Zoom | step | Points affichés | Répartition |
|------|------|-----------------|-------------|
| 0 | 8 | 64 points | 1 point sur 8 |
| 1 | 4 | 256 points | 1 point sur 4 |
| 2 | 2 | 1024 points | 1 point sur 2 |
| 3 | 1 | 4096 points | Tous |

#### Valeurs de maxViewZoom
**GFS :** `backend/gfs/src/main/scala/org/soaringmeteo/gfs/Subgrid.scala:41`
```scala
val maxViewZoom = 8  // Empirique
```

**WRF :** `backend/wrf/src/main/scala/org/soaringmeteo/wrf/NetCdf.scala:235`
```scala
val maxViewZoom = if (resolution < 4) 12 else 10
// 2km → zoom 12, 6km → zoom 10
```

#### Application côté frontend
**Fichier :** `frontend/src/map/Map.ts:182-191`

```typescript
setWindLayerSource: (url: string, minViewZoom: number, ...) => {
  secondaryLayer.setMinZoom(minViewZoom);  // OpenLayers masque si zoom < minViewZoom
  secondaryLayer.setSource(new VectorTileSource({
    url: url,
    extent: extent,
    maxZoom: maxZoom,
    tileSize: tileSize,
    format: new MVT(),
    transition: 1000
  }));
}
```

**Flux complet :**
```
Backend calcule minViewZoom
    ↓
Écrit dans forecast.json
    ↓
Frontend charge vectorTiles.minZoom
    ↓
App.tsx → setWindLayerSource()
    ↓
OpenLayers applique setMinZoom()
    ↓
Affichage conditionnel selon zoom carte
```

---

## 🎯 Couches de vent disponibles

**Fichier :** `frontend/src/layers/Wind.tsx`

7 couches configurées :
- `wind-surface` - Vent de surface
- `wind-boundary-layer` - Vent de la couche limite
- `wind-soaring-layer-top` - Vent au sommet de la couche ascendante
- `wind-300m-agl` - Vent à 300m AGL
- `wind-2000m-amsl` - Vent à 2000m AMSL
- `wind-3000m-amsl` - Vent à 3000m AMSL
- `wind-4000m-amsl` - Vent à 4000m AMSL

Les couches haute altitude excluent automatiquement les points où l'élévation du terrain dépasse l'altitude de la couche.

---

## 🎨 Frontend - Fonctionnement détaillé

### Architecture et patterns

Le frontend SoaringMeteo utilise **SolidJS** (framework réactif) avec une architecture centralisée autour d'une classe **Domain** qui gère tout l'état de l'application.

#### 1. Gestion d'état centralisée (`State.tsx`)

**Classe Domain** - Source unique de vérité :

```typescript
class Domain {
  // État réactif (Solid.js Store)
  state: {
    model: Model                      // GFS ou WRF
    forecastMetadata: ForecastMetadata // Run actuel
    selectedZone: Zone                // Zone géographique
    hourOffset: number                // Heure depuis init forecast

    primaryLayer: Layer               // Couche principale affichée
    primaryLayerEnabled: boolean
    windLayer: Layer                  // Couche vent
    windLayerEnabled: boolean

    detailedView?: DetailedView       // Popup détails localisation

    // Paramètres utilisateur
    windNumericValuesShown: boolean
    utcTimeShown: boolean
    mapKeyShown: boolean
  }

  // Collections forecast runs disponibles
  gfsRuns: ForecastMetadata[]
  wrfRuns: ForecastMetadata[]

  // Composants réactifs (regénérés auto selon état)
  primaryLayerReactiveComponents: Accessor<ReactiveComponents>
  windLayerReactiveComponents: Accessor<ReactiveComponents>
}
```

**Persistence :**
- **localStorage** : Couches sélectionnées, modèle, zone, paramètres
- **URL params** : `?model=wrf&zone=europe&lat=45.5&lng=9.5&z=7`
- Restauration automatique au chargement

**Méthodes principales :**
```typescript
// Navigation modèle/zone/temps
setModel(modelName: 'gfs' | 'wrf')
setZone(zone: Zone)
setHourOffset(offset: number)
nextHourOffset() / previousHourOffset()
nextDay() / previousDay()

// Gestion couches
setPrimaryLayer(layer: Layer)
enablePrimaryLayer(enabled: boolean)
setWindLayer(layer: Layer)
enableWindLayer(enabled: boolean)

// Vue détaillée
showLocationForecast(lat, lng, viewType)
hideLocationForecast()

// Paramètres
showWindNumericValues(boolean)
showUtcTime(boolean)
showMapKey(boolean)

// URLs assets
urlOfRasterAtCurrentHourOffset() → PNG URL
urlOfVectorTilesAtCurrentHourOffset() → MVT tiles URL
```

---

#### 2. Flux de données

```
┌──────────────────┐
│ Chargement app   │
│ index.ts         │
└────────┬─────────┘
         ▼
┌─────────────────────────────────┐
│ App.tsx - Loader                │
│ - Fetch forecast.json (GFS+WRF) │
│ - Create Domain                 │
└────────┬────────────────────────┘
         ▼
┌────────────────────────────────────┐
│ Domain créé avec état initial     │
│ - Load localStorage               │
│ - Parse URL params                │
│ - Select default forecast run     │
└────────┬───────────────────────────┘
         ▼
┌────────────────────────────────────┐
│ App.tsx - Layout 3 zones          │
│ TopZone    : Sélecteur heure      │
│ MiddleZone : Carte + détails      │
│ BottomZone : Navigation jours     │
└────────┬───────────────────────────┘
         ▼
┌────────────────────────────────────┐
│ Effects sync état → rendu         │
│ - Map layers updated              │
│ - Canvas diagrams redrawn         │
│ - UI components reactive          │
└───────────────────────────────────┘
```

**Quand l'utilisateur change l'heure :**
```
Clic bouton heure
  ↓
domain.setHourOffset(newOffset)
  ↓
state.hourOffset updated (signal)
  ↓
┌─────────────────────────────────────┐
│ Effets déclenchés en parallèle:    │
│ 1. urlOfRasterAtCurrentHourOffset()│
│    → setPrimaryLayerSource(newURL) │
│ 2. urlOfVectorTilesAtCurrentHourOffset()│
│    → setWindLayerSource(newURL)    │
│ 3. meteogram canvas redraw         │
│ 4. sounding canvas redraw          │
│ 5. period selector highlight       │
└─────────────────────────────────────┘
```

---

#### 3. Système de couches (Layers)

**Interface Layer :**
```typescript
type Layer = {
  key: string                    // Identifiant unique
  name: Accessor<string>         // Nom localisé
  title: Accessor<string>        // Titre légende
  dataPath: string              // Chemin fichiers data

  reactiveComponents(props) → ReactiveComponents
}

type ReactiveComponents = {
  summarizer: Accessor<Summarizer>  // Données popup
  mapKey: JSX.Element               // Légende couleurs
  help: JSX.Element                 // Documentation
}
```

**Couches primaires (rasters PNG) :**
1. **XC Potential** (`xc-potential`) - Potentiel vol distance 0-100%
2. **Soaring Layer Depth** (`soaring-layer-depth`) - Profondeur ascendances (m)
3. **Thermal Velocity** (`thermal-velocity`) - Vitesse thermique (m/s)
4. **Cumuli Depth** (`cumuli-depth`) - Profondeur cumulus (m)
5. **Clouds & Rain** (`clouds-rain`) - Nuages % + pluie mm

**Couches vent (tuiles vectorielles MVT) :**
1. Surface
2. Boundary Layer
3. Soaring Layer Top
4. 300m AGL
5. 2000m AMSL
6. 3000m AMSL
7. 4000m AMSL

**Pattern Summarizer :**
```typescript
const summarizer = summarizerFromLocationDetails(props,
  (detailedForecast, locationForecasts) => [
    [() => m().labelThQ(), <span>{forecast.xcPotential}%</span>],
    [() => m().labelThermalVel(), <span>{forecast.thermalVelocity} m/s</span>],
    // ... autres valeurs
  ]
)
```

Le summarizer se régénère automatiquement quand `hourOffset`, `zone`, ou `forecastMetadata` changent.

---

#### 4. Intégration OpenLayers (`map/Map.ts`)

**Stack de couches (bas → haut) :**
```
1. baseLayer       → Tuiles XYZ topomap
2. primaryLayer    → ImageStatic (PNG météo)
3. secondaryLayer  → VectorTileLayer (vent MVT)
4. markerLayer     → Point marker (localisation cliquée)
```

**MapHooks - Interface de contrôle :**
```typescript
type MapHooks = {
  locationClicks: Accessor<MapBrowserEvent>

  setPrimaryLayerSource(url, projection, extent)
  hidePrimaryLayer()

  setWindLayerSource(url, minZoom, extent, maxZoom, tileSize)
  hideWindLayer()
  enableWindNumericalValues(boolean)

  showMarker(lat, lng)
  hideMarker()
}
```

**Systèmes de coordonnées :**
- **Web Mercator** (EPSG:3857) - Affichage
- **WRF Lambert** - Projection données WRF
  ```
  +proj=lcc +lat_1=46 +lat_2=46 +lat_0=46 +lon_0=10
  +a=6370000 +b=6370000 +units=m
  ```
- **Geographic** (lat/lon) - Données GFS

**Effects de synchronisation (App.tsx) :**
```typescript
// Effect 1: Mise à jour couche primaire
createEffect(() => {
  const url = domain.urlOfRasterAtCurrentHourOffset()
  const zone = domain.effectiveZone()
  if (state.primaryLayerEnabled) {
    mapHooks.setPrimaryLayerSource(url, zone.raster.proj, zone.raster.extent)
  } else {
    mapHooks.hidePrimaryLayer()
  }
})

// Effect 2: Mise à jour couche vent
createEffect(() => {
  const vectorTiles = domain.effectiveZone().vectorTiles
  const url = domain.urlOfVectorTilesAtCurrentHourOffset()
  if (state.windLayerEnabled) {
    mapHooks.setWindLayerSource(
      url,
      vectorTiles.minZoom,  // ← Zoom min calculé par backend
      vectorTiles.extent,
      vectorTiles.zoomLevels - 1,
      vectorTiles.tileSize
    )
  } else {
    mapHooks.hideWindLayer()
  }
})

// Effect 3: Position marqueur
createEffect(() => {
  const detailedView = state.detailedView
  if (detailedView) {
    mapHooks.showMarker(detailedView.latitude, detailedView.longitude)
  } else {
    mapHooks.hideMarker()
  }
})

// Effect 4: Style affichage vent
createEffect(() => {
  mapHooks.enableWindNumericalValues(state.windNumericValuesShown)
})
```

**Style vent :**
```typescript
// Mode graphique: icônes wind-0.png à wind-9.png
// - Rotation selon direction
// - Scale selon vitesse (0.5 → 0.8)

// Mode numérique: icône + texte vitesse
// - Offset calculé selon vitesse
// - Texte noir semi-transparent
```

---

#### 5. Diagrammes Canvas

**Meteogram (`diagrams/Meteogram.tsx`)** - Prévision 24-72h

Structure 5 strates canvas empilées :
```
1. ThQ Diagram (20px)
   └─ Carrés couleur potentiel XC (cliquable)

2. Thermal Velocity (20px)
   └─ Carrés couleur vitesse thermique

3. High Air (20px)
   └─ Nuages haute altitude (>5000m)

4. Main Air (hauteur dynamique)
   ├─ Fond bleu ciel
   ├─ Couche limite (vert)
   ├─ Couches inversion (violet)
   ├─ Cumulus (formes nuages)
   ├─ Flèches vent multi-altitudes
   ├─ Ligne 0°C
   ├─ Grilles altitude
   └─ Lignes pression (rouge)

5. Rain Diagram (60px)
   ├─ Risque orage (éclairs colorés)
   ├─ Pluie convective (cyan)
   ├─ Pluie totale (bleu)
   ├─ Température (ligne rouge)
   ├─ Point rosée (ligne bleue)
   └─ Échelle pression
```

**Rendering :**
- 3 canvas : clé gauche, diagramme principal, clé droite
- Context 2D avec `Diagram` class pour transformations
- Responsive : hauteur ajustée selon espace dispo
- Interactivité : clic carré ThQ → jump to hour

**Sounding (`diagrams/Sounding.tsx`)** - Profil vertical

```
Axe X: Température (°C) par paliers de 10°
Axe Y: Altitude AGL (mètres)

Éléments dessinés:
├─ Couche limite (remplissage vert)
├─ Couches inversion (remplissage violet)
├─ Couverture nuageuse par altitude
├─ Ligne température (rouge)
├─ Ligne point rosée (bleu)
├─ Flèches vent à chaque niveau
└─ Cumulus si présents
```

**Bouton zoom :** Alterne entre plage complète et plage focalisée

**Classe Diagram (`diagrams/Diagram.ts`)** - Transformations coordonnées

```typescript
class Diagram {
  // Origine locale : coin bas-gauche, axe Y vers le haut

  line(from: [x,y], to: [x,y], style, dash?, clip?)
  fillRect(from, to, style)
  rect(from, to, style)
  text(content, location, style, align?, baseline?)
  fillShape(points[], style, strokeStyle?)
  cumulusCloud(bottomLeft, topRight)

  // Projections privées
  projectX(x) → canvas.x
  projectY(y) → canvas.y (inversé)
}

class Scale {
  constructor(domain: [min, max], range: [minPx, maxPx])
  apply(value) → pixelPosition
}
```

**Utilities :**
- `setupCanvas()` - Gestion device pixel ratio (mobile)
- `computeElevationLevels()` - Grilles altitude
- `temperaturesRange()` - Min/max pour scaling

---

#### 6. Composants UI et interactions

**Hiérarchie composants :**
```
App
├─ TopZone
│  └─ HourSelectorAndMeteogram (lazy)
│     ├─ PeriodSelector (boutons heures)
│     └─ Meteogram (canvas)
│
├─ MiddleZone
│  ├─ LayerKeys (légende carte)
│  ├─ SoundingDiagram (profil vertical)
│  └─ LocationDetails (popup)
│     ├─ Summary (tableau valeurs)
│     ├─ Meteogram détaillé
│     └─ Sounding
│
├─ BottomZone
│  ├─ DaySelector (prev/next day)
│  └─ HelpButton
│
└─ BurgerButton
   └─ Burger (menu overlay)
      ├─ LayersSelector
      │  ├─ Radio modèle (GFS/WRF)
      │  ├─ Select init time
      │  ├─ Radio zones
      │  ├─ Fieldset couche primaire
      │  └─ Fieldset couche vent
      │
      ├─ Settings (modal)
      │  ├─ Select langue
      │  ├─ Checkbox vent numérique
      │  ├─ Radio timezone (local/UTC)
      │  └─ Checkbox légende visible
      │
      └─ Liens statiques
         ├─ About
         ├─ Support
         ├─ Documents
         ├─ SoarGFS
         └─ SoarWRF
```

**Interactions clés :**

1. **Clic carte :**
   ```
   MapBrowserEvent
     ↓
   locationClicks signal updated
     ↓
   LocationDetails effect triggered
     ↓
   domain.showLocationForecast(lat, lng, 'summary')
     ↓
   Fetch locations/*.json si pas en cache
     ↓
   Affiche popup avec 3 boutons vue
     ↓
   Marker affiché sur carte
   ```

2. **Changement couche :**
   ```
   Clic radio button
     ↓
   domain.setPrimaryLayer(layer)
     ↓
   state.primaryLayer updated
     ↓
   Effect watches → setPrimaryLayerSource()
     ↓
   Map PNG overlay updated
     ↓
   primaryLayerReactiveComponents memo régénéré
     ↓
   MapKey + Help + Summarizer updated
   ```

3. **Navigation temporelle :**
   ```
   Clic bouton heure
     ↓
   domain.setHourOffset(offset)
     ↓
   4 effects déclenchés:
     - Primary layer URL
     - Wind layer URL
     - Meteogram redraw
     - Sounding redraw (si visible)
   ```

4. **Changement langue :**
   ```
   Select langue → setLang(lang)
     ↓
   setLanguageTag() [Paraglide]
     ↓
   Fetch messages/{lang}.js
     ↓
   m() signal updated
     ↓
   Tous composants utilisant m() re-render
   ```

---

#### 7. Modèle de données

**ForecastMetadata** - Métadonnées runs
```typescript
{
  runPath: string              // "2024-11-14T06"
  init: Date                   // Quand run initialisé
  firstTimeStep?: Date         // Première heure prévision
  latest: number              // Nombre heures forecast
  modelPath: 'gfs' | 'wrf'
  availableZones: Zone[]
}

// Méthodes:
fetchLocationForecasts(zone, lat, lng) → LocationForecasts
closestPoint(zone, lng, lat) → [x, y]
urlOfRasterAtHourOffset(...) → PNG URL
urlOfVectorTilesAtHourOffset(...) → MVT URL
```

**LocationForecasts** - Données localisation
```typescript
{
  elevation: number (m)
  dayForecasts: DayForecasts[]

  atHourOffset(offset) → DetailedForecast
  offsetAndDates() → [offset, Date][]
}

DetailedForecast {
  time: Date
  xcPotential: 0-100
  thermalVelocity: m/s
  cloudCover: 0-1
  meanSeaLevelPressure: hPa
  isothermZero?: m

  surface: {
    temperature: °C
    dewPoint: °C
    wind: {u, v} km/h
  }

  boundaryLayer: {
    depth: m AGL
    soaringLayerDepth: m
    wind: {u, v}
    cumulusClouds?: {bottom, top} m AGL
  }

  aboveGround: [{
    elevation: m
    temperature: °C
    dewPoint: °C
    cloudCover: 0-1
    u, v: km/h
  }]

  rain: {
    convective: mm
    total: mm
  }

  winds: {
    soaringLayerTop
    _300MAGL
    _2000MAMSL
    _3000MAMSL
    _4000MAMSL
  }
}
```

**Clustering locations :**
- Données groupées 4×4 points par fichier JSON
- Réduit nombre de requêtes réseau
- Format: `locations/{cluster}.json`

---

#### 8. Patterns réactifs SolidJS

**Primitives utilisées :**

1. **createStore** - État objet réactif (State)
2. **createSignal** - Bindings bi-directionnels (locationClicks, zoom)
3. **createMemo** - Valeurs dérivées cachées (reactive components)
4. **createEffect** - Side effects (sync carte, canvas)
5. **createResource** - Chargement async (forecast runs)
6. **on()** - Tracking dépendances explicite

**Lazy loading :**
```typescript
const HourSelectorAndMeteogram = lazy(() =>
  import('./PeriodSelector').then(m => ({ default: m.HourSelectorAndMeteogram }))
)
```

Modules chargés à la demande :
- Meteogram
- Sounding
- Help modal

**Fine-grained reactivity :**
```
Changer hourOffset:
  ├─ Update: state.hourOffset
  ├─ Triggers: effects watching hourOffset
  ├─ Unaffected: model, zone, layers
  └─ Result: Fast, minimal re-renders
```

---

#### 9. Optimisations performances

1. **Canvas rendering :**
   - Device pixel ratio scaling (mobile)
   - setTimeout(..., 0) évite NS_ERROR_FAILURE
   - Canvas séparés (clés + diagramme)

2. **Lazy modules :**
   - Diagrams chargés à la demande
   - Help modal lazy
   - Réduit bundle initial

3. **Layer caching :**
   - Layer components mémoïsés
   - Summarizers régénérés uniquement si dépendances changent

4. **Map layer reuse :**
   - Couches réutilisées, sources updated
   - Évite recréation couteuse

5. **Clustered data :**
   - 4×4 points par JSON
   - Moins de fichiers à charger

---

#### 10. Internationalisation (i18n)

**Paraglide.js :**
```typescript
// Fichiers générés: generated-i18n/messages/{lang}.js
// Import dynamique: import(`./.../${lang}.js`)
// Accès: m().functionName()
// Reactive: useI18n() → { m }

// 8 langues: de, en, es, fr, it, pl, pt, sk
// Sauvegarde localStorage
// Restauration au chargement
```

**Patterns :**
```typescript
// Dans composants:
const { m } = useI18n()
<span>{ m().labelWindSpeed() }</span>

// Pour layers (accessors):
name: usingMessages(m => m.layerWindSurface())
```

---

#### 11. Gestion erreurs

1. **Forecast data unavailable :**
   - `fetchLocationForecasts()` → `undefined`
   - UI: "No data for this location"

2. **Zone mismatch :**
   - Zone non dispo pour run WRF
   - Fallback `effectiveZone()` → zone 6km
   - Radio button désactivé

3. **Network failures :**
   - PNG tiles fail → carte vide
   - MVT tiles fail → pas de vent
   - Forecast fetch fail → alert()

4. **Coord outside zone :**
   - `closestPoint()` → `undefined`
   - Pas de popup

---

#### 12. Résumé flux application

**Séquence démarrage :**
```
1. index.ts → start(container)
2. Create map element
3. initializeMap() → OpenLayers map
4. Render Loader component
5. Fetch forecast.json (GFS + WRF)
6. Create Domain with state
7. Render App with 3 zones
8. Effects sync state → map/canvas
9. App ready, interactive
```

**Interaction utilisateur typique :**
```
User clicks hour button
  ↓
domain.setHourOffset(newOffset)
  ↓
Solid.js signals propagate
  ↓
Effects triggered in parallel:
  - Map primary layer: new PNG
  - Map wind layer: new MVT tiles
  - Meteogram: canvas redraw
  - Sounding: canvas redraw (if visible)
  - Period selector: highlight update
  ↓
All UI reactively updated
  ↓
LocalStorage + URL updated
```

---

## 📊 Technologies utilisées

### Backend
- **Scala** 2.13.12 avec SBT 1.9.7
- **GeoTrellis** 3.7.1 (traitement géospatial, raster, vector tiles)
- **H2** 2.2.224 (base de données sur disque)
- **Circe** 0.14.5 (JSON)
- **GRIB Java** 5.5.3 (fichiers météo)
- **Squants** 1.8.3 (unités physiques)

### Frontend
- **SolidJS** 1.8.12 (framework réactif)
- **TypeScript** 5.3.3
- **OpenLayers** 8.1.0 (cartographie)
- **Vite** 4.5.5 (build)
- **Inlang/Paraglide** (internationalisation)

---

## 📁 Fichiers clés analysés

### Backend
- `backend/common/src/main/scala/org/soaringmeteo/ConvectiveClouds.scala` - Calcul cumulus
- `backend/common/src/main/scala/org/soaringmeteo/out/Raster.scala` - Génération PNG
- `backend/common/src/main/scala/org/soaringmeteo/out/VectorTiles.scala` - Génération tuiles vent
- `backend/common/src/main/scala/org/soaringmeteo/out/ForecastMetadata.scala` - Métadonnées
- `backend/gfs/src/main/scala/org/soaringmeteo/gfs/Subgrid.scala` - Config GFS
- `backend/wrf/src/main/scala/org/soaringmeteo/wrf/NetCdf.scala` - Config WRF

### Frontend
- `frontend/src/layers/CumuliDepth.tsx` - Couche cumulus
- `frontend/src/layers/Wind.tsx` - Couches vent
- `frontend/src/map/Map.ts` - Configuration OpenLayers
- `frontend/src/App.tsx` - Application principale
- `frontend/src/data/Model.ts` - Types de données

### Documentation
- `docs/decisions/0001-on-disk-storage.md` - ADR sur H2
- `docs/decisions/0000-map-overlays.md` - ADR sur OpenLayers

---

## 🐛 Points d'attention

1. **Anomalie clouds-rain :** Vérifier si 1010/1020/1030/1050/1100 sont corrects ou s'ils devraient être 110/120/130/150/200

2. **Pas de valeurs numériques pour cumulus :** Contrairement au vent, les hauteurs de cumulus ne sont pas affichées numériquement sur la carte

3. **Threshold 15 :** La valeur empirique limite le nombre de flèches de vent par tuile - peut être ajustée selon les besoins

---

**Date d'analyse :** 2025-11-14
**Analysé par :** Claude (Anthropic)
