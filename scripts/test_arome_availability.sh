#!/bin/bash
#═══════════════════════════════════════════════════════════════
# Test de Disponibilité des Données AROME
# Usage: bash test_arome_availability.sh
#═══════════════════════════════════════════════════════════════

set -e

BASE_URL="https://object.files.data.gouv.fr/meteofrance-pnt/pnt"
CURRENT_DATE=$(date -u +%Y-%m-%d)
YESTERDAY=$(date -u -d "yesterday" +%Y-%m-%d)

echo "╔════════════════════════════════════════════════════════════╗"
echo "║     🔍 Test de Disponibilité AROME - $(date -u +%H:%M) UTC     ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

test_run() {
    local DATE=$1
    local RUN=$2

    echo "📅 Test: $DATE ${RUN}Z"

    # Test URL du répertoire
    local DIR_URL="${BASE_URL}/${DATE}T${RUN}:00:00Z/arome/0025/SP1/"

    echo -n "   Répertoire SP1: "
    if curl -s --head --max-time 10 "$DIR_URL" 2>/dev/null | head -1 | grep -q "200\|302"; then
        echo "✓ Accessible"

        # Test d'un fichier GRIB spécifique
        local TEST_FILE="arome__0025__SP1__00H06H__${DATE}T${RUN}:00:00Z.grib2"
        local FILE_URL="${BASE_URL}/${DATE}T${RUN}:00:00Z/arome/0025/SP1/${TEST_FILE}"

        echo -n "   Fichier test (SP1_00H06H): "

        # Récupérer la taille du fichier
        local RESPONSE=$(curl -s --head --max-time 10 "$FILE_URL" 2>/dev/null)

        if echo "$RESPONSE" | head -1 | grep -q "200"; then
            local SIZE=$(echo "$RESPONSE" | grep -i "content-length" | awk '{print $2}' | tr -d '\r')
            if [ -n "$SIZE" ] && [ "$SIZE" -gt 0 ]; then
                local SIZE_MB=$((SIZE / 1024 / 1024))
                echo "✓ Disponible (${SIZE_MB} MB)"

                # Tester tous les fichiers
                echo "   Test des 12 fichiers GRIB:"
                local AVAILABLE=0
                local TOTAL=0

                for PACKAGE in SP1 SP2 SP3; do
                    for GROUP in 00H06H 07H12H 13H18H 19H24H; do
                        TOTAL=$((TOTAL + 1))
                        local GRIB_FILE="arome__0025__${PACKAGE}__${GROUP}__${DATE}T${RUN}:00:00Z.grib2"
                        local GRIB_URL="${BASE_URL}/${DATE}T${RUN}:00:00Z/arome/0025/${PACKAGE}/${GRIB_FILE}"

                        if curl -s --head --max-time 5 "$GRIB_URL" 2>/dev/null | head -1 | grep -q "200"; then
                            AVAILABLE=$((AVAILABLE + 1))
                            echo -n "."
                        else
                            echo -n "✗"
                        fi
                    done
                done
                echo ""
                echo "   Résultat: $AVAILABLE/$TOTAL fichiers disponibles"

                if [ $AVAILABLE -eq 12 ]; then
                    echo "   ✅ RUN COMPLET ET UTILISABLE"
                    return 0
                elif [ $AVAILABLE -ge 9 ]; then
                    echo "   ⚠️  RUN UTILISABLE (mais incomplet)"
                    return 0
                else
                    echo "   ❌ RUN INCOMPLET"
                    return 1
                fi
            else
                echo "✗ Fichier vide"
                return 1
            fi
        else
            echo "✗ Non disponible (404)"
            return 1
        fi
    else
        echo "✗ Non accessible (404)"
        return 1
    fi
}

echo "═══════════════════════════════════════════════════════════"
echo "Test 1: Run d'aujourd'hui 06Z"
echo "═══════════════════════════════════════════════════════════"
if test_run "$CURRENT_DATE" "06"; then
    RECOMMENDED_DATE="$CURRENT_DATE"
    RECOMMENDED_RUN="06"
else
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "Test 2: Run d'aujourd'hui 00Z"
    echo "═══════════════════════════════════════════════════════════"
    if test_run "$CURRENT_DATE" "00"; then
        RECOMMENDED_DATE="$CURRENT_DATE"
        RECOMMENDED_RUN="00"
    else
        echo ""
        echo "═══════════════════════════════════════════════════════════"
        echo "Test 3: Run d'hier 18Z"
        echo "═══════════════════════════════════════════════════════════"
        if test_run "$YESTERDAY" "18"; then
            RECOMMENDED_DATE="$YESTERDAY"
            RECOMMENDED_RUN="18"
        else
            echo ""
            echo "═══════════════════════════════════════════════════════════"
            echo "Test 4: Run d'hier 12Z"
            echo "═══════════════════════════════════════════════════════════"
            if test_run "$YESTERDAY" "12"; then
                RECOMMENDED_DATE="$YESTERDAY"
                RECOMMENDED_RUN="12"
            else
                echo ""
                echo "❌ AUCUN RUN DISPONIBLE"
                echo ""
                echo "Vérifications supplémentaires:"
                echo "1. Connectivité internet:"
                ping -c 3 data.gouv.fr
                echo ""
                echo "2. URL de base accessible:"
                curl -s --head "$BASE_URL" | head -1
                exit 1
            fi
        fi
    fi
fi

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║                    ✅ RECOMMANDATION                       ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "Run à utiliser: $RECOMMENDED_DATE ${RECOMMENDED_RUN}Z"
echo ""
echo "Configuration pour le script:"
echo "  RUN_DATE=\"$RECOMMENDED_DATE\""
echo "  RUN_HOUR=\"$RECOMMENDED_RUN\""
echo ""

# Test de vitesse de téléchargement
echo "═══════════════════════════════════════════════════════════"
echo "Test de vitesse de téléchargement (1 fichier)"
echo "═══════════════════════════════════════════════════════════"

TEST_FILE="arome__0025__SP1__00H06H__${RECOMMENDED_DATE}T${RECOMMENDED_RUN}:00:00Z.grib2"
TEST_URL="${BASE_URL}/${RECOMMENDED_DATE}T${RECOMMENDED_RUN}:00:00Z/arome/0025/SP1/${TEST_FILE}"

echo "Téléchargement de SP1_00H06H..."
START_TIME=$(date +%s)

if timeout 60 wget -q --show-progress "$TEST_URL" -O "/tmp/test_arome.grib2" 2>&1; then
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    FILE_SIZE=$(du -h /tmp/test_arome.grib2 | cut -f1)

    echo "✓ Téléchargé: $FILE_SIZE en ${DURATION}s"

    # Vérifier que c'est bien un fichier GRIB valide
    if command -v wgrib2 &> /dev/null; then
        echo ""
        echo "Vérification GRIB avec wgrib2:"
        wgrib2 /tmp/test_arome.grib2 | head -5
    else
        echo "⚠️  wgrib2 non installé - impossible de vérifier l'intégrité"
    fi

    rm -f /tmp/test_arome.grib2

    # Estimation temps total
    ESTIMATED_TOTAL=$((DURATION * 12))
    ESTIMATED_MIN=$((ESTIMATED_TOTAL / 60))
    echo ""
    echo "Temps estimé pour 12 fichiers: ~${ESTIMATED_MIN} minutes"
else
    echo "✗ Échec du téléchargement"
fi

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║                    FIN DU TEST                             ║"
echo "╚════════════════════════════════════════════════════════════╝"
