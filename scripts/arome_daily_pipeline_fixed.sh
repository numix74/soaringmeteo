#!/bin/bash
#═══════════════════════════════════════════════════════════════
# AROME Pays Basque - Pipeline Complet (Version Corrigée)
#
# Ce script télécharge les données AROME et lance le traitement
# Usage: ./arome_daily_pipeline_fixed.sh
#═══════════════════════════════════════════════════════════════

set -e
set -o pipefail

# Configuration
PROJECT_DIR="/home/ubuntu/soaringmeteo"
BACKEND_DIR="$PROJECT_DIR/backend"
LOG_DIR="$BACKEND_DIR/logs"
LOG_FILE="$LOG_DIR/arome_$(date +%Y%m%d_%H%M).log"
DATA_DIR="/mnt/soaringmeteo-data/arome/grib/pays_basque"
BASE_URL="https://object.files.data.gouv.fr/meteofrance-pnt/pnt"

# Créer le répertoire de logs
mkdir -p "$LOG_DIR"
mkdir -p "$DATA_DIR"

# Redirection vers log (avec copie vers stdout pour debug)
exec > >(tee -a "$LOG_FILE") 2>&1

# Fonctions utilitaires
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

log_error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ❌ ERREUR: $1" >&2
}

log_success() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ✅ $1"
}

log_warning() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ⚠️  $1"
}

# Bannière
log "╔════════════════════════════════════════════╗"
log "║   🇫🇷 AROME Pays Basque - Pipeline Auto   ║"
log "╚════════════════════════════════════════════╝"

#═══════════════════════════════════════════════════════════════
# ÉTAPE 1: Déterminer le run AROME à utiliser
#═══════════════════════════════════════════════════════════════

log "🔍 Détection du run AROME disponible..."

# Fonction pour tester si un run est disponible
test_run_available() {
    local DATE=$1
    local RUN=$2

    local TEST_URL="${BASE_URL}/${DATE}T${RUN}:00:00Z/arome/0025/SP1/arome__0025__SP1__00H06H__${DATE}T${RUN}:00:00Z.grib2"

    if curl -s --head --max-time 10 "$TEST_URL" 2>/dev/null | head -1 | grep -q "200"; then
        return 0
    else
        return 1
    fi
}

# Tester différents runs par ordre de préférence
CURRENT_DATE=$(date -u +%Y-%m-%d)
YESTERDAY=$(date -u -d "yesterday" +%Y-%m-%d)
CURRENT_HOUR=$(date -u +%H)

RUN_DATE=""
RUN_HOUR=""

# Stratégie de sélection du run
if [ "$CURRENT_HOUR" -ge "12" ]; then
    # Après 12h UTC, essayer le run 06Z d'aujourd'hui
    if test_run_available "$CURRENT_DATE" "06"; then
        RUN_DATE="$CURRENT_DATE"
        RUN_HOUR="06"
        log "✓ Utilisation du run 06Z d'aujourd'hui"
    elif test_run_available "$CURRENT_DATE" "00"; then
        RUN_DATE="$CURRENT_DATE"
        RUN_HOUR="00"
        log "⚠️  Run 06Z non disponible, utilisation du run 00Z"
    fi
else
    # Avant 12h UTC, essayer le run 00Z d'aujourd'hui
    if test_run_available "$CURRENT_DATE" "00"; then
        RUN_DATE="$CURRENT_DATE"
        RUN_HOUR="00"
        log "✓ Utilisation du run 00Z d'aujourd'hui"
    fi
fi

# Si aucun run aujourd'hui, essayer hier
if [ -z "$RUN_DATE" ]; then
    log_warning "Aucun run disponible aujourd'hui, essai avec hier..."

    if test_run_available "$YESTERDAY" "18"; then
        RUN_DATE="$YESTERDAY"
        RUN_HOUR="18"
        log "✓ Utilisation du run 18Z d'hier"
    elif test_run_available "$YESTERDAY" "12"; then
        RUN_DATE="$YESTERDAY"
        RUN_HOUR="12"
        log "✓ Utilisation du run 12Z d'hier"
    else
        log_error "Aucun run AROME disponible"
        exit 1
    fi
fi

log "📅 Run sélectionné: $RUN_DATE ${RUN_HOUR}Z"

#═══════════════════════════════════════════════════════════════
# ÉTAPE 2: Téléchargement des fichiers GRIB
#═══════════════════════════════════════════════════════════════

log "📥 Étape 1/3 : Téléchargement des fichiers GRIB..."

DOWNLOADED=0
FAILED=0
TOTAL=12

# Fonction de téléchargement avec retry
download_file() {
    local PACKAGE=$1
    local GROUP=$2
    local MAX_RETRIES=3
    local RETRY_DELAY=5

    local FILE="arome__0025__${PACKAGE}__${GROUP}__${RUN_DATE}T${RUN_HOUR}:00:00Z.grib2"
    local URL="${BASE_URL}/${RUN_DATE}T${RUN_HOUR}:00:00Z/arome/0025/${PACKAGE}/${FILE}"
    local OUT="$DATA_DIR/${PACKAGE}_${GROUP}.grib2"

    # Vérifier si existe déjà et est récent
    if [ -f "$OUT" ] && [ -s "$OUT" ]; then
        local FILE_AGE=$(($(date +%s) - $(stat -c %Y "$OUT")))
        if [ $FILE_AGE -lt 86400 ]; then
            local SIZE=$(du -h "$OUT" | cut -f1)
            log "   ${PACKAGE} ${GROUP}: déjà présent ($SIZE)"
            return 0
        fi
    fi

    log "   ${PACKAGE} ${GROUP}: téléchargement..."

    # Retry loop
    for attempt in $(seq 1 $MAX_RETRIES); do
        if timeout 300 wget -q --tries=1 "$URL" -O "$OUT.tmp" 2>/dev/null; then
            if [ -s "$OUT.tmp" ]; then
                # Vérifier la taille minimale (au moins 100 KB)
                local SIZE_BYTES=$(stat -c %s "$OUT.tmp")
                if [ $SIZE_BYTES -gt 102400 ]; then
                    mv "$OUT.tmp" "$OUT"
                    local SIZE=$(du -h "$OUT" | cut -f1)
                    log "   ${PACKAGE} ${GROUP}: ✓ $SIZE"
                    return 0
                else
                    log_warning "   ${PACKAGE} ${GROUP}: fichier trop petit (${SIZE_BYTES} bytes)"
                    rm -f "$OUT.tmp"
                fi
            else
                log_warning "   ${PACKAGE} ${GROUP}: fichier vide"
                rm -f "$OUT.tmp"
            fi
        else
            log_warning "   ${PACKAGE} ${GROUP}: tentative $attempt/$MAX_RETRIES échouée"
        fi

        if [ $attempt -lt $MAX_RETRIES ]; then
            log "   Attente de ${RETRY_DELAY}s avant nouvelle tentative..."
            sleep $RETRY_DELAY
            RETRY_DELAY=$((RETRY_DELAY * 2))  # Backoff exponentiel
        fi
    done

    log_error "   ${PACKAGE} ${GROUP}: échec après $MAX_RETRIES tentatives"
    rm -f "$OUT.tmp"
    return 1
}

# Télécharger tous les fichiers
for PACKAGE in SP1 SP2 SP3; do
    log "   📦 Package ${PACKAGE}..."
    for GROUP in 00H06H 07H12H 13H18H 19H24H; do
        if download_file "$PACKAGE" "$GROUP"; then
            DOWNLOADED=$((DOWNLOADED + 1))
        else
            FAILED=$((FAILED + 1))
        fi
    done
done

log "📊 Résultat: $DOWNLOADED téléchargés, $FAILED échecs"

# Vérifier si on a assez de fichiers
if [ $DOWNLOADED -lt 9 ]; then
    log_error "Pas assez de fichiers téléchargés ($DOWNLOADED/$TOTAL)"
    log_error "Le traitement ne peut pas continuer"
    exit 1
fi

if [ $FAILED -gt 0 ]; then
    log_warning "$FAILED fichiers manquants - traitement avec données incomplètes"
fi

#═══════════════════════════════════════════════════════════════
# ÉTAPE 3: Vérification des fichiers
#═══════════════════════════════════════════════════════════════

log "🔍 Vérification des fichiers téléchargés..."

TOTAL_SIZE=0
for GRIB_FILE in "$DATA_DIR"/*.grib2; do
    if [ -f "$GRIB_FILE" ]; then
        SIZE_BYTES=$(stat -c %s "$GRIB_FILE")
        TOTAL_SIZE=$((TOTAL_SIZE + SIZE_BYTES))
        SIZE_MB=$(echo "scale=1; $SIZE_BYTES / 1024 / 1024" | bc)
        log "   $(basename "$GRIB_FILE"): ${SIZE_MB} MB"
    fi
done

TOTAL_SIZE_MB=$(echo "scale=1; $TOTAL_SIZE / 1024 / 1024" | bc)
log "📊 Taille totale: ${TOTAL_SIZE_MB} MB"

# Vérifier avec wgrib2 si disponible
if command -v wgrib2 &> /dev/null; then
    log "🔍 Vérification GRIB avec wgrib2..."
    INVALID=0
    for GRIB_FILE in "$DATA_DIR"/*.grib2; do
        if [ -f "$GRIB_FILE" ]; then
            if ! wgrib2 "$GRIB_FILE" > /dev/null 2>&1; then
                log_error "   $(basename "$GRIB_FILE"): INVALIDE"
                INVALID=$((INVALID + 1))
            fi
        fi
    done

    if [ $INVALID -gt 0 ]; then
        log_error "$INVALID fichiers GRIB invalides"
        exit 1
    else
        log_success "Tous les fichiers GRIB sont valides"
    fi
else
    log_warning "wgrib2 non installé - vérification d'intégrité ignorée"
fi

#═══════════════════════════════════════════════════════════════
# ÉTAPE 4: Traitement Scala
#═══════════════════════════════════════════════════════════════

log "⚙️  Étape 2/3 : Traitement AROME avec Scala..."

# Vérifier que les prérequis sont présents
if [ ! -f "$BACKEND_DIR/build.sbt" ]; then
    log_error "Fichier build.sbt introuvable dans $BACKEND_DIR"
    exit 1
fi

CONFIG_FILE="$BACKEND_DIR/pays_basque.conf"
if [ ! -f "$CONFIG_FILE" ]; then
    log_error "Fichier de configuration introuvable: $CONFIG_FILE"
    exit 1
fi

# Vérifier que SBT est installé
if ! command -v sbt &> /dev/null; then
    log_error "SBT n'est pas installé"
    exit 1
fi

# Lancer le traitement
cd "$BACKEND_DIR"

log "Lancement de: sbt \"arome/run $CONFIG_FILE\""

if sbt "arome/run $CONFIG_FILE" 2>&1 | tee -a "$LOG_FILE.sbt"; then
    log_success "Traitement Scala terminé avec succès"
else
    log_error "Le traitement Scala a échoué"
    log "Voir les logs détaillés dans: $LOG_FILE.sbt"
    exit 1
fi

#═══════════════════════════════════════════════════════════════
# ÉTAPE 5: Vérification des sorties
#═══════════════════════════════════════════════════════════════

log "📊 Étape 3/3 : Vérification des sorties..."

OUTPUT_DIR="/mnt/soaringmeteo-data/arome/output/pays_basque/maps"

if [ -d "$OUTPUT_DIR" ]; then
    MAPS_COUNT=$(find "$OUTPUT_DIR" -type f -name "*.png" -o -name "*.mvt" 2>/dev/null | wc -l)
    log "   Cartes générées: $MAPS_COUNT fichiers"

    HOURS_COUNT=$(find "$OUTPUT_DIR" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | wc -l)
    log "   Heures de prévision: $HOURS_COUNT répertoires"

    if [ $MAPS_COUNT -gt 0 ]; then
        log_success "Génération des cartes réussie"
    else
        log_warning "Aucune carte générée"
    fi
else
    log_warning "Répertoire de sortie introuvable: $OUTPUT_DIR"
fi

#═══════════════════════════════════════════════════════════════
# ÉTAPE 6: Nettoyage
#═══════════════════════════════════════════════════════════════

log "🧹 Nettoyage des anciennes données..."

# Nettoyer les cartes > 7 jours
if [ -d "$OUTPUT_DIR" ]; then
    CLEANED=$(find "$OUTPUT_DIR" -type f -mtime +7 -delete -print 2>/dev/null | wc -l)
    if [ $CLEANED -gt 0 ]; then
        log "   $CLEANED anciens fichiers supprimés"
    fi
fi

# Nettoyer les logs > 30 jours
CLEANED=$(find "$LOG_DIR" -name "arome_*.log" -mtime +30 -delete -print 2>/dev/null | wc -l)
if [ $CLEANED -gt 0 ]; then
    log "   $CLEANED anciens logs supprimés"
fi

# Nettoyer les GRIB temporaires > 2 jours
CLEANED=$(find "$DATA_DIR" -name "*.tmp" -o -name "*.gbx9" -o -name "*.ncx4" -mtime +2 -delete -print 2>/dev/null | wc -l)
if [ $CLEANED -gt 0 ]; then
    log "   $CLEANED fichiers temporaires supprimés"
fi

#═══════════════════════════════════════════════════════════════
# FIN
#═══════════════════════════════════════════════════════════════

log "╔════════════════════════════════════════════╗"
log "║            ✅ PIPELINE TERMINÉ             ║"
log "╚════════════════════════════════════════════╝"
log ""
log "Statistiques finales:"
log "  • Run utilisé: $RUN_DATE ${RUN_HOUR}Z"
log "  • Fichiers GRIB: $DOWNLOADED/$TOTAL"
log "  • Taille totale: ${TOTAL_SIZE_MB} MB"
log "  • Cartes générées: $MAPS_COUNT"
log "  • Log complet: $LOG_FILE"
log ""

exit 0
