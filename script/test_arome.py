from meteofetch import Arome001
import os

DATA_DIR = "/mnt/soaringmeteo-data/arome"
os.makedirs(DATA_DIR, exist_ok=True)

print("📥 Téléchargement AROME HD SP1...")
datasets = Arome001.get_latest_forecast(paquet='SP1')

for var, data in datasets.items():
    output = f"{DATA_DIR}/arome_{var}.nc"
    data.to_netcdf(output)
    size_mb = os.path.getsize(output) / 1e6
    print(f"✅ {var}: {size_mb:.1f} MB")
    
print(f"\n✅ Total: {len(datasets)} variables")
