import xarray as xr
import glob

GRIB_DIR = "/home/ubuntu/WRF_BUILD/WPS-4.5/DATA_AROME/20251012_00"
files = sorted(glob.glob(f"{GRIB_DIR}/arome*.grib2"))[:5]

print(f"📂 {len(files)} fichiers\n")

# Lire UN fichier test d'abord
f = files[0]
print(f"Test: {f.split('/')[-1]}")

# 2m
try:
    ds_2m = xr.open_dataset(
        f, 
        engine='cfgrib',
        backend_kwargs={'filter_by_keys': {'typeOfLevel': 'heightAboveGround', 'level': 2}}
    )
    print(f"✅ 2m: {list(ds_2m.data_vars)}")
except Exception as e:
    print(f"⚠️  2m: {e}")

# 10m
try:
    ds_10m = xr.open_dataset(
        f,
        engine='cfgrib', 
        backend_kwargs={'filter_by_keys': {'typeOfLevel': 'heightAboveGround', 'level': 10}}
    )
    print(f"✅ 10m: {list(ds_10m.data_vars)}")
except Exception as e:
    print(f"⚠️  10m: {e}")

# Surface
try:
    ds_sfc = xr.open_dataset(
        f,
        engine='cfgrib',
        backend_kwargs={'filter_by_keys': {'typeOfLevel': 'surface'}}
    )
    print(f"✅ surface: {list(ds_sfc.data_vars)}")
except Exception as e:
    print(f"⚠️  surface: {e}")
