# Analyse du dépôt SoaringMeteo

## 📋 Vue d'ensemble du projet

**SoaringMeteo** (https://soaringmeteo.org) est un site web de prévisions météorologiques spécialement conçu pour les pilotes de vol libre (parapente, deltaplane et planeur).

### Objectif
Fournir des données météorologiques adaptées au vol à voile à partir de deux sources :
- **GFS** (Global Forecast System) - Données globales de NOAA
- **WRF** (Weather Research and Forecasting) - Prévisions régionales haute résolution

---

## 🏗️ Architecture

### Backend (Scala 2.13.12)
Trois modules pour le traitement des données météo :
- **`common/`** - Utilitaires partagés (parsing GRIB, calculs météo, génération raster/vector tiles)
- **`gfs/`** - Pipeline de téléchargement et traitement des données GFS
- **`wrf/`** - Pipeline de traitement des données WRF

### Frontend (SolidJS + TypeScript)
Application web monopage avec :
- Carte interactive (OpenLayers)
- Système de couches météo (thermiques, vent, nuages)
- Diagrammes (météogrammes, émagrammes)
- Support de 8 langues
- Progressive Web App (PWA)

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
