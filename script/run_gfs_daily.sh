#!/bin/bash
RUN_TIME=${1:-00}
DATE=$(date +%Y%m%d)
LOG_DIR="/var/log/soaringmeteo"

mkdir -p $LOG_DIR
exec > >(tee -a "$LOG_DIR/gfs_${DATE}_${RUN_TIME}z.log")
exec 2>&1

echo "=== GFS Run ${RUN_TIME}Z started at $(date) ==="

cd ~/soaringmeteo/backend
sbt -Dconfig.file=/home/ubuntu/soaringmeteo/backend/gfs/pyrenees.conf \
  "gfs/run --gfs-run-init-time $RUN_TIME /mnt/soaringmeteo-data/gfs/gribs /mnt/soaringmeteo-data/gfs/output"

echo "=== Copying to nginx at $(date) ==="
sudo cp -r /mnt/soaringmeteo-data/gfs/output/7/gfs/* /usr/share/nginx/html/v2/data/7/gfs/

echo "=== GFS Run ${RUN_TIME}Z completed at $(date) ==="
