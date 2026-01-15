#!/usr/bin/env python3
"""
Compare seulement les heures natives (0, 3, 6, 9, 12...) entre v1 et v2.

Si les heures natives sont identiques → Problème interpolation temporelle
Si les heures natives diffèrent → Problème parsing GRIB ou calculs
"""

import numpy as np
from PIL import Image
from pathlib import Path

# Chemins
V1_BASE = Path("/home/ubuntu/soaringmeteo/output/7/gfs")
V2_BASE = Path("/home/ubuntu/soaringmeteo/output-unified/7/gfs")

# Configuration
RUN_DATE = "2026-01-06T00"
ZONE = "pyrenees"
LAYERS = ["clouds-rain", "xc-flying-potential"]

# Heures natives GFS (pas de 3h)
NATIVE_HOURS = list(range(0, 120, 3))  # 0, 3, 6, 9, 12, ..., 117

def compare_images(img1_path, img2_path):
    """Compare deux images PNG pixel par pixel."""
    if not img1_path.exists() or not img2_path.exists():
        return None

    img1 = np.array(Image.open(img1_path))
    img2 = np.array(Image.open(img2_path))

    if img1.shape != img2.shape:
        print(f"⚠️  Tailles différentes: {img1.shape} vs {img2.shape}")
        return None

    # Comparer pixels
    total_pixels = img1.shape[0] * img1.shape[1]
    identical = np.all(img1 == img2, axis=-1) if len(img1.shape) == 3 else (img1 == img2)
    identical_count = np.sum(identical)
    different_count = total_pixels - identical_count

    similarity = (identical_count / total_pixels) * 100

    return {
        'total': total_pixels,
        'identical': identical_count,
        'different': different_count,
        'similarity': similarity
    }

def main():
    print("=" * 70)
    print("COMPARAISON HEURES NATIVES v1 vs v2")
    print("=" * 70)
    print(f"Run: {RUN_DATE}")
    print(f"Zone: {ZONE}")
    print(f"Heures natives GFS: {len(NATIVE_HOURS)} ({NATIVE_HOURS[0]} à {NATIVE_HOURS[-1]} par pas de 3)")
    print()

    for layer in LAYERS:
        print(f"\n{'=' * 70}")
        print(f"COUCHE: {layer}")
        print(f"{'=' * 70}\n")

        # Mapping des noms de couches
        v1_layer = "xc-potential" if layer == "xc-flying-potential" else layer
        v2_layer = layer

        v1_layer_path = V1_BASE / RUN_DATE / ZONE / v1_layer
        v2_layer_path = V2_BASE / RUN_DATE / ZONE / v2_layer

        if not v1_layer_path.exists():
            print(f"❌ V1 path not found: {v1_layer_path}")
            continue
        if not v2_layer_path.exists():
            print(f"❌ V2 path not found: {v2_layer_path}")
            continue

        results = []

        for hour in NATIVE_HOURS:
            # V1 : heures natives seulement (0, 3, 6, 9...)
            v1_file = v1_layer_path / f"{hour}.png"

            # V2 : toutes les heures, on prend les natives
            v2_file = v2_layer_path / f"{hour}.png"

            if not v1_file.exists():
                print(f"  Heure {hour:3d} : ⚠️  V1 manquant")
                continue

            if not v2_file.exists():
                print(f"  Heure {hour:3d} : ⚠️  V2 manquant")
                continue

            result = compare_images(v1_file, v2_file)

            if result is None:
                print(f"  Heure {hour:3d} : ❌ Erreur comparaison")
                continue

            results.append(result)

            # Afficher résultat
            status = "✅" if result['similarity'] == 100.0 else "⚠️ " if result['similarity'] > 95.0 else "❌"
            print(f"  Heure {hour:3d} : {status} {result['similarity']:.2f}% identique ({result['different']} pixels diff)")

        # Statistiques globales
        if results:
            avg_similarity = np.mean([r['similarity'] for r in results])
            min_similarity = np.min([r['similarity'] for r in results])
            max_similarity = np.max([r['similarity'] for r in results])
            total_diff = np.sum([r['different'] for r in results])

            print(f"\n{'─' * 70}")
            print(f"STATISTIQUES {layer}:")
            print(f"  Similarité moyenne : {avg_similarity:.2f}%")
            print(f"  Similarité min     : {min_similarity:.2f}%")
            print(f"  Similarité max     : {max_similarity:.2f}%")
            print(f"  Pixels différents  : {total_diff} (total sur {len(results)} heures)")

            if avg_similarity == 100.0:
                print(f"  ✅ HEURES NATIVES 100% IDENTIQUES")
                print(f"  → Différences constatées viennent de l'INTERPOLATION TEMPORELLE")
            elif avg_similarity > 95.0:
                print(f"  ⚠️  HEURES NATIVES ~{avg_similarity:.1f}% IDENTIQUES")
                print(f"  → Petites différences même sur heures natives")
                print(f"  → Probable: arrondis, conversions Squants, ou ColorMap bins")
            else:
                print(f"  ❌ HEURES NATIVES SIGNIFICATIVEMENT DIFFÉRENTES")
                print(f"  → Problème parsing GRIB ou calculs dérivés")
        else:
            print("\n  ❌ Aucune heure native comparable trouvée")

    print(f"\n{'=' * 70}")
    print("CONCLUSION:")
    print("=" * 70)
    print()
    print("Si similarité heures natives = 100% → Cause = Interpolation temporelle")
    print("Si similarité heures natives < 100% → Cause = Parsing/Calculs/ColorMaps")
    print()

if __name__ == "__main__":
    main()
