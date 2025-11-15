#!/usr/bin/env python3
import xarray as xr
import pandas as pd
import numpy as np
import glob
import json
import os
from datetime import datetime

DATE_DIR = datetime.utcnow().strftime("%Y%m%d")
GRIB_DIR = f"/home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME/{DATE_DIR}_00"
OUTPUT_DIR = f"/mnt/soaringmeteo-data/arome/{DATE_DIR}_00"
SITES_CSV = "/home/ubuntu/sites_pays_basque.csv"

sites = pd.read_csv(SITES_CSV)
files = sorted([f for f in glob.glob(f"{GRIB_DIR}/arome*.grib2") if os.path.getsize(f) > 1e6])

print(f"🏔️  {len(sites)} sites × {len(files)} heures")

sites_data = {site: [] for site in sites['Site']}

for hour, f in enumerate(files):
    try:
        ds_2m = xr.open_dataset(f, engine='cfgrib',
            backend_kwargs={'filter_by_keys': {'typeOfLevel': 'heightAboveGround', 'level': 2}})
        ds_10m = xr.open_dataset(f, engine='cfgrib',
            backend_kwargs={'filter_by_keys': {'typeOfLevel': 'heightAboveGround', 'level': 10}})
        
        for _, site in sites.iterrows():
            t2m = ds_2m.t2m.sel(latitude=site['Latitude'], longitude=site['Longitude'], method='nearest')
            u10 = ds_10m.u10.sel(latitude=site['Latitude'], longitude=site['Longitude'], method='nearest')
            v10 = ds_10m.v10.sel(latitude=site['Latitude'], longitude=site['Longitude'], method='nearest')
            
            wspd = float(np.sqrt(u10**2 + v10**2))
            wdir = float((270 - np.arctan2(v10, u10) * 180 / np.pi) % 360)
            
            sites_data[site['Site']].append({
                'hour': hour,
                't2m_c': float(t2m - 273.15),
                'wspd_ms': wspd,
                'wspd_kmh': wspd * 3.6,
                'wdir_deg': wdir
            })
    except Exception as e:
        print(f"⚠️  Hour {hour}: {e}")
        continue

for site_name, data in sites_data.items():
    output = f"{OUTPUT_DIR}/site_{site_name.replace(' ', '_')}.json"
    with open(output, 'w') as f:
        json.dump(data, f, indent=2)

print(f"✅ {len(sites_data)} sites sauvegardés")
