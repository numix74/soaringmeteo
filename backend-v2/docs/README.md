# Backend v2 - Architecture Unifiée

## Vue d'ensemble

Ce dossier contient la **nouvelle architecture unifiée** pour SoaringMeteo qui remplacera progressivement `backend/`.

### Principe Fondamental

⚠️ **RÈGLE #1** : Le code de production dans `backend/` **ne doit jamais être modifié** pendant le développement de v2.

### Objectifs

1. **Vraiment unifié** : Un seul parser par format (GRIB/NetCDF), pas 3 versions différentes
2. **Autonome** : Fonctionne indépendamment de `backend/`
3. **Extensible** : Ajouter ICON, ECMWF, etc. = quelques heures (vs plusieurs semaines)
4. **Interpolation temporelle** : Prévisions toutes les heures pour tous les modèles
5. **Prêt pour downscaling** : Résolution 1km (Phase 7)

## Architecture

### Structure des fichiers

```
backend-v2/
├── common/                      # Module commun unifié
│   └── src/main/scala/org/soaringmeteo/
│       ├── UnifiedModelData.scala       # Structure de données unifiée
│       │
│       ├── parsing/                     # Système de parsing générique
│       │   ├── RawData.scala           # Données brutes intermédiaires
│       │   ├── ModelRegistry.scala     # Registre des modèles (GFS, AROME, WRF)
│       │   ├── GribParser.scala        # Parser GRIB générique
│       │   ├── TemporalInterpolator.scala  # Interpolation temporelle
│       │   ├── UnifiedPipeline.scala   # Pipeline de traitement
│       │   │
│       │   └── handlers/               # Gestion fichiers modèle-spécifiques
│       │       ├── FileHandler.scala   # Interface
│       │       ├── GfsFileHandler.scala     # GFS (1 fichier/heure)
│       │       └── AromeFileHandler.scala   # AROME (fichiers groupés)
│       │
│       ├── grib/                        # Utilitaires GRIB (copié de backend/)
│       ├── Wind.scala, Winds.scala, ... # Types de base (copiés)
│       └── Thermals.scala, XCFlyingPotential.scala, ...
│
├── app/                         # Application CLI (TODO Phase 4)
│   └── src/main/scala/org/soaringmeteo/
│       └── Main.scala
│
└── build.sbt                    # Configuration SBT
```

### Fichiers copiés depuis backend/common

Les fichiers suivants sont **copiés** (pas en liens symboliques) pour rendre backend-v2/ autonome :

- `Wind.scala`, `Winds.scala`, `ConvectiveClouds.scala`
- `Point.scala`, `Extent.scala`, `Interpolation.scala`
- `Thermals.scala`, `XCFlyingPotential.scala`
- `Forecast.scala` (pour AirData)
- `grib/` (Grib.scala + helpers)
- `Temperatures.scala`

**Raison** : Quand `backend/` sera supprimé, ces copies deviendront les versions canoniques.

## Concepts clés

### 1. UnifiedModelData

Structure de données unique pour **tous** les modèles météo :

```scala
case class UnifiedModelData(
  time: OffsetDateTime,
  elevation: Length,
  thermalVelocity: Velocity,
  soaringLayerDepth: Length,
  surfaceWind: Wind,
  // ... tous les champs météo
  cape: Option[SpecificEnergy],  // Optionnel (WRF n'a pas CAPE)
  xcFlyingPotential: Int
)
```

### 2. ModelRegistry

Registre central de tous les modèles avec leurs spécifications :

```scala
val GFS = ModelSpec(
  id = "gfs",
  name = "Global Forecast System (NOAA)",
  format = FileFormat.GRIB,
  variables = VariableMapping(
    temperature2m = "Temperature_height_above_ground",
    dewPoint2m = "",  // Calculé depuis T + RH
    pblHeight = "Planetary_Boundary_Layer_Height_surface",
    // ... tous les noms de variables GFS
  ),
  nativeTimeStep = 3,      // GFS toutes les 3h
  maxHourOffset = 120,     // 5 jours
  pressureLevels = Seq(1000, 975, 950, ..., 200)
)

val AROME = ModelSpec(
  id = "arome",
  name = "AROME France (Météo-France)",
  format = FileFormat.GRIB2,
  variables = VariableMapping(
    temperature2m = "2 metre temperature",
    dewPoint2m = "2 metre dewpoint temperature",
    pblHeight = "Planetary boundary layer height",
    // ... noms de variables AROME
  ),
  nativeTimeStep = 1,      // AROME toutes les heures
  maxHourOffset = 42,
  pressureLevels = Seq.empty  // AROME utilise des niveaux d'altitude
)
```

### 3. FileHandler

Gère les différences de structure de fichiers entre modèles :

- **GFS** : 1 fichier par heure (`gfs.t00z.pgrb2.0p25.f006`)
- **AROME** : Fichiers groupés par plages horaires (`SP1_00H06H.grib2`, `SP2_07H12H.grib2`, ...)
- **WRF** : Fichiers horodatés (`wrfout_d01_2023-10-29_18:00:00`)

```scala
trait FileHandler {
  def prepareData(hourOffset: Int, initTime: OffsetDateTime): ParseableData
  def availableHourOffsets(initTime: OffsetDateTime): Seq[Int]
}
```

### 4. GribParser (générique)

Un **seul** parser qui fonctionne avec **tous** les modèles :

```scala
object GribParser {
  def parseGrid(
    parseableData: ParseableData,  // Du FileHandler
    extent: Extent,
    gridResolutionKm: Double
  ): IndexedSeq[IndexedSeq[RawData]] = {
    val modelSpec = parseableData.modelSpec
    val vars = modelSpec.variables

    // Utilise les noms de variables du ModelSpec
    val temperature2m = Feature(vars.temperature2m)
    val pblHeight = Feature(vars.pblHeight)
    // ...
  }
}
```

### 5. TemporalInterpolator

Génère les heures intermédiaires par interpolation linéaire :

- **GFS** : 0h, 3h, 6h → interpolé → 0h, 1h, 2h, 3h, 4h, 5h, 6h, ...
- **AROME** : Déjà toutes les heures → pas d'interpolation
- **WRF** : Dépend de la configuration

```scala
object TemporalInterpolator {
  def interpolateGrids(
    gridBefore: IndexedSeq[IndexedSeq[RawData]],
    gridAfter: IndexedSeq[IndexedSeq[RawData]],
    fraction: Double  // 0.0 = avant, 1.0 = après
  ): IndexedSeq[IndexedSeq[RawData]]
}
```

### 6. UnifiedPipeline

Orchestre tout le traitement :

```scala
val pipeline = UnifiedPipeline(
  modelSpec = ModelRegistry.GFS,
  fileHandler = GfsFileHandler("/path/to/grib"),
  extent = Extent(-4.0, 41.25, 3.75, 44.25),  // Pyrénées
  gridResolutionKm = 2.5,
  outputDirectory = os.Path("/output-unified")
)

pipeline.process(initTime = OffsetDateTime.parse("2025-12-28T00:00Z"))
```

Le pipeline :
1. Lit les fichiers GRIB via FileHandler
2. Parse avec GribParser
3. Interpole temporellement (si nécessaire)
4. Convertit RawData → UnifiedModelData
5. Génère les outputs (PNG, MVT, JSON)

## Workflow de traitement

```
Input GRIB Files
    ↓
FileHandler.prepareData()
    ↓
GribParser.parseGrid()
    ↓
RawData (grille)
    ↓
[Si heure interpolée]
TemporalInterpolator.interpolateGrids()
    ↓
UnifiedModelData.fromRawData()
    ↓
UnifiedModelData (grille)
    ↓
Outputs (PNG/MVT/JSON)
```

## Compilation

```bash
cd backend-v2
sbt compile
sbt test
```

## Ajout d'un nouveau modèle

Pour ajouter un nouveau modèle (exemple : ICON de DWD) :

### 1. Ajouter la spécification dans ModelRegistry.scala

```scala
val ICON = ModelSpec(
  id = "icon",
  name = "ICON (DWD)",
  format = FileFormat.GRIB2,
  variables = VariableMapping(
    temperature2m = "T_2M",
    dewPoint2m = "TD_2M",
    pblHeight = "H_PBL",
    // ... mapper tous les noms de variables ICON
  ),
  nativeTimeStep = 1,
  maxHourOffset = 78,
  pressureLevels = Seq(1000, 950, 925, 850, 700, 500, 300, 200)
)
```

### 2. Créer IconFileHandler.scala

```scala
class IconFileHandler(gribDirectory: os.Path) extends FileHandler {
  override def prepareData(hourOffset: Int, initTime: OffsetDateTime): ParseableData = {
    // Logique spécifique à la structure des fichiers ICON
    val fileName = s"icon_global_icosahedral_${formatTime(initTime)}_${hourOffset}_T_2M.grib2"
    val filePath = gribDirectory / fileName

    ParseableData(
      primaryFile = filePath,
      secondaryFile = None,
      initTime = initTime,
      hourOffset = hourOffset,
      validTime = initTime.plusHours(hourOffset),
      modelSpec = ModelRegistry.ICON
    )
  }

  override def availableHourOffsets(initTime: OffsetDateTime): Seq[Int] =
    0 to ModelRegistry.ICON.maxHourOffset
}
```

### 3. Utiliser dans la pipeline

```scala
val iconPipeline = UnifiedPipeline(
  modelSpec = ModelRegistry.ICON,
  fileHandler = new IconFileHandler(os.Path("/data/icon")),
  extent = Extent(-4.0, 41.25, 3.75, 44.25),
  gridResolutionKm = 2.5,
  outputDirectory = os.Path("/output-unified")
)

iconPipeline.process(initTime)
```

**C'est tout !** GribParser, TemporalInterpolator, et le reste du code fonctionnent directement.

## Avantages vs backend/

| Aspect | backend/ | backend-v2/ |
|--------|----------|-------------|
| Parseurs | 3 parseurs distincts (GFS, AROME, WRF) | 1 parser générique |
| Duplication | Haute (code répété 3×) | Aucune |
| Ajouter un modèle | 2-3 semaines | 1-2 jours |
| Interpolation temporelle | Non | Oui (toutes les heures) |
| Structure de données | 3 structures (Forecast, AromeData, WrfData) | 1 structure (UnifiedModelData) |
| Testabilité | Difficile (couplage fort) | Facile (composants isolés) |
| Downscaling | Nécessite refactoring | Prêt (Phase 7) |

## Phases de développement

- [x] **Phase 1** : Structures de données (UnifiedModelData, RawData)
- [x] **Phase 2** : Parseurs génériques + FileHandlers
- [x] **Phase 3** : Pipeline unifiée + Interpolation temporelle
- [x] **Phase 4** : Configuration + Main CLI
- [x] **Phase 5** : Validation vs backend/ (80% complété)
- [ ] **Phase 6** : Moteur de phénomènes (82 phénomènes météo)
- [ ] **Phase 7** : Downscaling 1km
- [ ] **Phase 8** : Déploiement production

## État actuel (30 décembre 2025)

✅ **Phases 1-4 terminées, Phase 5 en cours (80%)**
- Architecture complètement fonctionnelle
- GFS pipeline opérationnel à 100%
  - 118 heures traitées (H+3 à H+120)
  - 78 heures interpolées
  - 0 erreurs
- Structure de sortie compatible frontend
- Format PNG et ColorMaps validés

### Chemins des données source

⚠️ **IMPORTANT** : Backend-v2 lit les fichiers GRIB générés par le backend v1 (lecture seule).

**Données GFS** : `/home/ubuntu/soaringmeteo/output/grib/gfs/`
- Structure : `{YYMMDD}/{HH}/GFS-{zone}-{hour}.grib2`
- Exemple : `251230/00/GFS-pyrenees-006.grib2`
- Configuré dans : `app/src/main/resources/unified.conf` ligne 15

**Données AROME** : `/home/ubuntu/soaringmeteo/backend/arome/grib/`
- Fichiers groupés : `SP1_00H06H.grib2`, `SP2_07H12H.grib2`, etc.

**Sortie backend-v2** : `/home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/`

## Outputs

Structure de sortie harmonisée (compatible avec le frontend existant) :

```
output-unified/
└── 7/                              # Version du format
    ├── gfs/
    │   └── 2025-12-28T00/          # Date ISO-8601
    │       └── pyrenees/
    │           ├── thermal-velocity/  # PNG rasters par couche
    │           │   ├── 3.png
    │           │   ├── 4.png
    │           │   └── ...
    │           ├── wind-surface/
    │           │   ├── 3.png
    │           │   └── ...
    │           └── locations/      # JSON location forecasts
    ├── arome/
    │   └── 2025-12-28T06/
    │       └── pays-basque/
    │           ├── thermal-velocity/
    │           └── ...
    └── wrf/
        └── ...
```

**Note** : Structure compatible frontend `{layer}/{hour}.png` (ex: `thermal-velocity/6.png`)

## Prochaines étapes

1. ~~Phase 4 : Créer Main.scala avec configuration~~ ✅
2. ~~Intégration outputs : Réutiliser Raster.scala, VectorTiles.scala, JsonData.scala~~ ✅
3. ~~Tests : Valider avec données réelles GFS~~ ✅
4. **Phase 5 finale** :
   - [ ] Tester frontend avec outputs v2
   - [ ] Benchmarks performance (v1 vs v2)
   - [ ] Documentation complète
5. **Phase 6** : AROME parser + ICON support

## Ressources

- **Plan complet** : `/home/ubuntu/.claude/plans/twinkling-squishing-reddy.md`
- **Documentation backend/** : `../backend/README.md`
- **Documentation CLAUDE** : `../CLAUDE.md`

---

**Dernière mise à jour** : 30 décembre 2025
**Statut** : Phase 5 en cours (80%) - GFS pipeline opérationnel ✅
