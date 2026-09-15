"""Generate test/blockclasses.litematic, the SchemaForge test schematic (ticket P1-04).

Usage:
    pip install -r tools/requirements.txt
    python tools/gen_test_schematic.py [output path]

Layout (12 x 12 x 6, x = 0..11 across, z = 0..11 rows, y = 0..5 up):
    y 0-1  stone base; one hole at (10, 1, 6) holds the water source, enclosed by stone
    y 2    one row per block class (z below); y 3 holds the upper door halves
    z 0    full blocks: oak planks, also the wall for the wall torches in row 1
    z 1    wall torches facing S/W/E/N (supports: row 0, a plank at x=6, the terracotta at (9, 2, 2))
    z 2    glazed terracotta facing N/E/S/W
    z 3    stone buttons: floor, and wall-mounted on the terracotta
    z 4    levers: floor, and wall-mounted on the log at (4, 2, 5)
    z 5    oak logs, axis y/x/z
    z 6    oak slabs: bottom, and top next to the log (side carrier)
    z 7    oak stairs, bottom half, facing N/E/S/W
    z 8    oak trapdoors, closed and open
    z 9    oak doors, hinge left and right; plus the rail end piece at x=5
    z 10   rails: straight east-west, curve north-west at x=5
    z 11   white carpets and a three-piece oak fence (row shared, 13 classes do not fit 12 rows)

Every block state lists all of its properties so that Litematica does not fill in defaults.
The printed material list follows Litematica's rules (upper door half free, wall torch -> torch,
water source -> water bucket) for comparison with SchemaForge's materialTotals in P1-02 AK2.
"""

import sys
from collections import Counter
from pathlib import Path

from litemapy import BlockState, Region

# Minecraft 26.2 (version.json "world_version") and Litematica 0.28.8 (LitematicaSchematic.SCHEMATIC_VERSION).
MC_DATA_VERSION = 4903
LITEMATIC_VERSION = 7
LITEMATIC_SUBVERSION = 1

WIDTH, HEIGHT, LENGTH = 12, 6, 12
ROW_Y = 2
WATER = (10, 1, 6)

FALSE, TRUE = "false", "true"


def block(block_id, **properties):
    return BlockState("minecraft:" + block_id, **properties)


def build():
    region = Region(0, 0, 0, WIDTH, HEIGHT, LENGTH)
    stone = block("stone")
    for x in range(WIDTH):
        for z in range(LENGTH):
            region[x, 0, z] = stone
            region[x, 1, z] = stone
    region[WATER] = block("water", level="0")

    y = ROW_Y
    planks = block("oak_planks")

    # z 0: full blocks
    for x in range(WIDTH):
        region[x, y, 0] = planks

    # z 1: wall torches; facing points away from the supporting block
    region[1, y, 1] = block("wall_torch", facing="south")
    region[6, y, 1] = planks
    region[5, y, 1] = block("wall_torch", facing="west")
    region[7, y, 1] = block("wall_torch", facing="east")
    region[9, y, 1] = block("wall_torch", facing="north")

    # z 2: glazed terracotta
    for x, facing in ((0, "north"), (3, "east"), (6, "south"), (9, "west")):
        region[x, y, 2] = block("white_glazed_terracotta", facing=facing)

    # z 3: buttons
    region[1, y, 3] = block("stone_button", face="floor", facing="north", powered=FALSE)
    region[3, y, 3] = block("stone_button", face="wall", facing="south", powered=FALSE)

    # z 4: levers
    region[1, y, 4] = block("lever", face="floor", facing="east", powered=FALSE)
    region[4, y, 4] = block("lever", face="wall", facing="north", powered=FALSE)

    # z 5: logs
    for x, axis in ((1, "y"), (4, "x"), (7, "z")):
        region[x, y, 5] = block("oak_log", axis=axis)

    # z 6: slabs
    region[1, y, 6] = block("oak_slab", type="bottom", waterlogged=FALSE)
    region[4, y, 6] = block("oak_slab", type="top", waterlogged=FALSE)

    # z 7: stairs, one block apart so every shape stays straight
    for x, facing in ((1, "north"), (3, "east"), (5, "south"), (7, "west")):
        region[x, y, 7] = block("oak_stairs", facing=facing, half="bottom", shape="straight", waterlogged=FALSE)

    # z 8: trapdoors
    for x, is_open in ((1, FALSE), (3, TRUE)):
        region[x, y, 8] = block("oak_trapdoor", facing="north", half="bottom", open=is_open, powered=FALSE, waterlogged=FALSE)

    # z 9: doors (two blocks high)
    for x, hinge in ((1, "left"), (4, "right")):
        for half, dy in (("lower", 0), ("upper", 1)):
            region[x, y + dy, 9] = block("oak_door", facing="south", half=half, hinge=hinge, open=FALSE, powered=FALSE)
    region[5, y, 9] = block("rail", shape="north_south", waterlogged=FALSE)

    # z 10: rails
    for x in range(1, 5):
        region[x, y, 10] = block("rail", shape="east_west", waterlogged=FALSE)
    region[5, y, 10] = block("rail", shape="north_west", waterlogged=FALSE)

    # z 11: carpets and a fence connected along x
    for x in (1, 3):
        region[x, y, 11] = block("white_carpet")
    for x in (6, 7, 8):
        region[x, y, 11] = block("oak_fence", north=FALSE, south=FALSE, waterlogged=FALSE,
                                 west=TRUE if x > 6 else FALSE, east=TRUE if x < 8 else FALSE)
    return region


def item_for(state):
    """Litematica material-list item for a block state, or None if it costs nothing."""
    block_id = state.id.removeprefix("minecraft:")
    if block_id == "air":
        return None
    if block_id.endswith("_door") and state["half"] == "upper":
        return None
    if block_id == "water":
        return "water_bucket" if state["level"] == "0" else None
    if block_id == "wall_torch":
        return "torch"
    return block_id


def materials(region):
    counts = Counter()
    for x, y, z in region.block_positions():
        item = item_for(region[x, y, z])
        if item is not None:
            counts[item] += 1
    return counts


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "test" / "blockclasses.litematic"
    out.parent.mkdir(parents=True, exist_ok=True)

    region = build()
    schematic = region.as_schematic(name="blockclasses", author="SchemaForge",
                                    description="SchemaForge test schematic (P1-04)", mc_version=MC_DATA_VERSION)
    schematic.lm_version = LITEMATIC_VERSION
    schematic.lm_subversion = LITEMATIC_SUBVERSION
    schematic.save(str(out))

    counts = materials(region)
    print(f"Wrote {out} ({WIDTH}x{LENGTH}x{HEIGHT})")
    print("Material list (item -> count):")
    for item, count in sorted(counts.items(), key=lambda e: (-e[1], e[0])):
        print(f"  minecraft:{item:<26} {count}")
    print(f"  {'total':<36} {sum(counts.values())}")


if __name__ == "__main__":
    main()
