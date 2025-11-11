#!/bin/bash
#═══════════════════════════════════════════════════════════════
# Monitoring AROME - Vérification de l'état du pipeline
# Usage: bash monitor_arome.sh
#═══════════════════════════════════════════════════════════════

LOG_DIR="/home/ubuntu/soaringmeteo/backend/logs"
OUTPUT_DIR="/mnt/soaringmeteo-data/arome/output/pays_basque/maps"
GRIB_DIR="/mnt/soaringmeteo-data/arome/grib/pays_basque"

echo "╔════════════════════════════════════════════════════════════╗"
echo "║         🔍 Monitoring AROME Pays Basque                   ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

#═══════════════════════════════════════════════════════════════
# 1. Dernière exécution
#═══════════════════════════════════════════════════════════════

echo "📅 Dernière exécution du pipeline:"
echo "════════════════════════════════════════════════════════════"

if [ -d "$LOG_DIR" ]; then
    LAST_LOG=$(ls -t "$LOG_DIR"/arome_*.log 2>/dev/null | head -1)

    if [ -n "$LAST_LOG" ]; then
        LOG_DATE=$(basename "$LAST_LOG" | sed 's/arome_//' | sed 's/.log//')
        LOG_SIZE=$(du -h "$LAST_LOG" | cut -f1)
        LOG_MTIME=$(stat -c %y "$LAST_LOG" | cut -d' ' -f1,2 | cut -d'.' -f1)

        echo "  Fichier: $(basename "$LAST_LOG")"
        echo "  Date: $LOG_MTIME"
        echo "  Taille: $LOG_SIZE"
        echo ""

        # Vérifier le statut
        if grep -q "✅ PIPELINE TERMINÉ" "$LAST_LOG"; then
            echo "  Statut: ✅ SUCCÈS"

            # Extraire les stats
            RUN_INFO=$(grep "Run utilisé:" "$LAST_LOG" | tail -1 | cut -d':' -f2-)
            if [ -n "$RUN_INFO" ]; then
                echo "  Run:$RUN_INFO"
            fi

            GRIB_COUNT=$(grep "Fichiers GRIB:" "$LAST_LOG" | tail -1 | cut -d':' -f2-)
            if [ -n "$GRIB_COUNT" ]; then
                echo "  GRIB téléchargés:$GRIB_COUNT"
            fi

            MAPS_COUNT=$(grep "Cartes générées:" "$LAST_LOG" | tail -1 | cut -d':' -f2-)
            if [ -n "$MAPS_COUNT" ]; then
                echo "  Cartes générées:$MAPS_COUNT"
            fi

        elif grep -q "❌ ERREUR" "$LAST_LOG"; then
            echo "  Statut: ❌ ÉCHEC"
            echo ""
            echo "  Dernières erreurs:"
            grep "❌" "$LAST_LOG" | tail -3 | sed 's/^/    /'
        else
            echo "  Statut: ⚠️  EN COURS ou INCOMPLET"
        fi

        # Vérifier si le log est récent (< 24h)
        LOG_AGE=$(($(date +%s) - $(stat -c %Y "$LAST_LOG")))
        HOURS_AGE=$((LOG_AGE / 3600))

        echo ""
        if [ $HOURS_AGE -gt 24 ]; then
            echo "  ⚠️  Attention: Dernière exécution il y a ${HOURS_AGE}h"
        else
            echo "  ✓ Dernière exécution il y a ${HOURS_AGE}h"
        fi
    else
        echo "  ❌ Aucun log trouvé"
    fi
else
    echo "  ❌ Répertoire de logs introuvable: $LOG_DIR"
fi

echo ""

#═══════════════════════════════════════════════════════════════
# 2. État des fichiers GRIB
#═══════════════════════════════════════════════════════════════

echo "📦 Fichiers GRIB disponibles:"
echo "════════════════════════════════════════════════════════════"

if [ -d "$GRIB_DIR" ]; then
    GRIB_COUNT=$(ls -1 "$GRIB_DIR"/*.grib2 2>/dev/null | wc -l)
    echo "  Nombre de fichiers: $GRIB_COUNT/12"

    if [ $GRIB_COUNT -gt 0 ]; then
        TOTAL_SIZE=0
        for FILE in "$GRIB_DIR"/*.grib2; do
            SIZE=$(stat -c %s "$FILE" 2>/dev/null || echo 0)
            TOTAL_SIZE=$((TOTAL_SIZE + SIZE))
        done
        TOTAL_MB=$((TOTAL_SIZE / 1024 / 1024))
        echo "  Taille totale: ${TOTAL_MB} MB"

        # Fichier le plus récent
        NEWEST=$(ls -t "$GRIB_DIR"/*.grib2 2>/dev/null | head -1)
        if [ -n "$NEWEST" ]; then
            NEWEST_DATE=$(stat -c %y "$NEWEST" | cut -d' ' -f1,2 | cut -d'.' -f1)
            NEWEST_AGE=$(($(date +%s) - $(stat -c %Y "$NEWEST")))
            NEWEST_HOURS=$((NEWEST_AGE / 3600))
            echo "  Plus récent: $(basename "$NEWEST") (${NEWEST_HOURS}h)"
        fi

        if [ $GRIB_COUNT -eq 12 ]; then
            echo "  ✅ Complet (12/12)"
        elif [ $GRIB_COUNT -ge 9 ]; then
            echo "  ⚠️  Utilisable (${GRIB_COUNT}/12)"
        else
            echo "  ❌ Incomplet (${GRIB_COUNT}/12)"
        fi
    else
        echo "  ❌ Aucun fichier GRIB"
    fi
else
    echo "  ❌ Répertoire GRIB introuvable: $GRIB_DIR"
fi

echo ""

#═══════════════════════════════════════════════════════════════
# 3. État des cartes générées
#═══════════════════════════════════════════════════════════════

echo "🗺️  Cartes générées:"
echo "════════════════════════════════════════════════════════════"

if [ -d "$OUTPUT_DIR" ]; then
    PNG_COUNT=$(find "$OUTPUT_DIR" -name "*.png" 2>/dev/null | wc -l)
    MVT_COUNT=$(find "$OUTPUT_DIR" -name "*.mvt" 2>/dev/null | wc -l)
    TOTAL_MAPS=$((PNG_COUNT + MVT_COUNT))

    echo "  PNG: $PNG_COUNT fichiers"
    echo "  MVT: $MVT_COUNT fichiers"
    echo "  Total: $TOTAL_MAPS fichiers"

    # Heures de prévision disponibles
    HOURS=$(find "$OUTPUT_DIR" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | wc -l)
    echo "  Heures de prévision: $HOURS répertoires"

    if [ $TOTAL_MAPS -gt 0 ]; then
        # Taille totale
        TOTAL_SIZE=$(du -sh "$OUTPUT_DIR" 2>/dev/null | cut -f1)
        echo "  Taille totale: $TOTAL_SIZE"

        # Carte la plus récente
        NEWEST=$(find "$OUTPUT_DIR" -type f \( -name "*.png" -o -name "*.mvt" \) -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2-)
        if [ -n "$NEWEST" ]; then
            NEWEST_AGE=$(($(date +%s) - $(stat -c %Y "$NEWEST")))
            NEWEST_HOURS=$((NEWEST_AGE / 3600))
            echo "  Plus récente: ${NEWEST_HOURS}h"
        fi

        if [ $HOURS -ge 24 ]; then
            echo "  ✅ Prévisions complètes (${HOURS}h)"
        elif [ $HOURS -gt 0 ]; then
            echo "  ⚠️  Prévisions partielles (${HOURS}h)"
        fi
    else
        echo "  ❌ Aucune carte générée"
    fi
else
    echo "  ❌ Répertoire de sortie introuvable: $OUTPUT_DIR"
fi

echo ""

#═══════════════════════════════════════════════════════════════
# 4. Espace disque
#═══════════════════════════════════════════════════════════════

echo "💾 Espace disque:"
echo "════════════════════════════════════════════════════════════"

df -h /mnt/soaringmeteo-data 2>/dev/null | tail -1 | awk '{
    printf "  Partition: %s\n", $1
    printf "  Taille: %s\n", $2
    printf "  Utilisé: %s (%s)\n", $3, $5
    printf "  Disponible: %s\n", $4
}'

USAGE=$(df /mnt/soaringmeteo-data 2>/dev/null | tail -1 | awk '{print $5}' | sed 's/%//')
if [ "$USAGE" -gt 80 ]; then
    echo "  ⚠️  Attention: Espace disque > 80%"
elif [ "$USAGE" -gt 90 ]; then
    echo "  ❌ CRITIQUE: Espace disque > 90%"
else
    echo "  ✓ Espace disque OK"
fi

echo ""

#═══════════════════════════════════════════════════════════════
# 5. Prochaine exécution cron
#═══════════════════════════════════════════════════════════════

echo "⏰ Prochaine exécution:"
echo "════════════════════════════════════════════════════════════"

if crontab -l 2>/dev/null | grep -q "arome_daily_pipeline"; then
    CRON_LINE=$(crontab -l 2>/dev/null | grep "arome_daily_pipeline" | grep -v "^#")
    CRON_TIME=$(echo "$CRON_LINE" | awk '{print $1, $2, $3, $4, $5}')

    echo "  Configuration cron: $CRON_TIME"

    # Calculer la prochaine exécution (approximatif)
    CRON_HOUR=$(echo "$CRON_TIME" | awk '{print $2}')
    CURRENT_HOUR=$(date +%H)

    if [ "$CRON_HOUR" -gt "$CURRENT_HOUR" ]; then
        HOURS_UNTIL=$((CRON_HOUR - CURRENT_HOUR))
        echo "  Prochaine exécution: dans ${HOURS_UNTIL}h (aujourd'hui à ${CRON_HOUR}h UTC)"
    else
        HOURS_UNTIL=$((24 - CURRENT_HOUR + CRON_HOUR))
        echo "  Prochaine exécution: dans ${HOURS_UNTIL}h (demain à ${CRON_HOUR}h UTC)"
    fi
else
    echo "  ❌ Pas de tâche cron AROME configurée"
fi

echo ""

#═══════════════════════════════════════════════════════════════
# 6. Recommandations
#═══════════════════════════════════════════════════════════════

echo "💡 Recommandations:"
echo "════════════════════════════════════════════════════════════"

ISSUES=0

# Vérifier si le dernier run a échoué
if [ -f "$LAST_LOG" ]; then
    if ! grep -q "✅ PIPELINE TERMINÉ" "$LAST_LOG"; then
        echo "  ⚠️  Le dernier pipeline n'a pas terminé correctement"
        echo "     → Vérifier: $LAST_LOG"
        ISSUES=$((ISSUES + 1))
    fi
fi

# Vérifier si les GRIB sont anciens
if [ -d "$GRIB_DIR" ]; then
    NEWEST=$(ls -t "$GRIB_DIR"/*.grib2 2>/dev/null | head -1)
    if [ -n "$NEWEST" ]; then
        AGE=$(($(date +%s) - $(stat -c %Y "$NEWEST")))
        if [ $AGE -gt 172800 ]; then  # 48h
            echo "  ⚠️  Les fichiers GRIB ont plus de 48h"
            echo "     → Lancer manuellement: bash arome_daily_pipeline.sh"
            ISSUES=$((ISSUES + 1))
        fi
    fi
fi

# Vérifier l'espace disque
if [ "$USAGE" -gt 85 ]; then
    echo "  ⚠️  Espace disque critique"
    echo "     → Nettoyer les anciennes données"
    ISSUES=$((ISSUES + 1))
fi

# Vérifier si les cartes sont récentes
if [ -d "$OUTPUT_DIR" ]; then
    NEWEST=$(find "$OUTPUT_DIR" -type f -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2-)
    if [ -n "$NEWEST" ]; then
        AGE=$(($(date +%s) - $(stat -c %Y "$NEWEST")))
        if [ $AGE -gt 86400 ]; then  # 24h
            echo "  ⚠️  Les cartes ont plus de 24h"
            echo "     → Vérifier le cron et les logs"
            ISSUES=$((ISSUES + 1))
        fi
    fi
fi

if [ $ISSUES -eq 0 ]; then
    echo "  ✅ Aucun problème détecté"
fi

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║                    FIN DU MONITORING                       ║"
echo "╚════════════════════════════════════════════════════════════╝"

exit $ISSUES
