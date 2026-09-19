package dev.tore.schemaforge.core.view;

import java.util.OptionalDouble;

/** Player state the safety stops watch (P3-03); production implementation {@code compat/McSafetyView}. */
public interface SafetyView {
    float health();

    int food();

    /** Distance to the nearest other player, empty if nobody else is around. */
    OptionalDouble nearestOtherPlayer();
}
