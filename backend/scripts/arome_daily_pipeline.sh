#!/bin/bash
set -e

# Chemins du projet
PROJECT_DIR="/home/ubuntu/soaringmeteo"
BACKEND_DIR="$PROJECT_DIR/backend"
LOG_DIR="$BACKEND_DIR/logs"
LOG_FILE="$LOG_DIR/arome_$(date +%Y%m%d_%H%M).log"
DATA_DIR="/mnt/soaringmeteo-data/arome/grib/pays_basque"
BASE_URL="https://object.files.data.gouv.fr/meteofrance-pnt/pnt"

# Créer le répertoire de logs
mkdir -p "$LOG_DIR"

# Redirection vers log
exec >> "$LOG_FILE" 2>&1

echo "[$(date '+%Y-%m-%d %H:%M:%S')] ╔════════════════════════════════════════════╗"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] ║   🇫🇷 AROME Pays Basque - Pipeline Auto   ║"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] ╚════════════════════════════════════════════╝"

# Déterminer le run
RUN_HOUR="06"
CURRENT_HOUR=$(date -u +%H)

if [ "$CURRENT_HOUR" -lt "09" ]; then
    RUN_DATE=$(date -u -d "yesterday" +%Y-%m-%d)
else
    RUN_DATE=$(date -u +%Y-%m-%d)
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] 📅 Run: $RUN_DATE ${RUN_HOUR}h UTC"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] 📥 Téléchargement des fichiers GRIB..."

mkdir -p "$DATA_DIR"

DOWNLOADED=0
FAILED=0

# Fonction de téléchargement
download_file() {
    local PACKAGE=$1
    local GROUP=$2
    
    local FILE="arome__0025__${PACKAGE}__${GROUP}__${RUN_DATE}T${RUN_HOUR}:00:00Z.grib2"
    local URL="${BASE_URL}/${RUN_DATE}T${RUN_HOUR}:00:00Z/arome/0025/${PACKAGE}/${FILE}"
    local OUT="$DATA_DIR/${PACKAGE}_${GROUP}.grib2"

    # Vérifier si existe et est récent
    if [ -f "$OUT" ] && [ -s "$OUT" ]; then
        local FILE_AGE=$(($(date +%s) - $(stat -c %Y "$OUT")))
        if [ $FILE_AGE -lt 86400 ]; then
            local SIZE=$(du -h "$OUT" | cut -f1)
            echo "[$(date '+%Y-%m-%d %H:%M:%S')]    ${PACKAGE} ${GROUP}: déjà présent ($SIZE)"
            return 0
        fi
    fi

    echo "[$(date '+%Y-%m-%d %H:%M:%S')]    ${PACKAGE} ${GROUP}: téléchargement..."
    
    if timeout 300 wget -q --tries=3 "$URL" -O "$OUT" 2>/dev/null; then
        if [ -s "$OUT" ]; then
            local SIZE=$(du -h "$OUT" | cut -f1)
            echo "[$(date '+%Y-%m-%d %H:%M:%S')]    ${PACKAGE} ${GROUP}: ✓ $SIZE"
            return 0
        else
            echo "[$(date '+%Y-%m-%d %H:%M:%S')]    ${PACKAGE} ${GROUP}: ✗ vide"
            rm -f "$OUT"
            return 1
        fi
    else
        echo "[$(date '+%Y-%m-%d %H:%M:%S')]    ${PACKAGE} ${GROUP}: ✗ échec"
        rm -f "$OUT"
        return 1
    fi
}

# Télécharger tous les fichiers
for PACKAGE in SP1 SP2 SP3; do
    for GROUP in 00H06H 07H12H 13H18H 19H24H; do
        if download_file "$PACKAGE" "$GROUP"; then
            DOWNLOADED=$((DOWNLOADED + 1))
        else
            FAILED=$((FAILED + 1))
        fi
    done
done

echo "[$(date '+%Y-%m-%d %H:%M:%S')] 📊 Résultat: $DOWNLOADED téléchargés, $FAILED échecs"

if [ $DOWNLOADED -lt 9 ]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ❌ ERREUR: Pas assez de fichiers ($DOWNLOADED/12)"
    exit 1
fi

# Vérification
echo "[$(date '+%Y-%m-%d %H:%M:%S')] 🔍 Vérification des fichiers..."
ls -lh "$DATA_DIR"/*.grib2 | grep -v "gbx9\|ncx4" | awk '{printf "[%s] %s - %s\n", strftime("%Y-%m-%d %H:%M:%S"), $9, $5}'

# Traitement
echo "[$(date '+%Y-%m-%d %H:%M:%S')] ⚙️  Traitement AROME..."
cd "$BACKEND_DIR"

if sbt "arome/run $BACKEND_DIR/pays_basque.conf"; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ✅ Traitement terminé"
    
    MAPS_COUNT=$(find /mnt/soaringmeteo-data/arome/output/pays_basque/maps/ -type f 2>/dev/null | wc -l)
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 📊 $MAPS_COUNT cartes générées"
    
    # Nettoyage
    find /mnt/soaringmeteo-data/arome/output/pays_basque/maps/ -type f -mtime +7 -delete 2>/dev/null || true
    find "$LOG_DIR" -name "arome_*.log" -mtime +30 -delete 2>/dev/null || true
    
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ╔════════════════════════════════════════════╗"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ║            ✅ PIPELINE TERMINÉ             ║"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ╚════════════════════════════════════════════╝"
else
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ❌ ERREUR traitement"
    exit 1
fi
