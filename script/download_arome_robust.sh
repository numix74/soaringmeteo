#!/bin/bash
DATA_DIR="/home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME"
BASE_URL="https://object.data.gouv.fr/meteofrance-pnt/pnt"
DATE="2025-10-13"
RUN="00"

mkdir -p "$DATA_DIR/20251013_00"

for HOUR in $(seq -w 0 42); do
    FILE="arome__001__SP1__${HOUR}H__${DATE}T${RUN}:00:00Z.grib2"
    URL="${BASE_URL}/${DATE}T${RUN}:00:00Z/arome/001/SP1/${FILE}"
    OUT="$DATA_DIR/20251013_00/arome_${RUN}z_f${HOUR}.grib2"
    
    [ -f "$OUT" ] && [ -s "$OUT" ] && continue
    
    echo -n "f${HOUR}..."
    if timeout 30 wget -q --tries=1 "$URL" -O "$OUT" 2>/dev/null; then
        echo "✓"
    else
        echo "✗"
        rm -f "$OUT"
    fi
done

COUNT=$(ls -1 $DATA_DIR/20251013_00/*.grib2 2>/dev/null | wc -l)
echo "✅ $COUNT fichiers"
