"""Generates the wireless panel's blockstate and block/item model JSON.

Run from the project root:  python tools/generate_models.py

The panel is built from multipart pieces, each authored floor-mounted with the front
(the arrow end, where floor panels read or output) facing north, then rotated per face/facing exactly like a vanilla lever:
  - base_<mode>: the plate, the three channel pips (tintindex 0-2) and the antenna
  - gauge_<0..15>: five LEDs, each stepping through off/dim/mid/bright (15 steps total)
  - light_<on|off>: the green "channel active" light (glows when on)
"""

import json
from pathlib import Path

MOD = "wireless_redstone"
ROOT = Path(__file__).resolve().parent.parent / "src" / "client" / "resources" / "assets" / MOD
MODELS = ROOT / "models"

# (face, facing) -> blockstate rotation. Floors match vanilla levers. Walls and ceilings are
# chosen so the three pips read left-to-right for someone looking at the panel (lever
# rotations would mirror them): on walls the gauge ends up on top, on ceilings it is
# the edge nearest the player who placed it.
ROTATIONS = {
    ("floor", "north"): {}, ("floor", "east"): {"y": 90},
    ("floor", "south"): {"y": 180}, ("floor", "west"): {"y": 270},
    ("wall", "north"): {"x": 270, "y": 180}, ("wall", "east"): {"x": 270, "y": 270},
    ("wall", "south"): {"x": 270}, ("wall", "west"): {"x": 270, "y": 90},
    ("ceiling", "north"): {"x": 180}, ("ceiling", "east"): {"x": 180, "y": 90},
    ("ceiling", "south"): {"x": 180, "y": 180}, ("ceiling", "west"): {"x": 180, "y": 270},
}

PARENT = "minecraft:block/thin_block"  # gives the item sensible hand/GUI transforms


def tex(name):
    return f"{MOD}:block/{name}"


def box(frm, to, texture, faces=("up", "north", "south", "east", "west"), uv=None, tint=None, **extra):
    """A cuboid whose faces all use one texture; uv defaults to the element's own footprint."""
    element = {"from": frm, "to": to, "faces": {}}
    for face in faces:
        f = {"texture": texture}
        if uv is not None:
            f["uv"] = uv
        if tint is not None:
            f["tintindex"] = tint
        element["faces"][face] = f
    element.update(extra)
    return element


def plate():
    top = {"uv": [0, 0, 16, 16], "texture": "#panel"}
    side = {"uv": [0, 14, 16, 16], "texture": "#panel"}
    return {
        "from": [0, 0, 0], "to": [16, 2, 16],
        "faces": {
            "down": {"uv": [0, 0, 16, 16], "texture": "#panel", "cullface": "down"},
            "up": top, "north": side, "south": side, "east": side, "west": side,
        },
    }


def pips():
    # Three 2x2 pips across the middle of the plate, raised a quarter pixel.
    return [
        box([x, 2, 7], [x + 2, 2.25, 9], "#pip", uv=[0, 0, 2, 2], tint=slot)
        for slot, x in enumerate((4, 7, 10))
    ]


def antenna(mode):
    rod = box([12, 2, 11], [13, 9, 12], "#antenna", uv=[7, 0, 8, 7])
    if mode == "transmitter":
        # A small redstone bulb, like a torch tip.
        tip = box([11, 9, 10], [14, 12, 13], "#tip", faces=("up", "down", "north", "south", "east", "west"),
                  uv=[0, 0, 3, 3])
        return [rod, tip]
    # A tilted dish facing forward.
    dish = box([10, 9, 9], [15, 10, 14], "#tip", faces=("up", "down", "north", "south", "east", "west"),
               uv=[0, 0, 5, 5], rotation={"origin": [12.5, 9.5, 11.5], "axis": "x", "angle": 22.5})
    return [rod, dish]


def base(mode):
    return {
        "parent": PARENT,
        "ambientocclusion": False,
        "textures": {
            "particle": tex("panel"),
            "panel": tex("panel"),
            "pip": tex("pip"),
            "antenna": tex("antenna"),
            "tip": tex("transmitter_tip" if mode == "transmitter" else "receiver_dish"),
        },
        "elements": [plate(), *pips(), *antenna(mode)],
    }


GAUGE_LEDS = 5
GAUGE_STEPS = 3  # brightness levels per LED; 5 x 3 = 15


def gauge(power):
    # gauge.png holds four 2x2 swatches in its top row: off, dim, mid, bright.
    elements = []
    for led in range(GAUGE_LEDS):
        level = max(0, min(GAUGE_STEPS, power - GAUGE_STEPS * led))
        x = 1 + led * 3
        u = level * 2
        element = box([x, 2, 2], [x + 2, 2.25, 4], "#gauge", uv=[u, 0, u + 2, 2])
        if level > 0:
            element["light_emission"] = 4 + 3 * level
        elements.append(element)
    return {"parent": PARENT, "textures": {"particle": tex("panel"), "gauge": tex("gauge")}, "elements": elements}


def light(on):
    element = box([2, 2, 11], [4, 3, 13], "#light", uv=[0, 0, 2, 2])
    if on:
        element["light_emission"] = 15
    return {
        "parent": PARENT,
        "textures": {"particle": tex("panel"), "light": tex("light_on" if on else "light_off")},
        "elements": [element],
    }


def blockstate():
    parts = []
    for (face, facing), rotation in ROTATIONS.items():
        where = {"face": face, "facing": facing}
        for mode in ("transmitter", "receiver"):
            parts.append({"when": {**where, "mode": mode}, "apply": {"model": f"{MOD}:block/panel_base_{mode}", **rotation}})
        for power in range(16):
            parts.append({"when": {**where, "power": str(power)}, "apply": {"model": f"{MOD}:block/panel_gauge_{power}", **rotation}})
        for lit in ("true", "false"):
            name = "on" if lit == "true" else "off"
            parts.append({"when": {**where, "lit": lit}, "apply": {"model": f"{MOD}:block/panel_light_{name}", **rotation}})
    return {"multipart": parts}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    for mode in ("transmitter", "receiver"):
        write(MODELS / "block" / f"panel_base_{mode}.json", base(mode))
    for power in range(16):
        write(MODELS / "block" / f"panel_gauge_{power}.json", gauge(power))
    for on in (True, False):
        write(MODELS / "block" / f"panel_light_{'on' if on else 'off'}.json", light(on))
    write(ROOT / "blockstates" / "wireless_panel.json", blockstate())

    # Items: the panel shows as a transmitter with an empty gauge; the remote is a flat sprite.
    write(ROOT / "items" / "wireless_panel.json", {"model": {"type": "minecraft:composite", "models": [
        {"type": "minecraft:model", "model": f"{MOD}:block/panel_base_transmitter"},
        {"type": "minecraft:model", "model": f"{MOD}:block/panel_gauge_0"},
        {"type": "minecraft:model", "model": f"{MOD}:block/panel_light_off"},
    ]}})
    write(MODELS / "item" / "remote.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/remote"}})
    write(ROOT / "items" / "remote.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/remote"}})
    print(f"Wrote models and blockstate under {ROOT}")


if __name__ == "__main__":
    main()
