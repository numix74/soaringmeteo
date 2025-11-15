#!/bin/bash
INPUT_HP1="$1"
OUTPUT_DIR="$2"

if [ -z "$INPUT_HP1" ] || [ -z "$OUTPUT_DIR" ]; then
    echo "Usage: $0 <input_HP1.grib2> <output_dir>"
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

# Tous les 250m de 250 à 3000m
HEIGHTS="250 500 750 1000 1250 1500 1750 2000 2250 2500 2750 3000"

for HEIGHT in $HEIGHTS; do
    wgrib2 "$INPUT_HP1" -match "UGRD:${HEIGHT} m above ground" -grib "$OUTPUT_DIR/u_${HEIGHT}m.grib2" 2>/dev/null
    wgrib2 "$INPUT_HP1" -match "VGRD:${HEIGHT} m above ground" -grib "$OUTPUT_DIR/v_${HEIGHT}m.grib2" 2>/dev/null
    
    if [ -f "$OUTPUT_DIR/u_${HEIGHT}m.grib2" ] && [ -s "$OUTPUT_DIR/u_${HEIGHT}m.grib2" ]; then
        SIZE=$(du -h "$OUTPUT_DIR/u_${HEIGHT}m.grib2" | cut -f1)
        echo "  ✓ U${HEIGHT}m: $SIZE"
    fi
done

echo "Extraction complete"
