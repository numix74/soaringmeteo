#!/bin/bash
set -e

# Variables AVANT DATA_DIR
DATE=$(date +%Y%m%d)
RUN="00"
LOG_DIR="/var/log/soaringmeteo"
DATA_DIR="/home/ubuntu/WRF_BUILD/WPS-4.5/DATA"

mkdir -p $LOG_DIR
exec > >(tee -a "$LOG_DIR/download_gfs_${DATE}.log")
exec 2>&1

echo "=========================================="
echo "Téléchargement GFS - $(date)"
echo "Date: $DATE Run: ${RUN}Z"
echo "=========================================="

# Créer dossier pour aujourd'hui
mkdir -p $DATA_DIR/${DATE}_${RUN}

# Télécharger GFS
for HOUR in 000 003 006 009 012 015 018 021 024; do
    OUTPUT_FILE="$DATA_DIR/${DATE}_${RUN}/gfs_${RUN}z_f${HOUR}.grib2"
    
    if [ -f "$OUTPUT_FILE" ]; then
        echo "Fichier f${HOUR} déjà présent, skip"
        continue
    fi
    
    echo "Téléchargement f${HOUR}..."
    wget -q --show-progress \
        "https://nomads.ncep.noaa.gov/pub/data/nccf/com/gfs/prod/gfs.${DATE}/${RUN}/atmos/gfs.t${RUN}z.pgrb2.0p25.f${HOUR}" \
        -O "$OUTPUT_FILE" || {
            echo "Échec téléchargement f${HOUR}"
            exit 1
        }
done

# Nettoyer anciennes données (>3 jours)
find $DATA_DIR -name "gfs_*.grib2" -mtime +3 -delete 2>/dev/null || true

echo "Téléchargement GFS terminé : $(du -sh $DATA_DIR/${DATE}_${RUN})"
echo "=========================================="
