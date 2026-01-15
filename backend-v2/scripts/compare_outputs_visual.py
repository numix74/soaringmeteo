#!/usr/bin/env python3
"""
Script de comparaison visuelle outputs v1 vs v2.
Génère des images côte à côte pour inspection visuelle.
"""

import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import argparse

def create_comparison_image(v1_path: Path, v2_path: Path, output_path: Path):
    """Crée une image côte à côte comparant v1 et v2."""
    
    # Charger les images
    try:
        img_v1 = Image.open(v1_path).convert('RGB')
        img_v2 = Image.open(v2_path).convert('RGB')
    except Exception as e:
        print(f"⚠️  Erreur chargement {v1_path.name}: {e}")
        return False
    
    # Vérifier dimensions
    if img_v1.size != img_v2.size:
        print(f"⚠️  Tailles différentes: v1={img_v1.size} vs v2={img_v2.size}")
        # Redimensionner v2 pour matcher v1
        img_v2 = img_v2.resize(img_v1.size)
    
    # Créer image composite
    width, height = img_v1.size
    margin = 10
    label_height = 30
    
    composite = Image.new('RGB', 
                         (width * 2 + margin * 3, height + margin * 2 + label_height), 
                         'white')
    
    # Coller les images
    composite.paste(img_v1, (margin, margin + label_height))
    composite.paste(img_v2, (width + margin * 2, margin + label_height))
    
    # Ajouter labels
    draw = ImageDraw.Draw(composite)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 20)
    except:
        font = ImageFont.load_default()
    
    draw.text((margin + 10, 5), "v1 (production)", fill='black', font=font)
    draw.text((width + margin * 2 + 10, 5), "v2 (backend-v2)", fill='blue', font=font)
    
    # Ajouter séparateur vertical
    for y in range(margin, height + margin + label_height):
        composite.putpixel((width + margin + margin // 2, y), (200, 200, 200))
    
    # Sauvegarder
    output_path.parent.mkdir(parents=True, exist_ok=True)
    composite.save(output_path, quality=95)
    return True

def main():
    parser = argparse.ArgumentParser(description='Compare outputs v1 vs v2 visuellement')
    parser.add_argument('--layer', required=True, help='Layer à comparer (ex: thermal-velocity)')
    parser.add_argument('--hour', type=int, required=True, help='Heure à comparer (ex: 6)')
    parser.add_argument('--v1-run', default='2025-12-30T00', help='Run v1 (ex: 2025-12-30T00)')
    parser.add_argument('--v2-run', default='2025-12-30T00', help='Run v2 (ex: 2025-12-30T00)')
    parser.add_argument('--zone', default='pyrenees', help='Zone géographique')
    parser.add_argument('--output', help='Fichier de sortie (auto si non spécifié)')
    
    args = parser.parse_args()
    
    # Chemins
    v1_base = Path('/home/ubuntu/soaringmeteo/output/7/gfs')
    v2_base = Path('/home/ubuntu/soaringmeteo/backend-v2/target/output-unified/7/gfs')
    
    v1_path = v1_base / args.v1_run / args.zone / args.layer / f"{args.hour}.png"
    v2_path = v2_base / args.v2_run / args.zone / args.layer / f"{args.hour}.png"
    
    if not v1_path.exists():
        print(f"❌ Fichier v1 introuvable: {v1_path}")
        return 1
    
    if not v2_path.exists():
        print(f"❌ Fichier v2 introuvable: {v2_path}")
        return 1
    
    # Output
    if args.output:
        output_path = Path(args.output)
    else:
        output_path = Path(f"/tmp/comparison_{args.layer}_H{args.hour:03d}.png")
    
    print(f"Comparaison : {args.layer} H+{args.hour}")
    print(f"  v1: {v1_path}")
    print(f"  v2: {v2_path}")
    
    success = create_comparison_image(v1_path, v2_path, output_path)
    
    if success:
        print(f"✓ Image de comparaison créée: {output_path}")
        return 0
    else:
        return 1

if __name__ == '__main__':
    sys.exit(main())
