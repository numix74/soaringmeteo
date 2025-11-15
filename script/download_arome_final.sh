#!/bin/bash
DATA_DIR="/home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME"
BASE_URL="https://object.files.data.gouv.fr/meteofrance-pnt/pnt"
DATE="2025-10-15"
RUN="15"

mkdir -p "$DATA_DIR/${DATE//-/}_${RUN}"

echo "🌪️  Téléchargement AROME 0.025° COMPLET"

GROUPS=("00H06H" "07H12H" "13H18H" "19H24H" "25H30H" "31H36H" "37H42H")

for PACKAGE in SP1 SP2 SP3; do
    echo "📦 Package $PACKAGE..."
    for GROUP in "${GROUPS[@]}"; do
        FILE="arome__0025__${PACKAGE}__${GROUP}__${DATE}T${RUN}:00:00Z.grib2"
        URL="${BASE_URL}/${DATE}T${RUN}:00:00Z/arome/0025/${PACKAGE}/${FILE}"
        OUT="$DATA_DIR/${DATE//-/}_${RUN}/${PACKAGE}_${GROUP}.grib2"
        
        [ -f "$OUT" ] && [ -s "$OUT" ] && continue
        
        echo -n "  ${GROUP}..."
        if timeout 180 wget -q --tries=2 "$URL" -O "$OUT" 2>/dev/null; then
            echo " ✓ $(du -h "$OUT" | cut -f1)"
        else
            echo " ✗"
            rm -f "$OUT"
        fi
    done
done

echo "✅ Téléchargement terminé"
ls -lh "$DATA_DIR/${DATE//-/}_${RUN}/"
