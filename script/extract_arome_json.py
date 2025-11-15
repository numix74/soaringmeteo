#!/usr/bin/env python3
import xarray as xr
import glob
import numpy as np
import os
import json
from datetime import datetime

DATE_DIR = datetime.utcnow().strftime("%Y%m%d")
GRIB_DIR = f"/home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME/{DATE_DIR}_00"
OUTPUT_DIR = f"/mnt/soaringmeteo-data/arome/{DATE_DIR}_00"

os.makedirs(OUTPUT_DIR, exist_ok=True)

files = sorted([f for f in glob.glob(f"{GRIB_DIR}/arome*.grib2") if os.path.getsize(f) > 1e6])
print(f"📂 {len(files)} fichiers")

metadata = []

for i, f in enumerate(files):
    try:
        ds_2m = xr.open_dataset(f, engine='cfgrib',
            backend_kwargs={'filter_by_keys': {'typeOfLevel': 'heightAboveGround', 'level': 2}})
        ds_10m = xr.open_dataset(f, engine='cfgrib',
            backend_kwargs={'filter_by_keys': {'typeOfLevel': 'heightAboveGround', 'level': 10}})
        
        ds = xr.merge([ds_2m, ds_10m], compat='override')
        ds['wspd_10m'] = np.sqrt(ds.u10**2 + ds.v10**2)
        ds['wdir_10m'] = (270 - np.arctan2(ds.v10, ds.u10) * 180 / np.pi) % 360
        
        data = {
            't2m': float(ds.t2m.mean()),
            'u10': float(ds.u10.mean()),
            'v10': float(ds.v10.mean()),
            'wspd_10m': float(ds.wspd_10m.mean()),
            'wdir_10m': float(ds.wdir_10m.mean())
        }
        
        with open(f"{OUTPUT_DIR}/arome_f{i:03d}.json", 'w') as out:
            json.dump(data, out)
        
        metadata.append({'hour': i, 'success': True})
        
    except Exception as e:
        print(f"⚠️  f{i:03d}: {e}")
        metadata.append({'hour': i, 'success': False, 'error': str(e)})

with open(f"{OUTPUT_DIR}/metadata.json", 'w') as f:
    json.dump(metadata, f, indent=2)

success_count = sum(1 for m in metadata if m['success'])
print(f"✅ {success_count}/{len(files)} fichiers traités")
