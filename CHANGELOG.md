# Changelog

All notable changes to Wireless Redstone are listed here. Versions follow
[Semantic Versioning](https://semver.org/).

## [0.1.0] — 2026-11-10

Initial release, for Minecraft 26.3 (Fabric).

### Added

- **Wireless Panel**: one block with transmitter and receiver modes, toggled with an empty-hand
  right-click. Mounts on floors, walls and ceilings.
- **Dye channels**: three dye slots per panel (4,096 channels), shown as colored pips. Add dyes
  with right-click, remove the last with sneak-right-click.
- Analog signal strength (0–15), carrying the strongest transmitter on the channel.
- Fixed 2-game-tick delay at any distance; feedback loops are safe.
- Floor panels work through the side their arrow points to; wall and ceiling panels work like
  levers. Redstone dust only connects where a panel reads or outputs.
- Green status light and a five-LED strength gauge on every panel.
- **Wireless Remote**: right-click a panel to copy its channel, right-click elsewhere for a
  one-second full-strength pulse.
- Unlimited range within a dimension, with an optional `maxRange` server setting.
- Never force-loads chunks; saved channel state keeps unloaded transmitters' signals and cleans
  up after transmitters removed by commands or editors.

[0.1.0]: https://github.com/wbellman/wireless-redstone/releases/tag/v0.1.0
