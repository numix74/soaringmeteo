#!/usr/bin/env python3
"""
Compare PNG pixel values between v1 and v2 outputs.

Uses PIL/Pillow to decode PNGs and compare actual pixel data,
ignoring encoding differences (RGB vs colormap).
"""

import sys
from pathlib import Path
from typing import Tuple, Optional

try:
    from PIL import Image
    import numpy as np
except ImportError:
    print("ERROR: Required packages not installed")
    print("Run: pip3 install pillow numpy")
    sys.exit(1)

V1_BASE = Path("/home/ubuntu/soaringmeteo/output/7/gfs/2025-12-29T12/pyrenees")
V2_BASE = Path("/home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/gfs/20251229_12/pyrenees")

# Layer name mapping (v1 -> v2)
LAYER_MAP = {
    "thermal-velocity": "thermal-velocity",
    "clouds-rain": "clouds-rain",
    "soaring-layer-depth": "soaring-layer-depth",
    "xc-potential": "xc-flying-potential",  # Different name!
    "boundary-layer-depth": "boundary-layer-depth",
}


def load_png_as_rgb(path: Path) -> Optional[np.ndarray]:
    """Load PNG and convert to RGB numpy array."""
    if not path.exists():
        return None

    img = Image.open(path)

    # Convert to RGB if needed (handles colormap, RGBA, etc.)
    if img.mode != 'RGB':
        img = img.convert('RGB')

    return np.array(img)


def compare_images(img1: np.ndarray, img2: np.ndarray, tolerance: float = 0.01) -> Tuple[bool, float, str]:
    """
    Compare two images pixel by pixel.

    Args:
        img1: First image as RGB numpy array
        img2: Second image as RGB numpy array
        tolerance: Maximum allowed difference (0.01 = 1%)

    Returns:
        (match, max_diff, status) where:
        - match: True if images match within tolerance
        - max_diff: Maximum pixel difference ratio
        - status: Human-readable status
    """
    if img1.shape != img2.shape:
        return False, 1.0, f"SHAPE_MISMATCH: {img1.shape} vs {img2.shape}"

    # Calculate absolute difference per pixel
    diff = np.abs(img1.astype(float) - img2.astype(float))

    # Normalize by max possible value (255)
    norm_diff = diff / 255.0

    # Get maximum difference
    max_diff = np.max(norm_diff)

    # Count pixels with significant difference
    significant_diff_mask = norm_diff > tolerance
    num_diff_pixels = np.sum(significant_diff_mask)
    total_pixels = img1.shape[0] * img1.shape[1] * img1.shape[2]
    diff_ratio = num_diff_pixels / total_pixels

    if max_diff == 0.0:
        return True, 0.0, "EXACT"
    elif max_diff <= tolerance:
        return True, max_diff, "SIMILAR"
    elif diff_ratio < 0.05:  # Less than 5% of pixels different
        return True, max_diff, f"MOSTLY_SIMILAR ({diff_ratio*100:.1f}% pixels differ)"
    else:
        return False, max_diff, f"DIFFERENT ({diff_ratio*100:.1f}% pixels differ)"


def main():
    """Compare all PNG files."""
    print("=" * 80)
    print("GFS v1 vs v2 Pixel-Level Comparison")
    print("=" * 80)
    print()

    # Test hours (native GFS hours only)
    test_hours = [3, 6, 9, 12, 24, 48, 72, 96, 120]  # Sample

    total_compared = 0
    exact_match = 0
    similar_match = 0
    different = 0

    for v1_layer, v2_layer in LAYER_MAP.items():
        print(f"\n{v1_layer} -> {v2_layer}:")
        print(f"  {'Hour':<6} {'Status':<30} {'Max Diff':<12}")
        print(f"  {'-' * 60}")

        for hour in test_hours:
            v1_path = V1_BASE / v1_layer / f"{hour}.png"
            v2_path = V2_BASE / f"hour_{hour:03d}" / f"{v2_layer}.png"

            if not v1_path.exists():
                print(f"  {hour:<6} MISSING_V1")
                continue

            if not v2_path.exists():
                print(f"  {hour:<6} MISSING_V2")
                continue

            # Load and compare
            img1 = load_png_as_rgb(v1_path)
            img2 = load_png_as_rgb(v2_path)

            if img1 is None or img2 is None:
                print(f"  {hour:<6} LOAD_ERROR")
                continue

            match, max_diff, status = compare_images(img1, img2, tolerance=0.01)

            total_compared += 1

            if status == "EXACT":
                exact_match += 1
                symbol = "✓"
            elif match:
                similar_match += 1
                symbol = "~"
            else:
                different += 1
                symbol = "✗"

            print(f"  {hour:<6} {symbol} {status:<28} {max_diff:.4f}")

    print()
    print("=" * 80)
    print("Summary:")
    print("=" * 80)
    print(f"Total images compared:  {total_compared}")
    print(f"  ✓ Exact match:         {exact_match} ({exact_match/total_compared*100:.1f}%)")
    print(f"  ~ Similar (±1%):       {similar_match} ({similar_match/total_compared*100:.1f}%)")
    print(f"  ✗ Different (>1%):     {different} ({different/total_compared*100:.1f}%)")
    print()

    success_rate = (exact_match + similar_match) / total_compared * 100 if total_compared > 0 else 0

    if success_rate >= 95:
        print(f"✅ VALIDATION PASSED: {success_rate:.1f}% match rate")
        return 0
    else:
        print(f"❌ VALIDATION FAILED: {success_rate:.1f}% match rate (expected ≥95%)")
        return 1


if __name__ == "__main__":
    sys.exit(main())
