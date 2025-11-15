#!/usr/bin/env python3
import xarray as xr
import glob
import numpy as np
import os
import json

GRIB_DIR = "/home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME/20251012_00"
OUTPUT_DIR = "/mnt/soaringmeteo-data/arome/20251012_00"
os.makedirs(OUTPUT_DIR, exist_ok=True)

files = sorted([f for f in glob.glob(f"{GRIB_DIR}/arome*.grib2") if os.path.getsize(f) > 1e6])
print(f"📂 {len(files)} fichiers\n")

metadata = []

for i, f in enumerate(files):
    hour = i  # f00, f01, f02...
    
    try:
        ds_2m = xr.open_dataset(f, engine='cfgrib',
            backend_kwargs={'filter_by_keys': {'typeOfLevel': 'heightAboveGround', 'level': 2}})
        ds_10m = xr.open_dataset(f, engine='cfgrib',
            backend_kwargs={'filter_by_keys': {'typeOfLevel': 'heightAboveGround', 'level': 10}})
        
        ds = xr.merge([ds_2m, ds_10m], compat='override')
        
        # Calculs
        ds['wspd_10m'] = np.sqrt(ds.u10**2 + ds.v10**2)
        ds['wdir_10m'] = (270 - np.arctan2(ds.v10, ds.u10) * 180 / np.pi) % 360
        
        # Sauvegarder
        output = f"{OUTPUT_DIR}/arome_f{hour:03d}.nc"
        ds.to_netcdf(output)
        
        metadata.append({
            'hour': hour,
            'file': output,
            'vars': list(ds.data_vars),
            'size_mb': os.path.getsize(output)/1e6
        })
        
        if (i+1) % 10 == 0:
            print(f"  {i+1}/{len(files)} ✓")
            
    except Exception as e:
        print(f"⚠️  f{hour:03d}: {e}")

# Sauvegarder index
with open(f"{OUTPUT_DIR}/index.json", 'w') as f:
    json.dump(metadata, f, indent=2)

print(f"\n✅ {len(metadata)} fichiers NetCDF")
print(f"📁 {OUTPUT_DIR}")
print(f"💾 {sum(m['size_mb'] for m in metadata):.1f} MB total")
