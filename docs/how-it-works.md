# How it works

The complete rules for Wireless Redstone. For a quick start, see the [README](../README.md).

## The Wireless Panel

A thin, flat block that attaches to a floor, wall or ceiling, like a lever. It has an antenna,
three channel pips, a green status light and a five-LED strength gauge.

It has two **modes**, toggled by right-clicking with an empty hand (with a comparator-like
click):

- **Transmitter** — antenna tipped with a red bulb. Reads a redstone signal and sends it on its
  channel.
- **Receiver** — antenna tipped with a small dish. Outputs the signal on its channel.

New panels start as transmitters. A panel with no channel does nothing.

## Channels

- A channel is **three dye slots, in order**. With 16 dye colors that is 16³ = **4,096**
  channels. Red / Lime / Blue and Blue / Lime / Red are different channels.
- Right-click a panel with a dye to put it in the next empty slot. The dye is used up.
  Sneak-right-click with an empty hand to take the most recent dye back out.
- The pips on the panel show the colors, left to right for someone facing the panel, on floors,
  walls and ceilings alike.
- A panel needs all three dyes to be on a channel.
- Channels are **public**: anyone who uses the same three colors joins the channel.
- Channels are **per dimension**: the same colors in the Overworld and the Nether are separate
  channels.

## Signal rules

### Which side does what

Every panel is either floor-mounted or wall/ceiling-mounted.

| | Floor | Wall or ceiling |
|---|---|---|
| **Transmitter reads** | The block its arrow points at | The block it is mounted on, plus any redstone dust running into the panel |
| **Receiver outputs** | Into the block its arrow points at, strongly powering it (like a repeater's output) | Like a lever: strongly powers the block it is mounted on and weakly powers all its neighbors |

- A floor panel's red arrow points the way the player was facing when they placed it. Switching
  between transmitter and receiver keeps the same working side.
- A floor receiver does not power the block under it or the blocks to its sides and back.
- A floor transmitter ignores its sides and back.
- **Redstone dust only connects to a panel on a side that does something:** the arrow end of a
  floor panel, or any side of a wall or ceiling panel. If dust looks connected, it is.

### Strength

- Signals are **analog**: strength 0–15 carries across unchanged.
- A channel carries the **strongest** signal among its transmitters and any active remote
  pulses (that are within range of the receiver, if a range limit is set).
- Comparators can read a receiver's output strength like any other redstone component.

### Timing

- Each change takes a **fixed 2 game ticks** (one repeater tick) from a transmitter's input
  changing to its receivers' output changing, regardless of distance.
- Panels update on scheduled ticks and never update each other recursively. A receiver wired
  back into a transmitter on its own channel therefore behaves like a clock or a latch, never a
  crash or a stack overflow.

## Feedback on the block

- **Status light:** solid green while the panel's channel is carrying a signal above 0. Off when
  the panel has no channel or the channel is idle. This works for transmitters too, so you can
  see that the channel is live from either end.
- **Gauge:** five red LEDs along one edge. Each LED steps through dim, medium and bright, giving
  15 steps for strengths 1–15. A transmitter shows what it is sending; a receiver shows what it
  is outputting. Lit LEDs glow faintly in the dark.

## The Wireless Remote

A handheld, momentary transmitter.

- **Set its channel:** right-click a panel that has a channel. The remote copies it.
- **Use it:** right-click anywhere else to send a strength-15 pulse for 20 game ticks (one
  second) on its channel, from the player's position, in the player's current dimension.
- After each press there is a one-second cooldown (the length of a pulse).
- The tooltip shows the remote's channel, or how to set one.

## Unloaded chunks

- The mod **never force-loads chunks**.
- What each transmitter is sending is saved with the world. A transmitter in an unloaded chunk
  keeps holding its last value, so receivers elsewhere still see it.
- A receiver in an unloaded chunk catches up when its chunk loads.
- The remote's pulses are not saved; they only last a second.

## Stale channel data

If a transmitter disappears without being broken normally — `/fill`, `/setblock`, a world
editor, chunk corruption — its saved signal could linger. Whenever a saved transmitter's chunk
is loaded, the mod checks that a transmitter on that channel is still there and forgets it
otherwise.

## Breaking and pistons

- Breaking a panel drops the panel and every dye in it.
- Pistons pop panels off like levers (the panel and its dyes drop).
- A removed transmitter stops sending immediately; its receivers update on their next tick.

## Server configuration

`config/wireless_redstone.json`, created with defaults the first time the server starts:

```json
{
  "maxRange": 0
}
```

- `maxRange` — the maximum distance in blocks between a transmitter (or a remote) and a
  receiver. `0` means unlimited. Distances are straight-line, in the same dimension.

The file is read when the server starts.
