#!/bin/bash
# Backup avant modification

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/home/ubuntu/backups_soaringmeteo"
mkdir -p $BACKUP_DIR

FILE="$1"
if [ ! -f "$FILE" ]; then
    echo "❌ Fichier non trouvé: $FILE"
    exit 1
fi

BACKUP_FILE="$BACKUP_DIR/$(basename $FILE).${TIMESTAMP}.bak"
cp "$FILE" "$BACKUP_FILE"

echo "✅ Backup créé"
echo "   Source: $FILE"
echo "   Backup: $BACKUP_FILE"
ls -lh "$BACKUP_FILE"
