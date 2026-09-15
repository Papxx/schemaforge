package dev.tore.schemaforge.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Pure placement math for {@link LitematicaAdapter#snapshot}, kept free of Litematica types so it is unit-testable.
 * Behaviour matches Litematica 0.28.8 {@code SchematicPlacement.getSubRegionBoxes} (checked via javap, see
 * docs/NOTES-litematica-api.md); implemented independently. Added in P1-02.
 */
final class PlacementTransform {
    private PlacementTransform() {
    }

    /** Inclusive world-space box of one sub-region. */
    record Box(BlockPos min, BlockPos max) {
        Box union(Box other) {
            return new Box(
                new BlockPos(Math.min(min.getX(), other.min.getX()), Math.min(min.getY(), other.min.getY()), Math.min(min.getZ(), other.min.getZ())),
                new BlockPos(Math.max(max.getX(), other.max.getX()), Math.max(max.getY(), other.max.getY()), Math.max(max.getZ(), other.max.getZ()))
            );
        }
    }

    /** Mirror first ({@code LEFT_RIGHT} negates z, {@code FRONT_BACK} negates x), then rotate around the y axis. */
    static BlockPos transform(BlockPos pos, Mirror mirror, Rotation rotation) {
        int x = pos.getX();
        int z = pos.getZ();
        switch (mirror) {
            case LEFT_RIGHT -> z = -z;
            case FRONT_BACK -> x = -x;
            case NONE -> {
            }
        }
        return switch (rotation) {
            case NONE -> new BlockPos(x, pos.getY(), z);
            case CLOCKWISE_90 -> new BlockPos(-z, pos.getY(), x);
            case CLOCKWISE_180 -> new BlockPos(-x, pos.getY(), -z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, pos.getY(), -x);
        };
    }

    /**
     * Offset from a region's origin corner to its opposite corner. Area sizes are signed (the sign gives the
     * direction from the origin), so each non-negative component shrinks by one and each negative one grows by one.
     */
    static BlockPos relativeEnd(BlockPos areaSize) {
        return new BlockPos(towardZero(areaSize.getX()), towardZero(areaSize.getY()), towardZero(areaSize.getZ()));
    }

    /**
     * World box of a sub-region. The region's position only gets the placement transform; its far corner gets the
     * placement transform and then the sub-region's own one, because a sub-region rotates around its own origin.
     */
    static Box subRegionBox(BlockPos placementOrigin, Mirror placementMirror, Rotation placementRotation,
                            BlockPos regionPos, Mirror regionMirror, Rotation regionRotation, BlockPos areaSize) {
        BlockPos start = transform(regionPos, placementMirror, placementRotation).offset(placementOrigin);
        BlockPos end = transform(transform(relativeEnd(areaSize), placementMirror, placementRotation), regionMirror, regionRotation)
            .offset(start);
        return new Box(
            new BlockPos(Math.min(start.getX(), end.getX()), Math.min(start.getY(), end.getY()), Math.min(start.getZ(), end.getZ())),
            new BlockPos(Math.max(start.getX(), end.getX()), Math.max(start.getY(), end.getY()), Math.max(start.getZ(), end.getZ()))
        );
    }

    private static int towardZero(int size) {
        return size >= 0 ? size - 1 : size + 1;
    }
}
