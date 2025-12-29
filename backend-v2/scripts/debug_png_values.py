#!/usr/bin/env python3
"""Debug PNG pixel values to understand the difference."""

from PIL import Image
import numpy as np
from pathlib import Path

v1_file = Path("/home/ubuntu/soaringmeteo/output/7/gfs/2025-12-29T12/pyrenees/thermal-velocity/3.png")
v2_file = Path("/home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/gfs/20251229_12/pyrenees/hour_003/thermal-velocity.png")

print("=" * 80)
print("PNG Pixel Value Debug")
print("=" * 80)

# Load images
img_v1 = Image.open(v1_file)
img_v2 = Image.open(v2_file)

print(f"\nv1: {img_v1.mode} {img_v1.size}")
print(f"v2: {img_v2.mode} {img_v2.size}")

# Convert to RGB for comparison
if img_v1.mode != 'RGB':
    img_v1_rgb = img_v1.convert('RGB')
else:
    img_v1_rgb = img_v1

if img_v2.mode != 'RGB':
    img_v2_rgb = img_v2.convert('RGB')
else:
    img_v2_rgb = img_v2

arr_v1 = np.array(img_v1_rgb)
arr_v2 = np.array(img_v2_rgb)

print(f"\nv1 array shape: {arr_v1.shape}")
print(f"v2 array shape: {arr_v2.shape}")

# Show first 10 pixels
print("\nFirst 10 pixels (row 0):")
print("Pixel | v1 (R,G,B)      | v2 (R,G,B)      | Diff")
print("-" * 60)
for i in range(min(10, arr_v1.shape[1])):
    v1_px = arr_v1[0, i]
    v2_px = arr_v2[0, i]
    diff = np.abs(v1_px.astype(int) - v2_px.astype(int))
    print(f"{i:5d} | {str(tuple(v1_px)):15} | {str(tuple(v2_px)):15} | {tuple(diff)}")

# Statistics
print("\nValue statistics:")
print(f"v1 min: {arr_v1.min()}, max: {arr_v1.max()}, mean: {arr_v1.mean():.2f}")
print(f"v2 min: {arr_v2.min()}, max: {arr_v2.max()}, mean: {arr_v2.mean():.2f}")

# Unique colors
unique_v1 = np.unique(arr_v1.reshape(-1, 3), axis=0)
unique_v2 = np.unique(arr_v2.reshape(-1, 3), axis=0)
print(f"\nUnique colors:")
print(f"v1: {len(unique_v1)} colors")
print(f"v2: {len(unique_v2)} colors")

print("\nv1 unique colors (first 10):")
for color in unique_v1[:10]:
    print(f"  RGB{tuple(color)} = #{color[0]:02x}{color[1]:02x}{color[2]:02x}")

print("\nv2 unique colors (first 10):")
for color in unique_v2[:10]:
    print(f"  RGB{tuple(color)} = #{color[0]:02x}{color[1]:02x}{color[2]:02x}")
