package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.SafetyView;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Watches for reasons to pause a running build (P3-03): damage, hunger, another player close by, the build area
 * out of view and an inventory that holds nothing the open tasks need. Decides only, pauses nothing itself.
 * The settings are read on every check, so a changed setting takes effect at once.
 */
public final class SafetyMonitor {
    public record Config(boolean pauseOnDamage, int minFood, int pausePlayerRadius) {
        public static Config defaults() {
            return new Config(true, 6, 16);
        }
    }

    public enum Reason { DAMAGE, FOOD, PLAYER_NEARBY, CHUNK_UNLOADED, NO_MATERIALS }

    /**
     * @param message    one line for the chat, already naming the reason
     * @param autoResume true continues on its own once the reason is gone; false waits for {@code .sf resume}
     */
    public record Trigger(Reason reason, String message, boolean autoResume) {
    }

    /** Health is only compared once a first value has been seen. */
    private static final float UNKNOWN_HEALTH = -1;

    /** Health lost below this is rounding, not damage. */
    private static final float DAMAGE_EPSILON = 0.01f;

    private final Supplier<Config> config;

    private float lastHealth = UNKNOWN_HEALTH;

    public SafetyMonitor(Supplier<Config> config) {
        this.config = config;
    }

    /**
     * The first reason to pause right now, checked in the order of ARCHITECTURE.md §5, or empty to carry on.
     * Called once per tick while the build runs; not while it is paused, so the health of the pausing tick stays
     * the reference and resuming after damage does not pause again at once.
     */
    public Optional<Trigger> check(SafetyView safety, boolean chunkLoaded, boolean outOfMaterials) {
        Config c = config.get();
        float health = safety.health();
        boolean hurt = lastHealth != UNKNOWN_HEALTH && health < lastHealth - DAMAGE_EPSILON;
        lastHealth = health;

        if (c.pauseOnDamage() && hurt) {
            return trigger(Reason.DAMAGE, String.format(Locale.ROOT, "Took damage, %.1f health left.", health), false);
        }
        if (safety.food() < c.minFood()) {
            return trigger(Reason.FOOD, "Food is down to " + safety.food() + "; eat something.", true);
        }
        OptionalDouble nearest = safety.nearestOtherPlayer();
        if (nearest.isPresent() && nearest.getAsDouble() <= c.pausePlayerRadius()) {
            return trigger(Reason.PLAYER_NEARBY,
                String.format(Locale.ROOT, "Another player is %.0f blocks away.", nearest.getAsDouble()), true);
        }
        if (!chunkLoaded) {
            return trigger(Reason.CHUNK_UNLOADED, "The build area is not loaded.", true);
        }
        if (outOfMaterials) {
            return trigger(Reason.NO_MATERIALS, "None of the items this cluster needs is in the inventory.", true);
        }
        return Optional.empty();
    }

    /** True once the reason behind {@code trigger} is gone; DAMAGE always waits for {@code .sf resume}. */
    public boolean cleared(Trigger trigger, SafetyView safety, boolean chunkLoaded, boolean outOfMaterials) {
        Config c = config.get();
        return switch (trigger.reason()) {
            case DAMAGE -> false;
            case FOOD -> safety.food() >= c.minFood();
            case PLAYER_NEARBY -> safety.nearestOtherPlayer().orElse(Double.MAX_VALUE) > c.pausePlayerRadius();
            case CHUNK_UNLOADED -> chunkLoaded;
            case NO_MATERIALS -> !outOfMaterials;
        };
    }

    private static Optional<Trigger> trigger(Reason reason, String message, boolean autoResume) {
        return Optional.of(new Trigger(reason, message, autoResume));
    }
}
