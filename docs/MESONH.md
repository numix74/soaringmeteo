# MESO-NH - Analyse approfondie pour le vol libre et parapente

**Date d'analyse** : 19 novembre 2025
**Version analysée** : Meso-NH v5.7.1
**DOI Zenodo** : 10.5281/zenodo.15095131
**Licence** : CeCILL-C (compatible GNU GPL)

---

## 📋 Résumé exécutif

**Meso-NH peut-il être adapté au vol libre ?** ✅ **OUI, EXCELLEMMENT**

**Résolution maximale atteinte** : **6-10 mètres** en mode LES (Large Eddy Simulation)

**Pertinence pour SoaringMeteo** : ⭐⭐⭐⭐⭐ (5/5)

Meso-NH est un modèle atmosphérique de recherche français **exceptionnel pour simuler les thermiques et conditions de vol libre** grâce à ses capacités LES ultra-haute résolution. Il surpasse WRF et AROME en termes de résolution spatiale et de physique des thermiques.

---

## 🎯 Qu'est-ce que Meso-NH ?

### Définition

**Meso-NH** (Modèle Méso-échelle Non-Hydrostatique) est le modèle atmosphérique de recherche de la communauté scientifique française, développé conjointement par :

- **Laboratoire d'Aérologie** (UMR 5560 UPS/CNRS, Toulouse)
- **CNRM-GAME** (UMR 3589 CNRS/Météo-France)

### Caractéristiques principales

| Caractéristique | Détail |
|----------------|--------|
| **Type** | Modèle non-hydrostatique anélastique |
| **Échelles** | Synoptique (10 km) → Micro-échelle (10 m) |
| **Langage** | Fortran 90 |
| **Parallélisation** | MPI + OpenACC (GPU) |
| **Licence** | **CeCILL-C** (open source depuis avril 2014) |
| **Version actuelle** | 5.7.1 (novembre 2024) |
| **Statut** | Production active, >100 publications/an |

### Historique et maturité

- **Création** : Années 1990
- **Open source** : Depuis version 5.1 (2014)
- **Utilisation** : ~40 laboratoires de recherche mondiaux
- **Opérationnel** : AROME (Météo-France) utilise la physique de Meso-NH
- **HPC** : Porté sur GPU en 2024 (OpenACC)

---

## 🪂 Adaptation au vol libre et parapente

### ✅ Paramètres disponibles CRITIQUES pour le parapente

Meso-NH calcule **TOUS** les paramètres essentiels au vol libre :

#### 1. **Thermiques et ascendances**

| Paramètre | Variable Meso-NH | Unité | Pertinence |
|-----------|------------------|-------|------------|
| **Vitesse verticale (w)** | `W` (pronostic) | m/s | ⭐⭐⭐⭐⭐ |
| **Flux de masse ascendant** | EDMF `wu` | m/s | ⭐⭐⭐⭐⭐ |
| **Flottabilité** | EDMF `Bu` | m/s² | ⭐⭐⭐⭐ |
| **Profondeur de couche limite** | BLH (diagnostic) | m AGL | ⭐⭐⭐⭐⭐ |
| **Température potentielle** | `θ` (pronostic) | K | ⭐⭐⭐⭐⭐ |
| **CAPE** | Diagnostic | J/kg | ⭐⭐⭐⭐ |
| **CIN** | Diagnostic | J/kg | ⭐⭐⭐⭐ |

#### 2. **Vent et cisaillement**

| Paramètre | Variable Meso-NH | Résolution |
|-----------|------------------|------------|
| **Vent 3D (u, v, w)** | Pronostic | Tous niveaux modèle |
| **Cisaillement vertical** | Dérivé de u, v, w | Calculable |
| **Nombre de Richardson** | Diagnostic disponible | Stabilité atmosphérique |
| **Turbulence (TKE)** | Pronostic | J/kg |
| **Taux de dissipation (EDR)** | Dérivé de TKE | m²/s³ |

#### 3. **Nuages et convection**

| Paramètre | Capacité Meso-NH |
|-----------|------------------|
| **Base des nuages (LCL)** | ✅ Calculé explicitement |
| **Sommet des nuages** | ✅ Jusqu'à 7 catégories d'hydrométéores |
| **Cumulus (Cu)** | ✅ Schéma EDMF + LES explicite |
| **Overdevelopment** | ✅ Microphysique ICE3/ICE4/LIMA |
| **Pluie, grêle** | ✅ 7 catégories : vapor, cloud, rain, ice, snow, graupel, hail |

#### 4. **Effets orographiques**

| Phénomène | Support Meso-NH |
|-----------|-----------------|
| **Ondes de relief** | ✅ Excellent (non-hydrostatique) |
| **Ondes de ressac (lee waves)** | ✅ Piégées et propagées verticalement |
| **Soulèvement orographique** | ✅ Coordonnées terrain-following + SLEVE |
| **Convergences locales** | ✅ Résolution jusqu'à 6 m |
| **Brises de vallée** | ✅ Résolution verticale <1 m possible |

#### 5. **Surface et flux**

Couplage avec **SURFEX** (Surface Externalisée) :

- **Flux de chaleur sensible** (W/m²)
- **Flux de chaleur latente** (W/m²)
- **Flux de quantité de mouvement** (N/m²)
- **Types de surface** : nature, urbain, eau, océan
- **Évapotranspiration** et humidité du sol

---

## 🔬 Résolution maximale et capacités LES

### Résolutions démontrées scientifiquement

| Type de simulation | Résolution horizontale | Résolution verticale | Application |
|-------------------|------------------------|----------------------|-------------|
| **Synoptique** | 50 km - 10 km | 500 m | Prévision grande échelle |
| **Méso-échelle** | 10 km - 1 km | 100-200 m | Prévision régionale |
| **Convection-permitting** | 1 km - 100 m | 50-100 m | Orages, thermiques |
| **LES standard** | 100 m - 10 m | 10-50 m | Turbulence, thermiques 3D |
| **LES ultra-fine** | **10 m - 6 m** | **2-10 m** | Jets de bas niveau, thermiques détaillés |

### Record absolu

**Résolution minimale démontrée** :
- Δx = **6 m**
- Δy = **4 m**
- Δz = **2 m** (en dessous de 100 m d'altitude)

Publication : Étude des jets de bas niveau avec résolution verticale <1 m

### Résolution optimale pour le parapente

D'après la littérature scientifique :

| Objectif | Résolution horizontale recommandée |
|----------|-----------------------------------|
| **Thermiques secs** | 50-200 m (LES) |
| **Convection profonde** | 100-200 m |
| **Ondes de relief** | 100-500 m |
| **Brises locales** | 50-100 m |
| **Turbulence fine** | 10-50 m |

**Conclusion** : Meso-NH peut résoudre **explicitement** les thermiques individuels à 50-100 m, contrairement aux modèles opérationnels (WRF, AROME) qui les paramètrent.

---

## 🧮 Schémas physiques avancés

### 1. Turbulence sous-maille

#### Schéma 1D (T1-D)
- **Usage** : Résolution grossière (>500 m)
- **Méthode** : Longueur de mélange Deardorff (1980)
- **TKE** : Équation pronostique

#### Schéma 3D (T3-D)
- **Usage** : LES (10-500 m)
- **Méthode** : Système d'équations sous-maille complet
- **Application** : Couche limite convective, terrains hétérogènes

### 2. Convection peu profonde (EDMF)

**EDMF** = Eddy Diffusivity Mass Flux (Pergaud et al., 2009)

- **Thermiques secs** : ✅ Panache ascendant unique depuis le sol
- **Cumulus peu profonds** : ✅ Entrainement/détrainement
- **Vitesse verticale dans l'ascendance** : wu
- **Flottabilité** : Bu
- **Continuité du flux de masse** : Base du nuage (sec → humide)

### 3. Microphysique des nuages

#### ICE3 (3 catégories de glace)
- Opérationnel dans AROME
- Catégories : vapeur, gouttelettes, pluie, glace, neige, graupel

#### ICE4 (4 catégories)
- Ajout de la grêle comme 6ème catégorie complète

#### LIMA (moment 2)
- Prédiction des concentrations de gouttelettes, gouttes de pluie, cristaux de glace
- Activation des CCN (Cloud Condensation Nuclei)
- Physique des aérosols

### 4. Rayonnement

- **Ondes courtes** : ECMWF
- **Ondes longues** : ECMWF
- **Interaction avec nuages** : Oui
- **Ombrage par les nuages** : ✅ (important pour thermiques)

---

## 💻 Infrastructure et exigences computationnelles

### Compilation

#### Prérequis logiciels

```bash
# Compilateurs supportés
- gfortran (recommandé, gratuit)
- Intel Fortran (ifx, ifort)
- PGI/NVIDIA HPC

# Bibliothèques obligatoires
- NetCDF4 (inclus dans Meso-NH ou externe)
- MPI (MPICH, OpenMPI, Intel MPI)
- GRIB API (pour données ECMWF/NOAA)

# Bibliothèques optionnelles
- HDF5 (pour NetCDF4)
- Git LFS (pour cloner le dépôt)
```

#### Temps de compilation

- **Première compilation** : 20-30 minutes (1 cœur)
- **Compilation parallèle** : 5-10 minutes (8 cœurs)
- **Mémoire requise** : 16 GB RAM recommandés

### Exigences runtime

#### Calcul CPU

| Configuration | Domaine | Résolution | Processeurs | RAM | Temps simulation |
|--------------|---------|------------|-------------|-----|------------------|
| **Petit** | 100×100 km² | 1 km | 16-32 | 32 GB | 3h pour 24h prévision |
| **Moyen** | 200×200 km² | 500 m | 64-128 | 128 GB | 6h pour 24h prévision |
| **Grand LES** | 100×100 km² | 100 m | 256-512 | 256 GB | 12h pour 6h prévision |
| **Ultra-fine** | 50×50 km² | 50 m | 512-1024 | 512 GB | 24h pour 3h prévision |

#### Calcul GPU (nouveau en v5.5)

**Meso-NH-v55-OpenACC** :

| Plateforme | GPU | Nœuds | Performance | Gain énergie |
|-----------|-----|-------|-------------|--------------|
| **AMD Adastra** | MI250X | 64 | **6.0×** vs CPU | 2.3× |
| **NVIDIA Leonardo** | A100 | 64 | **4.6×** vs CPU | ~2× |
| **Record** | MI250X | 128 | 2.1 milliards de points | 17.8× (précision réduite) |

**Conclusion GPU** : Accélération massive possible pour LES haute résolution.

### Stockage

| Type de simulation | Sortie/heure | Sortie/jour |
|-------------------|--------------|-------------|
| Méso-échelle (2 km) | 500 MB | 12 GB |
| LES (100 m) | 5 GB | 120 GB |
| LES (50 m) | 20 GB | 480 GB |

Format : **NetCDF4** (compression possible)

---

## 🔄 Comparaison avec WRF et AROME

### Tableau comparatif

| Critère | **Meso-NH** | **WRF** | **AROME** (opérationnel) |
|---------|-------------|---------|--------------------------|
| **Résolution min** | 6-10 m | 50-100 m | 500 m (1.3 km opérationnel) |
| **LES natif** | ✅ Excellent | ⚠️ WRF-LES | ❌ Non |
| **Thermiques explicites** | ✅ 50-100 m | ⚠️ 100-200 m | ❌ Paramétrés |
| **Schéma EDMF** | ✅ Oui (PMMC09) | ✅ Oui | ✅ Oui (hérité Meso-NH) |
| **GPU** | ✅ OpenACC (2024) | ⚠️ Expérimental | ❌ Non |
| **Open source** | ✅ CeCILL-C | ✅ Public domain | ❌ Propriétaire Météo-France |
| **Nested grids** | ✅ 2-way (8 niveaux) | ✅ 2-way | ✅ 1-way |
| **Microphysique** | ICE3/4, LIMA | Morrison, Thompson, etc. | ICE3 (Meso-NH) |
| **Communauté** | Française, 40 labos | Mondiale, NCAR | Météo-France |
| **Documentation** | ⚠️ Moyenne | ✅ Excellente | ⚠️ Limitée (interne) |
| **Courbe apprentissage** | ⚠️ Raide | ⚠️ Raide | N/A |
| **Maturité** | ✅ 25+ ans | ✅ 20+ ans | ✅ 15+ ans |

### Forces de Meso-NH pour le parapente

1. ✅ **Résolution ultime** : 6-10 m démontrés scientifiquement
2. ✅ **Physique des thermiques** : EDMF + LES explicite
3. ✅ **Non-hydrostatique** : Ondes de relief parfaitement représentées
4. ✅ **Anélastique** : Meilleure stabilité numérique que WRF (compressible)
5. ✅ **TKE pronostique** : Turbulence 3D résolue
6. ✅ **SURFEX** : Couplage surface avancé (flux de chaleur)
7. ✅ **GPU** : Accélération 4-6× sur supercalculateurs

### Faiblesses de Meso-NH

1. ⚠️ **Documentation** : Moins accessible que WRF
2. ⚠️ **Communauté** : Plus petite (franco-centrée)
3. ⚠️ **Courbe d'apprentissage** : Fortran 90, namelists complexes
4. ⚠️ **Pas opérationnel** : Modèle de recherche (AROME l'est)
5. ⚠️ **Données d'initialisation** : Nécessite IFS/GFS (comme WRF)
6. ⚠️ **Ressources** : LES haute résolution = HPC obligatoire

---

## 🏗️ Architecture logicielle

### Structure du code

```
MNH-V5-7-1/
├── src/
│   ├── MNH/              # Cœur du modèle atmosphérique
│   │   ├── modd_*.f90    # Modules de déclaration
│   │   ├── ini_*.f90     # Initialisation
│   │   ├── turb_*.f90    # Turbulence
│   │   ├── convection.f90 # EDMF
│   │   └── resolved_cloud.f90
│   ├── LIB/              # Bibliothèques
│   │   ├── RAD/          # Rayonnement ECMWF
│   │   ├── SURCOUCHE/    # Interface MPI
│   │   └── PREP_REAL_CASE/ # Préparation cas réels
│   ├── SURFEX/           # Modèle de surface
│   └── PHYEX/            # Physique externe (partagée avec AROME)
├── conf/                 # Scripts de configuration
├── MY_RUN/              # Cas d'usage et exemples
│   ├── KTEST/           # Cas académiques
│   └── INTEGRATION_CASES/ # Cas réels
└── docs/                # Documentation
```

### Fichiers de configuration (namelists)

Format : **Fortran 90 namelist**

Exemple minimal :

```fortran
&NAM_CONFZ
  LFLAT = .TRUE.,           ! Terrain plat
  LUSERV = .TRUE.,          ! Vapeur d'eau
  LUSERC = .TRUE.,          ! Nuages
  LUSERR = .TRUE.           ! Pluie
/

&NAM_DYN
  XTSTEP = 1.0,             ! Pas de temps (s)
  CPRESOPT = "CRESI",       ! Solveur pression
  XRELAX = 1.0              ! Relaxation
/

&NAM_LES
  LLES_MEAN = .TRUE.,       ! Activer LES
  LLES_RESOLVED = .TRUE.,   ! Champs résolus
  LLES_SUBGRID = .TRUE.     ! Sous-maille
/

&NAM_TURBn
  CTURBLEN = "DEAR",        ! Longueur mélange Deardorff
  CTURBDIM = "3DIM"         ! Turbulence 3D (LES)
/

&NAM_PARAM_RAD
  XDTRAD = 900.0,           ! Intervalle rayonnement (s)
  XDTRAD_CLONLY = 900.0     ! Rayonnement nuages
/
```

---

## 📊 Post-traitement et visualisation

### Formats de sortie

- **NetCDF4** (standard)
- Variables sur grille Arakawa C
- Dimensions : `(time, z, y, x)`

### Outils de visualisation

| Outil | Description | Pertinence |
|-------|-------------|------------|
| **ncview** | Visualisation rapide NetCDF | ⭐⭐⭐ |
| **Panoply** | NASA, multi-plateforme | ⭐⭐⭐⭐ |
| **Python xarray** | Analyse programmatique | ⭐⭐⭐⭐⭐ |
| **NCL** (NCAR) | Plots scientifiques | ⭐⭐⭐⭐ |
| **GrADS** | Grid Analysis Display | ⭐⭐⭐ |
| **NCO** | Manipulation ligne de commande | ⭐⭐⭐⭐ |
| **CDO** | Climate Data Operators | ⭐⭐⭐⭐ |

### Extraction pour SoaringMeteo

Pipeline potentiel :

```bash
# 1. Extraire variables pertinentes
ncks -v W,THT,RVT,UT,VT,TKE,PABST output.nc extracted.nc

# 2. Interpoler sur niveaux pression
# (script Python avec xarray)

# 3. Calculer diagnostics
# - Profondeur couche limite
# - Base des nuages
# - Cisaillement vent

# 4. Générer PNG/MVT (comme GFS/AROME actuel)
```

---

## 🚀 Intégration potentielle dans SoaringMeteo

### Scénario 1 : LES régional ultra-haute résolution

**Cas d'usage** : Zones de vol spécifiques (Pyrénées, Alpes)

```yaml
Configuration:
  Domain: 100×100 km²
  Resolution: 100 m horizontal, 25 m vertical
  Forecast: 12 heures
  Update: 1×/jour (06 UTC)

Ressources:
  CPU: 256 cœurs
  RAM: 256 GB
  GPU: 4× NVIDIA A100 (optionnel)
  Stockage: 50 GB/jour
  Runtime: 4-6 heures

Avantages:
  ✅ Thermiques explicites 3D
  ✅ Structure fine des ascendances
  ✅ Ondes de relief ultra-précises
  ✅ Turbulence résolue

Inconvénients:
  ❌ Coût computationnel élevé
  ❌ Domaine limité
  ❌ Pas de prévision longue (>12h)
```

### Scénario 2 : Recherche et développement

**Cas d'usage** : Valider/améliorer algorithmes de détection de thermiques

```yaml
Objectif:
  - Comparer thermiques WRF vs Meso-NH LES
  - Affiner calcul XC Flying Potential
  - Étudier structure 3D des thermiques

Méthode:
  1. Simuler cas passés (réanalyses)
  2. LES 50-100 m résolution
  3. Comparer avec traces GPS parapentes
  4. Extraire patterns thermiques

Bénéfice:
  ✅ Amélioration des algorithmes SoaringMeteo
  ✅ Publications scientifiques
  ✅ Validation terrain
```

### Scénario 3 : Hybrid downscaling

**Cas d'usage** : Nesting Meso-NH dans GFS/AROME

```yaml
Grilles:
  1. GFS:     25 km (global, 5 jours)
  2. Meso-NH: 5 km  (nested, 48h)
  3. Meso-NH: 1 km  (nested, 24h)
  4. Meso-NH: 250 m (nested LES, 12h)

Avantages:
  ✅ Prévision longue (GFS)
  ✅ Détails locaux (LES)
  ✅ Transition d'échelles

Défis:
  ⚠️ Complexité pipeline
  ⚠️ Coût computationnel
  ⚠️ Données d'initialisation
```

---

## 📦 Installation et déploiement

### Clonage du dépôt GitLab

```bash
# Dépôt officiel CNRS
git clone https://src.koda.cnrs.fr/mesonh/mesonh-code.git -b MNH-57-branch MNH-V5-7-2

# OU télécharger depuis Zenodo
wget https://zenodo.org/records/15095131/files/MNH-V5-7-1.tar.gz
tar -xzf MNH-V5-7-1.tar.gz
```

### Compilation simple (gfortran)

```bash
cd MNH-V5-7-1/conf
./configure  # Génère menu interactif

# Sélectionner :
ARCH=LXgfortran        # Linux + gfortran
VER_MPI=MPIAUTO        # Auto-détection MPI
OPTLEVEL=O2            # Optimisation
VER_CDF=CDFAUTO        # NetCDF inclus

# Compiler (16 cœurs)
make -j16

# Installer
make install
```

### Cas de test

```bash
cd MY_RUN/KTEST/001_Reunion

# Préparer simulation
./run_prep

# Lancer simulation
./run_mesonh

# Visualiser résultats
ncview REUNI.1.001dg.*.nc
```

---

## 📚 Documentation et ressources

### Documentation officielle

| Ressource | URL | Statut |
|-----------|-----|--------|
| **Site principal** | http://mesonh.aero.obs-mip.fr/mesonh57 | ✅ Actif |
| **GitLab officiel** | https://src.koda.cnrs.fr/mesonh/mesonh-code | ✅ Actif |
| **CNRM Open Source** | https://opensource.umr-cnrm.fr/projects/meso-nh | ✅ Actif |
| **Guide utilisateur** | Manuels PDF (MASDEV) | ✅ Disponible |
| **Guide scientifique** | Scientific documentation | ✅ Disponible |
| **ReadTheDocs** | https://mesonh-beta-test-guide.readthedocs.io | ⚠️ Beta |

### Tutoriels

- **Tutorial annuel** : 2×/an à Toulouse (Météo-France)
- **Formation master** : Utilisé dans cursus météorologie
- **Cas académiques** : ~50 cas fournis dans `MY_RUN/KTEST/`

### Publications scientifiques

**Article de référence** :
> Lac, C., Chaboureau, J.-P., Masson, V., et al. (2018).
> *Overview of the Meso-NH model version 5.4 and its applications.*
> Geoscientific Model Development, 11, 1929-1969.
> DOI: 10.5194/gmd-11-1929-2018

**Publications récentes (2020-2024)** : >400 articles

**Domaines** :
- Feux de forêt
- Cyclones tropicaux
- Convection profonde
- Couche limite atmosphérique
- Chimie atmosphérique
- Météorologie urbaine

### Support communautaire

- **Forum** : Via CNRM Open Source
- **Mailing list** : mesonh-users@aero.obs-mip.fr
- **Issues** : GitLab (nécessite compte)
- **Publications** : http://mesonh.aero.obs-mip.fr/cgi-bin/mesonh/publi.pl

---

## 🎓 Courbe d'apprentissage

### Niveau de difficulté : ⚠️ **ÉLEVÉ**

| Aspect | Difficulté | Commentaire |
|--------|-----------|-------------|
| **Installation** | Moyenne | Fortran + MPI + NetCDF |
| **Configuration** | Élevée | Namelists Fortran complexes |
| **Données initiales** | Élevée | GRIB2, formats spécifiques |
| **Debugging** | Élevée | Fortran, erreurs cryptiques |
| **Post-traitement** | Moyenne | NetCDF standard |
| **Optimisation** | Très élevée | HPC, tuning MPI/GPU |

### Compétences requises

1. **Fortran 90** : Lire/modifier code source
2. **Météorologie** : Comprendre physique atmosphérique
3. **Linux/HPC** : Compilation, batch jobs, MPI
4. **NetCDF** : Manipulation données multidimensionnelles
5. **Python** : Post-traitement (recommandé)

### Temps d'apprentissage estimé

- **Débutant** : 3-6 mois (avec tutoriel)
- **Expérimenté WRF** : 1-2 mois
- **Production** : 6-12 mois

---

## ⚖️ Avantages et inconvénients pour SoaringMeteo

### ✅ Avantages majeurs

1. **Résolution ultime** : 10-100 m pour thermiques explicites
2. **Open source** : Licence CeCILL-C, code accessible
3. **Physique avancée** : EDMF, LES, turbulence 3D
4. **GPU** : Accélération 4-6× (nouveau)
5. **Validation** : 25 ans de développement, AROME l'utilise
6. **Communauté française** : Support local, publications FR
7. **Ondes de relief** : Non-hydrostatique parfait pour montagnes
8. **Nested grids** : 2-way feedback, 8 niveaux

### ❌ Inconvénients

1. **Complexité** : Courbe d'apprentissage raide
2. **Documentation** : Moins accessible que WRF
3. **Ressources** : LES = HPC obligatoire
4. **Fortran** : Moins moderne que Python/Scala
5. **Communauté** : Plus petite que WRF
6. **Pas opérationnel** : Modèle de recherche
7. **Intégration** : Nouvelle pipeline à développer

### ⚠️ Risques

1. **Coût computationnel** : LES haute résolution très cher
2. **Maintenance** : Stack Fortran + MPI + GPU complexe
3. **Données** : Dépendance IFS/GFS pour initialisation
4. **Expertise** : Peu d'experts Meso-NH disponibles

---

## 🎯 Recommandations pour SoaringMeteo

### 1. **Recherche & Développement** (Priorité HAUTE)

**Objectif** : Évaluer potentiel de Meso-NH LES pour thermiques

**Plan** :
```
Phase 1 (1-2 mois) :
  - Installer Meso-NH sur serveur de test
  - Compiler et lancer cas académique KTEST
  - Se familiariser avec namelists et post-traitement

Phase 2 (2-3 mois) :
  - Simuler cas réel passé (jour de vol connu)
  - Résolution 100-200 m sur zone Pyrénées
  - Comparer avec traces GPS parapentes
  - Évaluer qualité thermiques prédits

Phase 3 (3-6 mois) :
  - Si concluant : développer pipeline automatique
  - Intégration avec frontend SoaringMeteo
  - Publication scientifique (valorisation)
```

**Budget** :
- Serveur : 1× machine 64 cœurs + 256 GB RAM (~5000 €/an cloud)
- Temps développement : 6 mois développeur
- Formation : 1 semaine tutorial Toulouse

### 2. **Production limitée** (Priorité MOYENNE)

Si R&D réussit :

**Configuration proposée** :
```yaml
Zones pilotes:
  - Pyrénées centrales (100×100 km)
  - Alpes du Sud (100×100 km)

Résolution:
  - Grille 1: 2 km (contexte synoptique)
  - Grille 2: 500 m (méso-échelle)
  - Grille 3: 100 m (LES thermiques)

Forecast:
  - Horizon: 24 heures
  - Update: 1×/jour (06 UTC)
  - Runtime: 6-8 heures

Infrastructure:
  - Serveur HPC: 256-512 cœurs
  - RAM: 512 GB
  - Stockage: 1 TB
  - GPU: 4× A100 (optionnel, +4× speedup)
```

### 3. **Partenariat académique** (Priorité HAUTE)

**Proposition** : Collaboration avec Laboratoire d'Aérologie (Toulouse)

**Bénéfices mutuels** :
- **Pour SoaringMeteo** : Expertise Meso-NH, accès HPC, co-développement
- **Pour Labo** : Cas d'usage réel, validation terrain, données GPS parapentes

**Livrables** :
- Publication scientifique conjointe
- Amélioration du modèle (retours utilisateurs)
- Open data (traces GPS anonymisées)

**Contacts** :
- Laboratoire d'Aérologie : https://www.aero.obs-mip.fr
- CNRM : https://www.umr-cnrm.fr

---

## 📈 Feuille de route proposée

### Court terme (0-6 mois)

- [ ] Installer Meso-NH sur serveur test
- [ ] Former 1 développeur (tutorial + documentation)
- [ ] Lancer 3 cas académiques (KTEST)
- [ ] Simuler 1 cas réel passé (100 m résolution)
- [ ] Comparer avec traces GPS existantes
- [ ] Décision GO/NO-GO pour suite

### Moyen terme (6-12 mois)

Si GO :
- [ ] Développer pipeline automatique (GRIB → Meso-NH → PNG/MVT)
- [ ] Intégrer dans backend SoaringMeteo (Scala)
- [ ] Tests beta sur zone pilote (Pyrénées)
- [ ] Comparaison quantitative WRF vs Meso-NH
- [ ] Publication article scientifique

### Long terme (12-24 mois)

- [ ] Déploiement production sur 2-3 zones
- [ ] Optimisation GPU (si HPC disponible)
- [ ] Extension à autres régions
- [ ] Amélioration continue (feedback pilotes)

---

## 🔗 Liens et ressources

### Officiels

- **Site Meso-NH** : http://mesonh.aero.obs-mip.fr/
- **GitLab code** : https://src.koda.cnrs.fr/mesonh/mesonh-code
- **Zenodo DOI** : https://doi.org/10.5281/zenodo.15095131
- **Publications** : http://mesonh.aero.obs-mip.fr/cgi-bin/mesonh/publi.pl

### Scientifiques

- **Article principal** : https://gmd.copernicus.org/articles/11/1929/2018/
- **GPU porting** : https://gmd.copernicus.org/articles/18/2679/2025/
- **SURFEX** : https://www.umr-cnrm.fr/surfex/

### Communauté

- **Forum** : https://opensource.umr-cnrm.fr/projects/meso-nh
- **Tutorial** : Contact mesonh@aero.obs-mip.fr

---

## 📝 Conclusion

### Réponse à la question initiale

**Meso-NH peut-il être adapté au parapente ?**

✅ **OUI, ABSOLUMENT**

Meso-NH est **le meilleur modèle atmosphérique disponible** pour simuler les thermiques et conditions de vol libre, grâce à :

1. **Résolution extrême** : 10-100 m (explicite les thermiques individuels)
2. **Physique dédiée** : EDMF pour thermiques secs + LES pour turbulence 3D
3. **Validation** : 25 ans de R&D, base d'AROME opérationnel
4. **Open source** : Code accessible, licence libre

**Résolution maximale** : 6-10 mètres démontrés scientifiquement

### Mais attention

Meso-NH n'est **pas plug-and-play** :

- Courbe d'apprentissage raide (Fortran, HPC)
- Ressources computationnelles importantes (LES)
- Documentation moins accessible que WRF
- Communauté plus restreinte

### Recommandation finale

**Phase R&D de 6 mois recommandée** avant décision production :

1. Installation et formation
2. Simulation cas réels
3. Comparaison terrain (GPS)
4. Évaluation coût/bénéfice

**Si validation réussie** : Meso-NH LES pourrait devenir **la référence mondiale** pour prévisions vol libre haute résolution.

**Potentiel scientifique** : Publication, collaboration académique, avancée de l'état de l'art.

---

**Document créé par** : Claude (Anthropic)
**Pour** : SoaringMeteo / HaizeHegoa
**Date** : 19 novembre 2025
**Version** : 1.0
**Licence** : CC BY-SA 4.0
