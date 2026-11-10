"""Builds the mod icon from 32x32 pixel rows (same art as the Aseprite sketch).

Run from the project root:  python tools/generate_icon.py

Writes:
  art/icon_32.png                                   the 1x source (open in Aseprite to edit)
  art/icon.png                                      512x512, for Modrinth
  src/main/resources/assets/wireless_redstone/icon.png   128x128, shown by Mod Menu
Edit ROWS / PALETTE here (or redraw icon_32.png and copy the rows back) and rerun.
"""

import struct
import zlib
from pathlib import Path

MOD = Path(__file__).resolve().parent.parent

ROWS = [
    "BBBBBBBBvBBBBBBBBBBBBBBvBBBBBBBB",
    "BBBBBBBvBBBBBBBBBBBBBBBBvBBBBBBB",
    "BBBBBBvBBBBBBBBBBBBBBBBBBvBBBBBB",
    "BBBBBvBBBBBwBBBBBBBBwBBBBBvBBBBB",
    "BBBBBvBBBBwBBBOOOOBBBwBBBBvBBBBB",
    "BBBBvBBBBwBBBORRrrOBBBwBBBBvBBBB",
    "BBBBvBBBBwBBBORrrrOBBBwBBBBvBBBB",
    "BBBBvBBBBwBBBOrrrqOBBBwBBBBvBBBB",
    "BBBBvBBBBwBBBOrrqqOBBBwBBBBvBBBB",
    "BBBBBvBBBBwBBBOqqOBBBwBBBBvBBBBB",
    "BBBBBvBBBBBwBBOaAOBBwBBBBBvBBBBB",
    "BBBBBBvBBBBBBBOaAOBBBBBBBvBBBBBB",
    "BBBBBBBvBBBBBBOaAOBBBBBBvBBBBBBB",
    "BBBBBBBBvBBBBBOaAOBBBBBvBBBBBBBB",
    "BBBBBBBBBBBBBBOaAOBBBBBBBBBBBBBB",
    "BBBBBBBBBBBBBBOaAOBBBBBBBBBBBBBB",
    "BBBBBBBBBBBBBBOaAOBBBBBBBBBBBBBB",
    "BBBBBBBBBBBBBBOaAOBBBBBBBBBBBBBB",
    "BBBBBBBBBBBBBBOaAOBBBBBBBBBBBBBB",
    "BBBBOOOOOOOOOOOaAOOOOOOOOOOOBBBB",
    "BBBBOLLLLLLLLLLLLLLLLLLLLLLOzBBB",
    "BBBBOLssLssLssLssLkkLLLGGGLOzBBB",
    "BBBBOLssLssLssLssLkkLLLHHHLOzBBB",
    "BBBBOLLLLLLLLLLLLLLLLLLLLLLOzBBB",
    "BBBBOLLLrrrrLLggggLLbbbbLLLOzBBB",
    "BBBBOLLLrrrrLLggggLLbbbbLLLOzBBB",
    "BBBBOLLLqqqqLLhhhhLLnnnnLLLOzBBB",
    "BBBBOMMMMMMMMMMMMMMMMMMMMMMOzBBB",
    "BBBBODDDDDDDDDDDDDDDDDDDDDDOzBBB",
    "BBBBOOOOOOOOOOOOOOOOOOOOOOOOzBBB",
    "BBBBBzzzzzzzzzzzzzzzzzzzzzzzzBBB",
    "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
]

PALETTE = {
    "B": "1E2533",  # background
    "z": "121820",  # drop shadow
    "O": "0C0F15",  # outline
    "L": "C9CED6", "M": "9AA1AD", "D": "6B7280",  # plate
    "s": "FF4D3D", "k": "4A1512",  # gauge LEDs lit / unlit
    "G": "39FF6A", "H": "1F9E45",  # channel light
    "r": "E43B2F", "R": "FF8A7A", "q": "8F1D16",  # red pip and bulb
    "g": "7ED321", "h": "4E8F12",  # lime pip
    "b": "3B5BDB", "n": "253A8F",  # blue pip
    "a": "D0D4DA", "A": "8A919C",  # antenna rod
    "w": "FF5A4A", "v": "A8362D",  # signal waves
}


def write_png(path, scale):
    size = len(ROWS) * scale
    raw = bytearray()
    for row in ROWS:
        line = bytearray([0])  # filter: none
        for ch in row:
            line += bytes.fromhex(PALETTE[ch]) * scale
        raw += bytes(line) * scale

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print(f"Wrote {path} ({size}x{size})")


def main():
    assert all(len(r) == 32 for r in ROWS) and len(ROWS) == 32
    write_png(MOD / "art" / "icon_32.png", 1)
    write_png(MOD / "art" / "icon.png", 16)
    write_png(MOD / "src" / "main" / "resources" / "assets" / "wireless_redstone" / "icon.png", 4)


if __name__ == "__main__":
    main()
