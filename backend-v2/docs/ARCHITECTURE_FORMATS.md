# Architecture Multi-Formats - SoaringMeteo Backend-v2

**Date** : 5 janvier 2026
**Statut** : Document de clarification

---

## Vue d'ensemble

### Trois types de formats différents

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. FORMATS SOURCE (Input météo)                                │
│    GRIB2, NetCDF, BUFR                                          │
│    ↓ Parsers spécialisés                                        │
├─────────────────────────────────────────────────────────────────┤
│ 2. FORMAT CACHE (Accélération - Optionnel)                     │
│    N5/Zarr                                                       │
│    ↓ Lecture rapide                                             │
├─────────────────────────────────────────────────────────────────┤
│ 3. FORMATS OUTPUT (Visualisation frontend)                     │
│    PNG, MVT (tuiles vectorielles), JSON                        │
└─────────────────────────────────────────────────────────────────┘
```

**Important** : Ces trois niveaux sont TOUS nécessaires et servent des rôles différents.

---

## 1. Formats SOURCE (Input météo)

### Formats supportés actuellement

#### GRIB2 ✅ (Production)
- **Modèles** : GFS (NOAA), AROME (Météo-France)
- **Parser** : `backend-v2/common/src/main/scala/org/soaringmeteo/parsing/GribParser.scala`
- **Bibliothèque** : `edu.ucar:grib:5.8.0`
- **Statut** : ✅ Opérationnel dans backend-v2

**Avantages GRIB2** :
- Standard international pour prévisions numériques
- Compression efficace (bzip2)
- Métadonnées riches
- Support multi-modèles

**Limitations** :
- Parsing lent (30-60s pour GFS 118h)
- Format binaire complexe

#### NetCDF ✅ (Production - WRF uniquement)
- **Modèle** : WRF (Weather Research & Forecasting)
- **Parser** : `backend/wrf/src/main/scala/org/soaringmeteo/wrf/NetCdf.scala`
- **Bibliothèque** : `edu.ucar:cdm-core:5.8.0` (via Grib.scala)
- **Statut** : ✅ Opérationnel dans backend v1

**Note technique** :
```scala
// NetCdf.scala ligne 39-43
def read(file: os.Path): Result =
  Grib.bracket(file) { grib =>  // Utilise ucar.nc2 en arrière-plan
    val forecasts = readAllTimeSteps(grib)
    forecasts
  }
```

La bibliothèque ucar.nc2 supporte **à la fois GRIB et NetCDF** via une API unifiée.

**Avantages NetCDF** :
- Auto-descriptif (métadonnées intégrées)
- Largement utilisé en sciences climatiques
- Support excellent Python (xarray)

**Limitations** :
- Fichiers plus gros que GRIB2 (moins de compression)
- Parsing aussi lent que GRIB

#### BUFR ❌ (Non supporté - À évaluer)
- **Modèles potentiels** : ECMWF, ICON (DWD), certains obs
- **Format** : Binary Universal Form for the Representation of meteorological data
- **Bibliothèque** : `edu.ucar:bufr:5.8.0` (disponible dans ucar.nc2)

**Cas d'usage** :
- Observations météo (stations, bouées, avions)
- Modèles ECMWF (alternative au GRIB)
- Modèle ICON DWD (selon configuration)

**Statut recommandé** : ⏸️ **À évaluer si besoin futur**
- Pas de modèle BUFR prévu à court terme
- ucar.nc2 supporte déjà BUFR (même API que GRIB/NetCDF)
- Effort d'implémentation : 2-3 jours si nécessaire

---

## 2. Format CACHE (N5/Zarr)

### Rôle de N5/Zarr

**N5/Zarr est un CACHE INTERMÉDIAIRE, pas un remplacement des parsers !**

### Flux complet avec cache

```
ÉTAPE 1 : Première exécution (parsing GRIB)
──────────────────────────────────────────
GRIB2 source → GribParser → UnifiedModelData (en mémoire)
                                    ↓
                            [OPTIONNEL] GribToN5Converter
                                    ↓
                            Cache N5/Zarr (disque)
                                    ↓
                    Raster/VectorTiles → PNG/MVT/JSON


ÉTAPE 2 : Exécutions suivantes (cache hit)
──────────────────────────────────────────
Cache N5/Zarr → N5Reader → UnifiedModelData (10-20x plus rapide !)
                                ↓
                    Raster/VectorTiles → PNG/MVT/JSON
```

### Quand utiliser le cache N5 ?

**Cas d'usage** :
1. **Re-génération outputs** : Changer ColorMaps, résolution PNG
2. **Développement** : Tester changements sans re-parser GRIB
3. **Interpolation temporelle** : Heures pré-calculées
4. **Downscaling** : Pré-calculer grilles 1km depuis 2.5km

**Quand NE PAS utiliser** :
- Première lecture GRIB (pas de cache encore)
- Production quotidienne si disque limité
- Runs de prévision uniques (pas de réutilisation)

### Architecture cache proposée

```
/home/ubuntu/soaringmeteo/zarr-cache/
├── gfs/
│   ├── 2025-12-31T00/
│   │   ├── pyrenees/
│   │   │   ├── .zgroup              # Métadonnées Zarr
│   │   │   ├── temperature_2m/
│   │   │   │   ├── .zarray          # Métadonnées dataset
│   │   │   │   ├── 0.0.0            # Chunks (time, lat, lon)
│   │   │   │   ├── 0.0.1
│   │   │   │   └── 0.1.0
│   │   │   ├── thermal_velocity/
│   │   │   ├── boundary_layer_depth/
│   │   │   ├── wind_u_10m/
│   │   │   └── wind_v_10m/
│   │   └── pays-basque/
│   └── 2025-12-30T12/
└── arome/
    └── 2025-12-31T00/
```

**Taille estimée** (avec compression GZip) :
- GFS Pyrénées (118h × 120×120 × 20 variables) : ~200MB
- AROME Pyrénées (43h × 120×120 × 20 variables) : ~80MB

**Gain de temps** :
- Parsing GRIB : 30-60s
- Lecture N5 : 1-3s
- **Accélération : 10-30x** ✅

---

## 3. Formats OUTPUT (Frontend)

### Formats actuels (inchangés)

1. **PNG Rasters** : Cartes météo colorées
2. **MVT (Mapbox Vector Tiles)** : Vents vectoriels
3. **JSON** : Métadonnées + données par location

**N5/Zarr ne change RIEN aux outputs** - c'est uniquement un cache interne backend.

---

## Architecture Multi-Formats Unifiée

### Principe : ModelRegistry + FileHandlers

```scala
// backend-v2/common/src/main/scala/org/soaringmeteo/parsing/ModelRegistry.scala

object ModelRegistry {

  case class ModelSpec(
    name: String,
    format: DataFormat,        // GRIB2, NetCDF, BUFR
    variables: VariableMapping,
    fileHandler: FileHandler
  )

  sealed trait DataFormat
  case object GRIB2 extends DataFormat
  case object NetCDF extends DataFormat
  case object BUFR extends DataFormat

  // Registre central des modèles
  val models = Map(
    "gfs" -> ModelSpec(
      name = "GFS",
      format = GRIB2,
      variables = gfsVariables,
      fileHandler = new GfsFileHandler
    ),
    "arome" -> ModelSpec(
      name = "AROME",
      format = GRIB2,
      variables = aromeVariables,
      fileHandler = new AromeFileHandler
    ),
    "wrf" -> ModelSpec(
      name = "WRF",
      format = NetCDF,
      variables = wrfVariables,
      fileHandler = new WrfFileHandler
    ),
    // Futur :
    "icon" -> ModelSpec(
      name = "ICON",
      format = GRIB2,  // ou BUFR selon source
      variables = iconVariables,
      fileHandler = new IconFileHandler
    )
  )
}
```

### Parsers génériques

```scala
// Un seul parser par FORMAT, pas par modèle

object GribParser {
  def parse(modelSpec: ModelSpec, files: Seq[Path]): UnifiedModelData
}

object NetCdfParser {
  def parse(modelSpec: ModelSpec, files: Seq[Path]): UnifiedModelData
}

object BufrParser {  // Futur si nécessaire
  def parse(modelSpec: ModelSpec, files: Seq[Path]): UnifiedModelData
}
```

**Avantage** : Ajouter ICON (GRIB2) ou ECMWF (BUFR) = créer ModelSpec + FileHandler, réutiliser parser existant.

---

## Roadmap Support Formats

### Phase 6 (Actuel)
- ✅ GRIB2 : GFS + AROME (opérationnel)
- ✅ N5/Zarr : Cache intégré et testé
- ⏸️ NetCDF : Fonctionnel dans backend v1, migration backend-v2 à planifier

### Phase 7 (Futur)
- [ ] NetCDF : Migrer WRF dans backend-v2
- [ ] BUFR : Évaluer si nécessaire pour ECMWF/ICON
- [ ] ICON : Support GRIB2 (réutiliser GribParser)

### Hors scope actuel
- HDF5 : Pas utilisé en météo opérationnelle
- GRIB1 : Format obsolète (remplacé par GRIB2)

---

## Réponses aux questions

### Q1 : "Je croyais que Zarr était rejeté"

**Réponse** : NON ! C'est **ndarray.scala** (bibliothèque Scala abandonnée) qui a été rejetée.

**N5 avec support Zarr** est validé :
- N5 = bibliothèque Java pour manipuler données multidimensionnelles
- Zarr v2 = format de sortie de N5 (interopérable avec Python)

```bash
# Cache créé par N5 est du Zarr valide :
cat /tmp/n5-example-cache/.zgroup
# {"zarr_format":2}  ← Format Zarr ✅
```

### Q2 : "Faut-il des parsers maintenant que nous avons N5 ?"

**Réponse** : OUI, absolument !

**N5 est un CACHE, pas une SOURCE** :
- GRIB2/NetCDF = données sources (NOAA, Météo-France)
- GribParser/NetCdfParser = lisent les sources → UnifiedModelData
- N5/Zarr = stockage intermédiaire rapide (optionnel)

**Analogie** :
```
GRIB = Fichier texte brut
Parser = Lecteur de fichiers
N5 = Base de données indexée
```

On a besoin du lecteur pour remplir la base de données !

### Q3 : "Envisager NetCDF et BUFR en input"

**Réponse** :

**NetCDF** : ✅ Déjà supporté (WRF)
- Bibliothèque ucar.nc2 supporte GRIB + NetCDF
- Migration vers backend-v2 à planifier (Phase 7)

**BUFR** : ⏸️ À évaluer si besoin
- Bibliothèque ucar.nc2 supporte déjà BUFR
- Aucun modèle BUFR prévu actuellement
- Effort d'implémentation faible (2-3 jours) si nécessaire

**Recommandation** :
1. Terminer intégration N5 cache (Phase 6)
2. Migrer WRF/NetCDF vers backend-v2 (Phase 7)
3. Évaluer BUFR seulement si modèle ECMWF/ICON requis

---

## Conclusion

### Architecture finale backend-v2

```
                    ┌─────────────────────────┐
                    │   FORMATS SOURCE        │
                    ├─────────────────────────┤
                    │ GRIB2 (GFS, AROME)      │
                    │ NetCDF (WRF)            │
                    │ BUFR (futur ECMWF?)     │
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │   PARSERS GÉNÉRIQUES    │
                    ├─────────────────────────┤
                    │ GribParser.scala        │
                    │ NetCdfParser.scala      │
                    │ BufrParser.scala (futur)│
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │ UnifiedModelData        │
                    │ (Structure commune)     │
                    └───────────┬─────────────┘
                                │
                ┌───────────────┴───────────────┐
                │                               │
    ┌───────────▼──────────┐      ┌────────────▼──────────┐
    │  CACHE N5/Zarr       │      │  OUTPUTS              │
    │  (Optionnel)         │      │  PNG, MVT, JSON       │
    └──────────────────────┘      └───────────────────────┘
```

### Tous les formats sont nécessaires

- **GRIB2/NetCDF/BUFR** : Sources météo (obligatoire)
- **N5/Zarr** : Cache rapide (optionnel mais très utile)
- **PNG/MVT/JSON** : Visualisation frontend (obligatoire)

**Aucun format ne remplace les autres** - chacun a son rôle spécifique.

---

**Maintenu par** : Claude Code
**Date** : 5 janvier 2026
