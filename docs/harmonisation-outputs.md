# Harmonisation de la structure des outputs

**Date**: 2025-11-15
**Auteur**: Claude
**Statut**: Implémentation en cours

## Objectif

Harmoniser la structure des répertoires de sortie pour tous les modèles météo (GFS, WRF, AROME) vers un format cohérent et extensible.

## Ancienne structure (DISPERSÉE)

### GFS
```
<outputDir>/<formatVersion>/gfs/<initDateString>/<subgridId>/
├── maps/<hour>/
└── location/
```

### WRF
```
<outputDir>/<formatVersion>/wrf/<initDateString>/<gridOutputPath>/
├── maps/<hour>/
└── location/
```

### AROME (INCOHÉRENT)
```
<outputDirectory>/maps/<hour>/
```
❌ Pas de versioning, pas de initDateString, pas de location JSON

---

## Nouvelle structure HARMONISÉE

```
/home/user/soaringmeteo/output/
├── <formatVersion>/           # Ex: "7"
│   ├── gfs/
│   │   ├── <initDateString>/  # Ex: "2025-11-15T06"
│   │   │   ├── pays-basque/
│   │   │   │   ├── maps/
│   │   │   │   │   └── <hour>/
│   │   │   │   │       ├── thermal-velocity.png
│   │   │   │   │       ├── wind-10m.mvt
│   │   │   │   │       └── ...
│   │   │   │   └── location/
│   │   │   │       ├── <clusterId>.json
│   │   │   │       └── ...
│   │   │   ├── pyrenees/
│   │   │   │   └── ...
│   │   │   └── forecast.json  # Métadonnées du run
│   │   └── ...
│   ├── wrf/
│   │   ├── <initDateString>/
│   │   │   ├── <zone>/
│   │   │   │   ├── maps/
│   │   │   │   └── location/
│   │   │   └── forecast.json
│   │   └── ...
│   └── arome/
│       ├── <initDateString>/
│       │   ├── pays-basque/
│       │   │   ├── maps/
│       │   │   │   └── <hour>/
│       │   │   │       ├── thermal-velocity.png
│       │   │   │       ├── wind-10m.mvt
│       │   │   │       └── ...
│       │   │   └── location/
│       │   │       ├── <clusterId>.json
│       │   │       └── ...
│       │   └── forecast.json
│       └── ...
```

---

## Modifications effectuées

### 1. Helpers communs créés (`backend/common/src/main/scala/org/soaringmeteo/out/`)

#### `OutputPaths.scala` (NOUVEAU)
Fonctions utilitaires pour générer les chemins harmonisés :
- `versionedOutputDir(baseOutputDir)` → `<base>/<version>/`
- `modelOutputDir(baseOutputDir, model)` → `<base>/<version>/<model>/`
- `runOutputDir(baseOutputDir, model, initDateString)` → `<base>/<version>/<model>/<initDate>/`
- `zoneOutputDir(baseOutputDir, model, initDateString, zone)` → `<base>/<version>/<model>/<initDate>/<zone>/`
- `mapsDir(...)` → `.../<zone>/maps/`
- `hourMapsDir(..., hour)` → `.../<zone>/maps/<hour>/`
- `locationDir(...)` → `.../<zone>/location/`
- `forecastMetadataFile(...)` → `.../<initDate>/forecast.json`

#### `package.scala` (MODIFIÉ)
Ajout de :
```scala
def createOutputDirectories(
  baseOutputDir: os.Path,
  model: String,
  initDateString: String,
  zones: Seq[String]
): Unit
```

### 2. GFS migré

**Fichiers modifiés** :
- `backend/gfs/src/main/scala/org/soaringmeteo/gfs/out/package.scala`
  - Ajout de `@deprecated` sur anciennes fonctions
  - Utilisation de `OutputPaths` pour rétro-compatibilité

- `backend/gfs/src/main/scala/org/soaringmeteo/gfs/Main.scala`
  - Import de `OutputPaths`
  - Création des répertoires harmonisés pour chaque subgrid
  - Utilisation des nouveaux chemins

**Comportement** :
- ✅ Compatible avec l'ancienne structure (via deprecated helpers)
- ✅ Utilise la nouvelle structure harmonisée
- ✅ Génère `forecast.json` au bon endroit
- ✅ Génère location JSON dans `<zone>/location/`

### 3. WRF migré

**Fichiers modifiés** :
- `backend/wrf/src/main/scala/org/soaringmeteo/wrf/DataPipeline.scala`
  - Import de `OutputPaths`
  - Utilisation des nouveaux chemins pour chaque zone
  - Création automatique des répertoires `maps/` et `location/`

**Comportement** :
- ✅ Utilise la nouvelle structure harmonisée
- ✅ Génère `forecast.json` au bon endroit
- ✅ Génère location JSON dans `<zone>/location/`

### 4. AROME migré

**Fichiers modifiés** :
- `backend/arome/src/main/scala/org/soaringmeteo/arome/Main.scala`
  - Import de `OutputPaths`, `JsonData`, `InitDateString`, `touchMarkerFile`, `ForecastMetadata`
  - Calcul de `initDateString` depuis `initTime`
  - Normalisation des noms de zones (ex: "Pays Basque" → "pays-basque")
  - Utilisation des nouveaux chemins harmonisés
  - Création automatique de tous les répertoires
  - ✅ Génération de location JSON via `generateLocationForecasts()`
  - ✅ Génération de forecast.json via `writeForecastMetadata()`

**Fichiers créés** :
- `backend/arome/src/main/scala/org/soaringmeteo/arome/out/AromeLocationJson.scala`
  - Structure JSON simplifiée adaptée aux données AROME
  - Clustering 4x4 comme GFS
  - Champs: thermal velocity, boundary layer, wind10m, cloud cover, CAPE, t2m, winds at heights
  - Format compatible pour méteogrammes (frontend devra être adapté)

- `backend/arome/src/main/scala/org/soaringmeteo/arome/out/Store.scala`
  - Ajout de `getDataForLocation()` pour récupérer toutes les heures d'un point (x,y)
  - Ajout de `decodeAromeData()` pour désérialiser depuis JSON

**Comportement** :
- ✅ Utilise la nouvelle structure harmonisée
- ✅ Versioning ajouté
- ✅ initDateString ajouté
- ✅ Location JSON implémenté (AromeLocationJson.scala)
- ✅ forecast.json implémenté (writeForecastMetadata)
- ⚠️ Structure JSON simplifiée vs GFS (AROME n'a pas les mêmes champs détaillés)

---

## Avantages de la nouvelle structure

✅ **Cohérence** : Tous les modèles suivent la même structure
✅ **Versioning** : Facile de migrer entre versions de format
✅ **Organisation** : Clair et prévisible
✅ **Extensibilité** : Facile d'ajouter de nouveaux modèles (ICON, ECMWF, etc.)
✅ **Maintenance** : Un seul point d'entrée `/home/user/soaringmeteo/output/`
✅ **Frontend** : URLs cohérentes `/v2/data/<version>/<model>/<initDate>/<zone>/...`

---

## Prochaines étapes

### 1. Compléter AROME (PRIORITAIRE)

#### A. Ajouter génération location JSON
Inspiré de GFS et WRF, ajouter dans `processZone()` après le traitement de tous les groupes :

```scala
// Récupérer toutes les données depuis la DB
val allHoursData: Map[Int, IndexedSeq[IndexedSeq[MeteoData]]] = ...

// Générer les fichiers location/*.json
JsonData.writeForecastsByLocation(
  zoneId,
  setting.zone.longitudes.size,
  setting.zone.latitudes.size,
  zoneOutputDir
) { (x, y) =>
  allHoursData.map { case (hour, grid) =>
    (hour, grid(x)(y))
  }
}
```

**Défi** : Les données sont actuellement traitées en parallèle et non stockées en mémoire.
**Solution** : Soit :
1. Récupérer depuis la DB après traitement
2. Conserver en mémoire pendant le traitement (attention RAM)

#### B. Ajouter génération forecast.json
Créer un fichier similaire à `GFS/JsonWriter.scala` pour AROME :

```scala
object AromeMetadataWriter {
  def writeForecastMetadata(
    modelOutputDir: os.Path,
    initTime: OffsetDateTime,
    initDateString: String,
    zones: Seq[AromeSetting]
  ): Unit = {
    // Créer les zones metadata
    val zonesMetadata = zones.map { setting =>
      val zoneId = setting.name.toLowerCase.replace(" ", "-")
      ForecastMetadata.Zone(
        zoneId,
        setting.name,
        ForecastMetadata.Raster(...),
        ForecastMetadata.VectorTiles(...)
      )
    }

    // Écrire forecast.json
    ForecastMetadata.overwriteLatestForecastMetadata(
      modelOutputDir,
      forecastHistory = Period.ofDays(2),
      initDateString,
      initTime,
      firstTimeStep = Some(initTime),
      latestHourOffset = 24,
      zonesMetadata
    )
  }
}
```

### 2. Mettre à jour le frontend

**Fichier** : `frontend/vite.config.ts`

```typescript
// Ancien
server.middlewares.use('/v2/data', serveStatic('../backend/target/forecast/data'))

// Nouveau
server.middlewares.use('/v2/data', serveStatic('/home/user/soaringmeteo/output'))
```

**Fichier** : `frontend/src/data/Model.ts`

Ajouter AROME aux modèles disponibles :
```typescript
export const models: Model[] = [
  { id: 'gfs', label: 'GFS' },
  { id: 'wrf', label: 'WRF' },
  { id: 'arome', label: 'AROME' }, // NOUVEAU
];
```

### 3. Mettre à jour les configurations

**AROME** : Modifier tous les fichiers `.conf` pour utiliser le nouveau chemin :

```hocon
# Ancien
output-directory = "/mnt/soaringmeteo-data/arome/output/pays_basque"

# Nouveau
output-directory = "/home/user/soaringmeteo/output"
```

**GFS/WRF** : Mettre à jour les scripts de déploiement pour utiliser `/home/user/soaringmeteo/output`

### 4. Mettre à jour NGINX

**Fichier** : `/etc/nginx/sites-available/soaringmeteo-unified`

```nginx
location /v2/data/ {
  alias /home/user/soaringmeteo/output/;
  autoindex off;
  add_header Access-Control-Allow-Origin *;
  add_header Cache-Control "public, max-age=3600";
}
```

### 5. Tester les pipelines

#### Test GFS
```bash
cd backend
# Utiliser le nouveau chemin de sortie
sbt "gfs/run -r /tmp/grib /home/user/soaringmeteo/output"
```

#### Test WRF
```bash
cd backend
sbt "wrf/run /home/user/soaringmeteo/output 2025-11-15T06:00:00Z 2025-11-15T12:00:00Z wrfout_*.nc"
```

#### Test AROME
```bash
cd backend
# Mettre à jour le fichier de config d'abord
sbt "arome/run backend/arome_pays_basque.conf"
```

### 6. Migration des données existantes (optionnel)

Si des données existent déjà dans l'ancienne structure, créer un script de migration :

```bash
#!/bin/bash
# migrate-outputs.sh

OLD_GFS="/mnt/soaringmeteo-data/gfs/output"
OLD_WRF="/mnt/soaringmeteo-data/wrf/output"
OLD_AROME="/mnt/soaringmeteo-data/arome/output"
NEW_BASE="/home/user/soaringmeteo/output"

# Migrer GFS
if [ -d "$OLD_GFS" ]; then
  cp -r "$OLD_GFS"/* "$NEW_BASE/"
fi

# Migrer WRF
if [ -d "$OLD_WRF" ]; then
  cp -r "$OLD_WRF"/* "$NEW_BASE/"
fi

# Migrer AROME (restructuration nécessaire)
# ...
```

---

## Compatibilité ascendante

Les anciens chemins GFS continueront de fonctionner via les fonctions deprecated :
- `versionedTargetPath()` → appelle `OutputPaths.modelOutputDir()`
- `runTargetPath()` → construit le chemin de la même manière
- `subgridTargetPath()` → construit le chemin de la même manière

**Recommandation** : Supprimer ces fonctions deprecated dans une future version majeure.

---

## Checklist d'implémentation

- [x] Créer `OutputPaths.scala` avec helpers communs
- [x] Migrer GFS vers nouveaux chemins
- [x] Migrer WRF vers nouveaux chemins
- [x] Migrer AROME vers nouveaux chemins (structure uniquement)
- [x] Ajouter génération location JSON pour AROME
- [x] Ajouter génération forecast.json pour AROME
- [ ] Mettre à jour frontend (vite.config.ts, Model.ts)
- [ ] Mettre à jour configurations AROME
- [ ] Mettre à jour NGINX
- [ ] Tester GFS avec nouveaux chemins
- [ ] Tester WRF avec nouveaux chemins
- [ ] Tester AROME avec nouveaux chemins
- [ ] Documenter dans CLAUDE.md
- [ ] Commit et push

---

## Notes techniques

### Zone ID normalization
AROME normalise les noms de zones :
```scala
val zoneId = setting.name.toLowerCase.replace(" ", "-")
// "Pays Basque" → "pays-basque"
```

### Marker file
Le fichier `marker` à la racine de `outputDir` est mis à jour pour indiquer qu'une nouvelle génération est disponible. Utilisé par les systèmes externes.

### Database H2
Les databases restent à leur emplacement actuel (pas de changement) :
- GFS: `./data.mv.db` (relatif au working directory)
- AROME: configuré dans `.conf` (ex: `/mnt/soaringmeteo-data/arome/arome.h2`)

### GRIB files
Les fichiers GRIB téléchargés/stockés restent à leur emplacement actuel (pas de changement).

---

## Questions ouvertes

1. **Faut-il migrer les données existantes** ou simplement regénérer ?
   - Recommandation : Regénérer (plus sûr)

2. **Faut-il centraliser aussi les GRIB** dans `/home/user/soaringmeteo/grib/` ?
   - Recommandation : Oui, pour cohérence (optionnel)

3. **Faut-il centraliser les databases H2** dans `/home/user/soaringmeteo/databases/` ?
   - Recommandation : Oui, pour cohérence (optionnel)

---

## Références

- Architecture originale : `docs/PHASE2_DOCUMENTATION.md`
- MeteoData trait : `backend/common/src/main/scala/org/soaringmeteo/MeteoData.scala`
- GFS JsonWriter : `backend/gfs/src/main/scala/org/soaringmeteo/gfs/JsonWriter.scala`
- Frontend state : `frontend/src/State.tsx`
- Vite config : `frontend/vite.config.ts`
