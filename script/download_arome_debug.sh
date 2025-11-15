#!/bin/bash
set -e

DATA_DIR="/home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME"
BASE_URL="https://object.data.gouv.fr/meteofrance-pnt/pnt"
DATE="2025-10-13"
RUN="00"

rm -rf "$DATA_DIR/20251013_00"
mkdir -p "$DATA_DIR/20251013_00"

# Test 5 fichiers avec timeout
for HOUR in 00 01 02 03 04; do
    FILE="arome__001__SP1__${HOUR}H__${DATE}T${RUN}:00:00Z.grib2"
    URL="${BASE_URL}/${DATE}T${RUN}:00:00Z/arome/001/SP1/${FILE}"
    OUT="$DATA_DIR/20251013_00/arome_${RUN}z_f${HOUR}.grib2"
    
    echo "Test f${HOUR}..."
    if timeout 30 wget --tries=2 "$URL" -O "$OUT" 2>&1 | tail -3; then
        echo "✅ $(du -h $OUT | cut -f1)"
    else
        echo "❌ Timeout ou erreur"
        rm -f "$OUT"
    fi
done

ls -lh "$DATA_DIR/20251013_00/"
