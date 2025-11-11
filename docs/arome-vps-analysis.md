# Analyse AROME - Configuration VPS Production

**Date:** 11 novembre 2025
**VPS:** /home/ubuntu/soaringmeteo/
**Status:** ⚠️ Pipeline non fonctionnel - téléchargements échouent

---

## 🔴 Problème Critique Identifié

### Le téléchargement AROME échoue systématiquement

**Symptôme dans les logs (14:54:01 UTC):**
```
[2025-11-11 14:54:01]    📦 Package SP1...
[2025-11-11 14:54:01]       1000 téléchargement...
[2025-11-11 14:54:01]       1000 ✗ ÉCHEC
```

**⚠️ ALERTE:** Le format du log ne correspond PAS au script actuel !
- Le script actuel affiche: `[YYYY-MM-DD HH:MM:SS]    ${PACKAGE} ${GROUP}: téléchargement...`
- Le log montre: `       1000 téléchargement...` (sans timestamp dans le message)

**Hypothèses:**
1. Une **ancienne version** du script est en cache ou s'exécute
2. Un **autre script** est appelé par erreur
3. Le script a été **modifié après** l'exécution du log

---

## 📂 Architecture Actuelle du VPS

### Structure des Répertoires

```
/home/ubuntu/soaringmeteo/
├── backend/
│   ├── scripts/
│   │   └── arome_daily_pipeline.sh ✅ (Script principal actuel)
│   ├── logs/
│   │   └── arome_20251111_1454.log (1.3KB - erreur)
│   ├── pays_basque.conf (configuration Scala)
│   └── build.sbt

/mnt/soaringmeteo-data/arome/
├── grib/
│   ├── pays_basque/ ⚠️ (Nouveau système - VIDE)
│   └── pays_basque_by_hour/ (Ancien système)
└── output/
    └── pays_basque/maps/

/home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME/
└── 20251017_00/ (Très ancien système - dernière utilisation 17 oct)

/var/log/soaringmeteo/
└── cron.log (logs de toutes les tâches cron)
```

### Scripts Présents sur le VPS

| Script | Taille | Date | Statut | Usage |
|--------|--------|------|--------|-------|
| `arome_daily_pipeline.sh` | 4497 bytes | 11 nov 15:17 | ✅ **ACTIF** | Script principal appelé par cron |
| `download_arome_final.sh` | 1047 bytes | 15 oct | ⚠️ TEST | Télécharge avec groupes 0025 |
| `download_arome_complete.sh` | 1015 bytes | 14 oct | ⚠️ TEST | Télécharge 3 packages |
| `download_arome_groups.sh` | 1109 bytes | 15 oct | ⚠️ TEST | Télécharge par groupes d'heures |
| `download_arome.sh` | 1222 bytes | 13 oct | ❌ OBSOLÈTE | API 001 (ancienne) |
| `download_arome_robust.sh` | 737 bytes | 13 oct | ❌ OBSOLÈTE | API 001 (ancienne) |
| `download_arome_debug.sh` | 739 bytes | 13 oct | ❌ OBSOLÈTE | API 001 (ancienne) |
| `run_arome_pipeline.sh` | 808 bytes | 13 oct | ❌ OBSOLÈTE | Utilise Python (pas Scala) |
| `extract_arome_winds.sh` | 789 bytes | 17 oct | ⚙️ OUTIL | Extraction des vents par wgrib2 |

---

## 📜 Analyse du Script Principal

### `/home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh`

**Objectif:** Pipeline complet de téléchargement + traitement AROME

#### Configuration
- **Source des données:** `https://object.files.data.gouv.fr/meteofrance-pnt/pnt`
- **Modèle:** AROME 0025 (résolution 2.5 km)
- **Run:** 06Z (06h UTC)
- **Zone:** Pays Basque
- **Stockage GRIB:** `/mnt/soaringmeteo-data/arome/grib/pays_basque`
- **Sortie:** `/mnt/soaringmeteo-data/arome/output/pays_basque/maps/`
- **Logs:** `/home/ubuntu/soaringmeteo/backend/logs/arome_YYYYMMDD_HHMM.log`

#### Logique de Détermination de la Date

```bash
RUN_HOUR="06"
CURRENT_HOUR=$(date -u +%H)

if [ "$CURRENT_HOUR" -lt "09" ]; then
    RUN_DATE=$(date -u -d "yesterday" +%Y-%m-%d)  # Avant 09h UTC = J-1
else
    RUN_DATE=$(date -u +%Y-%m-%d)                  # Après 09h UTC = J
fi
```

**Problème potentiel:** Si le script s'exécute à 10h UTC (selon le cron), il utilisera la date du jour. Mais si les données du run 06Z ne sont pas encore disponibles à 10h UTC, le téléchargement échouera.

#### Structure des Fichiers GRIB à Télécharger

**12 fichiers au total:**
- 3 packages (SP1, SP2, SP3)
- 4 groupes temporels par package (00H06H, 07H12H, 13H18H, 19H24H)

**Nomenclature:**
```
arome__0025__SP1__00H06H__2025-11-11T06:00:00Z.grib2
arome__0025__SP1__07H12H__2025-11-11T06:00:00Z.grib2
...
arome__0025__SP3__19H24H__2025-11-11T06:00:00Z.grib2
```

**Stockage local:**
```
/mnt/soaringmeteo-data/arome/grib/pays_basque/
├── SP1_00H06H.grib2
├── SP1_07H12H.grib2
├── SP1_13H18H.grib2
├── SP1_19H24H.grib2
├── SP2_00H06H.grib2
├── SP2_07H12H.grib2
├── SP2_13H18H.grib2
├── SP2_19H24H.grib2
├── SP3_00H06H.grib2
├── SP3_07H12H.grib2
├── SP3_13H18H.grib2
└── SP3_19H24H.grib2
```

#### Fonction de Téléchargement

**Points positifs:**
- ✅ Timeout de 300 secondes (5 min)
- ✅ 3 tentatives (`--tries=3`)
- ✅ Vérification de la taille du fichier
- ✅ Cache intelligent (ne retélécharge pas si < 24h)
- ✅ Gestion des erreurs

**Points à améliorer:**
- ⚠️ Pas de vérification de l'intégrité GRIB
- ⚠️ Pas de retry avec backoff exponentiel
- ⚠️ Pas de vérification si les données sont disponibles avant de télécharger

#### Condition de Succès

```bash
if [ $DOWNLOADED -lt 9 ]; then
    echo "❌ ERREUR: Pas assez de fichiers ($DOWNLOADED/12)"
    exit 1
fi
```

**Tolérance:** 9 fichiers minimum sur 12 (75%)

#### Traitement Scala

```bash
cd "$BACKEND_DIR"
sbt "arome/run $BACKEND_DIR/pays_basque.conf"
```

**⚠️ Problème potentiel:** Le script ne vérifie pas si:
- SBT est installé
- Le projet backend est compilé
- Le fichier `pays_basque.conf` existe

---

## 🔍 Analyse des Erreurs

### Erreur 1: Téléchargement Échoue

**Log du 11 novembre 14:54:**
```
[2025-11-11 14:54:01]    📦 Package SP1...
[2025-11-11 14:54:01]       1000 téléchargement...
[2025-11-11 14:54:01]       1000 ✗ ÉCHEC
```

**🚨 INCOHÉRENCE CRITIQUE:**

Le script actuel devrait afficher:
```bash
echo "[$(date '+%Y-%m-%d %H:%M:%S')]    ${PACKAGE} ${GROUP}: téléchargement..."
```

Ce qui donnerait:
```
[2025-11-11 14:54:01]    SP1 00H06H: téléchargement...
```

Mais le log montre:
```
[2025-11-11 14:54:01]       1000 téléchargement...
```

**CONCLUSION:** Un autre script s'exécute, ou une version obsolète est en cache !

### Erreur 2: Scripts Manquants dans le Cron

```
/bin/sh: 1: /home/ubuntu/download_arome_daily.sh: not found
/bin/sh: 1: /home/ubuntu/generate_arome_daily.sh: not found
```

**Cause:** L'ancien cron contient encore des références à ces scripts qui n'existent pas.

**Solution:** Nettoyer le crontab et garder uniquement:
```cron
0 10 * * * /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh >> /var/log/soaringmeteo/cron.log 2>&1
```

---

## 🔧 Diagnostic à Effectuer

### 1. Vérifier quelle version du script s'exécute réellement

```bash
# Afficher le contenu exact du script appelé par le cron
cat /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh | head -50

# Vérifier s'il y a des liens symboliques
ls -la /home/ubuntu/soaringmeteo/backend/scripts/

# Vérifier les permissions
ls -l /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh
```

### 2. Tester le téléchargement manuellement

```bash
# Tester avec la date et run actuels
DATE=$(date -u +%Y-%m-%d)
RUN_HOUR="06"
PACKAGE="SP1"
GROUP="00H06H"

URL="https://object.files.data.gouv.fr/meteofrance-pnt/pnt/${DATE}T${RUN_HOUR}:00:00Z/arome/0025/${PACKAGE}/arome__0025__${PACKAGE}__${GROUP}__${DATE}T${RUN_HOUR}:00:00Z.grib2"

echo "Test URL: $URL"
wget -S --spider "$URL" 2>&1 | grep -i "HTTP/"
```

### 3. Vérifier la disponibilité des données

```bash
# Lister les runs AROME disponibles aujourd'hui
curl -s https://object.files.data.gouv.fr/meteofrance-pnt/pnt/ | grep "$(date -u +%Y-%m-%d)" | grep arome
```

### 4. Vérifier le crontab actuel

```bash
# Afficher le cron complet
crontab -l

# Vérifier les logs cron système
grep -i arome /var/log/syslog | tail -20
```

### 5. Tester le script manuellement

```bash
# Exécuter en mode debug
bash -x /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh 2>&1 | tee /tmp/arome_debug.log
```

---

## 🎯 Solutions Proposées

### Solution 1: Corriger le Script de Téléchargement (URGENT)

Le problème est probablement que:
1. Les données du run 06Z ne sont pas disponibles à 10h UTC
2. L'URL de téléchargement est incorrecte
3. La structure des fichiers sur data.gouv.fr a changé

**Action:** Créer un script de test pour identifier le problème exact.

### Solution 2: Utiliser un Run Plus Ancien

Au lieu d'utiliser le run 06Z du jour (qui peut ne pas être disponible à 10h), utiliser le run 00Z:

```bash
RUN_HOUR="00"  # Au lieu de "06"

# Ou utiliser le dernier run disponible
if [ "$CURRENT_HOUR" -lt "15" ]; then
    RUN_HOUR="00"
    RUN_DATE=$(date -u +%Y-%m-%d)
else
    RUN_HOUR="06"
    RUN_DATE=$(date -u +%Y-%m-%d)
fi
```

### Solution 3: Ajouter une Vérification de Disponibilité

Avant de télécharger, vérifier que les données sont disponibles:

```bash
check_data_availability() {
    local TEST_URL="${BASE_URL}/${RUN_DATE}T${RUN_HOUR}:00:00Z/arome/0025/SP1/"

    if curl -s --head "$TEST_URL" | head -1 | grep -q "200\|302"; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] ✓ Données disponibles pour ${RUN_DATE} ${RUN_HOUR}Z"
        return 0
    else
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] ✗ Données non disponibles pour ${RUN_DATE} ${RUN_HOUR}Z"
        return 1
    fi
}

# Appeler avant de lancer les téléchargements
if ! check_data_availability; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Tentative avec run précédent..."
    RUN_DATE=$(date -u -d "yesterday" +%Y-%m-%d)
    RUN_HOUR="18"
    check_data_availability || exit 1
fi
```

### Solution 4: Nettoyer le Crontab

Supprimer toutes les références aux scripts obsolètes:

```bash
# Sauvegarder l'ancien cron
crontab -l > ~/crontab_backup_$(date +%Y%m%d).txt

# Éditer et garder uniquement:
crontab -e
```

**Nouveau crontab AROME simplifié:**
```cron
# AROME Pays Basque - Pipeline complet quotidien
0 10 * * * /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh >> /var/log/soaringmeteo/cron.log 2>&1

# Nettoyage hebdomadaire (dimanche 02h)
0 2 * * 0 find /mnt/soaringmeteo-data/arome/output/pays_basque/maps/ -type f -mtime +7 -delete 2>/dev/null
0 2 * * 0 find /home/ubuntu/soaringmeteo/backend/logs/ -name "arome_*.log" -mtime +30 -delete 2>/dev/null
```

### Solution 5: Améliorer les Logs

Ajouter plus d'informations de debug:

```bash
download_file() {
    local PACKAGE=$1
    local GROUP=$2

    local FILE="arome__0025__${PACKAGE}__${GROUP}__${RUN_DATE}T${RUN_HOUR}:00:00Z.grib2"
    local URL="${BASE_URL}/${RUN_DATE}T${RUN_HOUR}:00:00Z/arome/0025/${PACKAGE}/${FILE}"
    local OUT="$DATA_DIR/${PACKAGE}_${GROUP}.grib2"

    # DEBUG: Afficher l'URL complète
    echo "[$(date '+%Y-%m-%d %H:%M:%S')]    URL: $URL" >> "$LOG_FILE.debug"

    # Test de connectivité
    if ! curl -s --head --max-time 10 "$URL" | head -1 | grep -q "200\|302"; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')]    ${PACKAGE} ${GROUP}: ✗ URL inaccessible (404)"
        return 1
    fi

    # ... reste du code
}
```

---

## 📊 Comparaison des Systèmes AROME

### Système Actuel (Novembre 2025)
```
API: https://object.files.data.gouv.fr/meteofrance-pnt/pnt
Modèle: AROME 0025 (2.5 km)
Structure: Groupes temporels (00H06H, 07H12H, 13H18H, 19H24H)
Packages: SP1, SP2, SP3
Traitement: Scala (SBT)
Stockage: /mnt/soaringmeteo-data/arome/
Status: ❌ Non fonctionnel
```

### Ancien Système (Octobre 2025)
```
API: https://object.data.gouv.fr/meteofrance-pnt/pnt (ancienne URL)
Modèle: AROME 001 (plus haute résolution?)
Structure: Fichiers horaires individuels (f00, f01, ... f42)
Packages: SP1, SP2, SP3
Traitement: Python
Stockage: /home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME/
Status: ❌ Obsolète (arrêté après 17 octobre)
```

### Différences Clés

| Aspect | Système Actuel | Ancien Système |
|--------|----------------|----------------|
| URL de base | object.**files**.data.gouv.fr | object.data.gouv.fr |
| Résolution | 0025 | 001 |
| Fichiers | 12 groupes (4 x 3) | 129 fichiers (43 x 3) |
| Taille totale | ~500 MB | ~2 GB |
| Téléchargement | ~5-10 min | ~30-60 min |
| Traitement | Scala/SBT | Python |

---

## 🏗️ Architecture Cible (Recommandée)

```
┌─────────────────────────────────────────────────────────────┐
│ 1. TÉLÉCHARGEMENT (arome_daily_pipeline.sh)                 │
├─────────────────────────────────────────────────────────────┤
│ • Détection automatique du run disponible (00Z, 06Z, 12Z)   │
│ • Vérification de disponibilité avant téléchargement        │
│ • Téléchargement parallèle des 12 fichiers GRIB             │
│ • Vérification d'intégrité (wgrib2)                         │
│ • Retry intelligent avec backoff                            │
│ • Notification si échec                                     │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. TRAITEMENT SCALA (sbt arome/run)                         │
├─────────────────────────────────────────────────────────────┤
│ • Lecture des GRIB (SP1, SP2, SP3)                          │
│ • Extraction des vents par altitude (optionnel)             │
│ • Calculs météorologiques (thermique, vents, etc.)          │
│ • Génération PNG + MVT pour 25 heures                       │
│ • Stockage en base de données H2                            │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. PUBLICATION (rsync/nginx)                                │
├─────────────────────────────────────────────────────────────┤
│ • Copie vers répertoire nginx                               │
│ • Mise à jour des métadonnées                               │
│ • Nettoyage des anciennes données (> 7 jours)              │
└─────────────────────────────────────────────────────────────┘
```

---

## ✅ Plan d'Action Immédiat

### Phase 1: Diagnostic (À FAIRE EN PREMIER)

1. **Identifier le script exact qui s'exécute**
   ```bash
   # Comparer le script actuel avec les logs
   diff <(cat /home/ubuntu/soaringmeteo/backend/scripts/arome_daily_pipeline.sh) <(cat ~/arome_daily_pipeline_backup.sh)
   ```

2. **Tester la disponibilité des données**
   ```bash
   # Créer un script de test (à fournir)
   bash test_arome_availability.sh
   ```

3. **Vérifier les logs complets**
   ```bash
   tail -200 /home/ubuntu/soaringmeteo/backend/logs/arome_20251111_1454.log
   ```

### Phase 2: Correction (URGENT)

1. **Mettre à jour le script** avec les corrections proposées
2. **Nettoyer le crontab** (supprimer les anciennes références)
3. **Tester manuellement** le pipeline complet
4. **Activer le monitoring** (logs + alertes)

### Phase 3: Optimisation (MOYEN TERME)

1. Paralléliser les téléchargements (xargs ou GNU parallel)
2. Ajouter un système de cache intelligent
3. Implémenter des notifications (email/Slack)
4. Documenter la procédure de troubleshooting

---

## 📝 Scripts à Créer

### 1. Script de Test de Disponibilité

Je recommande de créer:
```
/home/ubuntu/soaringmeteo/backend/scripts/test_arome_availability.sh
```

Ce script testera:
- Quels runs sont disponibles aujourd'hui (00Z, 06Z, 12Z, 18Z)
- Si les 12 fichiers GRIB sont accessibles
- La taille approximative des fichiers
- Le temps de téléchargement estimé

### 2. Script de Monitoring

```
/home/ubuntu/soaringmeteo/backend/scripts/monitor_arome.sh
```

Pour vérifier:
- Date de la dernière mise à jour réussie
- Nombre de cartes générées
- Espace disque utilisé
- Erreurs dans les logs récents

---

## 🔗 Références

### URLs Importantes

- **API Data Gouv (actuelle):** https://object.files.data.gouv.fr/meteofrance-pnt/pnt/
- **Documentation AROME:** https://donneespubliques.meteofrance.fr/?fond=produit&id_produit=131&id_rubrique=51
- **GRIB2 Tools:** https://www.cpc.ncep.noaa.gov/products/wesley/wgrib2/

### Fichiers de Configuration

- **Config Scala:** `/home/ubuntu/soaringmeteo/backend/pays_basque.conf`
- **Build SBT:** `/home/ubuntu/soaringmeteo/backend/build.sbt`
- **Crontab:** `crontab -l`

### Logs à Surveiller

- **Pipeline AROME:** `/home/ubuntu/soaringmeteo/backend/logs/arome_*.log`
- **Cron global:** `/var/log/soaringmeteo/cron.log`
- **Système:** `/var/log/syslog` (grep arome)

---

## 📞 Contact et Support

**Dernière analyse:** 11 novembre 2025
**Statut:** ⚠️ Pipeline non fonctionnel - diagnostic en cours
**Prochaine étape:** Exécuter le diagnostic Phase 1

---

**Document créé pour:** VPS Production SoaringMeteo
**Environnement:** /home/ubuntu/soaringmeteo/
**Version:** 1.0 - Analyse initiale
