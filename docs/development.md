# Development

How the project is put together and how to work on it.

## Requirements

- JDK 25 or newer.
- Python 3 (only to regenerate models, textures or the icon), with [Pillow](https://pypi.org/project/Pillow/) for `cut_textures.py`.
- [Aseprite](https://www.aseprite.org/) (only to edit the art).

Gradle, Minecraft, Fabric Loader and Fabric API are downloaded by the Gradle wrapper. Versions
are set in [gradle.properties](../gradle.properties); current values for new Minecraft releases
are listed at <https://fabricmc.net/develop>.

## Common tasks

| Task | Command |
|---|---|
| Build the jar (also runs the headless tests) | `./gradlew build` → `build/libs/` |
| Play with the mod | `./gradlew runClient` |
| Run a dedicated server | `./gradlew runServer` |
| Headless logic tests | `./gradlew runGameTest` |
| Screenshot test (opens the game) | `./gradlew runClientGameTest` → `build/run/clientGameTest/screenshots/` |
| Regenerate blockstate and models | `python tools/generate_models.py` |
| Rebuild block textures from the art | `python tools/cut_textures.py` |
| Rebuild the icon | `python tools/generate_icon.py` |

Minecraft 26.x is not obfuscated, so the code uses Mojang's names directly and needs no
mappings.

## Layout

```
src/main/       Common code and data (runs on clients and servers)
  java/.../wirelessredstone/
    WirelessRedstone.java          Entrypoint: registers blocks, items and events
    block/WirelessPanelBlock.java  The panel: placement, interaction, signal input and output
    block/WirelessPanelBlockEntity.java  The three dye slots
    block/PanelMode.java           Transmitter / receiver
    channel/Channel.java           Packing three dyes into a channel number
    channel/ChannelNetwork.java    Per-dimension channel state (saved with the world)
    item/RemoteItem.java           The Wireless Remote
    config/WirelessConfig.java     config/wireless_redstone.json
  resources/
    data/wireless_redstone/        Recipes and loot table
    assets/wireless_redstone/icon.png
src/client/     Client-only code and assets
  java/.../client/WirelessRedstoneClient.java  Colors the channel pips
  resources/assets/wireless_redstone/          Blockstate, models, textures, language file
src/gametest/   Game tests (a separate test mod, wireless_redstone_test)
art/            Aseprite sources and exported sheets
tools/          Scripts that generate models, textures and the icon
```

## How the signal moves

1. A transmitter's neighbor changes, so it schedules a tick (1 game tick).
2. On that tick it reads its input and records the strength in the dimension's
   `ChannelNetwork`. If the value changed, the network schedules a tick on every loaded panel on
   that channel.
3. On their tick (1 game tick later) receivers read the channel's strongest signal, update their
   block state and notify the blocks they power.

Because every step is a scheduled tick, the delay is always 2 game ticks and loops can never
recurse. `ChannelNetwork` saves transmitter strengths with the world (so unloaded transmitters
keep their signal) and keeps the list of loaded panels and remote pulses in memory only.

## Tests

`src/gametest/java/.../WirelessRedstoneGameTests.java` holds headless game tests that build small
circuits and check the results tick by tick: delay, analog strength, which sides read and power,
wall and ceiling panels, dust connections, the remote, feedback loops and stale-data cleanup.
Every test uses its own dye channel because tests run side by side in one world.

`PanelScreenshotTest` opens a real client, builds a showcase scene and saves screenshots, for
checking models and textures by eye.

`./gradlew build` runs the headless tests; the screenshot test only runs on request.

## Art and models

The panel model is built from multipart pieces so each part can change on its own:

- `panel_base_<mode>` — the plate, the three pips (tint indexes 0–2) and the antenna.
- `panel_gauge_<0..15>` — the five LEDs at each strength.
- `panel_light_<on|off>` — the green status light (emissive when on).

`tools/generate_models.py` writes these models and the blockstate, including the rotations that
keep the pips reading left to right on floors, walls and ceilings. Edit the script rather than
the JSON files.

Textures come from `art/parts.aseprite` and `art/panel.aseprite`: export them to PNG next to the
`.aseprite` files, then run `tools/cut_textures.py` to crop them into
`src/client/resources/assets/wireless_redstone/textures/`. The remote's texture comes from
`art/remote.aseprite` the same way.

The icon is defined as pixel rows in `tools/generate_icon.py`, which writes `art/icon_32.png`,
the 512 px `art/icon.png` and the 128 px copy inside the mod.
