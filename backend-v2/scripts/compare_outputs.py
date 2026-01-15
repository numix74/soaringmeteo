#!/usr/bin/env python3
"""
Compare outputs between backend v1 and v2.
Generates difference metrics and visual diffs.
"""
import os
import sys
from pathlib import Path
import json
from datetime import datetime

try:
    import numpy as np
    from PIL import Image
except ImportError:
    print("⚠️  Missing dependencies. Install with: pip3 install numpy pillow")
    sys.exit(1)

OUTPUT_V1 = Path("/home/ubuntu/soaringmeteo/output/7")
OUTPUT_V2 = Path("/home/ubuntu/soaringmeteo/output-unified/7")

MODELS = ["gfs", "arome"]
LAYERS = [
    "thermal-velocity",
    "soaring-layer-depth",
    "wind-surface",
    "clouds-rain",
    "xc-flying-potential",
    "boundary-layer-depth",
    "temperature-2m",
    "dew-point-2m"
]

def find_latest_run(base_path, model):
    """Trouve le run le plus récent pour un modèle."""
    model_dir = base_path / model
    if not model_dir.exists():
        return None

    runs = [d for d in model_dir.iterdir() if d.is_dir()]
    if not runs:
        return None

    # Trier par nom (format ISO: 2025-12-28T00)
    runs.sort(reverse=True)
    return runs[0]

def compare_images(img1_path, img2_path):
    """Compare deux PNG et retourne les métriques de différence."""
    if not img1_path.exists() or not img2_path.exists():
        return {
            "missing": True,
            "v1_exists": img1_path.exists(),
            "v2_exists": img2_path.exists()
        }

    img1 = np.array(Image.open(img1_path))
    img2 = np.array(Image.open(img2_path))

    if img1.shape != img2.shape:
        return {
            "shape_mismatch": True,
            "v1_shape": img1.shape,
            "v2_shape": img2.shape
        }

    # Pixel-by-pixel difference
    diff = np.abs(img1.astype(float) - img2.astype(float))

    return {
        "identical": np.all(diff == 0),
        "max_diff": float(np.max(diff)),
        "mean_diff": float(np.mean(diff)),
        "pixels_different": int(np.sum(diff > 0)),
        "total_pixels": int(img1.size),
        "percent_different": float(np.sum(diff > 0) / img1.size * 100)
    }

def compare_json(json1_path, json2_path):
    """Compare deux fichiers JSON."""
    if not json1_path.exists() or not json2_path.exists():
        return {
            "missing": True,
            "v1_exists": json1_path.exists(),
            "v2_exists": json2_path.exists()
        }

    with open(json1_path) as f1, open(json2_path) as f2:
        data1 = json.load(f1)
        data2 = json.load(f2)

    return {
        "identical": data1 == data2,
        "v1_keys": sorted(data1.keys()) if isinstance(data1, dict) else None,
        "v2_keys": sorted(data2.keys()) if isinstance(data2, dict) else None
    }

def main():
    report = {
        "timestamp": datetime.now().isoformat(),
        "models": {}
    }

    for model in MODELS:
        print(f"\n{'='*60}")
        print(f"Comparing {model.upper()}")
        print('='*60)

        run_v1 = find_latest_run(OUTPUT_V1, model)
        run_v2 = find_latest_run(OUTPUT_V2, model)

        if not run_v1 or not run_v2:
            print(f"⚠️  Missing runs: v1={run_v1}, v2={run_v2}")
            continue

        print(f"V1 run: {run_v1.name}")
        print(f"V2 run: {run_v2.name}")

        model_report = {
            "v1_run": run_v1.name,
            "v2_run": run_v2.name,
            "layers": {}
        }

        # Trouver les zones
        zones_v1 = [z for z in run_v1.iterdir() if z.is_dir()]
        zones_v2 = [z for z in run_v2.iterdir() if z.is_dir()]

        for zone_v1 in zones_v1:
            zone_name = zone_v1.name
            zone_v2 = run_v2 / zone_name

            if not zone_v2.exists():
                print(f"⚠️  Zone {zone_name} missing in v2")
                continue

            print(f"\n  Zone: {zone_name}")

            for layer in LAYERS:
                layer_v1 = zone_v1 / layer
                layer_v2 = zone_v2 / layer

                if not layer_v1.exists() or not layer_v2.exists():
                    continue

                # Comparer tous les PNG de cette couche
                pngs_v1 = sorted(layer_v1.glob("*.png"))
                pngs_v2 = sorted(layer_v2.glob("*.png"))

                identical_count = 0
                different_count = 0
                max_diff_overall = 0

                for png_v1 in pngs_v1:
                    png_v2 = layer_v2 / png_v1.name
                    result = compare_images(png_v1, png_v2)

                    if result.get("identical"):
                        identical_count += 1
                    elif not result.get("missing"):
                        different_count += 1
                        max_diff_overall = max(max_diff_overall, result.get("max_diff", 0))

                total = len(pngs_v1)
                status = "✅" if identical_count == total else "⚠️"

                print(f"    {status} {layer}: {identical_count}/{total} identical", end="")
                if different_count > 0:
                    print(f" (max_diff={max_diff_overall:.2f})")
                else:
                    print()

                model_report["layers"][f"{zone_name}/{layer}"] = {
                    "total_files": total,
                    "identical": identical_count,
                    "different": different_count,
                    "max_diff": float(max_diff_overall)
                }

        report["models"][model] = model_report

    # Sauvegarder rapport
    report_path = Path("/tmp/comparison_report.json")
    with open(report_path, "w") as f:
        json.dump(report, f, indent=2)

    print(f"\n{'='*60}")
    print(f"Report saved to: {report_path}")
    print('='*60)

    return 0

if __name__ == "__main__":
    sys.exit(main())
