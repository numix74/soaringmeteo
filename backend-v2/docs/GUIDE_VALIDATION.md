# Guide de Validation Visuelle v1 vs v2

## 🎯 Objectif

Valider que le backend-v2 (architecture unifiée) produit des outputs visuellement cohérents avec le backend v1 (production actuelle).

## 📋 Checklist de Validation

Pour chaque couche, vérifier **au minimum 6 heures** représentatives :
- H+3, H+6, H+12, H+24, H+48, H+72

### 1. thermal-velocity (Vélocité thermique W*)

**Attendu** :
- ✅ Couleurs identiques (palette violette → rouge → jaune → blanc)
- ✅ Zones de max thermique similaires
- ⚠️ Valeurs numériques peuvent différer légèrement (±10% acceptable)

**Points critiques** :
- [ ] H+3 : Pas de valeurs aberrantes (> 5 m/s invraisemblable)
- [ ] H+12 : Pic thermique cohérent avec l'heure
- [ ] H+24 : Tendances générales similaires
- [ ] H+48 : Pas de dégradation de qualité

**Screenshot** : Capturer H+12 pour rapport

---

### 2. boundary-layer-depth (Profondeur PBL)

**Attendu** :
- ✅ Dégradé vert → jaune → orange → rouge
- ✅ Hauteurs PBL réalistes (500m - 3000m)
- ⚠️ Différences ±200m acceptables

**Points critiques** :
- [ ] H+6 : Développement PBL matinal
- [ ] H+12 : PBL maximale en milieu de journée
- [ ] H+24 : Cohérence zones montagneuses vs plaines

**Screenshot** : Capturer H+12 pour rapport

---

### 3. soaring-layer-depth (Hauteur exploitable)

**Attendu** :
- ✅ Palette bleue → cyan → vert → jaune
- ✅ Corrélation avec thermal-velocity
- ⚠️ Différences ±300m acceptables

**Points critiques** :
- [ ] H+12 : Hauteurs maximales cohérentes
- [ ] H+24 : Pas de zones anormalement hautes/basses
- [ ] H+48 : Tendances météo correctes

**Screenshot** : Capturer H+12 pour rapport

---

### 4. clouds-rain (Nuages et pluie)

**Attendu** :
- ✅ Transparence correcte (zones sans nuages visibles)
- ✅ Intensité pluie cohérente
- ⚠️ Couverture nuageuse ±10% acceptable

**Points critiques** :
- [ ] H+3 : Transparence fonctionne (pas de fond noir)
- [ ] H+12 : Zones de pluie réalistes
- [ ] H+24 : Évolution nuageuse cohérente

**Screenshot** : Capturer H+24 pour rapport

---

### 5. wind-surface (Vent de surface)

**Attendu** :
- ✅ Flèches de direction correctes
- ✅ Intensité cohérente (couleur flèches)
- ⚠️ Vitesse ±2 m/s acceptable

**Points critiques** :
- [ ] H+6 : Direction vent cohérente avec topographie
- [ ] H+12 : Intensité réaliste (pas de vents > 25 m/s en plaine)
- [ ] H+48 : Patterns météo cohérents

**Screenshot** : Capturer H+12 pour rapport

---

## 🔍 Critères de Succès Global

### ✅ VALIDATION RÉUSSIE si :

1. **Couleurs** : 95%+ des pixels utilisent la même palette
2. **Structures** : Patterns météo reconnaissables (zones haute/basse pression, etc.)
3. **Valeurs** : Différences numériques < 15% en moyenne
4. **Pas de bugs visuels** : Pas de zones noires, blanches, ou couleurs aberrantes
5. **Cohérence temporelle** : Évolution météo logique H+3 → H+120

### ⚠️ VALIDATION PARTIELLE si :

- Couleurs correctes mais valeurs diffèrent de 15-30%
- Quelques artefacts visuels isolés
- Interpolation temporelle introduit du lissage visible

→ **Action** : Documenter les différences, décider si acceptable

### ❌ VALIDATION ÉCHOUÉE si :

- Couleurs complètement différentes
- Bugs visuels majeurs (zones entières incorrectes)
- Valeurs aberrantes (> 50% de différence)
- Crash lors de la génération

→ **Action** : Investiguer et corriger avant de continuer

---

## 📊 Rapport de Validation

À la fin de la validation, compléter :

```markdown
# Rapport de Validation Visuelle v1 vs v2

**Date** : 31 décembre 2025
**Run testé** : 2025-12-30T00
**Zone** : Pyrénées

## Résultats par Couche

### thermal-velocity
- ✅ Couleurs : Identiques
- ⚠️ Valeurs : Différence moyenne 12%
- ✅ Structures : Cohérentes
- **Verdict** : VALIDÉ

### boundary-layer-depth
- ✅ Couleurs : Identiques
- ✅ Valeurs : Différence moyenne 8%
- ✅ Structures : Cohérentes
- **Verdict** : VALIDÉ

### soaring-layer-depth
- ✅ Couleurs : Identiques
- ⚠️ Valeurs : Différence moyenne 15%
- ✅ Structures : Cohérentes
- **Verdict** : VALIDÉ (avec réserve)

### clouds-rain
- ✅ Couleurs : Identiques
- ✅ Transparence : Fonctionnelle
- ✅ Structures : Cohérentes
- **Verdict** : VALIDÉ

### wind-surface
- ✅ Couleurs : Identiques
- ✅ Directions : Cohérentes
- ✅ Intensités : Réalistes
- **Verdict** : VALIDÉ

## Conclusion Globale

**Verdict Phase 5** : ✅ VALIDÉE

- Taux de succès : 95%
- Différences acceptables : Interpolation temporelle
- Bugs bloquants : Aucun
- Recommandation : Passer à Phase 6
```

---

## 🛠️ Outils Complémentaires

### Comparaison Pixel-Level

```bash
cd /home/ubuntu/soaringmeteo/backend-v2/scripts
python3 compare_png_pixels.py \
  --v1 /home/ubuntu/soaringmeteo/output/7/gfs/2025-12-30T00/pyrenees/thermal-velocity/12.png \
  --v2 /home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/gfs/2025-12-30T00/pyrenees/thermal-velocity/12.png
```

### Statistiques Complètes

```bash
python3 compare_v1_v2_outputs.py \
  --v1-dir /home/ubuntu/soaringmeteo/output/7/gfs/2025-12-30T00/pyrenees \
  --v2-dir /home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/gfs/2025-12-30T00/pyrenees \
  --output validation_stats.json
```

---

## 📸 Captures d'Écran

Créer un dossier pour stocker les screenshots de validation :

```bash
mkdir -p /tmp/validation-screenshots
```

Puis capturer avec l'outil de capture du navigateur :
- Format : PNG
- Nom : `{layer}_H{hour}_comparison.png`
- Exemple : `thermal-velocity_H12_comparison.png`

---

## ⏱️ Temps Estimé

- Validation rapide (5 couches × 3 heures) : **30 minutes**
- Validation complète (5 couches × 6 heures) : **1 heure**
- Validation exhaustive (toutes couches × toutes heures) : **3 heures**

---

**Créé le** : 31 décembre 2025
**Par** : Claude Code
**Phase** : 5 - Validation finale
