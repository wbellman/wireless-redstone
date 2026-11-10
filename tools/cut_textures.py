"""Cuts the exported art sheets into the mod's textures.

Edit the .aseprite files in ../art, export each to a .png of the same name, then run:
    python tools/cut_textures.py

Each small part is pasted at the top-left of a 16x16 texture, which is where the
models' UVs (see generate_models.py) look for it.
"""

from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ART = HERE.parent / "art"
TEXTURES = HERE.parent / "src" / "client" / "resources" / "assets" / "wireless_redstone" / "textures"

# texture name -> (x, y, width, height) on parts.png
PARTS = {
    "pip": (0, 0, 2, 2),
    "light_off": (3, 0, 2, 2),
    "light_on": (6, 0, 2, 2),
    "transmitter_tip": (9, 0, 3, 3),
    "receiver_dish": (0, 4, 5, 5),
    "gauge": (0, 12, 8, 2),
}
# The antenna rod samples column x=7, rows 0-6 (see generate_models.py), so keep its position.
ANTENNA = (7, 4, 1, 7)


def save(image, folder, name):
    out = TEXTURES / folder / f"{name}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    image.save(out)
    print(f"wrote {out.relative_to(HERE.parent)}")


def main():
    parts = Image.open(ART / "parts.png").convert("RGBA")
    for name, (x, y, w, h) in PARTS.items():
        tile = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        tile.paste(parts.crop((x, y, x + w, y + h)), (0, 0))
        save(tile, "block", name)

    x, y, w, h = ANTENNA
    antenna = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    antenna.paste(parts.crop((x, y, x + w, y + h)), (7, 0))
    save(antenna, "block", "antenna")

    save(Image.open(ART / "panel.png").convert("RGBA"), "block", "panel")
    save(Image.open(ART / "remote.png").convert("RGBA"), "item", "remote")


if __name__ == "__main__":
    main()
