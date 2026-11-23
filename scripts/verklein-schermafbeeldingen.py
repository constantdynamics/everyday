#!/usr/bin/env python3
"""Turns the full-size screenshots that the tests render into small JPEGs for the documentation.

    verklein-schermafbeeldingen.py <folder with PNGs> <output folder>
"""
import sys
from pathlib import Path

from PIL import Image

MAX_WIDTH = 540


def main() -> None:
    source, target = Path(sys.argv[1]), Path(sys.argv[2])
    target.mkdir(parents=True, exist_ok=True)
    for old in target.glob("*.jpg"):
        old.unlink()
    for png in sorted(source.glob("*.png")):
        image = Image.open(png).convert("RGB")
        # Portrait screens become 540 wide; landscape ones keep the same pixel density.
        scale = MAX_WIDTH / min(image.width, image.height) if image.width > image.height else MAX_WIDTH / image.width
        if scale < 1:
            image = image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)
        image.save(target / (png.stem + ".jpg"), "JPEG", quality=80, optimize=True)
        print(f"{png.name} -> {png.stem}.jpg ({image.width}x{image.height})")


if __name__ == "__main__":
    main()
