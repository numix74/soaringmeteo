# GPU Gratuits pour Meso-NH - Analyse de pertinence

**Date** : 19 novembre 2025
**Source** : Medium - "Free GPU using VS Code"
**Contexte** : Complémentarité avec Meso-NH pour SoaringMeteo

---

## 📋 Résumé exécutif

**Question** : Les GPU gratuits (Colab/Kaggle/Paperspace) sont-ils pertinents pour Meso-NH ?

**Réponse** : ⚠️ **OUI pour R&D/apprentissage, NON pour production**

### Verdict rapide

| Critère | Pertinence | Note |
|---------|-----------|------|
| **Apprentissage Meso-NH** | ✅ Excellent | ⭐⭐⭐⭐⭐ |
| **Tests petits cas** | ✅ Bon | ⭐⭐⭐⭐ |
| **Compilation** | ⚠️ Possible mais limité | ⭐⭐⭐ |
| **Production LES** | ❌ Insuffisant | ⭐ |
| **Coût** | ✅ Gratuit ! | ⭐⭐⭐⭐⭐ |

---

## 🎯 Services GPU gratuits disponibles (2024-2025)

### 1. **Google Colab** (⭐ NOUVEAU : Intégration VS Code officielle)

**Novembre 2025** : Google a lancé une extension officielle VS Code pour Colab !

#### Spécifications

| Paramètre | Gratuit | Pro | Pro+ |
|-----------|---------|-----|------|
| **GPU** | NVIDIA T4 | T4/A100 | A100 prioritaire |
| **VRAM** | 15 GB | 15-40 GB | 40 GB |
| **RAM** | 12 GB | 25 GB | 50 GB |
| **Stockage** | 15 GB (Drive) | 100 GB | 200 GB |
| **Prix** | Gratuit | $9.99/mois | $49.99/mois |
| **Limites session** | ~12h | ~24h | ~24h |
| **Compute Units** | Limité | 100 CU | Plus élevé |

#### Nouveauté 2025 : Extension VS Code

```bash
# Installation
1. Installer extension "Colab" dans VS Code Marketplace
2. Se connecter avec compte Google
3. Sélectionner "Colab" dans le dropdown kernel
4. Pull GPU T4 gratuit directement dans VS Code !

# Avantages
✅ IntelliSense local
✅ Extensions VS Code
✅ Debugging tools
✅ Git intégration
✅ Terminaux multiples
```

**Calcul Compute Units** :
- T4 : ~11.7 CU/heure → ~8.5h avec 100 CU
- A100 : ~62 CU/heure → ~1.6h avec 100 CU

### 2. **Kaggle**

#### Spécifications

| Paramètre | Valeur |
|-----------|--------|
| **GPU** | T4 ou P100 (variable) |
| **VRAM** | 16 GB (T4) ou 16 GB (P100) |
| **RAM** | 29 GB |
| **CPU** | 4 cœurs |
| **Stockage** | 20 GB temporaire |
| **Quota** | **30 GPU-heures/semaine** |
| **Session max** | 9 heures |
| **Background** | ✅ Continue après fermeture |

#### Avantages Kaggle

- **Quota généreux** : 30h GPU/semaine (meilleur gratuit disponible)
- **Dual T4 beta** : Training distribué possible
- **Stable** : Moins de déconnexions que Colab
- **Datasets** : Accès direct à datasets publics

### 3. **Paperspace Gradient** (Free tier)

#### Spécifications

| Paramètre | Valeur |
|-----------|--------|
| **GPU** | M4000 (8 GB VRAM) |
| **RAM** | 30 GB |
| **Stockage** | 5 GB |
| **Session max** | 6 heures |
| **Privacy** | ⚠️ Notebooks publics seulement |

#### Problèmes

- GPU M4000 **obsolète** (architecture Maxwell 2015)
- Disponibilité **très limitée** (DigitalOcean cost-cutting)
- Pas de notebooks privés en gratuit
- Pas recommandé pour 2025

---

## 🔬 Compatibilité avec Meso-NH

### Exigences Meso-NH (rappel)

D'après l'analyse précédente (`docs/MESONH.md`) :

#### Compilation

```yaml
Minimum:
  CPU: 16 cœurs
  RAM: 16 GB
  Temps: 20-30 minutes (1 cœur), 5-10 min (8 cœurs)

Recommandé:
  CPU: 16-32 cœurs
  RAM: 32 GB
```

#### Runtime (CPU)

| Configuration | Domaine | Résolution | CPU | RAM | Temps |
|--------------|---------|------------|-----|-----|-------|
| Petit | 100×100 km | 1 km | 16-32 | 32 GB | 3h/24h |
| Moyen | 200×200 km | 500 m | 64-128 | 128 GB | 6h/24h |
| LES | 100×100 km | 100 m | 256-512 | 256 GB | 12h/6h |

#### Runtime (GPU) - Meso-NH v5.5 OpenACC

| Plateforme | GPU | Speedup | Production |
|-----------|-----|---------|-----------|
| Adastra | AMD MI250X | 6.0× | ✅ |
| Leonardo | NVIDIA A100 | 4.6× | ✅ |
| **Colab** | NVIDIA T4 | ~2-3× | ⚠️ Tests |

### Comparaison GPU

| GPU | Architecture | VRAM | TFLOPS FP32 | TFLOPS FP64 | Prix |
|-----|-------------|------|-------------|-------------|------|
| **A100** | Ampere | 40-80 GB | 19.5 | 9.7 | $3/h |
| **T4** | Turing | 15 GB | 8.1 | 0.25 | Gratuit ! |
| **P100** | Pascal | 16 GB | 9.3 | 4.7 | Gratuit ! |
| MI250X | CDNA2 | 128 GB | 47.9 | 47.9 | $4/h |

### OpenACC sur GPU gratuits

**Compilation Meso-NH avec OpenACC** nécessite :

```bash
# Compilateur requis
NVIDIA HPC SDK (nvfortran)
# OU
GNU gfortran 13+ avec OpenACC

# Flags
-acc -gpu=cc70 (T4) ou cc60 (P100)
```

✅ **COMPATIBLE** : T4 et P100 supportent OpenACC
⚠️ **LIMITATION** : Performances réduites vs A100

---

## 🎯 Scénarios d'utilisation

### ✅ Scénario 1 : Apprentissage & Formation

**Cas d'usage** : Apprendre Meso-NH sans infrastructure

```yaml
Plateforme: Google Colab Pro (10$/mois) ou Kaggle (gratuit)
GPU: T4
Durée: 1-3 mois

Activités:
  ✅ Compiler Meso-NH (possible en 30 min sur Colab)
  ✅ Lancer cas KTEST académiques (REUNION, ARMCU, etc.)
  ✅ Tester configurations namelists
  ✅ Apprendre post-traitement NetCDF
  ✅ Valider installation avant HPC

Avantages:
  ✅ Coût zéro (ou 10$/mois)
  ✅ Accès immédiat
  ✅ VS Code intégration
  ✅ Risque faible

Limitations:
  ⚠️ Sessions limitées (9-12h)
  ⚠️ Domaines petits seulement
  ⚠️ Pas de simulation production
```

**Verdict** : ⭐⭐⭐⭐⭐ **Excellent pour démarrer**

### ✅ Scénario 2 : Tests & Prototypage

**Cas d'usage** : Tester configuration avant déploiement HPC

```yaml
Plateforme: Kaggle (30h/semaine gratuit)
GPU: P100
Domaine: 50×50 km
Résolution: 500 m
Prévision: 6 heures

Objectif:
  ✅ Valider pipeline GRIB → Meso-NH
  ✅ Tester schémas physiques (EDMF, ICE3)
  ✅ Debug namelists
  ✅ Extraire variables pour SoaringMeteo
  ✅ Benchmark avant HPC

Temps estimé:
  Compilation: 1h (première fois)
  Simulation: 2-4h
  Post-processing: 30 min
  Total: ~5h → 6 tests/semaine

Kaggle quota: 30h/semaine = 6 simulations complètes
```

**Verdict** : ⭐⭐⭐⭐ **Très bon pour R&D**

### ⚠️ Scénario 3 : LES basse résolution

**Cas d'usage** : Simulation LES minimale

```yaml
Plateforme: Google Colab Pro ($10/mois)
GPU: T4
Domaine: 20×20 km (petit !)
Résolution: 200 m (LES minimal)
Grille: 100×100×50 = 500k points
Prévision: 3 heures

Faisabilité:
  ⚠️ Mémoire GPU: 15 GB T4 - probablement suffisant
  ⚠️ Runtime: 4-8 heures - limite session 12h
  ⚠️ Résolution: 200 m = LES bas de gamme
  ❌ Speedup: T4 << A100 (3× vs 6×)

Conclusion:
  Possible MAIS résolution/domaine très limités
  Pas vraiment de LES "haute résolution"
```

**Verdict** : ⭐⭐ **Possible mais frustrant**

### ❌ Scénario 4 : Production LES haute résolution

**Cas d'usage** : LES 100 m pour SoaringMeteo (objectif final)

```yaml
Configuration souhaitée:
  Domaine: 100×100 km
  Résolution: 100 m
  Grille: 1000×1000×100 = 100M points
  Prévision: 12 heures

Exigences réelles:
  CPU: 256+ cœurs
  RAM: 256 GB
  GPU: 4× A100
  Runtime: 6-8 heures

GPU gratuits:
  ❌ T4: 15 GB VRAM insuffisant
  ❌ Session: 12h max insuffisant
  ❌ Puissance: T4 << A100
  ❌ Pas de multi-GPU
  ❌ Réseau: Pas d'infiniband MPI
```

**Verdict** : ⭐ **Impossible en gratuit**

---

## 💡 Recommandations pour SoaringMeteo

### Phase 1 : Apprentissage (0-3 mois) - ✅ GPU GRATUITS

**Objectif** : Se familiariser avec Meso-NH

```yaml
Budget: 0-30 $ (Colab Pro optionnel)

Action plan:
  1. Installer Meso-NH sur Google Colab via VS Code extension
  2. Compiler et tester 5 cas KTEST académiques
  3. Apprendre namelists et physique du modèle
  4. Tester post-traitement NetCDF → PNG/MVT
  5. Identifier paramètres pertinents pour parapente

Plateforme: Google Colab + VS Code extension (nouvelle)
Durée: 2-3 mois
Développeur: 1 personne (vous ou développeur)

Livrables:
  ✅ Expertise Meso-NH acquise
  ✅ Pipeline post-traitement prototypé
  ✅ Décision GO/NO-GO pour phase suivante
```

**Verdict** : ⭐⭐⭐⭐⭐ **FORTEMENT RECOMMANDÉ**

### Phase 2 : Prototypage (3-6 mois) - ⚠️ GPU GRATUITS + PETIT HPC

**Objectif** : Simuler cas réels petits domaines

```yaml
Budget: 30-100 $/mois

Approche hybride:
  - Kaggle (gratuit): Tests rapides (30h/semaine)
  - Colab Pro (10$/mois): Compilations et debugging
  - Cloud HPC (50$/mois): 1-2 simulations réelles/semaine

Configuration tests:
  Domaine: 50×50 km
  Résolution: 500 m
  GPU: P100 (Kaggle) pour tests
  CPU: 32 cœurs (cloud) pour simulations réelles

Objectif:
  ✅ Valider pipeline complet
  ✅ Comparer avec traces GPS parapente
  ✅ Benchmark performances
```

**Verdict** : ⭐⭐⭐⭐ **Bon compromis coût/résultat**

### Phase 3 : Production (6+ mois) - ❌ GPU GRATUITS INSUFFISANTS

**Objectif** : LES haute résolution opérationnel

```yaml
Budget: 500-2000 $/mois (cloud) ou 5000-10000 € achat serveur

Infrastructure requise:
  ❌ GPU gratuits: Totalement insuffisants
  ✅ HPC cloud: AWS/GCP avec A100
  ✅ Serveur dédié: AMD EPYC + 4× A100
  ✅ Partenariat académique: Accès IDRIS/CINES (France)

LES production:
  Domaine: 100×100 km
  Résolution: 100 m
  GPU: 4× A100 ou équivalent
  Coût: 3-4 $/heure GPU × 4 × 8h = 96-128 $/jour
```

**Verdict** : ❌ **HPC dédié obligatoire**

---

## 🎓 Guide pratique : Meso-NH sur Colab

### Installation Meso-NH sur Google Colab (2025)

```python
# Notebook Colab
# Cellule 1: Installation dépendances
!apt-get update
!apt-get install -y gfortran libopenmpi-dev openmpi-bin
!apt-get install -y libnetcdf-dev libnetcdff-dev
!pip install netCDF4

# Cellule 2: Cloner Meso-NH (si accès public possible)
# OU télécharger depuis Zenodo
!wget https://zenodo.org/records/15095131/files/MNH-V5-7-1.tar.gz
!tar -xzf MNH-V5-7-1.tar.gz

# Cellule 3: Compilation (30-45 min)
%cd MNH-V5-7-1/conf
!./configure
# Sélectionner:
# ARCH=LXgfortran
# VER_MPI=MPIAUTO
# OPTLEVEL=O2
# VER_CDF=CDFAUTO

!make -j4  # 4 cœurs Colab

# Cellule 4: Test cas académique
%cd ../MY_RUN/KTEST/001_Reunion
!./run_prep
!./run_mesonh

# Cellule 5: Visualisation
import xarray as xr
ds = xr.open_dataset('REUNI.1.001dg.000.nc')
print(ds)
```

### VS Code + Colab Extension (Méthode 2025)

```bash
# 1. Installer extension VS Code
Extensions → Rechercher "Colab" → Installer

# 2. Ouvrir notebook Jupyter local
File → New File → Jupyter Notebook

# 3. Sélectionner kernel
Clic sur "Select Kernel" → "Colab" → Se connecter

# 4. Vous avez maintenant GPU T4 dans VS Code !
# Test GPU
!nvidia-smi

# 5. Installer Meso-NH (voir script ci-dessus)
```

---

## 📊 Tableau comparatif final

| Critère | GPU Gratuits | Cloud HPC | Serveur dédié | Académique |
|---------|-------------|-----------|---------------|-----------|
| **Coût/mois** | 0-10 $ | 500-2000 $ | 400-800 $ (amortissement) | 0 $ |
| **GPU** | T4, P100 | A100, H100 | A100 | A100, MI250X |
| **Apprentissage** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Prototypage** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Production LES** | ⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Limites session** | 9-12h | Aucune | Aucune | Quotas |
| **Maintenance** | ✅ Zéro | ✅ Faible | ⚠️ Moyenne | ✅ Zéro |
| **Disponibilité** | ⚠️ Variable | ✅ Haute | ✅ Totale | ⚠️ File d'attente |

---

## ✅ Conclusion : Complémentarité avec Meso-NH

### Question 1 : Est-ce complémentaire ?

✅ **OUI, TRÈS COMPLÉMENTAIRE** pour phases initiales :

```
GPU Gratuits (Colab/Kaggle)
    ↓
Apprentissage Meso-NH (2-3 mois)
    ↓
Prototypage petits cas (1-2 mois)
    ↓
Validation décision GO/NO-GO
    ↓
SI GO → HPC dédié (production LES)
```

**Réduction risque** : Investir 0-30 $ avant 5000-10000 € HPC

### Question 2 : Est-ce efficient ?

✅ **OUI, TRÈS EFFICIENT** pour ROI :

| Phase | Sans GPU gratuit | Avec GPU gratuit | Économie |
|-------|-----------------|------------------|----------|
| Apprentissage | Cloud 100 $/mois × 3 = 300 $ | Colab 0-30 $ | **270 $** |
| Tests | Cloud 200 $/mois × 3 = 600 $ | Kaggle gratuit | **600 $** |
| Validation | Serveur 5000 € (risque élevé) | 30 $ → décision | **Risque réduit** |
| **Total** | 900 $ + risque | 30 $ + faible risque | **900 $** |

### Question 3 : Est-ce pertinent ?

✅ **OUI, ABSOLUMENT PERTINENT** comme tremplin :

**Pour SoaringMeteo** :
1. ✅ Tester Meso-NH **AVANT** d'investir massivement
2. ✅ Former développeur avec **coût quasi nul**
3. ✅ Valider pipeline complet (GRIB → NetCDF → PNG/MVT)
4. ✅ Prototyper intégration backend Scala
5. ✅ Décider GO/NO-GO avec données réelles

**Limitations claires** :
- ❌ **PAS** pour production LES haute résolution
- ❌ **PAS** pour domaines >50 km
- ❌ **PAS** pour prévisions opérationnelles

---

## 🎯 Plan d'action recommandé

### Semaine 1-4 : Exploration gratuite

```yaml
Budget: 0 $ (100% gratuit)
Plateforme: Google Colab (gratuit) + Kaggle

Actions:
  - Installer extension VS Code Colab
  - Compiler Meso-NH sur Colab
  - Lancer 3 cas KTEST
  - Se familiariser avec namelists

Temps: 20-30 heures développeur
```

### Mois 2-3 : Tests réels

```yaml
Budget: 10-30 $ (Colab Pro)
Plateforme: Colab Pro + Kaggle

Actions:
  - Simuler 1 cas réel passé (jour de vol connu)
  - Domaine 50×50 km, résolution 500 m
  - Comparer avec traces GPS
  - Extraire variables parapente

Temps: 40-60 heures
```

### Mois 4-6 : Décision

```yaml
Budget: 50-100 $
Plateforme: Colab Pro + location HPC ponctuelle

Actions:
  - Si tests concluants → 2-3 simulations LES basse résolution
  - Benchmark performances
  - Évaluation coût/bénéfice production
  - Décision GO/NO-GO

Décision:
  SI GO → Investir HPC (serveur ou cloud)
  SI NO GO → Rester WRF/AROME actuels
```

---

## 🔗 Ressources

### Extensions VS Code

- **Google Colab** : https://marketplace.visualstudio.com/items?itemName=ms-toolsai.vscode-jupyter-colab
- **Remote SSH** : Pour Kaggle/Paperspace (si besoin)

### Tutoriels

- **Colab + VS Code 2025** : https://developers.googleblog.com/en/google-colab-is-coming-to-vs-code/
- **Meso-NH installation** : docs/MESONH.md

### Alternatives payantes (si GPU gratuits insuffisants)

| Service | GPU | Prix/h | Pertinence Meso-NH |
|---------|-----|--------|-------------------|
| **Lambda Labs** | A100 | $1.29 | ⭐⭐⭐⭐⭐ |
| **Vast.ai** | A100 | $0.80-1.50 | ⭐⭐⭐⭐⭐ |
| **RunPod** | A100 | $1.39 | ⭐⭐⭐⭐ |
| **AWS EC2** | A100 | $3.06 | ⭐⭐⭐ |
| **GCP** | A100 | $2.93 | ⭐⭐⭐ |

**Recommandation** : Lambda Labs ou Vast.ai (meilleur prix/performance)

---

## 📝 Conclusion finale

### Réponse aux 3 questions

1. **Complémentaire ?** ✅ OUI - Excellent tremplin avant HPC
2. **Efficient ?** ✅ OUI - ROI exceptionnel (0-30 $ vs 900 $ cloud)
3. **Pertinent ?** ✅ OUI - Validation risque faible avant gros investissement

### Stratégie recommandée

```mermaid
GPU Gratuits (Colab/Kaggle)
    [0-3 mois, 0-30 $]
        ↓
    Apprentissage + Prototypage
        ↓
    Décision GO/NO-GO
        ↓
    ┌────────┴────────┐
    ↓                  ↓
  GO                  NO GO
    ↓                  ↓
HPC dédié          Garder WRF/AROME
[500-2000 $/mois]  [Status quo]
```

### Action immédiate

**COMMENCER DÈS MAINTENANT** :
1. Installer extension VS Code "Colab"
2. Tester compilation Meso-NH (1 journée)
3. Lancer cas KTEST (2 heures)
4. Décider en 1 semaine si continuer

**Coût** : 0 $ ✅
**Risque** : Quasi nul ✅
**Potentiel** : Immense ⭐⭐⭐⭐⭐

---

**Auteur** : Claude (Anthropic)
**Date** : 19 novembre 2025
**Licence** : CC BY-SA 4.0
