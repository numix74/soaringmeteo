#!/bin/bash
set -e

LOG_FILE="/var/log/soaringmeteo/arome_pipeline_$(date +%Y%m%d).log"
exec > >(tee -a "$LOG_FILE") 2>&1

echo "╔════════════════════════════════════════╗"
echo "║   🇫🇷 AROME HD Pipeline - $(date +%H:%M)   ║"
echo "╚════════════════════════════════════════╝"

# 1. Téléchargement
echo "1/3 Téléchargement..."
/home/ubuntu/download_arome.sh || exit 1

# 2. Extraction globale
echo "2/3 Extraction globale..."
python3 /home/ubuntu/extract_arome_json.py || exit 1

# 3. Extraction sites
echo "3/3 Extraction sites..."
python3 /home/ubuntu/extract_arome_sites.py || exit 1

echo "✅ Pipeline terminé - $(date)"
