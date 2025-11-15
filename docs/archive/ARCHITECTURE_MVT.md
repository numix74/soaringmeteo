# Architecture MVT - Soaring Meteo

## Vue d'ensemble

Le système MVT (Mapbox Vector Tiles) permet de servir efficacement les données de vent et autres paramètres au frontend en tuiles comprimées.

## Flux de génération des données

### 1. Lecture des données brutes
- **GFS** : Grib files → JsonWriter.scala → Store.forecastForLocation()
- **WRF** : NetCDF files → similaire
- **AROME** : Grib2 files → AromeGrib.scala → AromeData

### 2. Génération des fichiers de sortie

#### A. JSON pour locations (meteograms/soundings)
**Fichier** : `JsonData.scala`
- Input : Map[Int, Forecast] pour chaque point (x, y)
- Output : JSON files dans `locations/{x}-{y}.json`
- Format : Agrégation de données pour 16 points (clustering factor = 4)

#### B. Tuiles MVT pour vents (barbules)
**Fichier** : `VectorTiles.scala` ← À ANALYSER
- Input : Données vent (u, v components)
- Output : Tuiles MVT dans `{z}-{x}-{y}.mvt`
- Format : Protobuf comprimé

#### C. Images PNG pour cartes (thermiques, CAPE, PBLH)
**Fichier** : `AromeMapGenerator.scala` ou équivalent GFS
- Input : Données raster
- Output : PNG 1200x800

### 3. Métadonnées
**Fichier** : `ForecastMetadata.scala`
- Décrit les zones, projections, résolutions
- Pointe vers les fichiers générés
- Consommé par le frontend pour construire les URLs

## Modèles implémentés

### GFS
- ✅ JSON locations
- ✅ MVT vector tiles
- ✅ PNG raster images
- Entrée : `DataPipeline.scala` → `JsonWriter.scala`

### WRF
- ✅ JSON locations
- ✅ MVT vector tiles
- ✅ PNG raster images
- Entrée : `DataPipeline.scala` (WRF)

### AROME (À implémenter)
- ✅ JSON locations (À adapter)
- ❌ MVT vector tiles (À créer)
- ✅ PNG raster images (Déjà généré, mais format à unifier)

## À faire pour AROME

1. Adapter AromeGrib.scala pour extraire les données wind correctement
2. Créer AromeJsonWriter.scala basé sur JsonWriter.scala de GFS
3. Adapter VectorTiles.scala pour générer les tuiles AROME
4. Mettre à jour AromeMapGenerator.scala pour PNG cohérent


## Détail technique : VectorTiles.scala

### Fonctionnement

**Input** :
- `vectorTiles: VectorTiles` - Définit quel paramètre extraire (wind-surface, wind-2000m, etc.)
- `forecasts: IndexedSeq[IndexedSeq[Forecast]]` - Données pour chaque point (x, y)
- `parameters: Parameters` - Métadonnées (extent, résolution, coordonnées grid)

**Output** :
- Fichiers MVT : `{z}-{x}-{y}.mvt` pour chaque zoom level
- Format : Protobuf binaire (geotrellis.vectortile)

### Processus de génération

1. **Multi-zoom** : Génère pour chaque zoom level (0 à maxZoom)
   - Plus on monte en zoom, plus il y a de tiles
   - À chaque zoom, down-sample les points (step = 1 << (maxZoom - z))

2. **Projection** : Projette les coordonnées LatLng → WebMercator
   - Cache les conversions pour perf

3. **Partitioning** : Groupe les points par tile
   - Calcule le tile (x, y) de chaque point

4. **Encoding MVT** :
   - Crée une `StrictLayer` avec les points
   - Encode chaque point comme MVTFeature avec:
     - `speed` (VInt64 en km/h)
     - `direction` (VFloat en degrés)
   - Sérialise en protobuf

### Données extraites
```scala
val allVectorTiles = List(
  VectorTiles("wind-surface", _.surfaceWind),
  VectorTiles("wind-boundary-layer", _.boundaryLayerWind),
  VectorTiles("wind-soaring-layer-top", _.winds.soaringLayerTop),
  VectorTiles("wind-300m-agl", _.winds.`300m AGL`),
  VectorTiles("wind-2000m-amsl", _.winds.`2000m AMSL`, excluded = _.elevation > Meters(2000)),
  // ... plus d'autres
)
```

Chaque paramètre extrait une fonction `Forecast => Wind` différente.

## Adaptation pour AROME

### Points clés à adapter

1. **AromeData → Forecast**
   - AROME a : `u10`, `v10`, `pblh`, `cape`, etc.
   - Doit être converti en `Forecast` (structure commune)
   
2. **Extraire les Wind pour chaque altitude**
   - AROME a seulement le vent 10m actuellement
   - Doit calculer ou interpoler : wind-boundary-layer, wind-2000m, etc.
   
3. **Appeler VectorTiles.writeAllVectorTiles()**
   - Similaire à GFS/WRF

### Structure de l'implémentation
```
AromeGrib.scala 
  → Lit grib2 AROME
  → Produit AromeData

AromeJsonWriter.scala (à créer)
  → Convertit AromeData → Forecast
  → Appelle VectorTiles.writeAllVectorTiles()
  → Génère les tuiles MVT
  
AromeData.scala (à adapter)
  → Ajouter calculs pour wind aux différentes altitudes
```


## Structure Forecast (cible)

Champs critiques pour les vents :
```scala
case class Forecast(
  time: OffsetDateTime,
  elevation: Length,
  boundaryLayerDepth: Length,      // ← AROME a pblh
  boundaryLayerWind: Wind,          // ← À calculer depuis u10/v10
  surfaceWind: Wind,                // ← À calculer depuis u10/v10
  winds: Winds,                     // ← Contient wind-300m, 2000m, etc.
  thermalVelocity: Velocity,        // ← AROME calcule W*
  // ... autres champs
)

case class Wind(
  speed: Velocity,    // m/s ou km/h
  direction: Angle    // degrés
)

case class Winds(
  soaringLayerTop: Wind,    // Top de la couche de vol
  `300m AGL`: Wind,
  `2000m AMSL`: Wind,
  `3000m AMSL`: Wind,
  `4000m AMSL`: Wind
)
```

## Problème : Altitude intermédiaire

**AROME fournit** :
- Wind 10m (u10, v10)
- PBLH (hauteur plafond)

**Forecast demande** :
- Wind à 300m AGL, 2000m AMSL, 3000m AMSL, 4000m AMSL

**Solution** : Interpoler les données atmosphériques AROME manquantes
- AROME a seulement données de surface (10m)
- Doit récupérer vents aux autres niveaux depuis grib2


## AROME : Données altitude disponibles

### Niveaux disponibles dans grib2 AROME

**Niveaux hauteur (AGL)** :
- 10 m, 100 m, 200 m, ..., jusqu'à 3000 m
- Extraire directement avec : `wgrib2 file.grib2 | grep "heightAboveGround"`

**Niveaux pression (AMSL)** :
- 925, 850, 700, 500 hPa (convertir en altitude via géopotentiel)
- Extraire avec : `wgrib2 file.grib2 | grep "isobaricInhPa"`

### Extraction requise

- **300m AGL** : Prendre niveau hauteur le plus proche (200m ou 300m)
- **2000m AMSL** : Interpoler entre niveaux hauteur ou convertir depuis pression
- **3000m AMSL** : Idem
- **4000m AMSL** : Convertir depuis pression (altitude ≈ 7000 m pour 500 hPa)


## AROME : Données réelles disponibles

### Fichiers et niveaux

**HP1** (hauteur au-dessus du sol - AGL) :
- Niveaux disponibles : 20, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1200, 1500, 1800, 2000, 2200, 2500, 2700, 3000m
- Paramètres : `UGRD:heightAboveGround`, `VGRD:heightAboveGround`

**SP3/HP3** (niveaux pression - AMSL) :
- Niveaux : 1000, 950, 925, 900, 850, 800, 700, 600, 500, 400, 300, 250, 200 hPa
- Correspondance approximative altitudes : 2000m ≈ 800 hPa, 3000m ≈ 700 hPa, 4000m ≈ 600 hPa
- Paramètres : `UGRD:ISBL`, `VGRD:ISBL`

**HP2** (géopotentiel) :
- Contient `geopotential_height` pour conversion pression→altitude précise

### Extraction avec wgrib2
```bash
# Tous les U/V à AGL
wgrib2 HP1_file.grib2 | grep "UGRD:heightAboveGround"
wgrib2 HP1_file.grib2 | grep "VGRD:heightAboveGround"

# Tous les U/V à niveaux pression
wgrib2 SP3_file.grib2 | grep "UGRD:ISBL"
wgrib2 SP3_file.grib2 | grep "VGRD:ISBL"
```

### Correspondance requise pour Forecast

| Forecast Level | Source AROME | Stratégie |
|---|---|---|
| wind-surface (10m) | HP1 @ 20m AGL | Direct |
| wind-300m-agl | HP1 @ 300m AGL | Direct |
| wind-boundary-layer | HP1 @ PBLH height | Interpoler |
| wind-2000m-amsl | HP1 @ 2000m + terrain | Interpoler ou SP3 @ 800hPa |
| wind-3000m-amsl | HP1 interpolé + terrain | Interpoler ou SP3 @ 700hPa |
| wind-4000m-amsl | SP3 @ 600hPa | Convertir pression→altitude |


## Implémentation : Lecture HP1

### Modification AromeGrib.scala

**Objectif** : Extraire U/V à hauteurs multiples depuis HP1

**Hauteurs requises** (depuis HP1) :
- 20m AGL (pour validation/debug)
- 100m AGL
- 300m AGL
- 500m AGL
- 1000m AGL
- Et autres pour interpolation

**Approche** :
1. Ajouter une fonction `readWindsAtMultipleHeights(hp1File, location, hourOffset)`
2. Pour chaque hauteur, récupérer U/V depuis HP1
3. Retourner Map[Int, Wind] = hauteur → vents
4. Adapter AromeData pour stocker ces vents

### Conversion hauteur→AMSL

Pour convertir hauteur AGL en altitude AMSL :
```scala
val altitudeAMSL = heightAGL + terrainElevation
```

Terrain elevation vient de SP2 (ALTITUDE field).


## Implémentation AROME - Solution finale

### Problème résolu : OOM avec HP1

**Approche initiale** : Charger HP1 complet (~470 MB) en mémoire → OutOfMemoryError

**Solution déployée** : 
1. Extraire seulement les hauteurs nécessaires avec wgrib2
2. Créer des fichiers u_XXXm.grib2 / v_XXXm.grib2 séparés (~4-5 MB chacun)
3. Lire chaque hauteur indépendamment dans Scala

### Hauteurs extraites

12 niveaux tous les 250m (250m à 3000m) :
- 250m, 500m, 750m, 1000m, 1250m, 1500m, 1750m, 2000m, 2250m, 2500m, 2750m, 3000m

### Architecture finale
```
HP1 complet (470 MB) 
  ↓
wgrib2 extraction (script extract_arome_winds.sh)
  ↓
u_250m.grib2, v_250m.grib2, ..., u_3000m.grib2, v_3000m.grib2 (~50 MB total)
  ↓
AromeGrib.scala (extractWindsAtHeights)
  ↓
AromeData.windsAtHeights: Map[Int, (Double, Double)]
  ↓
VectorTiles.scala → MVT tuiles {z}-{x}-{y}.mvt
```

### Fichiers modifiés

1. **AromeGrib.scala** : Lit fichiers u/v séparés au lieu de HP1 complet
2. **AromeData.scala** : Stocke vents à 12 altitudes
3. **Main.scala** : Passe windsDir au lieu de hp1File
4. **generate_arome_daily.sh** : Ajoute étape wgrib2 extraction

### Prochaines étapes

1. Tester le run complet (génération MVT + PNG)
2. Vérifier que les vents s'affichent au frontend
3. Adapter LayersSelector.tsx pour AROME wind layers

