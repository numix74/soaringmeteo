# Analyse Différences Couches clouds-rain et xc-flying-potential

**Date** : 5 janvier 2026
**Objectif** : Identifier et corriger les différences visuelles (<6%) détectées en Phase 5

---

## Contexte

Phase 5 a validé 5 couches météo :
- ✅ **3/5 pixel-perfect** : thermal-velocity, soaring-layer-depth, wind-surface
- ⚠️ **2/5 petites différences acceptables (<6%)** : **clouds-rain**, **xc-flying-potential**

Serveur validation : http://51.38.221.186:5000/

---

## Résultats de l'Analyse

### ✅ Conclusion Principale

**LES ALGORITHMES SONT IDENTIQUES** entre backend v1 et v2 !

Les différences visuelles ne viennent PAS des calculs, mais probablement :
1. Des données d'entrée (parsing GRIB différent)
2. De conversions d'unités
3. De valeurs manquantes gérées différemment

---

## 1. Couche clouds-rain

### Code v1 (backend/common/.../Raster.scala ligne 146-153)

```scala
Raster(
  "clouds-rain",
  doubleData { meteoData =>
    val rain = meteoData.totalRain          // ← Type: Double (millimètres)
    if (rain >= 0.2) {
      rain + 100
    } else {
      meteoData.totalCloudCover.toDouble    // ← Type: Int converti en Double
    }
  },
  ColorMap(...),
  RgbaPngEncoding
)
```

### Code v2 (backend-v2/common/.../Raster.scala ligne 140-146)

```scala
Raster(
  "clouds-rain",
  doubleData(d => {
    val rain = d.totalRain.toMillimeters    // ← Type: Length converti en millimètres
    if (rain >= 0.2) {
      rain + 100
    } else {
      d.totalCloudCover.toDouble            // ← Type: Int converti en Double
    }
  }),
  ColorMap(...),
  RgbaEncoding
)
```

### ColorMaps

**IDENTIQUES** dans v1 et v2 :
```scala
ColorMap(
  5.0    -> 0xffffff00,  // Blanc transparent (nuages bas)
  20.0   -> 0xffffffff,  // Blanc opaque
  40.0   -> 0xbdbdbdff,  // Gris clair
  60.0   -> 0x888888ff,  // Gris moyen
  80.0   -> 0x4d4d4dff,  // Gris foncé
  100.2  -> 0x111111ff,  // Presque noir (nuages épais)
  101.0  -> 0x9df8f6ff,  // Cyan clair (pluie légère)
  102.0  -> 0x0000ffff,  // Bleu (pluie modérée)
  104.0  -> 0x2a933bff,  // Vert foncé
  106.0  -> 0x49ff36ff,  // Vert clair
  110.0  -> 0xfcff2dff,  // Jaune
  120.0  -> 0xfaca1eff,  // Orange
  130.0  -> 0xf87c00ff,  // Orange foncé
  150.0  -> 0xf70c00ff,  // Rouge
  200.0  -> 0xac00dbff   // Violet (pluie intense)
).withFallbackColor(0xac00dbff)
```

### Différence Potentielle

**v1** : `meteoData.totalRain` est un `Double` direct (millimètres)
**v2** : `d.totalRain` est une `Length` (squants) convertie en millimètres

**Question** : Est-ce que la conversion `Length.toMillimeters` est exacte ?

---

## 2. Couche xc-flying-potential

### Code v1 (backend/common/.../Raster.scala ligne 60-75)

```scala
Raster(
  "xc-potential",
  intData(_.xcFlyingPotential),  // ← Appel XCFlyingPotential.apply()
  ColorMap(...),
  RgbPngEncoding
)
```

### Code v2 (backend-v2/common/.../Raster.scala)

```scala
Raster(
  "xc-flying-potential",
  intData(_.xcFlyingPotential),  // ← Appel XCFlyingPotential.apply()
  ColorMap(...),
  RgbEncoding
)
```

### Fichiers XCFlyingPotential.scala

**STRICTEMENT IDENTIQUES** entre v1 et v2 (comparaison diff = 0 différences)

```scala
object XCFlyingPotential {
  def apply(thermalVelocity: Velocity, soaringLayerDepth: Length, wind: Wind): Int = {
    val thermalVelocityCoeff = logistic(thermalVelocity.toMetersPerSecond, 1.55, 5)
    val soaringLayerDepthCoeff = logistic(soaringLayerDepth.toMeters, 400, 4)
    val thermalCoeff = (2 * thermalVelocityCoeff + soaringLayerDepthCoeff) / 3
    val windCoeff = 1 - logistic(wind.speed.toKilometersPerHour, 16, 6)
    math.round(thermalCoeff * windCoeff * 100).intValue
  }

  private def logistic(x: Double, mu: Double, k: Int): Double = {
    val L = 1
    val s = mu / k
    L / (1 + math.exp(-(x - mu) / s))
  }
}
```

### ColorMaps

**IDENTIQUES** dans v1 et v2 :
```scala
ColorMap(
  10  -> 0x333333,  // Gris très foncé (mauvais potentiel)
  20  -> 0x990099,  // Violet
  30  -> 0xff0000,  // Rouge
  40  -> 0xff9900,  // Orange
  50  -> 0xffcc00,  // Jaune-orange
  60  -> 0xffff00,  // Jaune
  70  -> 0x66ff00,  // Vert clair
  80  -> 0x00ffff,  // Cyan
  90  -> 0x99ffff,  // Cyan clair
  100 -> 0xffffff   // Blanc (excellent potentiel)
)
```

### Différence Potentielle

L'algorithme est identique. Les différences doivent venir des **données d'entrée** :
- `thermalVelocity` : Squants Velocity
- `soaringLayerDepth` : Squants Length
- `wind` : Wind (objet avec u, v)

**Question** : Est-ce que les valeurs parsées depuis GRIB sont exactement les mêmes ?

---

## 3. Fichier ConvectiveClouds.scala

**STRICTEMENT IDENTIQUE** entre v1 et v2 (comparaison diff = 0 différences)

Formule de Hennig pour base des cumuli :
```scala
val convectiveCloudsBottom = Meters(122.6 * (T_surface - TD_surface).toCelsiusScale) + groundLevel
```

Pas de différence ici non plus.

---

## Hypothèses sur les Causes

### Hypothèse 1 : Parsing GRIB différent (Probable ⭐)

**v1** : Parse directement vers `Forecast` avec valeurs spécifiques GFS
**v2** : Parse via `GribParser` générique → `RawData` → `UnifiedModelData`

**Variables critiques à vérifier** :
- `totalRain` (précipitations totales)
- `totalCloudCover` (couverture nuageuse)
- `thermalVelocity` (vitesse thermique)
- `soaringLayerDepth` (profondeur de vol)
- `boundaryLayerWind` (vent couche limite)

**Test** : Comparer valeurs parsées GRIB pour un point (x,y) spécifique entre v1 et v2

### Hypothèse 2 : Conversions Squants (Possible)

**v1** : Types primitifs (Double, Int)
**v2** : Squants (Length, Velocity, Temperature, etc.)

**Exemple** :
```scala
// v1
val rain: Double = 1.5  // millimètres

// v2
val rain: Length = Millimeters(1.5)
rain.toMillimeters  // 1.5 (conversion)
```

**Question** : Y a-t-il des erreurs d'arrondi dans les conversions ?

### Hypothèse 3 : Valeurs manquantes (Possible)

**v1** : Gère valeurs manquantes avec valeur par défaut
**v2** : Utilise `Option` et conversions `getOrElse`

**Exemple** :
```scala
// v1
val cape: Double = capeOption.getOrElse(0.0)

// v2
val cape: Option[SpecificEnergy] = parseGRIB(...)
cape.fold(0.0)(_.toGrays)  // Conversion via squants
```

**Test** : Vérifier comportement si CAPE, CIN, ou autres variables manquent

### Hypothèse 4 : Interpolation temporelle (Peu probable)

Backend-v2 fait interpolation temporelle (heures manquantes).

**Question** : Est-ce que les heures interpolées diffèrent des heures natives ?

**Test** : Comparer seulement heures natives (0, 3, 6, 9...) pas interpolées (1, 2, 4, 5...)

---

## Plan de Diagnostic

### Étape 1 : Extraire valeurs brutes GRIB

**Objectif** : Comparer parsing GRIB v1 vs v2 pour un point spécifique

**Point de test** : (lat=43.0, lon=-1.0) - Pays Basque centre
**Run** : 2025-12-31T00
**Heures** : 0, 6, 12, 18, 24 (heures natives, pas interpolées)

**Variables à comparer** :
```
- totalRain (APCP en GRIB)
- totalCloudCover (TCDC en GRIB)
- thermalVelocity (calculé depuis SHTFL + HPBL)
- soaringLayerDepth (calculé)
- boundaryLayerWind (calculé depuis U/V à niveau PBL)
```

**Script diagnostic** :
```scala
// Dans backend v1
val forecast = Store.forecastForLocation(initTime, subgrid, x, y, hourOffsets)
println(s"v1 totalRain = ${forecast.totalRain}")
println(s"v1 totalCloudCover = ${forecast.totalCloudCover}")

// Dans backend-v2
val unified = // ... charger UnifiedModelData
println(s"v2 totalRain = ${unified.totalRain.toMillimeters}")
println(s"v2 totalCloudCover = ${unified.totalCloudCover}")
```

### Étape 2 : Comparer valeurs calculées

**Objectif** : Vérifier que XCFlyingPotential produit même résultat avec mêmes inputs

**Test unitaire** :
```scala
val thermal = MetersPerSecond(2.0)
val depth = Meters(1500)
val wind = Wind(MetersPerSecond(3), MetersPerSecond(4))  // ~18 km/h

val v1Result = XCFlyingPotential(thermal, depth, wind)
val v2Result = XCFlyingPotential(thermal, depth, wind)

assert(v1Result == v2Result)  // Doit être identique
```

### Étape 3 : Analyser images pixel par pixel

**Objectif** : Identifier quels pixels diffèrent exactement

**Script Python** :
```python
import numpy as np
from PIL import Image

v1 = np.array(Image.open('v1/clouds-rain/12.png'))
v2 = np.array(Image.open('v2/clouds-rain/12.png'))

diff = np.abs(v1.astype(int) - v2.astype(int))
diff_pixels = np.where(diff > 0)

print(f"Pixels différents : {len(diff_pixels[0])}")
print(f"Différence max : {diff.max()}")
print(f"Différence moyenne : {diff[diff > 0].mean()}")

# Afficher positions des différences
for y, x in zip(diff_pixels[0][:10], diff_pixels[1][:10]):
    print(f"Pixel ({x},{y}): v1={v1[y,x]} v2={v2[y,x]} diff={diff[y,x]}")
```

### Étape 4 : Vérifier interpolation temporelle

**Objectif** : Confirmer que heures natives (GRIB direct) sont identiques

**Test** :
- Comparer seulement heures 0, 3, 6, 9, 12... (pas 1, 2, 4, 5...)
- Si heures natives identiques → problème interpolation
- Si heures natives différentes → problème parsing GRIB

---

## Prochaines Actions

### Action 1 : Diagnostic parsing GRIB (Priorité HAUTE)

**Fichiers à comparer** :
- `backend/gfs/src/main/scala/org/soaringmeteo/gfs/in/GfsGrib.scala`
- `backend-v2/common/src/main/scala/org/soaringmeteo/parsing/GribParser.scala`

**Focus** :
- Extraction `APCP` (precipitation) → `totalRain`
- Extraction `TCDC` (cloud cover) → `totalCloudCover`
- Calcul `thermalVelocity` depuis `SHTFL` + `HPBL`

### Action 2 : Test unitaire XCFlyingPotential

Créer test avec valeurs connues pour confirmer que l'algorithme est identique.

### Action 3 : Analyse pixel par pixel

Script Python pour identifier patterns de différences.

### Action 4 : Vérifier valeurs manquantes

Est-ce que v2 gère Option[...] différemment de v1 ?

---

## Conclusions Préliminaires

1. ✅ **Algorithmes identiques** : ConvectiveClouds, XCFlyingPotential
2. ✅ **ColorMaps identiques** : clouds-rain, xc-flying-potential
3. ⚠️ **Différence probable** : Parsing GRIB ou conversions Squants
4. 📊 **Différences faibles** : <6% selon Phase 5

**Les différences sont suffisamment petites pour être acceptables**, mais il vaut mieux comprendre et documenter la cause exacte.

---

## Décision

**Option A** : Accepter les différences (<6%) et documenter
**Option B** : Investiguer parsing GRIB pour identifier cause exacte
**Option C** : Comparer valeurs GRIB point par point avec script diagnostic

**Recommandation** : **Option B + C** - Investiguer pour comprendre, puis décider si acceptable.

---

**Maintenu par** : Claude Code
**Date** : 5 janvier 2026, 23:00 UTC
