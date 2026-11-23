#!/usr/bin/env python3
"""Turns the full-size screenshots that the tests make into small JPEGs for the documentation.

    verklein-schermafbeeldingen.py <output folder> <folder with PNGs> [<another folder> ...]

Folders that do not exist are skipped, so a failed test job does not hide the
pictures of the other one.
"""
import sys
from pathlib import Path

from PIL import Image

SHORT_SIDE = 540


def main() -> None:
    target = Path(sys.argv[1])
    sources = [Path(argument) for argument in sys.argv[2:]]
    target.mkdir(parents=True, exist_ok=True)
    for old in target.glob("*.jpg"):
        old.unlink()
    for source in sources:
        if not source.is_dir():
            print(f"(geen map {source})")
            continue
        for png in sorted(source.glob("*.png")):
            image = Image.open(png).convert("RGB")
            scale = SHORT_SIDE / min(image.width, image.height)
            if scale < 1:
                image = image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)
            image.save(target / (png.stem + ".jpg"), "JPEG", quality=80, optimize=True)
            print(f"{png} -> {png.stem}.jpg ({image.width}x{image.height})")


if __name__ == "__main__":
    main()
