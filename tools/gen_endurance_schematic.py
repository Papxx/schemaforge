"""Generate test/endurance.litematic, the schematic for the 30 minute unattended run (ticket P3-06).

Usage:
    pip install -r tools/requirements.txt
    python tools/gen_endurance_schematic.py [output path]

Layout (60 x 30 x 60, x/z across, y up). The point is not block variety - that is what
blockclasses.litematic is for - but keeping the printer busy long enough, far enough apart and
across enough clusters that navigation, restock pauses and the verifying rounds all get exercised:

    y 0          full stone floor, 60 x 60. One flat layer, so the run starts with pure placement
                 work and the player has something to stand on everywhere.
    y 1..29      hollow stone walls on a 15 block grid (x or z divisible by 15). Hollow, so the
                 printer has to walk around instead of standing in one spot: with cluster-size 5
                 that is well over a thousand clusters, far more than fit in one reach.
    y 4, 14, 24  oak plank bands in the walls, so a wrong layer order shows up as a visible stripe.

Only two block types (stone, oak planks) on purpose: a restock run should fail because the
inventory ran out, not because some block class has no placement rule yet.

The printed material list is what a chest setup for the run has to cover.
"""

import sys
from collections import Counter
from pathlib import Path

from litemapy import BlockState, Region

# Minecraft 26.2 (version.json "world_version") and Litematica 0.28.8 (LitematicaSchematic.SCHEMATIC_VERSION).
MC_DATA_VERSION = 4903
LITEMATIC_VERSION = 7
LITEMATIC_SUBVERSION = 1

WIDTH, HEIGHT, LENGTH = 60, 30, 60
GRID = 15
BANDS = (4, 14, 24)


def block(block_id, **properties):
    return BlockState("minecraft:" + block_id, **properties)


def is_wall(x, z):
    """Walls sit on a grid, so the inside of every 15x15 cell stays empty."""
    return x % GRID == 0 or z % GRID == 0


def build():
    region = Region(0, 0, 0, WIDTH, HEIGHT, LENGTH)
    stone = block("stone")
    planks = block("oak_planks")

    for x in range(WIDTH):
        for z in range(LENGTH):
            region[x, 0, z] = stone
            if not is_wall(x, z):
                continue
            for y in range(1, HEIGHT):
                region[x, y, z] = planks if y in BANDS else stone
    return region


def materials(region):
    counts = Counter()
    for x, y, z in ((x, y, z) for x in range(WIDTH) for y in range(HEIGHT) for z in range(LENGTH)):
        state = region[x, y, z]
        if state.id != "minecraft:air":
            counts[state.id.removeprefix("minecraft:")] += 1
    return counts


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "test" / "endurance.litematic"
    out.parent.mkdir(parents=True, exist_ok=True)

    region = build()
    schematic = region.as_schematic(name="endurance", author="SchemaForge",
                                    description="SchemaForge endurance run (P3-06)", mc_version=MC_DATA_VERSION)
    schematic.lm_version = LITEMATIC_VERSION
    schematic.lm_subversion = LITEMATIC_SUBVERSION
    schematic.save(str(out))

    counts = materials(region)
    total = sum(counts.values())
    print(f"Wrote {out} ({WIDTH}x{LENGTH}x{HEIGHT})")
    print("Material list (item -> count):")
    for item, count in sorted(counts.items(), key=lambda e: (-e[1], e[0])):
        print(f"  minecraft:{item:<26} {count}")
    print(f"  {'total':<36} {total}")
    print(f"  {'shulker boxes of 1728':<36} {total / 1728:.1f}")


if __name__ == "__main__":
    main()
