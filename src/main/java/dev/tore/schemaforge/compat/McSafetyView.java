package dev.tore.schemaforge.compat;

import dev.tore.schemaforge.core.view.SafetyView;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;

import java.util.OptionalDouble;

/** {@link SafetyView} over the local player and the level around it (P3-03); client thread only. */
public final class McSafetyView implements SafetyView {
    private final LocalPlayer player;
    private final ClientLevel level;

    public McSafetyView(LocalPlayer player, ClientLevel level) {
        this.player = player;
        this.level = level;
    }

    @Override
    public float health() {
        return player.getHealth();
    }

    @Override
    public int food() {
        return player.getFoodData().getFoodLevel();
    }

    /** Spectators are ignored: they cannot interfere with the build and are often staff just looking. */
    @Override
    public OptionalDouble nearestOtherPlayer() {
        return level.players().stream()
            .filter(other -> other != player && !other.isSpectator())
            .mapToDouble(this::distanceTo)
            .min();
    }

    private double distanceTo(AbstractClientPlayer other) {
        return player.position().distanceTo(other.position());
    }
}
