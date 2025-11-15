#!/bin/bash
DATA_DIR="/home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME"
BASE_URL="https://object.data.gouv.fr/meteofrance-pnt/pnt"
DATE="2025-10-13"
RUN="00"

mkdir -p "$DATA_DIR/${DATE//-/}_${RUN}"

echo "🌪️  Téléchargement AROME complet (SP1 + SP2 + SP3)"

# Télécharger les 3 packages
for PACKAGE in SP1 SP2 SP3; do
    echo "📦 Package $PACKAGE..."
    for HOUR in $(seq -w 0 42); do
        FILE="arome__001__${PACKAGE}__${HOUR}H__${DATE}T${RUN}:00:00Z.grib2"
        URL="${BASE_URL}/${DATE}T${RUN}:00:00Z/arome/001/${PACKAGE}/${FILE}"
        OUT="$DATA_DIR/${DATE//-/}_${RUN}/${PACKAGE}_f${HOUR}.grib2"
        
        [ -f "$OUT" ] && [ -s "$OUT" ] && continue
        
        echo -n "  f${HOUR}..."
        if timeout 60 wget -q --tries=2 "$URL" -O "$OUT" 2>/dev/null; then
            echo "✓"
        else
            echo "✗"
            rm -f "$OUT"
        fi
    done
done

COUNT=$(ls -1 $DATA_DIR/${DATE//-/}_${RUN}/*.grib2 2>/dev/null | wc -l)
echo "✅ $COUNT fichiers téléchargés"
