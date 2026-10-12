"""Generate application icon formats from the approved LoongSim.png artwork.

Run with Python 3 and Pillow: python scripts/update-app-icons.py
"""

from pathlib import Path

from PIL import Image


ICON_DIR = (
    Path(__file__).resolve().parents[1]
    / "logisim/src/main/resources/resources/logisim/img"
)
WINDOW_SIZES = (16, 20, 24, 32, 48, 64, 128, 256)
ICO_SIZES = (16, 20, 24, 32, 40, 48, 64, 96, 128, 256)


def main():
    with Image.open(ICON_DIR / "LoongSim.png") as source:
        artwork = source.convert("RGBA")
    if artwork.width != artwork.height:
        raise ValueError("LoongSim.png must be square")

    for size in WINDOW_SIZES:
        artwork.resize((size, size), Image.Resampling.LANCZOS).save(
            ICON_DIR / f"logisim-icon-{size}.png"
        )

    artwork.save(
        ICON_DIR / "LoongSim.ico",
        format="ICO",
        sizes=[(size, size) for size in ICO_SIZES],
    )
    artwork.save(ICON_DIR / "LoongSim.icns", format="ICNS")
    print(f"Updated PNG, ICO and ICNS icons in {ICON_DIR}")


if __name__ == "__main__":
    main()
