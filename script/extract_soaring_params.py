#!/usr/bin/env python3
"""
Script d'extraction des paramètres de vol libre depuis wrfout
SoaringMeteo Pays Basque
"""

import sys
import os
import numpy as np
import xarray as xr
from netCDF4 import Dataset
from wrf import getvar, interplevel, to_np, latlon_coords, ALL_TIMES
import json
from datetime import datetime

def calculate_thermal_velocity(wrfout_file):
    """
    Calcule W* (vitesse thermique) selon formule standard
    W* = [(g/T) * H * PBLH]^(1/3)
    """
    ncfile = Dataset(wrfout_file)
    
    # Paramètres nécessaires
    pblh = getvar(ncfile, "PBLH", timeidx=ALL_TIMES)
    hfx = getvar(ncfile, "HFX", timeidx=ALL_TIMES)
    t2 = getvar(ncfile, "T2", timeidx=ALL_TIMES)
    
    # Constantes
    g = 9.81
    rho = 1.2
    cp = 1004
    
    # Calcul W*
    hfx_positive = np.maximum(hfx, 0)
    w_star = np.power((g / t2) * (hfx_positive / (rho * cp)) * pblh, 1./3.)
    w_star = np.nan_to_num(w_star, nan=0.0, posinf=0.0, neginf=0.0)
    
    ncfile.close()
    return w_star

def extract_all_params(wrfout_file, output_dir):
    """Extrait tous les paramètres utiles pour vol libre"""
    print(f"\n📂 Traitement: {os.path.basename(wrfout_file)}")
    
    ncfile = Dataset(wrfout_file)
    os.makedirs(output_dir, exist_ok=True)
    
    print("  ├─ Extraction PBLH...")
    pblh = getvar(ncfile, "PBLH", timeidx=ALL_TIMES)
    
    print("  ├─ Calcul W*...")
    w_star = calculate_thermal_velocity(wrfout_file)
    
    print("  ├─ Extraction vents 10m...")
    u10 = getvar(ncfile, "U10", timeidx=ALL_TIMES)
    v10 = getvar(ncfile, "V10", timeidx=ALL_TIMES)
    wspd10 = np.sqrt(u10**2 + v10**2)
    wdir10 = (270 - np.arctan2(v10, u10) * 180 / np.pi) % 360
    
    print("  ├─ Extraction vents 2000m...")
    p = getvar(ncfile, "pressure", timeidx=ALL_TIMES)
    ua = getvar(ncfile, "ua", timeidx=ALL_TIMES)
    va = getvar(ncfile, "va", timeidx=ALL_TIMES)
    u_2000 = interplevel(ua, p, 800, timeidx=ALL_TIMES)
    v_2000 = interplevel(va, p, 800, timeidx=ALL_TIMES)
    wspd_2000 = np.sqrt(u_2000**2 + v_2000**2)
    wdir_2000 = (270 - np.arctan2(v_2000, u_2000) * 180 / np.pi) % 360
    
    # Extraire coordonnées
    lats, lons = latlon_coords(pblh)
    times = pblh.coords['Time'].values
    
    # Sauvegarder
    output_file = os.path.join(output_dir, f"{os.path.basename(wrfout_file).replace('wrfout', 'soaring')}")
    print(f"  └─ Sauvegarde: {output_file}")
    
    ds = xr.Dataset({
        'pblh': (['time', 'south_north', 'west_east'], to_np(pblh)),
        'w_star': (['time', 'south_north', 'west_east'], w_star),
        'wspd_10m': (['time', 'south_north', 'west_east'], to_np(wspd10)),
        'wdir_10m': (['time', 'south_north', 'west_east'], to_np(wdir10)),
        'wspd_2000m': (['time', 'south_north', 'west_east'], to_np(wspd_2000)),
        'wdir_2000m': (['time', 'south_north', 'west_east'], to_np(wdir_2000)),
    })
    
    ds.coords['lat'] = (['south_north', 'west_east'], to_np(lats))
    ds.coords['lon'] = (['south_north', 'west_east'], to_np(lons))
    ds.coords['time'] = times
    
    ds.attrs['description'] = 'Paramètres vol libre extraits de WRF'
    ds.attrs['source'] = os.path.basename(wrfout_file)
    ds.to_netcdf(output_file)
    
    ncfile.close()
    return {'output_file': output_file, 'pblh_max': float(np.max(to_np(pblh))), 'w_star_max': float(np.max(w_star))}

def main():
    if len(sys.argv) < 2:
        print("Usage: python extract_soaring_params.py <wrfout_directory>")
        sys.exit(1)
    
    wrfout_dir = sys.argv[1]
    output_dir = sys.argv[2] if len(sys.argv) > 2 else os.path.join(wrfout_dir, 'soaring_data')
    
    print("╔════════════════════════════════════════════════════════════╗")
    print("║  🌪️  EXTRACTION PARAMÈTRES VOL LIBRE - PAYS BASQUE       ║")
    print("╚════════════════════════════════════════════════════════════╝")
    
    wrfout_files = sorted([os.path.join(wrfout_dir, f) for f in os.listdir(wrfout_dir) if f.startswith('wrfout_d01') and f.endswith('.nc')])
    
    if not wrfout_files:
        print(f"❌ Aucun fichier wrfout trouvé dans {wrfout_dir}")
        sys.exit(1)
    
    print(f"\n📁 Répertoire: {wrfout_dir}")
    print(f"📊 Fichiers wrfout: {len(wrfout_files)}")
    print(f"💾 Sortie: {output_dir}\n")
    
    results = []
    for wrfout_file in wrfout_files:
        try:
            result = extract_all_params(wrfout_file, output_dir)
            results.append(result)
        except Exception as e:
            print(f"❌ Erreur: {str(e)}\n")
            continue
    
    print("\n╔════════════════════════════════════════════════════════════╗")
    print("║              ✅ EXTRACTION TERMINÉE                        ║")
    print("╚════════════════════════════════════════════════════════════╝")
    print(f"\n📊 Fichiers traités: {len(results)}/{len(wrfout_files)}")

if __name__ == '__main__':
    main()
