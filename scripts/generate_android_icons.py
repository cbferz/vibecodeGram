#!/usr/bin/env python3
"""Generate Android launcher mipmap assets from assets/app-icon/icon.png."""
from pathlib import Path
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets" / "app-icon" / "icon.png"
RES = ROOT / "app" / "src" / "main" / "res"
SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

if not SOURCE.is_file():
    raise SystemExit(f"Launcher icon not found: {SOURCE}")

with Image.open(SOURCE) as original:
    image = ImageOps.exif_transpose(original).convert("RGBA")
    if image.width != image.height:
        side = min(image.size)
        left = (image.width - side) // 2
        top = (image.height - side) // 2
        image = image.crop((left, top, left + side, top + side))
    for folder, size in SIZES.items():
        target_dir = RES / folder
        target_dir.mkdir(parents=True, exist_ok=True)
        output = image.resize((size, size), Image.Resampling.LANCZOS)
        output.save(target_dir / "ic_launcher.png", format="PNG", optimize=True)
        output.save(target_dir / "ic_launcher_round.png", format="PNG", optimize=True)
        print(f"Generated {folder}/ic_launcher.png ({size}x{size})")

print("ANDROID_LAUNCHER_ICONS_GENERATED")
