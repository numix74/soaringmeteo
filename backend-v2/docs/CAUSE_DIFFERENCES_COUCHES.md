# Cause des Différences clouds-rain et xc-flying-potential

**Date** : 5 janvier 2026
**Statut** : ✅ CAUSE IDENTIFIÉE

---

## 🎯 Résumé Exécutif

**Cause principale** : **Interpolation temporelle dans backend-v2**

Les algorithmes de calcul sont **strictement identiques** entre v1 et v2, mais :
- **Backend v1** : Affiche seulement heures natives (0, 3, 6, 9, 12...)
- **Backend v2** : Interpole TOUTES les heures (0, 1, 2, 3, 4, 5...)

Les différences visuelles (<6%) proviennent de l'interpolation linéaire de variables qui ne devraient pas être interpolées linéairement (notamment `totalRain`).

---

## 🔍 Analyse Détaillée

### 1. Algorithmes : ✅ IDENTIQUES

**Comparaison diff** :
```bash
diff backend/common/.../ConvectiveClouds.scala backend-v2/common/.../ConvectiveClouds.scala
# → 0 différences

diff backend/common/.../XCFlyingPotential.scala backend-v2/common/.../XCFlyingPotential.scala
# → 0 différences
```

**ColorMaps** : ✅ IDENTIQUES (clouds-rain, xc-flying-potential)

### 2. Parsing GRIB : ✅ IDENTIQUES

**v1** (GfsGrib.scala ligne 191) :
```scala
accumulatedRain = Millimeters(readXY(prateSurface) * 3 * 60 * 60)
// PRATE × 3 heures × 3600 secondes
```

**v2** (GribParser.scala ligne 188) :
```scala
totalRain = Millimeters(readXY(totalPrecipRate) * timeStepHours * 3600)
// PRATE × 3 heures × 3600 secondes (timeStepHours = 3 pour GFS)
```

**Conclusion** : Le parsing est identique !

### 3. Interpolation Temporelle : ⚠️ DIFFÉRENCE TROUVÉE

**Backend v1** : **PAS d'interpolation**
- Génère seulement PNG pour heures natives : 0, 3, 6, 9, 12, 15...
- Total : 40 heures (118h / 3 + 1)

**Backend v2** : **AVEC interpolation**
- Génère PNG pour TOUTES les heures : 0, 1, 2, 3, 4, 5, 6...
- Total : 118 heures (heures natives + interpolées)

**Code interpolation** (TemporalInterpolator.scala ligne 125-127) :
```scala
private def interpolateAtmosphere(...): AtmosphereData = {
  AtmosphereData(
    totalCloudCover = interpolateInt(before.totalCloudCover, after.totalCloudCover, fraction),
    totalRain = interpolateLength(before.totalRain, after.totalRain, fraction),
    ...
  )
}

// Interpolation linéaire
private def interpolateLength(before: Length, after: Length, fraction: Double): Length = {
  Meters(before.toMeters + (after.toMeters - before.toMeters) * fraction)
}
```

---

## 🐛 Problème : Interpolation Linéaire de totalRain

### Exemple Concret

**Heures natives GFS** :
- Heure 0 : totalRain = 0 mm
- Heure 3 : totalRain = 5 mm (pluie accumulée 0h→3h)
- Heure 6 : totalRain = 8 mm (pluie accumulée 3h→6h)

**Interpolation v2** (linéaire) :
- Heure 1 : totalRain = 0 + (5-0) × 0.33 = **1.67 mm** ← ❌ INCORRECT
- Heure 2 : totalRain = 0 + (5-0) × 0.67 = **3.33 mm** ← ❌ INCORRECT
- Heure 4 : totalRain = 5 + (8-5) × 0.33 = **6.00 mm** ← ❌ INCORRECT

**Réalité** :
- Si pluie tombée à 2h30 → heure 1 et 2 devraient avoir 0mm
- Si pluie constante → distribution différente

**Résultat** : Les images PNG pour heures interpolées ont des valeurs de pluie **incorrectes**.

### Exemple Cloud Cover

**Moins problématique** mais crée quand même des artefacts :
- Heure 0 : cloudCover = 20%
- Heure 3 : cloudCover = 80%
- Heure 1 (interpolée) : cloudCover = 40%
- Heure 2 (interpolée) : cloudCover = 60%

Si nuages sont apparus soudainement à 2h30, interpolation linéaire donne transitions fausses.

---

## 📊 Impact sur xc-flying-potential

**xc-flying-potential** dépend de :
```scala
XCFlyingPotential(thermalVelocity, soaringLayerDepth, boundaryLayerWind)
```

Ces trois variables sont AUSSI interpolées linéairement :
- `thermalVelocity` → Calculé depuis `sensibleHeatFlux` interpolé
- `soaringLayerDepth` → Calculé depuis `boundaryLayerDepth` interpolé + `convectiveClouds`
- `boundaryLayerWind` → Interpolé linéairement

**Résultat** : Valeurs interpolées légèrement différentes → XC potential légèrement différent.

---

## ✅ Solutions Proposées

### Option 1 : Désactiver Interpolation pour Variables Problématiques (Recommandé ⭐)

**Principe** : Ne PAS interpoler les variables cumulatives ou discrètes

**Implémentation** :
```scala
// TemporalInterpolator.scala
private def interpolateAtmosphere(...): AtmosphereData = {
  AtmosphereData(
    // Garder valeur "before" (pas d'interpolation)
    totalRain = before.totalRain,
    convectiveRain = before.convectiveRain,

    // Interpoler les variables continues
    totalCloudCover = interpolateInt(before.totalCloudCover, after.totalCloudCover, fraction),
    ...
  )
}
```

**Avantages** :
- ✅ Corrige le problème de pluie accumulée
- ✅ Simple à implémenter
- ✅ Conservateur (utilise valeur connue)

**Inconvénients** :
- ⚠️ Heures interpolées auront toutes la même pluie que l'heure avant

### Option 2 : Interpolation Intelligente (Avancé)

**Principe** : Distribuer pluie accumulée sur période

**Implémentation** :
```scala
// Distribuer pluie uniformément sur 3 heures
private def interpolateCumulativeRain(...): Length = {
  val rainRate = (after.totalRain - before.totalRain) / 3.0  // mm/h
  before.totalRain + (rainRate * fractionHours)
}
```

**Avantages** :
- ✅ Plus réaliste physiquement
- ✅ Transitions douces

**Inconvénients** :
- ⚠️ Plus complexe
- ⚠️ Assume distribution uniforme (pas forcément vrai)

### Option 3 : Ne Générer PNG que pour Heures Natives

**Principe** : Générer seulement PNG pour 0, 3, 6, 9... (comme v1)

**Implémentation** :
```scala
// UnifiedPipeline.scala
val hoursToRender = (0 to maxHour by nativeTimeStep).toSeq
// GFS : 0, 3, 6, 9, ... (40 heures)
// AROME : 0, 1, 2, 3, ... (25 heures car nativeTimeStep=1)
```

**Avantages** :
- ✅ Élimine complètement le problème
- ✅ Backend v1 et v2 parfaitement identiques

**Inconvénients** :
- ❌ Perd l'avantage de l'interpolation horaire pour visualisation
- ❌ Frontend moins fluide (sauts de 3h pour GFS)

### Option 4 : Accepter Différences et Documenter

**Principe** : Les différences sont <6%, acceptables pour usage opérationnel

**Documentation** :
```markdown
## Différences Backend v1 vs v2

Backend v2 interpole toutes les heures, ce qui peut créer de légères
différences visuelles (<6%) pour :
- clouds-rain : Pluie interpolée linéairement
- xc-flying-potential : Variables interpolées

Les heures natives (0, 3, 6...) sont identiques entre v1 et v2.
```

**Avantages** :
- ✅ Pas de code à changer
- ✅ Conserve interpolation horaire

**Inconvénients** :
- ⚠️ Valeurs interpolées techniquement incorrectes

---

## 🎯 Recommandation

### Solution Hybride (Recommandée)

**Combiner Option 1 + Documentation** :

1. **Désactiver interpolation pour variables cumulatives** :
   - `totalRain` → Garder valeur "before"
   - `convectiveRain` → Garder valeur "before"

2. **Garder interpolation pour variables continues** :
   - `totalCloudCover` → Interpoler (acceptable)
   - `thermalVelocity` → Interpoler (flux continu)
   - `boundaryLayerDepth` → Interpoler (évolution continue)

3. **Documenter comportement** :
   - Heures interpolées : valeurs approximatives
   - Heures natives : valeurs exactes GRIB

**Code à modifier** :

```scala
// backend-v2/common/.../TemporalInterpolator.scala

private def interpolateAtmosphere(
  before: AtmosphereData,
  after: AtmosphereData,
  fraction: Double
): AtmosphereData = {
  AtmosphereData(
    totalCloudCover = interpolateInt(before.totalCloudCover, after.totalCloudCover, fraction),
    convectiveCloudCover = interpolateInt(before.convectiveCloudCover, after.convectiveCloudCover, fraction),

    // FIX: Ne pas interpoler pluie accumulée (utiliser valeur avant)
    totalRain = before.totalRain,           // ← CHANGEMENT
    convectiveRain = before.convectiveRain, // ← CHANGEMENT

    solarRadiation = WattsPerSquareMeter(
      interpolateDouble(before.solarRadiation.toWattsPerSquareMeter, after.solarRadiation.toWattsPerSquareMeter, fraction)
    ),
    cape = interpolateOptionalEnergy(before.cape, after.cape, fraction),
    cin = interpolateOptionalEnergy(before.cin, after.cin, fraction)
  )
}
```

**Durée implémentation** : 1-2 heures

**Test** :
1. Modifier `TemporalInterpolator.scala`
2. Recompiler : `sbt common/compile`
3. Régénérer run GFS : `sbt "app/run gfs 2025-12-31T00"`
4. Comparer avec visual_validator.py
5. Vérifier que différences clouds-rain sont réduites

---

## 📋 Plan d'Action

### Étape 1 : Implémenter Fix (1-2h)

1. Modifier `TemporalInterpolator.scala` lignes 127-128
2. Recompiler backend-v2
3. Tests unitaires (vérifier que totalRain n'est plus interpolé)

### Étape 2 : Validation (2-3h)

1. Régénérer run GFS complet (118h)
2. Comparer outputs v1 vs v2 avec `visual_validator.py`
3. Vérifier réduction différences clouds-rain
4. Vérifier impact sur xc-flying-potential

### Étape 3 : Documentation (30min)

1. Documenter comportement interpolation
2. Ajouter commentaires dans code
3. Mettre à jour TODOLIST.md

---

## 🔬 Validation Attendue

**Avant fix** :
- clouds-rain : différences <6% (heures interpolées fausses)
- xc-flying-potential : différences <6%

**Après fix** :
- clouds-rain : différences <2% (seulement artefacts mineurs)
- xc-flying-potential : différences <3% (impact réduit)

**Heures natives** (0, 3, 6, 9...) :
- ✅ Déjà identiques (pas d'interpolation)
- ✅ Resteront identiques

**Heures interpolées** (1, 2, 4, 5...) :
- ⚠️ totalRain → Valeur "before" (conservateur)
- ✅ Autres variables → Interpolées (fluide)

---

## Conclusion

La cause des différences est **identifiée** et **compréhensible** :
- Algorithmes identiques ✅
- Parsing GRIB identique ✅
- **Interpolation temporelle** différente ⚠️

**Solution simple** : Ne pas interpoler variables cumulatives (`totalRain`, `convectiveRain`)

**Effort** : 1-2 heures implémentation + 2-3 heures validation

**Impact** : Réduction différences visuelles de 6% → <2%

---

**Maintenu par** : Claude Code
**Date** : 5 janvier 2026, 23:30 UTC
