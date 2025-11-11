#!/bin/bash

SOURCE_DIR="/mnt/soaringmeteo-data/arome/grib/pays_basque"
TARGET_DIR="/mnt/soaringmeteo-data/arome/grib/pays_basque_by_hour"

echo "=== Extraction AROME par heure individuelle (v3) ==="

# Supprimer et recréer les répertoires
rm -rf "$TARGET_DIR"
mkdir -p "$TARGET_DIR/sp1"
mkdir -p "$TARGET_DIR/sp2"
mkdir -p "$TARGET_DIR/sp3"
mkdir -p "$TARGET_DIR/winds"

# Fonction pour extraire une plage d'heures
extract_package() {
    local package=$1
    local file_pattern=$2
    local hour_start=$3
    local hour_end=$4
    
    echo ""
    echo "Traitement $package : heures $hour_start à $hour_end"
    
    local input_file="$SOURCE_DIR/${file_pattern}"
    
    if [ ! -f "$input_file" ]; then
        echo "  ATTENTION: Fichier non trouvé: $input_file"
        return
    fi
    
    # Pour chaque heure dans la plage
    local hour=$hour_start
    
    while [ $hour -le $hour_end ]; do
        local output_file="$TARGET_DIR/$package/hour_${hour}.grib2"
        
        echo "  Extraction heure $hour -> hour_${hour}.grib2"
        
        # Utiliser le numéro d'heure absolu, pas l'offset
        if [ $hour -eq 0 ]; then
            # Pour l'heure 0 (analyse)
            wgrib2 "$input_file" -match ":anl:" -grib "$output_file" 2>/dev/null
        else
            # Pour les prévisions, utiliser le numéro d'heure absolu
            wgrib2 "$input_file" -match ":${hour} hour fcst:" -grib "$output_file" 2>/dev/null
        fi
        
        if [ -f "$output_file" ] && [ -s "$output_file" ]; then
            local size=$(du -h "$output_file" | cut -f1)
            local count=$(wgrib2 "$output_file" 2>/dev/null | wc -l)
            echo "    ✓ Créé: $size ($count messages)"
        else
            echo "    ✗ Vide ou échec"
        fi
        
        hour=$((hour + 1))
    done
}

# Extraire SP1
extract_package "sp1" "SP1_00H06H.grib2" 0 6
extract_package "sp1" "SP1_07H12H.grib2" 7 12
extract_package "sp1" "SP1_13H18H.grib2" 13 18
extract_package "sp1" "SP1_19H24H.grib2" 19 24
extract_package "sp1" "SP1_25H30H.grib2" 25 30
extract_package "sp1" "SP1_31H36H.grib2" 31 36
extract_package "sp1" "SP1_37H42H.grib2" 37 42

# Extraire SP2
extract_package "sp2" "SP2_00H06H.grib2" 0 6
extract_package "sp2" "SP2_07H12H.grib2" 7 12
extract_package "sp2" "SP2_13H18H.grib2" 13 18
extract_package "sp2" "SP2_19H24H.grib2" 19 24
extract_package "sp2" "SP2_25H30H.grib2" 25 30
extract_package "sp2" "SP2_31H36H.grib2" 31 36
extract_package "sp2" "SP2_37H42H.grib2" 37 42

# Extraire SP3
extract_package "sp3" "SP3_00H06H.grib2" 0 6
extract_package "sp3" "SP3_07H12H.grib2" 7 12
extract_package "sp3" "SP3_13H18H.grib2" 13 18
extract_package "sp3" "SP3_19H24H.grib2" 19 24
extract_package "sp3" "SP3_25H30H.grib2" 25 30
extract_package "sp3" "SP3_31H36H.grib2" 31 36
extract_package "sp3" "SP3_37H42H.grib2" 37 42

echo ""
echo "=== Extraction terminée ==="
echo ""
echo "Vérification complète:"
for pkg in sp1 sp2 sp3; do
    echo ""
    echo "Package $pkg:"
    echo "  Heures 0-10:"
    ls -lh "$TARGET_DIR/$pkg/" | grep -E "hour_[0-9]\.grib2|hour_10\.grib2" | awk '{print "    " $5, $9}'
    echo "  ..."
    echo "  Heures 20-24:"
    ls -lh "$TARGET_DIR/$pkg/" | grep -E "hour_2[0-4]\.grib2" | awk '{print "    " $5, $9}'
    non_empty=$(find "$TARGET_DIR/$pkg/" -type f -size +0 | wc -l)
    total=$(ls "$TARGET_DIR/$pkg/" | wc -l)
    echo "  Total: $non_empty fichiers non-vides / $total fichiers"
done

