package dev.tore.schemaforge.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * What the Printer actually has to do for one placement.
 * {@code requiresRealRotation} is true for rotation-dependent blocks without accurate placement.
 * Produced by the PlacementSolver in P2-02.
 */
public record PlacementPlan(
    BlockPos clickPos,
    Direction clickFace,
    Vec3 hitVec,
    float yaw,
    float pitch,
    boolean requiresRealRotation,
    Item handItem,
    boolean sneak
) {
}
