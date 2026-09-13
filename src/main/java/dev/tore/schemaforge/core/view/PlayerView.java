package dev.tore.schemaforge.core.view;

import net.minecraft.world.phys.Vec3;

/** Read-only player state for testable core logic. First consumer and test fake: P2-02 (PlacementSolver). */
public interface PlayerView {
    Vec3 eyePos();

    float yaw();

    float pitch();

    double reach();

    boolean hasLineOfSight(Vec3 target);
}
