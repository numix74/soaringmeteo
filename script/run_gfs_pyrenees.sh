#!/bin/bash
RUN_TIME=${1:-00}
DATE=$(date +%Y%m%d)
LOG_DIR="/var/log/soaringmeteo"

mkdir -p $LOG_DIR
exec > >(tee -a "$LOG_DIR/gfs_pyrenees_${DATE}_${RUN_TIME}z.log")
exec 2>&1

echo "=== GFS Pyrénées Run ${RUN_TIME}Z started at $(date) ==="

cd ~/soaringmeteo/backend

# Créer un lien symbolique dev.conf -> pyrenees.conf temporairement
ln -sf pyrenees.conf gfs/dev.conf

sbt "gfs/run --gfs-run-init-time $RUN_TIME /mnt/soaringmeteo-data/gfs/gribs /mnt/soaringmeteo-data/gfs/output"

# Restaurer dev.conf original si besoin
# git checkout gfs/dev.conf

echo "=== GFS Pyrénées Run ${RUN_TIME}Z completed at $(date) ==="
