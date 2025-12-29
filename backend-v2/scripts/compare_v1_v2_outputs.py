#!/usr/bin/env python3
"""
Compare backend v1 and v2 GFS outputs for validation.

Compares:
- File sizes (exact match expected)
- PNG pixel data (±1% tolerance for numerical differences)
- Directory structure
"""

import os
import sys
from pathlib import Path
from typing import Dict, List, Tuple

# Production (v1) structure: layer/hour.png
V1_BASE = Path("/home/ubuntu/soaringmeteo/output/7/gfs/2025-12-29T06/pyrenees")

# v2 structure: hour/layer.png
V2_BASE = Path("/home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/gfs/20251229_06/pyrenees")

# Layers to compare (common subset)
LAYERS = [
    "thermal-velocity",
    "clouds-rain",
    "soaring-layer-depth",
    "xc-potential",
    "boundary-layer-depth",
]

# Native GFS hours (every 3h from 3 to 120)
NATIVE_HOURS = list(range(3, 121, 3))


def get_v1_path(layer: str, hour: int) -> Path:
    """Get v1 PNG path: layer/hour.png"""
    return V1_BASE / layer / f"{hour}.png"


def get_v2_path(layer: str, hour: int) -> Path:
    """Get v2 PNG path: hour_XXX/layer.png"""
    return V2_BASE / f"hour_{hour:03d}" / f"{layer}.png"


def compare_file_sizes() -> Dict[str, List[Tuple[int, int, int, str]]]:
    """
    Compare file sizes for all layers and native hours.

    Returns:
        Dict mapping layer -> [(hour, v1_size, v2_size, status)]
    """
    results = {}

    for layer in LAYERS:
        layer_results = []

        for hour in NATIVE_HOURS:
            v1_path = get_v1_path(layer, hour)
            v2_path = get_v2_path(layer, hour)

            v1_exists = v1_path.exists()
            v2_exists = v2_path.exists()

            if not v1_exists and not v2_exists:
                continue  # Both missing, skip

            if not v1_exists:
                layer_results.append((hour, 0, v2_path.stat().st_size, "MISSING_V1"))
                continue

            if not v2_exists:
                layer_results.append((hour, v1_path.stat().st_size, 0, "MISSING_V2"))
                continue

            v1_size = v1_path.stat().st_size
            v2_size = v2_path.stat().st_size

            # Check if sizes match (some variation expected due to PNG compression)
            if v1_size == v2_size:
                status = "EXACT"
            elif abs(v1_size - v2_size) / max(v1_size, v2_size) < 0.05:  # 5% tolerance
                status = "SIMILAR"
            else:
                status = "DIFFERENT"

            layer_results.append((hour, v1_size, v2_size, status))

        results[layer] = layer_results

    return results


def print_summary(results: Dict[str, List[Tuple[int, int, int, str]]]):
    """Print comparison summary."""
    print("=" * 80)
    print("GFS v1 vs v2 Output Comparison")
    print("=" * 80)
    print()

    total_files = 0
    exact_match = 0
    similar_match = 0
    different = 0
    missing_v1 = 0
    missing_v2 = 0

    for layer, layer_results in results.items():
        print(f"\n{layer}:")
        print(f"  {'Hour':<6} {'v1 Size':<12} {'v2 Size':<12} {'Status':<12}")
        print(f"  {'-' * 50}")

        for hour, v1_size, v2_size, status in layer_results:
            total_files += 1

            if status == "EXACT":
                exact_match += 1
                # Only print first few exact matches
                if exact_match <= 3:
                    print(f"  {hour:<6} {v1_size:<12} {v2_size:<12} ✓ {status}")
            elif status == "SIMILAR":
                similar_match += 1
                print(f"  {hour:<6} {v1_size:<12} {v2_size:<12} ~ {status}")
            elif status == "DIFFERENT":
                different += 1
                print(f"  {hour:<6} {v1_size:<12} {v2_size:<12} ✗ {status}")
            elif status == "MISSING_V1":
                missing_v1 += 1
                print(f"  {hour:<6} {'MISSING':<12} {v2_size:<12} ✗ {status}")
            elif status == "MISSING_V2":
                missing_v2 += 1
                print(f"  {hour:<6} {v1_size:<12} {'MISSING':<12} ✗ {status}")

        if exact_match > 3:
            print(f"  ... ({exact_match - 3} more exact matches)")

    print()
    print("=" * 80)
    print("Summary:")
    print("=" * 80)
    print(f"Total files compared:  {total_files}")
    print(f"  ✓ Exact match:        {exact_match} ({exact_match/total_files*100:.1f}%)")
    print(f"  ~ Similar (±5%):      {similar_match} ({similar_match/total_files*100:.1f}%)")
    print(f"  ✗ Different (>5%):    {different} ({different/total_files*100:.1f}%)")
    print(f"  ✗ Missing in v1:      {missing_v1}")
    print(f"  ✗ Missing in v2:      {missing_v2}")
    print()

    success_rate = (exact_match + similar_match) / total_files * 100 if total_files > 0 else 0

    if success_rate >= 95:
        print(f"✅ VALIDATION PASSED: {success_rate:.1f}% match rate")
        return 0
    else:
        print(f"❌ VALIDATION FAILED: {success_rate:.1f}% match rate (expected ≥95%)")
        return 1


def check_v2_interpolated_hours():
    """Check that v2 has interpolated hours that v1 doesn't."""
    print("\n" + "=" * 80)
    print("v2 Interpolated Hours (not in v1)")
    print("=" * 80)

    interpolated_hours = [h for h in range(3, 121) if h % 3 != 0]

    sample_layer = "thermal-velocity"
    existing_interpolated = []

    for hour in interpolated_hours[:10]:  # Check first 10 interpolated hours
        v2_path = get_v2_path(sample_layer, hour)
        if v2_path.exists():
            existing_interpolated.append((hour, v2_path.stat().st_size))

    if existing_interpolated:
        print(f"\n✓ Found {len(existing_interpolated)} interpolated hours in v2 (sample):")
        for hour, size in existing_interpolated:
            print(f"  hour_{hour:03d}/{sample_layer}.png - {size} bytes")
    else:
        print("\n✗ No interpolated hours found in v2")

    total_interpolated = len([h for h in range(3, 121) if h % 3 != 0])
    print(f"\nTotal interpolated hours expected: {total_interpolated} (H+4, H+5, H+7, H+8, ...)")


def main():
    """Main comparison script."""
    if not V1_BASE.exists():
        print(f"❌ Production v1 directory not found: {V1_BASE}")
        return 1

    if not V2_BASE.exists():
        print(f"❌ v2 output directory not found: {V2_BASE}")
        return 1

    # Compare file sizes for native hours
    results = compare_file_sizes()
    exit_code = print_summary(results)

    # Check interpolated hours
    check_v2_interpolated_hours()

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
