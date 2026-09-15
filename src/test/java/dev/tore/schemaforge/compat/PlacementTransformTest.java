package dev.tore.schemaforge.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** P1-02 AK1: all 4 rotations × 3 mirror states against a hand-verified table. */
class PlacementTransformTest {
    /*
     * Input (x, y, z) = (1, 5, 2). Mirror first: LEFT_RIGHT → (1, 5, -2), FRONT_BACK → (-1, 5, 2).
     * Then rotate (x, z): CW90 → (-z, x), CW180 → (-x, -z), CCW90 → (z, -x); y never changes.
     * Sanity anchor: CW90 turns NORTH (0, 0, -1) into EAST (1, 0, 0), as vanilla Rotation does.
     */
    @ParameterizedTest(name = "{0} + {1} → ({2}, 5, {3})")
    @CsvSource({
        "NONE,        NONE,                 1,  2",
        "NONE,        CLOCKWISE_90,        -2,  1",
        "NONE,        CLOCKWISE_180,       -1, -2",
        "NONE,        COUNTERCLOCKWISE_90,  2, -1",
        "LEFT_RIGHT,  NONE,                 1, -2",
        "LEFT_RIGHT,  CLOCKWISE_90,         2,  1",
        "LEFT_RIGHT,  CLOCKWISE_180,       -1,  2",
        "LEFT_RIGHT,  COUNTERCLOCKWISE_90, -2, -1",
        "FRONT_BACK,  NONE,                -1,  2",
        "FRONT_BACK,  CLOCKWISE_90,        -2, -1",
        "FRONT_BACK,  CLOCKWISE_180,        1, -2",
        "FRONT_BACK,  COUNTERCLOCKWISE_90,  2,  1",
    })
    void transformMatchesTable(Mirror mirror, Rotation rotation, int x, int z) {
        assertEquals(new BlockPos(x, 5, z), PlacementTransform.transform(new BlockPos(1, 5, 2), mirror, rotation));
    }

    @Test
    void northBecomesEastOnClockwise90() {
        assertEquals(new BlockPos(1, 0, 0), PlacementTransform.transform(new BlockPos(0, 0, -1), Mirror.NONE, Rotation.CLOCKWISE_90));
    }

    @Test
    void relativeEndMovesEachComponentOneTowardZero() {
        assertEquals(new BlockPos(2, 0, -3), PlacementTransform.relativeEnd(new BlockPos(3, 1, -4)));
    }

    @Test
    void untransformedRegionSpansItsAreaSize() {
        PlacementTransform.Box box = PlacementTransform.subRegionBox(
            new BlockPos(100, 64, 200), Mirror.NONE, Rotation.NONE,
            BlockPos.ZERO, Mirror.NONE, Rotation.NONE, new BlockPos(12, 6, 12));
        assertEquals(new BlockPos(100, 64, 200), box.min());
        assertEquals(new BlockPos(111, 69, 211), box.max());
    }

    @Test
    void negativeAreaSizeExtendsTowardNegativeAxis() {
        PlacementTransform.Box box = PlacementTransform.subRegionBox(
            BlockPos.ZERO, Mirror.NONE, Rotation.NONE,
            BlockPos.ZERO, Mirror.NONE, Rotation.NONE, new BlockPos(-3, 2, 4));
        assertEquals(new BlockPos(-2, 0, 0), box.min());
        assertEquals(new BlockPos(0, 1, 3), box.max());
    }

    /*
     * Region at (2, 0, 1) with size (3, 2, 4), placement CW90 + LEFT_RIGHT at origin (10, 0, 10):
     *   start = transform((2, 0, 1)) = mirror (2, 0, -1) → CW90 (1, 0, 2) → + origin (11, 0, 12)
     *   end   = relativeEnd (2, 1, 3) → mirror (2, 1, -3) → CW90 (3, 1, 2) → + start (14, 1, 14)
     */
    @Test
    void placementTransformMovesRegionPositionAndExtent() {
        PlacementTransform.Box box = PlacementTransform.subRegionBox(
            new BlockPos(10, 0, 10), Mirror.LEFT_RIGHT, Rotation.CLOCKWISE_90,
            new BlockPos(2, 0, 1), Mirror.NONE, Rotation.NONE, new BlockPos(3, 2, 4));
        assertEquals(new BlockPos(11, 0, 12), box.min());
        assertEquals(new BlockPos(14, 1, 14), box.max());
    }

    /*
     * Same region, placement untransformed, but the sub-region itself turned CW180:
     *   start = (2, 0, 1)   end = relativeEnd (2, 1, 3) → CW180 (-2, 1, -3) → + start (0, 1, -2)
     * The region's position is not moved by its own rotation, only its extent.
     */
    @Test
    void subRegionRotationOnlyTurnsExtentAroundRegionOrigin() {
        PlacementTransform.Box box = PlacementTransform.subRegionBox(
            BlockPos.ZERO, Mirror.NONE, Rotation.NONE,
            new BlockPos(2, 0, 1), Mirror.NONE, Rotation.CLOCKWISE_180, new BlockPos(3, 2, 4));
        assertEquals(new BlockPos(0, 0, -2), box.min());
        assertEquals(new BlockPos(2, 1, 1), box.max());
    }

    @Test
    void unionCoversBothBoxes() {
        PlacementTransform.Box a = new PlacementTransform.Box(new BlockPos(0, 5, 0), new BlockPos(2, 6, 2));
        PlacementTransform.Box b = new PlacementTransform.Box(new BlockPos(-1, 7, 1), new BlockPos(1, 8, 4));
        assertEquals(new PlacementTransform.Box(new BlockPos(-1, 5, 0), new BlockPos(2, 8, 4)), a.union(b));
    }
}
