# H2 vs N5 : Analyse Comparative pour Cache Météo

**Date** : 5 janvier 2026
**Question** : Pourquoi N5 si nous avons déjà H2 ?

---

## ⚠️ Question Légitime

**Vous avez raison** : H2 est déjà utilisé comme "base de données indexée pour accès rapide" dans backend v1.

**Analysons si N5 apporte une vraie valeur ajoutée ou si c'est redondant.**

---

## H2 - Usage Actuel dans Backend v1

### Configuration

```scala
// backend/gfs/src/main/resources/reference.conf
h2db = {
  url = "jdbc:h2:file:./data"
  driver = org.h2.Driver
  connectionPool = disabled
  keepAliveConnection = true
}
```

**Fichier** : `/home/ubuntu/soaringmeteo/backend/gfs/data.mv.db` (49 MB)

### Schéma H2

```scala
// backend/gfs/src/main/scala/org/soaringmeteo/gfs/out/Store.scala

class GfsGrids extends Table {
  initTime: OffsetDateTime
  subgrid: String
  hourOffset: Int
  x: Int
  y: Int
  forecastData: String  // JSON sérialisé (Forecast → JSON texte)

  // Index
  PRIMARY KEY (initTime, subgrid, hourOffset, x, y)
  INDEX spatial_access (initTime, subgrid, x, y)
}
```

### Usage selon code (ligne 19-21)

```scala
/**
 * The on-disk storage system is used as a temporary storage
 * (we don't need it to be durable) to avoid keeping everything in-memory.
 */
```

**Rôle** : Cache temporaire pour éviter de garder toute la grille en RAM pendant le traitement.

### Opérations H2

1. **Écriture** : `save(initTime, subgrid, hourOffset, forecasts: Grid)`
   - Sérialise chaque Forecast en JSON
   - INSERT bulk dans H2

2. **Lecture par location** : `forecastForLocation(x, y)`
   - SELECT * WHERE initTime=? AND subgrid=? AND x=? AND y=?
   - Retourne toutes les heures pour un point (x,y)
   - Index spatial optimisé

3. **Vérification existence** : `exists(initTime, subgrid, hourOffset)`
   - COUNT(*) WHERE ...

---

## N5/Zarr - Proposition Alternative

### Configuration

```
/zarr-cache/gfs/2025-12-31T00/pyrenees/
  ├── .zgroup
  ├── temperature_2m/
  │   ├── .zarray
  │   └── 0.0.0, 0.0.1... (chunks binaires)
  ├── thermal_velocity/
  └── wind_u_10m/
```

### Structure données

- Format : Array binaire 3D (time, lat, lon)
- Chunking : (10h, 50lat, 50lon)
- Compression : GZip
- Type : Float32

### Opérations N5

1. **Écriture** : Un fichier par variable météo
   - Array 3D complet (118h × 121lat × 358lon)
   - Chunking automatique
   - Compression GZip

2. **Lecture** : Par variable ou chunk
   - Lecture complète : toutes les heures d'une variable
   - Lecture chunk : accès aléatoire par heure

---

## Comparaison Détaillée

| Critère | H2 (actuel) | N5/Zarr (proposé) |
|---------|-------------|-------------------|
| **Type** | Base relationnelle SQL | Stockage array multidimensionnel |
| **Format stockage** | JSON texte | Binaire (Float32) |
| **Compression** | Aucune | GZip/Blosc |
| **Taille fichier** | 49 MB (GFS actuel) | ~20-30 MB estimé (compression) |
| **Index** | B-tree SQL (x, y) | Chunking (time, lat, lon) |
| **Lecture par point** | ✅ Optimisé (index spatial) | ❌ Moins efficace (charge chunk) |
| **Lecture par heure** | ⚠️ Charge tous points | ✅ Optimisé (chunk temporel) |
| **Lecture complète** | ❌ N requêtes SQL | ✅ Lecture séquentielle rapide |
| **Interop Python** | ⚠️ Requiert connexion JDBC | ✅ xarray natif |
| **Complexité** | ⚠️ Schema SQL, sérialisation JSON | ✅ Arrays directs |
| **Maturité** | ✅ Utilisé en production | ❌ À intégrer |

---

## Cas d'usage Différents

### H2 est MEILLEUR pour :

1. **Requêtes par location** :
   ```scala
   // Récupérer toutes les heures pour un point (x,y)
   forecastForLocation(x=45, y=67)
   // → 1 requête SQL avec index spatial ✅
   ```

2. **Accès aléatoire spatial** :
   - Frontend demande météo pour location spécifique
   - Index spatial H2 optimisé

3. **Opérations transactionnelles** :
   - ACID garanties
   - Requêtes SQL complexes

### N5 est MEILLEUR pour :

1. **Lecture complète d'une variable** :
   ```scala
   // Charger toute la température de surface (118h × 121 × 358)
   readVariable("temperature_2m")
   // → Lecture séquentielle optimisée ✅
   ```

2. **Traitement batch** :
   - Régénérer tous les PNG d'une zone
   - Calculs statistiques sur grille complète

3. **Interopérabilité Python** :
   ```python
   import xarray as xr
   ds = xr.open_zarr('/zarr-cache/gfs/...')
   # → Validation croisée, analyse, plots
   ```

4. **Économie disque** :
   - Compression GZip : 5-10x
   - Format binaire vs JSON texte

---

## Analyse : N5 est-il Nécessaire ?

### ❌ Arguments CONTRE N5

1. **H2 fait déjà le job** :
   - Cache temporaire fonctionnel
   - Indexé pour requêtes par location
   - Prouvé en production

2. **Redondance** :
   - Deux systèmes de cache pour même données
   - Complexité maintenance

3. **Backend v1 n'a pas besoin** :
   - Pipeline GFS/AROME actuel fonctionne bien
   - Pas de plaintes performance

4. **Effort d'intégration** :
   - GribToN5Converter à écrire
   - Tests, validation
   - ~5-7 jours de travail

### ✅ Arguments POUR N5

1. **Interopérabilité Python** :
   - Validation croisée facile (xarray)
   - Analyse scientifique des données
   - Plots avec matplotlib/cartopy

2. **Économie disque** :
   - 49 MB (H2) → ~20-30 MB (N5 compressé)
   - Important si multi-modèles (GFS + AROME + WRF + ICON)

3. **Performance lectures complètes** :
   - Régénérer outputs sans re-parser GRIB
   - Utile pour développement/tests

4. **Standard scientifique** :
   - Zarr largement utilisé en climatologie
   - Compatible Dask (traitement parallèle)
   - Évolutif vers Cloud (S3, Azure)

---

## Recommandation

### Option 1 : **Garder H2 uniquement** (Recommandé court terme) ⭐

**Raison** : H2 remplit déjà le rôle de cache efficacement.

**Cas d'usage actuel** :
```
GRIB → Parser → UnifiedModelData
           ↓
        Store H2 (cache temporaire)
           ↓
     forecastForLocation(x, y)
           ↓
     Génération JSON locations
```

**Avantages** :
- ✅ Pas de changement (déjà en production)
- ✅ Optimisé pour requêtes par location
- ✅ Simple, prouvé

**Limitations** :
- ❌ Pas d'interopérabilité Python facile
- ❌ Format JSON moins efficace que binaire
- ❌ Difficile d'extraire données pour analyse scientifique

### Option 2 : **N5 en complément de H2** (Évaluation à faire)

**Scénario** : Les deux systèmes coexistent avec rôles différents

```
GRIB → Parser → UnifiedModelData
           ↓
     ┌─────┴─────┐
     ↓           ↓
  Store H2    Cache N5
  (locations) (grilles complètes)
     ↓           ↓
  JSON         Validation Python
  Frontend     Analyse scientifique
```

**H2 utilisé pour** :
- Génération JSON locations (accès par point)
- Frontend actuel (inchangé)

**N5 utilisé pour** :
- Validation croisée Python
- Régénération outputs (développement)
- Analyse scientifique (climatologie)

**Effort** : ~5-7 jours implémentation + tests

### Option 3 : **Remplacer H2 par N5** (Non recommandé)

**Raison** : H2 est meilleur pour requêtes par location.

❌ **Ne pas faire** : N5 n'est pas optimisé pour `forecastForLocation(x, y)`

---

## Benchmark Suggéré (si Option 2)

Avant de décider, mesurer :

1. **Taille disque** :
   - H2 : 49 MB actuel
   - N5 : ? (à mesurer avec GFS réel)

2. **Temps lecture** :
   - `forecastForLocation(x, y)` : H2 vs N5
   - Lecture variable complète : H2 vs N5

3. **Temps écriture** :
   - Sérialisation JSON H2 : ?
   - Écriture N5 binaire : ?

**Script benchmark** :
```scala
// Tester avec GFS 118h × 121 × 358
val forecast = // ... données parsées

// Test H2
val t0 = System.currentTimeMillis()
Store.save(initTime, subgrid, hourOffset, forecast)
val h2Write = System.currentTimeMillis() - t0

// Test N5
val t1 = System.currentTimeMillis()
N5Writer.save(cachePath, forecast)
val n5Write = System.currentTimeMillis() - t1

// Test lectures
// forecastForLocation(x=45, y=67)
// readVariable("temperature_2m")
```

---

## Décision Proposée

### Phase 6 : **SUSPENDRE N5, valider H2 d'abord** ⏸️

**Raisons** :
1. H2 fait déjà le job de cache
2. Pas de problème performance identifié en production
3. N5 apporterait surtout valeur pour Python/analyse scientifique

**Actions** :
1. ✅ Garder intégration N5 actuelle (exemple fonctionnel)
2. ✅ Documenter H2 vs N5 (ce document)
3. ⏸️ Reporter implémentation GribToN5Converter
4. 🔍 Évaluer besoin réel avant d'implémenter

**Critères pour réactiver N5** :
- Besoin validation Python fréquent
- Multi-modèles (GFS+AROME+WRF+ICON) → économie disque
- Problèmes performance H2 détectés
- Besoin Cloud storage (S3/Azure)

### Prochaines étapes Phase 6

**Au lieu de N5, focus sur** :
1. Affiner clouds-rain & xc-flying-potential (différences Phase 5)
2. Implémenter AROME parser backend-v2
3. Migrer WRF/NetCDF backend-v2

**N5 reste disponible** :
- Code exemple fonctionnel
- Documentation complète
- Réactivable en 2-3 jours si besoin confirmé

---

## Conclusion

**Vous aviez raison de poser la question !** ✅

H2 fait déjà le job de cache pour le cas d'usage actuel (requêtes par location).

**N5 apporterait valeur pour** :
- Interopérabilité Python (validation scientifique)
- Économie disque (multi-modèles)
- Performance lectures complètes (développement)

**Recommandation** : Garder H2, suspendre N5 jusqu'à besoin confirmé.

---

**Maintenu par** : Claude Code
**Date** : 5 janvier 2026
