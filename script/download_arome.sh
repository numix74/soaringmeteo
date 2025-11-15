#!/bin/bash
set -e

LOG_DIR="/var/log/soaringmeteo"
DATA_DIR="/home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME"
BASE_URL="https://object.data.gouv.fr/meteofrance-pnt/pnt"
DATE=$(date -u +%Y-%m-%d)
DATE_DIR=$(date +%Y%m%d)
RUN="00"

mkdir -p $LOG_DIR
exec > >(tee -a "$LOG_DIR/download_arome_${DATE_DIR}.log") 2>&1

echo "=========================================="
echo "🇫🇷 AROME HD - $(date)"
echo "=========================================="

find $DATA_DIR -type d -mtime +3 -exec rm -rf {} + 2>/dev/null || true
mkdir -p "$DATA_DIR/${DATE_DIR}_${RUN}"

SUCCESS=0
for HOUR in $(seq -w 0 42); do
    FILE="arome__001__SP1__${HOUR}H__${DATE}T${RUN}:00:00Z.grib2"
    URL="${BASE_URL}/${DATE}T${RUN}:00:00Z/arome/001/SP1/${FILE}"
    OUT="$DATA_DIR/${DATE_DIR}_${RUN}/arome_${RUN}z_f${HOUR}.grib2"
    
    [ -f "$OUT" ] && [ -s "$OUT" ] && { ((SUCCESS++)); continue; }
    
    if timeout 60 wget --tries=2 --no-verbose "$URL" -O "$OUT" 2>&1; then
        ((SUCCESS++))
        [ $((HOUR % 10)) -eq 0 ] && echo "  ✓ f${HOUR}"
    else
        rm -f "$OUT"
    fi
done

echo "✅ $SUCCESS/43 fichiers"
du -sh "$DATA_DIR/${DATE_DIR}_${RUN}"

[ $SUCCESS -lt 30 ] && exit 1
echo "=========================================="
