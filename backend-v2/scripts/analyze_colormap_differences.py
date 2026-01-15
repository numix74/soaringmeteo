#!/usr/bin/env python3
"""
Analyse détaillée des différences de pixels entre v1 et v2.

Objectif : Déterminer si les différences sont dues aux frontières de bins ColorMap.
"""

import numpy as np
from PIL import Image
from pathlib import Path
from collections import Counter

# Chemins
V1_BASE = Path("/home/ubuntu/soaringmeteo/output/7/gfs")
V2_BASE = Path("/home/ubuntu/soaringmeteo/output-unified/7/gfs")

# Configuration
RUN_DATE = "2026-01-06T00"
ZONE = "pyrenees"

# ColorMaps (copié depuis Raster.scala)
XC_FLYING_POTENTIAL_COLORMAP = {
    10: (0x33, 0x33, 0x33),
    20: (0x99, 0x00, 0x99),
    30: (0xff, 0x00, 0x00),
    40: (0xff, 0x99, 0x00),
    50: (0xff, 0xcc, 0x00),
    60: (0xff, 0xff, 0x00),
    70: (0x66, 0xff, 0x00),
    80: (0x00, 0xff, 0xff),
    90: (0x0000ff >> 16, 0x0000ff >> 8, 0x0000ff),
}

CLOUDS_RAIN_COLORMAP = {
    5.0: (0xff, 0xff, 0xff, 0x00),
    20.0: (0xff, 0xff, 0xff, 0xff),
    40.0: (0xbd, 0xbd, 0xbd, 0xff),
    60.0: (0x88, 0x88, 0x88, 0xff),
    80.0: (0x4d, 0x4d, 0x4d, 0xff),
    100.2: (0x11, 0x11, 0x11, 0xff),
    101.0: (0x9d, 0xf8, 0xf6, 0xff),
    102.0: (0x00, 0x00, 0xff, 0xff),
    104.0: (0x2a, 0x93, 0x3b, 0xff),
    106.0: (0x49, 0xff, 0x36, 0xff),
    110.0: (0xfc, 0xff, 0x2d, 0xff),
    120.0: (0xfa, 0xca, 0x1e, 0xff),
    130.0: (0xf8, 0x7c, 0x00, 0xff),
    150.0: (0xf7, 0x0c, 0x00, 0xff),
    200.0: (0xac, 0x00, 0xdb, 0xff),
}

def rgb_distance(rgb1, rgb2):
    """Calcule la distance euclidienne entre deux couleurs RGB(A)."""
    # Tronquer à 3 composantes (ignorer alpha si présent)
    r1, g1, b1 = rgb1[:3]
    r2, g2, b2 = rgb2[:3]
    return np.sqrt((r1 - r2)**2 + (g1 - g2)**2 + (b1 - b2)**2)

def find_nearest_bin(rgb, colormap):
    """Trouve le bin ColorMap le plus proche d'une couleur RGB."""
    min_dist = float('inf')
    nearest_value = None
    nearest_color = None

    for value, color in colormap.items():
        dist = rgb_distance(rgb, color)
        if dist < min_dist:
            min_dist = dist
            nearest_value = value
            nearest_color = color

    return nearest_value, nearest_color, min_dist

def analyze_layer(layer_name, hours_to_check):
    """Analyse les différences pour une couche donnée."""
    print(f"\n{'=' * 80}")
    print(f"ANALYSE DÉTAILLÉE: {layer_name}")
    print(f"{'=' * 80}\n")

    # Sélectionner le ColorMap
    if layer_name == "xc-flying-potential":
        v1_layer_name = "xc-potential"
        colormap = XC_FLYING_POTENTIAL_COLORMAP
        has_alpha = False
    elif layer_name == "clouds-rain":
        v1_layer_name = "clouds-rain"
        colormap = CLOUDS_RAIN_COLORMAP
        has_alpha = True
    else:
        print(f"❌ Couche {layer_name} non supportée")
        return

    v1_layer_path = V1_BASE / RUN_DATE / ZONE / v1_layer_name
    v2_layer_path = V2_BASE / RUN_DATE / ZONE / layer_name

    if not v1_layer_path.exists() or not v2_layer_path.exists():
        print(f"❌ Chemins introuvables")
        return

    all_v1_colors = []
    all_v2_colors = []
    all_v1_bins = []
    all_v2_bins = []
    rgb_distances = []
    bin_transitions = []

    for hour in hours_to_check:
        v1_file = v1_layer_path / f"{hour}.png"
        v2_file = v2_layer_path / f"{hour}.png"

        if not v1_file.exists() or not v2_file.exists():
            continue

        # Charger images
        img1 = np.array(Image.open(v1_file))
        img2 = np.array(Image.open(v2_file))

        if img1.shape != img2.shape:
            print(f"  ⚠️  Heure {hour}: tailles différentes")
            continue

        # Trouver pixels différents
        if has_alpha:
            different_mask = ~np.all(img1 == img2, axis=-1)
        else:
            different_mask = ~np.all(img1 == img2, axis=-1)

        diff_count = np.sum(different_mask)

        if diff_count == 0:
            continue

        print(f"\n📍 Heure {hour}: {diff_count} pixels différents")

        # Analyser chaque pixel différent
        diff_y, diff_x = np.where(different_mask)

        for i in range(min(10, len(diff_y))):  # Limiter à 10 exemples
            y, x = diff_y[i], diff_x[i]
            v1_color = tuple(img1[y, x])
            v2_color = tuple(img2[y, x])

            # Trouver bins ColorMap
            v1_bin, v1_bin_color, v1_dist = find_nearest_bin(v1_color, colormap)
            v2_bin, v2_bin_color, v2_dist = find_nearest_bin(v2_color, colormap)

            # Distance RGB
            color_distance = rgb_distance(v1_color, v2_color)

            print(f"  Pixel ({x},{y}):")
            print(f"    V1: RGB={v1_color[:3]} → Bin {v1_bin} (dist={v1_dist:.2f})")
            print(f"    V2: RGB={v2_color[:3]} → Bin {v2_bin} (dist={v2_dist:.2f})")
            print(f"    Distance RGB V1↔V2: {color_distance:.2f}")

            if v1_bin != v2_bin:
                bin_transitions.append((v1_bin, v2_bin))
                print(f"    ⚠️  TRANSITION DE BIN: {v1_bin} → {v2_bin}")
            else:
                print(f"    ✓ Même bin, différence RGB mineure")

            all_v1_colors.append(v1_color[:3])
            all_v2_colors.append(v2_color[:3])
            all_v1_bins.append(v1_bin)
            all_v2_bins.append(v2_bin)
            rgb_distances.append(color_distance)

    # Statistiques globales
    print(f"\n{'─' * 80}")
    print(f"STATISTIQUES GLOBALES {layer_name}:")
    print(f"{'─' * 80}")

    if len(rgb_distances) > 0:
        print(f"  Distance RGB moyenne: {np.mean(rgb_distances):.2f}")
        print(f"  Distance RGB min/max: {np.min(rgb_distances):.2f} / {np.max(rgb_distances):.2f}")
        print(f"  Total pixels analysés: {len(rgb_distances)}")

        bin_change_count = sum(1 for v1, v2 in zip(all_v1_bins, all_v2_bins) if v1 != v2)
        bin_change_pct = (bin_change_count / len(all_v1_bins)) * 100

        print(f"\n  Transitions de bins: {bin_change_count}/{len(all_v1_bins)} ({bin_change_pct:.1f}%)")

        if bin_transitions:
            print(f"\n  Transitions les plus fréquentes:")
            transition_counts = Counter(bin_transitions)
            for (v1_bin, v2_bin), count in transition_counts.most_common(5):
                print(f"    {v1_bin} → {v2_bin}: {count} fois")

        print(f"\n  Bins V1 utilisés: {sorted(set(all_v1_bins))}")
        print(f"  Bins V2 utilisés: {sorted(set(all_v2_bins))}")

        # Conclusion
        print(f"\n{'─' * 80}")
        print(f"CONCLUSION pour {layer_name}:")
        print(f"{'─' * 80}")

        if bin_change_pct > 70:
            print(f"  ⚠️  {bin_change_pct:.1f}% des différences = TRANSITIONS DE BINS")
            print(f"  → Cause principale: valeurs brutes tombent dans bins adjacents")
            print(f"  → Arrondis numériques ou conversions Squants légèrement différents")
        elif bin_change_pct > 30:
            print(f"  ⚠️  {bin_change_pct:.1f}% des différences = transitions de bins")
            print(f"  → Mix de transitions de bins + différences RGB mineures")
        else:
            print(f"  ✓ {bin_change_pct:.1f}% seulement sont des transitions de bins")
            print(f"  → La plupart des différences = variations RGB mineures dans même bin")
    else:
        print("  ℹ️  Aucune différence trouvée dans les heures analysées")

def main():
    print("=" * 80)
    print("ANALYSE COLORMAP - Différences v1 vs v2")
    print("=" * 80)
    print(f"Run: {RUN_DATE}")
    print(f"Zone: {ZONE}")
    print()

    # Heures natives avec différences significatives
    clouds_rain_hours = [6, 12, 27, 84]  # Heures avec <95% similarité
    xc_potential_hours = [12, 36, 39, 108, 111]  # Heures avec <99% similarité

    print("Analyse heures avec plus de différences (échantillon représentatif)")

    analyze_layer("clouds-rain", clouds_rain_hours)
    analyze_layer("xc-flying-potential", xc_potential_hours)

    print(f"\n{'=' * 80}")
    print("ANALYSE COMPLÉTÉE")
    print("=" * 80)

if __name__ == "__main__":
    main()
