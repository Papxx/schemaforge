# SchemaForge

**A Litematica printer for Meteor Client.** SchemaForge builds the placement you have loaded in Litematica block by
block, walks between work areas with Baritone and fetches missing material from chests it has seen, without breaking
anything unless you allow it.

Minecraft **26.2** · Fabric · Meteor Client addon · GPL-3.0

> **Status: beta.** Everything below is implemented and covered by unit tests (275 of them, no Minecraft client
> needed). In-game checks so far: the addon loads, `.sf doctor` and `.sf preview` work, and printing with Baritone
> navigation and the safety pauses runs. Container restock, undo, fluids, rails, the FAST profile and accurate placement
> have **not** been tried in-game yet. Please report what you find.

---

## Features

### Printer
- **Prints a Litematica placement** you choose by name. Enabled sub-regions, rotation and mirroring are read straight
  from Litematica.
- **Additive-only by default.** Wrong blocks already in the build area are left alone and listed as *mismatched*.
  Baritone is not allowed to break blocks during a run either.
- **Works in clusters.** The build is split into small cubes, done layer by layer and nearest-first. Each cluster is
  checked again before the printer moves on.
- **Support-aware order.** Full blocks come first. Torches, buttons, doors, carpets, ladders, signs and top slabs follow
  once the block they hang on exists. Up to three rounds catch blocks whose support only appeared later.
- **Vanilla-correct orientation.** Stairs, slabs, logs, furnaces, glazed terracotta, trapdoors, doors (including the
  hinge), buttons, levers, wall torches, ladders and standing signs are placed by clicking the right face with the right
  look direction.
- **Packet pacing.** Every packet goes through a per-tick budget. The `profile` setting chooses how fast:
  - `VANILLA_LEGIT`: one block per tick, with the camera really turning.
  - `FAST`: four per tick, with spoofed rotation. You get a chat warning, because anti-cheat plugins watch for this.
  - `CUSTOM`: your own values.
- **Server-friendly clicking.** It clicks existing neighbour blocks only (no air-placing, which Paper and Spigot
  reject), checks reach and line of sight, and sneaks while clicking so chests and doors next to the target don't open.
- **Hotbar handling.** Only the hotbar slots you allow (default `2-8`) are used. Items are swapped in from the main
  inventory when needed.

### Getting around
- **Baritone navigation** to clusters that are out of reach. A cluster Baritone can't reach is retried later and skipped
  after three tries.
- **Works without Baritone.** It then builds whatever is within reach and tells you so.

### Materials
- **`.sf preview`** shows size, block count, clusters, material totals, what is missing from your inventory, blocks the
  printer can't place and why, plus warnings (below or above build height, unloaded chunks).
- **`.sf materials`** is a table per item: total need, need for the next clusters, in your inventory, in known
  containers.
- **Container index.** Chests, barrels, shulker boxes and your ender chest are remembered when you open them. `.sf scan`
  walks to every container nearby and opens each one. The index is saved per server and dimension.
- **Automatic restock.** When the printer runs out, it walks to the nearest container that has the items, takes what
  the next few clusters need, and walks back. Nothing is ever dropped: if your inventory is full, only items from your
  trash list are put back into the container.
- **Shulkers from your inventory** (optional): place, take out, break and pick up again.

### Safety and convenience
- **Automatic pause** on damage, low food, another player nearby, an unloaded chunk or no material left. It continues
  on its own once the reason is gone; after damage it waits for `.sf resume`.
- **Resume after disconnect.** Checkpoints are saved while you build; `.sf resume` continues at the cluster where you
  stopped.
- **Progress HUD**: state, percent, blocks per minute, ETA and the most-missed items.
- **`.sf undo <n>`** takes back the last *n* blocks SchemaForge placed, even after a restart. It only removes a block if
  it is still exactly what was placed.

### Extras
- **Temporary supports** (additive-only off): a dirt block under a floating block, removed again when the cluster is
  done.
- **Fluids**: places water and lava sources with a bucket from a neighbour block, and only counts a real source block as
  done.
- **Rails**: straight pieces first, curves last. Any rail that vanilla connected differently from the schematic is
  reported.
- **Accurate placement**: if Litematica reports the EasyPlace protocol V2 (Carpet `accurateBlockPlacement`) or V3
  (Servux), the orientation is sent inside the click, so rotated blocks don't need the player to turn.

---

## Requirements

| | Version | Needed for |
|---|---|---|
| Minecraft | 26.2 | – |
| Fabric Loader | 0.19.3 or newer | – |
| Meteor Client | 26.2 snapshot | **required** |
| Litematica + MaLiLib | 0.28.8 / 0.29.6 (need Fabric API) | printing; the addon loads without them |
| Baritone (Meteor fork, 26.2) | 26.2-SNAPSHOT | walking to clusters and containers; optional |

The addon never crashes because a soft dependency is missing. It tells you in chat and `.sf doctor` shows what is there.

## Installation

1. Install Fabric Loader for Minecraft 26.2 and put **Meteor Client** into your `mods` folder.
2. Add **Litematica**, **MaLiLib** and **Fabric API**. Optionally add **Baritone** (the Meteor build for 26.2).
3. Download `schemaforge-<version>.jar` from the [releases](https://github.com/Papxx/schemaforge/releases) and put it
   into `mods`.
4. Start the game and run `.sf doctor`. Every line should say `OK`.

## Quick start

1. Load and place a schematic in Litematica as usual.
2. `.sf preview` checks size, materials and anything the printer can't place.
3. Fill your inventory, or open your storage chests once (or run `.sf scan`) so the restock knows where things are.
4. `.sf start` begins the build. If several placements are loaded, use `.sf start <name>`.
5. `.sf status`, `.sf pause`, `.sf resume` and `.sf stop` control the run. Add the **build-progress** HUD element if you
   want to keep an eye on it.

## Commands

| Command | What it does |
|---|---|
| `.sf doctor` | Versions of Minecraft, Meteor, Baritone, Litematica; checks every Litematica method SchemaForge relies on |
| `.sf preview [placement]` | Dry run: size, clusters, materials, missing items, unsupported blocks, warnings |
| `.sf start [placement]` | Starts printing (offers a saved checkpoint first, if one fits) |
| `.sf pause` / `.sf resume` / `.sf stop` | Controls the run; `resume` also continues from a checkpoint |
| `.sf status` | State, round, cluster *i/n*, blocks placed, blocks per minute, mismatched, left to place |
| `.sf materials [placement]` | Material table: total, next clusters, inventory, known containers, missing |
| `.sf containers` | The container index, nearest first |
| `.sf scan [radius]` / `.sf scan stop` | Visits and opens every container in loaded chunks (default radius 32) |
| `.sf undo <n>` / `.sf undo stop` | Removes the last *n* blocks SchemaForge placed (not while a build runs) |

## Modules and main settings

All modules are in Meteor's **SchemaForge** category.

**schema-printer**: the printer itself.

| Group | Settings |
|---|---|
| General | `placement`, `additive-only` (on), `ignore-air` (on) |
| Order | `layer-axis` (Y), `layer-ascending`, `cluster-size` (5) |
| Filters | `skip-if-world-is`, `treat-as-air` (grass), `never-place` (TNT), `substitutes` (`stone->cobblestone,andesite`), `ignore-properties` (waterlogged) |
| Placement | `profile` (VANILLA_LEGIT), `reach` (4.5), `line-of-sight`, `click-adjacent-only`, `allowed-hotbar-slots` (2-8), `use-accurate-placement`, `handle-fluids`; `blocks-per-tick`, `tick-interval`, `rotation-spoof` with profile CUSTOM |
| Safety | `pause-on-damage`, `min-food` (6), `pause-player-radius` (16, 0 = off) |
| Supports | `temp-supports`, `support-blocks` (only with additive-only off) |

**container-restock**: `learn-passively`, `lookahead-clusters` (3), `clicks-per-tick` (2), `trash`,
`use-inventory-shulkers` (off), `stale-after-hours` (24).

**build-resume**: `interval`, how often a checkpoint is written.

## On servers

- Keep `additive-only` on unless you want wrong blocks broken. With it on, SchemaForge only ever breaks blocks it
  placed itself (undo, the shulker it set down).
- `VANILLA_LEGIT` behaves like a careful player: one block per tick, real rotation, vanilla reach, no air-placing.
  `FAST` and `rotation-spoof` are what anti-cheat plugins (Grim, Vulcan and similar) look for, so use them only where
  you are allowed to.
- Accurate placement only switches on when Litematica says the server supports it. On a vanilla server nothing
  changes.

## Files

SchemaForge writes to `.minecraft/meteor-client/schemaforge/`:

- `containers-<server>-<dimension>.json` is the container index. The server address is stored only as a short hash.
- `resume-<placement>.json` is the checkpoint for `.sf resume`.
- `placementlog-<placement>.jsonl` has one line per placed block and is used by `.sf undo`.

## Building from source

Needs JDK 25.

```
./gradlew build          # jar in build/libs/, runs all unit tests
./gradlew test           # tests only: planner, solver, printer, restock ... against fakes, no game needed
./gradlew runClient      # dev client; put Litematica, MaLiLib and Fabric API jars into run/mods/
```

Before the first `runClient`, run `python tools/patch_malilib_dev.py` once. MaLiLib switches on a GPU debug check in
development environments that crashes Minecraft 26.2; normal game installs are not affected (details in
`docs/NOTES-devclient-crash.md`).

The design documents in `docs/` are in German: `ARCHITECTURE.md` (interfaces), `TASKS.md` (tickets and backlog),
`PLAN.md` (background) and `TESTLOG.md` (in-game test results).

## License and credits

SchemaForge is licensed under the **GNU GPL v3.0 only**, see [LICENSE](LICENSE).

- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) (GPL-3.0): addon API, rotation and inventory
  utilities.
- [Baritone](https://github.com/cabaletta/baritone) (LGPL-3.0): used through `baritone.api` only.
- [Litematica](https://github.com/maruohon/litematica) and MaLiLib (LGPL-3.0): read through a small reflection adapter
  that checks every method on start-up. The accurate placement format follows Litematica's EasyPlace protocol.
- No code is taken from the AGPL-licensed *litematica-printer*. Where it served as a reference, only the described
  behaviour was used, and the implementation was written from scratch.
