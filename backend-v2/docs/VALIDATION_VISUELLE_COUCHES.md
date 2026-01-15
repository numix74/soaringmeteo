# Validation Visuelle - Couches Restantes

**Date** : 31 décembre 2025
**Run** : 2025-12-31T00
**Résolution** : GFS grossière (32×7 points, 0.25°)

---

## 🎯 Objectif

Valider visuellement les **2 dernières couches** du backend-v2 :
1. `clouds-rain` (nuages et précipitations)
2. `xc-flying-potential` (potentiel de vol cross-country)

---

## 🖥️ Serveur de Validation

**URL** : http://51.38.221.186:5000/

**Script** : `/home/ubuntu/soaringmeteo/backend-v2/scripts/visual_validator.py`

### Fonctionnalités

- Comparaison côte à côte V1 vs V2
- Navigation temporelle (0 à 117h)
- Sélection de couche
- Indicateurs de disponibilité
- CSS optimisé : `image-rendering: pixelated` (pixels nets, pas de flou)

### Démarrage

```bash
cd /home/ubuntu/soaringmeteo/backend-v2/scripts
python3 visual_validator.py

# Accessible sur http://51.38.221.186:5000/
```

---

## 📊 Données Disponibles

### V1 (Production)

**Chemin** : `/home/ubuntu/soaringmeteo/output/7/gfs/2025-12-31T00/pyrenees/`

**Couches** :
- `clouds-rain` : 47 heures (102.png à 114.png par pas de 3h)
- `xc-potential` : 47 heures (note: nom différent de V2)

**Résolution** : 32×7 pixels (même que V2)

### V2 (Backend-v2)

**Chemin** : `/home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/gfs/2025-12-31T00/pyrenees/`

**Couches** :
- `clouds-rain` : 118 heures (0.png à 117.png, toutes les heures)
- `xc-flying-potential` : 118 heures (note: nom différent de V1)

**Résolution** : 32×7 pixels

**Mapping des noms** :
- V2 `xc-flying-potential` ↔ V1 `xc-potential`

---

## ✅ Critères de Validation

### 1. Couche `clouds-rain`

**À vérifier** :
- ✅ **Couleurs** : Palette identique à V1 (bleu pour pluie, gris pour nuages)
- ✅ **Distribution spatiale** : Zones de pluie/nuages au bon endroit (±27 km acceptable)
- ✅ **Évolution temporelle** : Cohérence dans le temps (déplacement fronts, dissipation)
- ✅ **Intensité** : Gradients de couleur cohérents avec V1

**Limitations résolution grossière** :
- ⚠️ Détails fins non visibles (32×7 = ~27 km/pixel)
- ⚠️ Contours imprécis (effet "pixelisé")
- ⚠️ Petites cellules convectives non représentées

### 2. Couche `xc-flying-potential`

**À vérifier** :
- ✅ **Couleurs** : Gradient rouge (mauvais) → vert (bon) → bleu (excellent)
- ✅ **Cohérence spatiale** : Zones favorables alignées avec relief et météo
- ✅ **Évolution temporelle** : Amélioration en journée, dégradation le soir
- ✅ **Corrélation** : Cohérence avec `thermal-velocity` et `soaring-layer-depth`

**Limitations résolution grossière** :
- ⚠️ Pas de détails vallée par vallée
- ⚠️ Effets orographiques locaux non capturés
- ⚠️ Zones de transition moins précises

---

## 📝 Procédure de Validation

### Étape 1 : Vérification Globale

1. Ouvrir http://51.38.221.186:5000/
2. Sélectionner `clouds-rain`
3. Parcourir heures 0 à 46 (plage commune V1/V2)
4. Vérifier cohérence visuelle grossière

### Étape 2 : Points Critiques

**Pour `clouds-rain`** :
- Heure 0 : État initial (nuages/pluie présents ?)
- Heure 12 : Développement diurne
- Heure 24 : Évolution à J+1
- Heure 46 : Fin de période commune V1/V2

**Pour `xc-flying-potential`** :
- Heure 6 : Début de journée (potentiel faible)
- Heure 12 : Maximum diurne (potentiel élevé ?)
- Heure 18 : Fin de journée (dégradation)
- Heure 24 : Nuit (potentiel nul)

### Étape 3 : Anomalies à Signaler

**Erreurs critiques (BLOQUER validation)** :
- ❌ Couleurs complètement différentes
- ❌ Décalage spatial > 2-3 pixels
- ❌ Évolution temporelle incohérente (sauts, inversions)
- ❌ Zones complètement absentes ou inversées

**Différences acceptables (NE PAS BLOQUER)** :
- ⚠️ Nuances de couleur légères (±5% acceptable)
- ⚠️ Différences < 1 pixel spatial (résolution grossière)
- ⚠️ Petites variations intensité (interpolation temporelle)
- ⚠️ Contours légèrement différents (effet pixelisation)

---

## 📋 Rapport de Validation (À Compléter)

### Couche `clouds-rain`

**Date validation** : _____________________

**Heures vérifiées** : _____________________

**Résultat global** :
- [ ] ✅ Validé - Cohérence acceptable
- [ ] ⚠️ Acceptable avec réserves (préciser)
- [ ] ❌ Rejeté - Erreurs critiques (préciser)

**Observations** :
```
_______________________________________________
_______________________________________________
_______________________________________________
```

**Captures d'écran** (si anomalies) :
```
_______________________________________________
```

---

### Couche `xc-flying-potential`

**Date validation** : _____________________

**Heures vérifiées** : _____________________

**Résultat global** :
- [ ] ✅ Validé - Cohérence acceptable
- [ ] ⚠️ Acceptable avec réserves (préciser)
- [ ] ❌ Rejeté - Erreurs critiques (préciser)

**Observations** :
```
_______________________________________________
_______________________________________________
_______________________________________________
```

**Captures d'écran** (si anomalies) :
```
_______________________________________________
```

---

## 🔧 Commandes Utiles

### Redémarrer le Serveur Flask

```bash
pkill -f visual_validator.py
cd /home/ubuntu/soaringmeteo/backend-v2/scripts
python3 visual_validator.py
```

### Vérifier Dimensions PNG

```bash
file /home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/gfs/2025-12-31T00/pyrenees/clouds-rain/0.png
# Attendu: PNG image data, 32 x 7, 8-bit/color RGBA
```

### Compter Fichiers Disponibles

```bash
# V1
ls /home/ubuntu/soaringmeteo/output/7/gfs/2025-12-31T00/pyrenees/clouds-rain/ | wc -l
ls /home/ubuntu/soaringmeteo/output/7/gfs/2025-12-31T00/pyrenees/xc-potential/ | wc -l

# V2
ls /home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/gfs/2025-12-31T00/pyrenees/clouds-rain/ | wc -l
ls /home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/gfs/2025-12-31T00/pyrenees/xc-flying-potential/ | wc -l
```

---

## 🎯 Prochaines Étapes Après Validation

### Si Validation Réussie ✅

1. Marquer Phase 5 comme **100% COMPLÈTE**
2. Documenter résultats dans TODOLIST.md
3. Préparer Phase 6 : AROME parser
4. (Optionnel) Commiter changements backend-v2

### Si Problèmes Détectés ⚠️

1. Documenter anomalies précises
2. Identifier cause (code, données, configuration)
3. Corriger et régénérer run 2025-12-31T00
4. Re-valider

### Migration Haute Résolution (Phase 6+)

Reportée à la refonte frontend :
- Voir `docs/RESOLUTION_CONFIGURATION.md`
- Activation : Modifier `unified.conf` (region, step)
- Impact : ×168 plus de points, ×30 taille fichiers
- Nécessite : Frontend adaptatif multi-résolution

---

## 📚 Références

- **TODOLIST.md** : `/home/ubuntu/.claude/plans/TODOLIST.md`
- **RESOLUTION_CONFIGURATION.md** : `/home/ubuntu/soaringmeteo/backend-v2/docs/RESOLUTION_CONFIGURATION.md`
- **visual_validator.py** : `/home/ubuntu/soaringmeteo/backend-v2/scripts/visual_validator.py`
- **Serveur validation** : http://51.38.221.186:5000/

---

**Maintenu par** : Claude Code
**Dernière mise à jour** : 31 décembre 2025
