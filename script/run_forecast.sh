#!/bin/bash
#===============================================================================
# Script : run_forecast.sh
# Exécution complète WPS+WRF pour prévision Pays Basque
#===============================================================================

set -e

WRF_DIR="/home/ubuntu/WRF_BUILD/WRFV4.5.2"
WPS_DIR="/home/ubuntu/WRF_BUILD/WPS-4.5"
LOG_DIR="/var/log/soaringmeteo"
DATE=$(date +%Y%m%d)
RUN="00"

mkdir -p $LOG_DIR
exec > >(tee -a "$LOG_DIR/run_forecast_${DATE}.log")
exec 2>&1

echo "=========================================="
echo "Run WRF Pays Basque - $(date)"
echo "Date: $DATE Run: ${RUN}Z"
echo "=========================================="

# Vérifier que les données GFS sont présentes
DATA_DIR="$WPS_DIR/DATA/${DATE}_${RUN}"
if [ ! -d "$DATA_DIR" ] || [ $(ls -1 $DATA_DIR/*.grib2 2>/dev/null | wc -l) -lt 9 ]; then
    echo "ERREUR: Données GFS manquantes dans $DATA_DIR"
    exit 1
fi

#==============================================================================
# PHASE 1 : WPS
#==============================================================================

echo "--- Phase 1/4 : WPS ---"
cd $WPS_DIR

# Nettoyer anciens fichiers
rm -f GRIBFILE.* FILE:* PFILE:* met_em.d*.nc

# Lier fichiers GRIB
./link_grib.csh $DATA_DIR/gfs_${RUN}z_f*.grib2

# Ungrib
echo "Ungrib..."
./ungrib.exe >& ungrib.log
if ! grep -q "Successful completion" ungrib.log; then
    echo "ERREUR: Ungrib a échoué"
    exit 1
fi

# Metgrid
echo "Metgrid..."
./metgrid.exe >& metgrid.log
if ! grep -q "Successful completion" metgrid.log; then
    echo "ERREUR: Metgrid a échoué"
    exit 1
fi

echo "WPS terminé avec succès"

#==============================================================================
# PHASE 2 : WRF
#==============================================================================

echo "--- Phase 2/4 : WRF Preparation ---"
cd $WRF_DIR/run

# Nettoyer anciens fichiers
rm -f wrfinput_d* wrfbdy_d01 wrfout_d* rsl.* met_em.d*.nc

# Lier fichiers met_em
ln -sf $WPS_DIR/met_em.d*.nc .

# Real.exe
echo "Real.exe..."
mpirun -np 4 ./real.exe >& real.log
if ! grep -q "SUCCESS COMPLETE REAL_EM" rsl.error.0000; then
    echo "ERREUR: Real.exe a échoué"
    tail -50 rsl.error.0000
    exit 1
fi

echo "Real.exe terminé avec succès"

#==============================================================================
# PHASE 3 : WRF Run
#==============================================================================

echo "--- Phase 3/4 : WRF Calcul (peut prendre 12-15h) ---"
echo "Début: $(date)"

mpirun -np 4 ./wrf.exe >& wrf.log

if ! grep -q "SUCCESS COMPLETE WRF" rsl.error.0000; then
    echo "ERREUR: WRF a échoué"
    tail -100 rsl.error.0000
    exit 1
fi

echo "Fin WRF: $(date)"
echo "WRF terminé avec succès"

#==============================================================================
# PHASE 4 : Post-traitement
#==============================================================================

echo "--- Phase 4/4 : Archivage ---"

# Créer dossier résultats
RESULT_DIR="/mnt/soaringmeteo-data/pays-basque/${DATE}_${RUN}"
mkdir -p $RESULT_DIR

# Copier résultats
cp wrfout_d*.nc $RESULT_DIR/
cp rsl.error.0000 $RESULT_DIR/wrf_run.log

# Compresser pour économiser l'espace
gzip $RESULT_DIR/wrfout_d*.nc

echo "Résultats dans: $RESULT_DIR"
du -sh $RESULT_DIR

echo "=========================================="
echo "Prévision terminée avec succès"
echo "=========================================="
