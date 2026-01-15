# Phase 7 : Downscaling Intelligent (1 km)

## Principe

Le downscaling n'est appliqué que si nécessaire, selon la résolution native du modèle :

### Règle de décision

```
SI résolution_native > 1.1 km
  ALORS downscaling vers 1 km
SINON
  Utiliser données natives (pas de downscaling)
```

## Résolutions des modèles

| Modèle | Résolution native | Action |
|--------|-------------------|--------|
| **GFS** | ~27 km (0.25°) | ✅ Downscaling 27km → 1km |
| **AROME** | 2.5 km | ✅ Downscaling 2.5km → 1km |
| **WRF** | Variable (0.5-10 km) | Dépend de la config |
| **ICON** | 2.5-13 km | ✅ Downscaling si > 1.1km |
| **ECMWF HRES** | 9 km | ✅ Downscaling 9km → 1km |

## Exemples

### Cas 1 : WRF configuré à 1 km
```scala
val WRF_1km = ModelSpec(
  id = "wrf-high-res",
  nativeResolution = Kilometers(1.0),  // ≤ 1.1km
  // ...
)
// → Pas de downscaling, utilisation directe
```

### Cas 2 : WRF configuré à 3 km
```scala
val WRF_3km = ModelSpec(
  id = "wrf-standard",
  nativeResolution = Kilometers(3.0),  // > 1.1km
  // ...
)
// → Downscaling 3km → 1km
```

### Cas 3 : AROME 2.5 km
```scala
val AROME = ModelSpec(
  id = "arome",
  nativeResolution = Kilometers(2.5),  // > 1.1km
  // ...
)
// → Downscaling 2.5km → 1km
```

## Implémentation

### 1. Ajout champ `nativeResolution` dans ModelRegistry

**Fichier** : `ModelRegistry.scala`

```scala
case class ModelSpec(
  id: String,
  name: String,
  format: FileFormat,
  variables: VariableMapping,
  fileStructure: FileStructure,
  nativeTimeStep: Int,
  maxHourOffset: Int,
  pressureLevels: Seq[Int],
  nativeResolution: Length        // 🆕 NOUVEAU
)

val GFS = ModelSpec(
  // ...
  nativeResolution = Kilometers(27.0)  // ~0.25°
)

val AROME = ModelSpec(
  // ...
  nativeResolution = Kilometers(2.5)
)

val WRF = ModelSpec(
  // ...
  nativeResolution = Kilometers(2.0)  // Valeur par défaut, configurable
)
```

### 2. Logique conditionnelle dans UnifiedPipeline

**Fichier** : `UnifiedPipeline.scala`

```scala
def process(initTime: OffsetDateTime): Try[ProcessingSummary] = {
  Try {
    // ... (parsing + interpolation temporelle)

    for (hour <- 0 to modelSpec.maxHourOffset) {
      var grid = // ... (parse ou interpole)

      // 🆕 Downscaling conditionnel selon résolution native
      if (shouldDownscale(modelSpec)) {
        logger.info(s"Downscaling from ${modelSpec.nativeResolution} to 1km")
        val dem = DigitalElevationModel.load(demFile)
        val downscaler = createDownscaler(config.getString("downscaling.method"))

        grid = downscaler.downscale(
          grid,
          inputResolution = modelSpec.nativeResolution,
          targetResolution = Kilometers(1.0),
          dem
        )
      } else {
        logger.info(s"Native resolution ${modelSpec.nativeResolution} ≤ 1km, no downscaling needed")
      }

      generateOutputs(grid, hour)
    }
  }
}

/**
 * Détermine si le downscaling est nécessaire.
 *
 * Règle : downscaling si résolution native > 1.1 km
 */
private def shouldDownscale(modelSpec: ModelSpec): Boolean = {
  val threshold = Kilometers(1.1)
  modelSpec.nativeResolution > threshold
}
```

### 3. Configuration flexible

**Fichier** : `unified.conf`

```hocon
unified {
  # Downscaling (Phase 7)
  downscaling {
    enabled = true
    target-resolution = 1.0  # km
    threshold = 1.1          # km - Downscale seulement si résolution > 1.1km
    method = "terrain-aware" # "bilinear" | "terrain-aware" | "ml"
    dem-file = "/data/dem/srtm_pyrenees_1km.tif"
  }

  # Override résolution native par modèle (optionnel)
  model-overrides {
    wrf {
      native-resolution = 1.0  # km - Force WRF à 1km (si config haute-res)
    }
  }
}
```

## Avantages

### 1. Performance
- **Pas de calculs inutiles** : Si WRF est déjà à 1km, pas de downscaling
- **Gain de temps** : Downscaling coûteux évité quand non nécessaire

### 2. Qualité
- **Préserve données natives** : Si résolution suffisante, on garde les données originales
- **Évite dégradation** : Downscaling peut introduire artefacts si mal paramétré

### 3. Flexibilité
- **Support multi-résolutions** : Même modèle peut avoir plusieurs configs (WRF 1km, WRF 3km)
- **Evolutif** : Futurs modèles haute-résolution (AROME 1km ?) automatiquement supportés

## Tests Phase 7

### Test 1 : AROME 2.5km → 1km
```bash
# Résolution native = 2.5km > 1.1km → downscaling activé
sbt "app/run --model=arome --init-time=2025-12-28T00:00Z"

# Vérifier logs
grep "Downscaling from 2.5" logs/pipeline.log
```

### Test 2 : WRF 1km → pas de downscaling
```bash
# Configurer WRF à 1km dans unified.conf
# Résolution native = 1km ≤ 1.1km → downscaling désactivé
sbt "app/run --model=wrf --init-time=2025-12-28T00:00Z"

# Vérifier logs
grep "no downscaling needed" logs/pipeline.log
```

### Test 3 : GFS 27km → 1km (cas extrême)
```bash
# Résolution native = 27km > 1.1km → downscaling activé
# Facteur de downscaling = 27
sbt "app/run --model=gfs --init-time=2025-12-28T00:00Z"

# Validation qualité terrain
python scripts/validate_downscaling.py \
  --input output-unified/7/maps/0/ \
  --dem data/dem/srtm_pyrenees_1km.tif \
  --model gfs
```

## Métriques de validation

Pour chaque modèle downscalé, valider :

1. **Cohérence spatiale** : Pas de discontinuités
2. **Préservation patterns** : Structures thermiques cohérentes
3. **Ajustement terrain** : Thermiques plus forts côté sud (exposition)
4. **Performance** : Temps de traitement acceptable

## Chronologie Phase 7 mise à jour

| Tâche | Durée | Description |
|-------|-------|-------------|
| **7.1** | 1j | Ajout `nativeResolution` dans ModelRegistry |
| **7.2** | 1j | Logique conditionnelle `shouldDownscale()` |
| **7.3** | 1 sem | BilinearDownscaler + tests |
| **7.4** | 1 sem | TerrainAwareDownscaler + DEM |
| **7.5** | 3j | Validation multi-modèles (GFS, AROME, WRF) |
| **7.6** | 2j | Tests performance + optimisations |

**Total Phase 7** : **2-3 semaines**

---

**Date** : 28 décembre 2025
**Précision apportée par** : Utilisateur
**Impact** : Optimisation significative (évite downscaling inutile pour modèles haute-résolution)
