#!/bin/bash
DATA_DIR="/home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME"
BASE_URL="https://object.data.gouv.fr/meteofrance-pnt/pnt"
DATE="2025-10-15"
RUN="00"

mkdir -p "$DATA_DIR/${DATE//-/}_${RUN}"

echo "🌪️  Téléchargement AROME par groupes (SP1, SP2, SP3)"

# Groupes d'échéances
GROUPS=("00-06" "07-12" "13-18" "19-24" "25-30" "31-36" "37-42")

for PACKAGE in SP1 SP2 SP3; do
    echo "📦 Package $PACKAGE..."
    for GROUP in "${GROUPS[@]}"; do
        FILE="arome__001__${PACKAGE}__${GROUP}H__${DATE}T${RUN}:00:00Z.grib2"
        URL="${BASE_URL}/${DATE}T${RUN}:00:00Z/arome/001/${PACKAGE}/${FILE}"
        OUT="$DATA_DIR/${DATE//-/}_${RUN}/${PACKAGE}_${GROUP}H.grib2"
        
        [ -f "$OUT" ] && [ -s "$OUT" ] && continue
        
        echo -n "  ${GROUP}H..."
        if timeout 120 wget -q --tries=2 "$URL" -O "$OUT" 2>/dev/null; then
            echo " ✓ $(du -h "$OUT" | cut -f1)"
        else
            echo " ✗"
            rm -f "$OUT"
        fi
    done
done

COUNT=$(ls -1 $DATA_DIR/${DATE//-/}_${RUN}/*.grib2 2>/dev/null | wc -l)
echo "✅ $COUNT fichiers téléchargés"
