# Configuration Résolution Backend-v2

**Date** : 31 décembre 2025
**Status** : Phase 5 - Configuration en résolution GFS grossière

---

## Situation Actuelle

### Résolution Active : GFS Grossière (32×7 points)

Le backend-v2 est actuellement configuré pour utiliser la **résolution native GFS** :

**Configuration** : `backend-v2/app/src/main/resources/unified.conf`

```hocon
region = "pyrenees-gfs"  # Zone GFS grossière
step = 0.25              # 0.25° ≈ 27 km
```

**Grille résultante** :
- Dimensions : 32 × 7 pixels
- Résolution : ~27 km par pixel
- Zone : Pyrénées (lon: -4.0 → 3.75, lat: 42.0 → 43.5)
- Nombre de points : 224 points

### Problème Identifié : Images Floues

**Cause** :
- Les PNG font 32×7 pixels
- Le navigateur les agrandit à 100% de la largeur de l'écran
- L'interpolation CSS par défaut crée un effet de flou

**Solution appliquée** :
```css
.version img {
    image-rendering: pixelated;
    image-rendering: -moz-crisp-edges;
    image-rendering: crisp-edges;
}
```

Cela désactive l'interpolation floue et affiche les pixels nets (style "pixel art").

**Serveur de validation** : http://51.38.221.186:5000/
- Script : `backend-v2/scripts/visual_validator.py`
- Couches validées : `clouds-rain`, `xc-flying-potential`

---

## Résolution Haute Disponible (Non Activée)

### Configuration Haute Résolution : 311×121 points

Pour activer la résolution fine (2.5 km, compatible AROME) :

**Modifier** : `backend-v2/app/src/main/resources/unified.conf`

```hocon
# Ligne 6 : Changer la région
region = "pyrenees"  # Au lieu de "pyrenees-gfs"

# Ligne 48 : Changer le pas de grille
step = 0.025         # Au lieu de 0.25

# Ligne 49 : Décommenter si nécessaire
# step = 0.025      # AROME resolution ~2.5 km (311×121 grid)
```

**Grille résultante** :
- Dimensions : 311 × 121 pixels
- Résolution : ~2.5 km par pixel
- Zone : Pyrénées (lon: -4.0 → 3.75, lat: 41.25 → 44.25)
- Nombre de points : 37,631 points (×168 plus de détails)

### Impacts du Passage en Haute Résolution

| Aspect | Résolution Grossière (32×7) | Résolution Fine (311×121) |
|--------|----------------------------|---------------------------|
| **Taille PNG** | 2-5 KB | 50-150 KB (×30) |
| **Temps génération** | ~10 secondes / 118h | ~2-3 minutes / 118h |
| **Détails météo** | Tendances régionales | Vallée par vallée |
| **Contours géo** | Pixelisés | Nets et précis |
| **Stockage total** | ~4 MB / run | ~120 MB / run (×30) |
| **Bande passante** | Faible | Moyenne |

### Compatibilité Frontend

**Frontend actuel (v1)** :
- Utilise probablement une résolution intermédiaire ou haute
- Les couches `thermal-velocity`, `soaring-layer-depth`, etc. s'affichent bien

**Impact d'une refonte frontend** :
- Supporter nativement les 2 résolutions (adaptative)
- Optimiser le chargement (lazy loading, progressive rendering)
- Ajouter un système de zoom/tiles pour haute résolution

---

## Décision Phase 5 : Validation en Résolution Grossière

**Décision** : Reporter l'activation de la haute résolution à la refonte frontend (Phase 6+)

**Raisons** :
1. ✅ La résolution grossière suffit pour valider la **logique de calcul**
2. ✅ Les couleurs et patterns régionaux sont vérifiables
3. ✅ Gain de temps (génération + validation rapide)
4. ✅ Économie de stockage pendant le développement
5. ⏳ La haute résolution nécessite une adaptation frontend

**Validation en cours** :
- `clouds-rain` : Distribution nuages/pluie (patterns régionaux)
- `xc-flying-potential` : Potentiel XC (gradients grossiers OK)

**Critères de validation** :
- ✅ Couleurs correctes (palette identique à v1)
- ✅ Zones de pluie/nuages au bon endroit (±27 km acceptable)
- ✅ Évolution temporelle cohérente
- ⚠️ Détails fins non vérifiables (reportés à Phase 6+)

---

## Plan de Migration vers Haute Résolution

### Phase 6+ : Activation Haute Résolution

**Prérequis** :
1. Refonte frontend avec support multi-résolution
2. Système de tiles/zoom pour grandes images
3. Optimisation bande passante (compression, lazy loading)
4. Tests de performance (temps de génération acceptable)

**Étapes** :
1. Modifier `unified.conf` : `region = "pyrenees"`, `step = 0.025`
2. Régénérer un run complet (118h) avec haute résolution
3. Mesurer performances (temps, stockage)
4. Valider visuellement les détails fins
5. Déployer frontend adapté
6. Migration progressive (coexistence v1/v2)

**Timeline** : À définir lors de la Phase 6

---

## Commandes Utiles

### Changer la Résolution

```bash
# Éditer la configuration
nano /home/ubuntu/soaringmeteo/backend-v2/app/src/main/resources/unified.conf

# Régénérer avec nouvelle résolution
cd /home/ubuntu/soaringmeteo/backend-v2
sbt "app/run --env=dev --init-time=2025-12-31T00:00Z"
```

### Vérifier la Résolution Active

```bash
# Vérifier dimensions PNG
file /home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/gfs/2025-12-31T00/pyrenees/clouds-rain/0.png

# Exemple sortie :
# 32 x 7 = résolution GFS grossière
# 311 x 121 = résolution haute (2.5 km)
```

### Lancer le Serveur de Validation

```bash
cd /home/ubuntu/soaringmeteo/backend-v2/scripts
python3 visual_validator.py

# Accessible sur http://51.38.221.186:5000/
```

---

## Références

- **GeographicZone.scala** : Définition des zones prédéfinies
  - `GeographicZone.PyreneesGFS` : 32×7 (0.25°)
  - `GeographicZone.Pyrenees` : 311×121 (0.025°)

- **unified.conf** : Configuration active

- **Visual Validator** : `backend-v2/scripts/visual_validator.py`

---

**Maintenu par** : Claude Code
**Dernière mise à jour** : 31 décembre 2025
