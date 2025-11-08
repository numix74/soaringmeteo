# Refactorisation MeteoData - SoaringMeteo

## Date
18 octobre 2025

## Objectif
Adapter pipeline GFS → pipeline multi-modèles (GFS + AROME + futur ICON/ECMWF)
en utilisant un trait `MeteoData` commun.

## Architecture Implémentée

### 1. Trait MeteoData (common/)
Interface unifiée pour tous les modèles météo.

**Fichier** : `backend/common/src/main/scala/org/soaringmeteo/MeteoDataAdapter.scala`
```scala
trait MeteoData {
  def thermalVelocity: Velocity
  def boundaryLayerDepth: Double
  def wind10m: Wind
  def windAtHeight(heightMeters: Int): Option[Wind]
  def temperature2m: Temperature
  def cape: Option[SpecificEnergy]
  def totalCloudCover: Int
  def time: OffsetDateTime
}
```

### 2. Adaptateurs Spécifiques

#### GfsMeteoDataAdapter
- **Fichier** : `backend/common/src/main/scala/org/soaringmeteo/MeteoDataAdapter.scala`
- Wraps `Forecast` (GFS)
- Mappe les champs GFS → MeteoData

#### AromeMeteoDataAdapter
- **Fichier** : `backend/arome/src/main/scala/org/soaringmeteo/arome/AromeMeteoDataAdapter.scala`
- Wraps `AromeData` (AROME)
- Mappe les champs AROME → MeteoData
- Calcule `thermalVelocity` depuis flux chaleur sensible

### 3. Pipeline Métier Adaptée

#### Raster (PNG export)
- **Fichier** : `backend/common/src/main/scala/org/soaringmeteo/out/Raster.scala`
- ✅ Accepte `IndexedSeq[IndexedSeq[MeteoData]]`
- Génère PNG pour thermiques, plafond, etc.

#### VectorTiles (MVT export)
- **Fichier** : `backend/common/src/main/scala/org/soaringmeteo/out/VectorTiles.scala`
- ✅ Accepte `IndexedSeq[IndexedSeq[MeteoData]]`
- Génère tuiles vecteur pour cartes interactives
- GeoTrellis 3.7.1 (tileWidth=4096 MVT standard)

#### Store (persistence)
- **Fichier** : `backend/gfs/src/main/scala/org/soaringmeteo/gfs/out/Store.scala`
- ❌ ENCORE À ADAPTER (voir "À Faire")

### 4. Helper Créés

#### Extent
- **Fichier** : `backend/common/src/main/scala/org/soaringmeteo/Extent.scala`
- Wrapper simple pour GeoTrellis Extent (imports disambiguation)

## Points Clés du Design

1. **Pas de duplication** : Raster/VectorTiles/Store réutilisables pour tous modèles
2. **Extensible** : Ajouter ICON/ECMWF = créer IconMeteoDataAdapter + basta
3. **Type-safe** : Adaptation à la compilation, pas runtime
4. **Clean Architecture** : Métier séparé des spécificités modèles

## Tests Effectués

- ✅ `common/compile` réussit
- ✅ MeteoData trait fonctionne
- ✅ Raster accepte MeteoData
- ✅ VectorTiles accepte MeteoData
- ⏳ Intégration AROME complète : à tester

## Erreurs Résolues (9 total)

1. Type mismatch CAPE (SpecificEnergy Try)
2. Proj4j CoordinateTransform abstract
3. GeoTrellis VectorTile.toBytes() API
4. GeoTrellis StrictLayer tileWidth
5. Scala Seq.empty type inference
6. VectorTiles.Parameters zoomLevels
7. Etc. (voir git history)

## Notes Techniques

### GeoTrellis 3.7.1
- Pas de `ProtobufTile` direct, utiliser `vt.toBytes`
- StrictLayer demande tous les types géométriques (points/lines/polygons/etc.)
- Seq.empty nécessite type explicite : `Seq.empty[MVTFeature[LineString]]`

### Squants
- `SpecificEnergy(double)` retourne `Try[SpecificEnergy]`
- Unwrap avec `.toOption`

### Proj4j
- Utiliser `CoordinateTransformFactory` pas direct `new CoordinateTransform`

## Fichiers Modifiés
```
backend/common/src/main/scala/org/soaringmeteo/
├── MeteoDataAdapter.scala (trait + GfsMeteoDataAdapter)
├── Extent.scala (nouveau)
├── out/Raster.scala (adapté)
└── out/VectorTiles.scala (adapté)

backend/arome/src/main/scala/org/soaringmeteo/arome/
├── AromeMeteoDataAdapter.scala (nouveau)
└── AromeVectorTilesParameters.scala (nouveau)
```

## À Faire

### Phase 2 (Priorité Haute)
1. **Adapter Store** pour MeteoData
   - Fichier : `backend/gfs/src/main/scala/org/soaringmeteo/gfs/out/Store.scala`
   - Signature : `save(date, meteoData)` au lieu de `save(date, forecast)`

2. **Intégrer AROME dans Main.scala**
   - Fichier : `backend/arome/src/main/scala/org/soaringmeteo/arome/Main.scala`
   - Wrap AromeData avec AromeMeteoDataAdapter avant Raster/VectorTiles

3. **Tests AROME complets**
   - Lancer avec vraies données GRIB
   - Vérifier PNG + MVT générés

### Phase 3 (Priorité Moyenne)
1. Documenter usage pour frontend
2. Ajouter logging/monitoring
3. Tests unitaires MeteoData adapters

### Phase 4 (Futur)
1. Adapter ICON (DWD)
2. Adapter ARPEGE (Météo-France)
3. Comparaisons multi-modèles

## Ressources

- [GeoTrellis Docs](https://github.com/locationtech/geotrellis/wiki)
- [Proj4j GitHub](https://github.com/locationtech/proj4j)
- [Squants](https://www.squants.com/)
- [MVT Spec](https://github.com/mapbox/vector-tile-spec)

## Auteur & Date
Claude (Anthropic) - 18 oct 2025
