# Guide de comparaison v1 vs v2

## 🎯 Deux approches disponibles

### Option 1 : Dual Frontend (recommandé pour test complet)

**Avantages** :
- Teste vraiment l'intégration frontend ↔ backend-v2
- Compare l'expérience utilisateur complète
- Voit animations, interactions, transitions

**Mise en place** :

```bash
# Terminal 1 - Frontend v1 (pointe sur outputs production)
cd /home/ubuntu/soaringmeteo/frontend
npm start
# → http://localhost:3000

# Terminal 2 - Frontend v2 (pointe sur outputs backend-v2)
cd /tmp/soaringmeteo-frontend-v2
npm start
# → http://localhost:3001
```

**Utilisation** :
1. Ouvrir deux fenêtres navigateur côte à côte
2. Gauche : http://localhost:3000 (v1)
3. Droite : http://localhost:3001 (v2)
4. Naviguer en parallèle (même zone, même heure, même couche)
5. Comparer visuellement

**Checklist de validation** :
- [ ] Carte s'affiche correctement
- [ ] Couleurs identiques/similaires
- [ ] Animation temporelle fonctionne (H+3 → H+120)
- [ ] Pas de trous/artefacts
- [ ] Valeurs numériques cohérentes
- [ ] Toutes les couches disponibles :
  - [ ] thermal-velocity
  - [ ] soaring-layer-depth
  - [ ] boundary-layer-depth
  - [ ] xc-flying-potential
  - [ ] clouds-rain
  - [ ] wind-surface
  - [ ] temperature-2m
  - [ ] dew-point-2m

---

### Option 2 : Comparaison PNG directe (recommandé pour analyse détaillée)

**Avantages** :
- Rapide (pas besoin de lancer le frontend)
- Génère images côte à côte automatiquement
- Facile à partager/archiver

**Utilisation** :

```bash
# Comparer une couche spécifique à une heure donnée
python3 /home/ubuntu/soaringmeteo/backend-v2/scripts/compare_outputs_visual.py \
  --layer thermal-velocity \
  --hour 6 \
  --v1-run 2025-12-30T00 \
  --v2-run 2025-12-30T00

# Output : /tmp/comparison_thermal-velocity_H006.png
```

**Exemples** :

```bash
# Comparer thermal-velocity H+6
python3 scripts/compare_outputs_visual.py --layer thermal-velocity --hour 6

# Comparer wind-surface H+12
python3 scripts/compare_outputs_visual.py --layer wind-surface --hour 12

# Comparer xc-flying-potential H+24
python3 scripts/compare_outputs_visual.py --layer xc-flying-potential --hour 24

# Spécifier output custom
python3 scripts/compare_outputs_visual.py \
  --layer thermal-velocity \
  --hour 6 \
  --output ~/comparisons/thermal_h6.png
```

**Batch comparison** (toutes les heures) :

```bash
#!/bin/bash
# Compare toutes les heures pour une couche donnée

LAYER="thermal-velocity"

for hour in {3..120..3}; do
    python3 scripts/compare_outputs_visual.py \
        --layer $LAYER \
        --hour $hour \
        --output /tmp/comparisons/${LAYER}_H$(printf "%03d" $hour).png
done

echo "✓ Comparaisons générées dans /tmp/comparisons/"
```

---

## 📋 Checklist de validation complète

### 1. Test fonctionnel (Option 1)
- [ ] Frontend v2 démarre sans erreur
- [ ] Carte s'affiche au chargement
- [ ] Sélection zone fonctionne
- [ ] Sélection couche fonctionne
- [ ] Animation temporelle fluide
- [ ] Météogramme s'affiche (location forecasts)
- [ ] Pas d'erreurs dans console navigateur

### 2. Comparaison visuelle (Option 1 ou 2)
- [ ] Couleurs identiques v1 vs v2
- [ ] Pas d'artefacts visuels
- [ ] Patterns météo cohérents
- [ ] Valeurs extrêmes similaires

### 3. Validation météorologique
- [ ] Thermal velocity : 0-5 m/s (valeurs réalistes)
- [ ] Boundary layer depth : 500-2500m (valeurs réalistes)
- [ ] Wind : directions cohérentes
- [ ] Température : valeurs saisonnières cohérentes
- [ ] Pas de discontinuités temporelles

### 4. Performance (si dual frontend)
- [ ] Temps de chargement comparable
- [ ] Fluidité animation comparable
- [ ] Consommation mémoire acceptable

---

## 🐛 Troubleshooting

### Frontend v2 ne démarre pas

```bash
# Vérifier dépendances
cd /tmp/soaringmeteo-frontend-v2
npm ci

# Réinstaller si nécessaire
rm -rf node_modules package-lock.json
npm install
```

### Outputs v2 introuvables

```bash
# Vérifier que le pipeline a bien généré les fichiers
ls /home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/gfs/2025-12-30T00/pyrenees/

# Relancer le pipeline si nécessaire
cd /home/ubuntu/soaringmeteo/backend-v2
sbt "app/run --model=gfs --init-time=2025-12-30T00:00Z"
```

### Script Python compare_outputs_visual.py échoue

```bash
# Installer dépendances Python
pip install Pillow

# Vérifier chemins
ls /home/ubuntu/soaringmeteo/output/7/gfs/2025-12-30T00/pyrenees/thermal-velocity/
ls /home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/gfs/2025-12-30T00/pyrenees/thermal-velocity/
```

---

## 📊 Rapport de validation attendu

Après tests, remplir ce template :

```markdown
# Validation backend-v2 Phase 5

**Date** : 2025-12-30
**Run testé** : 2025-12-30T00
**Zone** : pyrenees

## Tests fonctionnels

- Frontend v2 démarre : ✅/❌
- Cartes s'affichent : ✅/❌
- Animation temporelle : ✅/❌
- Météogramme : ✅/❌

## Comparaison visuelle

### thermal-velocity
- Couleurs : ✅ Identiques / ⚠️ Légèrement différentes / ❌ Très différentes
- Patterns : ✅ Cohérents / ❌ Incohérents
- Notes : ...

### wind-surface
- Couleurs : ✅/⚠️/❌
- Patterns : ✅/❌
- Notes : ...

[... autres couches]

## Validation météorologique

- Valeurs réalistes : ✅/❌
- Cohérence temporelle : ✅/❌
- Pas d'anomalies : ✅/❌
- Notes : ...

## Décision

✅ **GO** pour Phase 6
❌ **NO-GO** - Problèmes à résoudre :
  - ...
```

---

**Créé** : 30 décembre 2025  
**Maintenu par** : Claude Code
