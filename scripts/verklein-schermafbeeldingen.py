#!/usr/bin/env python3
"""Turns the full-size screenshots that the tests make into small JPEGs for the documentation.

    verklein-schermafbeeldingen.py <output folder> <folder with PNGs> [<another folder> ...]

Existing pictures are only replaced by new ones of the same name. A picture that is
no longer made is removed only when every source folder is present; when one is
missing, nothing is thrown away.
"""
import sys
from pathlib import Path

from PIL import Image

SHORT_SIDE = 540


def main() -> None:
    target = Path(sys.argv[1])
    sources = [Path(argument) for argument in sys.argv[2:]]
    complete = True
    pictures = []
    for source in sources:
        if not source.is_dir():
            print(f"(geen map {source})")
            complete = False
            continue
        # Pictures taken at the moment a test failed are for finding the cause, not for the documentation.
        pictures += [png for png in sorted(source.glob("*.png")) if not png.name.startswith("mislukt-")]
    if not pictures:
        print("Geen schermafbeeldingen gevonden; er is niets veranderd.")
        return

    target.mkdir(parents=True, exist_ok=True)
    made = set()
    for png in pictures:
        image = Image.open(png).convert("RGB")
        scale = SHORT_SIDE / min(image.width, image.height)
        if scale < 1:
            image = image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)
        image.save(target / (png.stem + ".jpg"), "JPEG", quality=80, optimize=True)
        made.add(png.stem + ".jpg")
        print(f"{png} -> {png.stem}.jpg ({image.width}x{image.height})")

    # An old picture is only removed when it is certain that it is no longer made:
    # that is, when every source folder was there.
    if complete:
        for old in sorted(target.glob("*.jpg")):
            if old.name not in made:
                old.unlink()
                print(f"{old.name} verwijderd (wordt niet meer gemaakt)")


if __name__ == "__main__":
    main()
