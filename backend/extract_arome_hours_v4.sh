#!/bin/bash

SOURCE_DIR="/mnt/soaringmeteo-data/arome/grib/pays_basque"
TARGET_DIR="/mnt/soaringmeteo-data/arome/grib/pays_basque_by_hour"

echo "=== Extraction AROME par heure individuelle (v4 - avec flux) ==="

# Supprimer et recréer les répertoires
rm -rf "$TARGET_DIR"
mkdir -p "$TARGET_DIR/sp1"
mkdir -p "$TARGET_DIR/sp2"
mkdir -p "$TARGET_DIR/sp3"
mkdir -p "$TARGET_DIR/winds"

# Fonction pour extraire SP3 avec les flux accumulés
extract_sp3_with_fluxes() {
    local file_pattern=$1
    local hour_start=$2
    local hour_end=$3
    
    echo ""
    echo "Traitement SP3 : heures $hour_start à $hour_end"
    
    local input_file="$SOURCE_DIR/${file_pattern}"
    
    if [ ! -f "$input_file" ]; then
        echo "  ATTENTION: Fichier non trouvé: $input_file"
        return
    fi
    
    local hour=$hour_start
    
    while [ $hour -le $hour_end ]; do
        local output_file="$TARGET_DIR/sp3/hour_${hour}.grib2"
        
        echo "  Extraction heure $hour -> hour_${hour}.grib2"
        
        if [ $hour -eq 0 ]; then
            # Pour l'heure 0, prendre l'analyse et ignorer les flux (pas de flux à t=0)
            wgrib2 "$input_file" -match ":anl:" -grib "$output_file" 2>/dev/null
        else
            # Pour les autres heures, prendre les flux accumulés 0-X hour
            wgrib2 "$input_file" -match ":0-${hour} hour acc fcst:" -grib "$output_file" 2>/dev/null
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

# Fonction standard pour SP1 et SP2
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
    
    local hour=$hour_start
    
    while [ $hour -le $hour_end ]; do
        local output_file="$TARGET_DIR/$package/hour_${hour}.grib2"
        
        echo "  Extraction heure $hour -> hour_${hour}.grib2"
        
        if [ $hour -eq 0 ]; then
            wgrib2 "$input_file" -match ":anl:" -grib "$output_file" 2>/dev/null
        else
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

# Extraire SP2
extract_package "sp2" "SP2_00H06H.grib2" 0 6
extract_package "sp2" "SP2_07H12H.grib2" 7 12
extract_package "sp2" "SP2_13H18H.grib2" 13 18
extract_package "sp2" "SP2_19H24H.grib2" 19 24

# Extraire SP3 avec traitement spécial pour les flux
extract_sp3_with_fluxes "SP3_00H06H.grib2" 0 6
extract_sp3_with_fluxes "SP3_07H12H.grib2" 7 12
extract_sp3_with_fluxes "SP3_13H18H.grib2" 13 18
extract_sp3_with_fluxes "SP3_19H24H.grib2" 19 24

echo ""
echo "=== Extraction terminée ==="
echo ""
echo "Vérification SP3 (flux):"
echo "Hour 0:"
wgrib2 "$TARGET_DIR/sp3/hour_0.grib2" -s 2>/dev/null
echo ""
echo "Hour 1:"
wgrib2 "$TARGET_DIR/sp3/hour_1.grib2" -s 2>/dev/null | head -10
echo ""
ls -lh "$TARGET_DIR/sp3/" | grep "hour_[0-9]\.grib2\|hour_1[0-9]\.grib2" | awk '{print $5, $9}'

