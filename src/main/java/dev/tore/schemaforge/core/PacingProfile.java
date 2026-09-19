package dev.tore.schemaforge.core;

import java.util.Optional;

/**
 * Packet pacing presets (P5-05, PLAN 3.1 point 5): how many placements per tick, on which ticks, and whether the
 * rotation is only sent to the server. CUSTOM takes the values of the individual settings.
 */
public enum PacingProfile {
    /** One block per tick with the camera really turning - what a player could do by hand. */
    VANILLA_LEGIT,
    /** Several blocks per tick with spoofed rotations; faster, and what anti-cheat plugins look for. */
    FAST,
    /** The blocks-per-tick, tick-interval and rotation-spoof settings as set. */
    CUSTOM;

    /** Placements per acting tick, act on every nth tick, rotation only for the server. */
    public record Pacing(int blocksPerTick, int tickInterval, boolean rotationSpoof) {
    }

    public static final Pacing LEGIT = new Pacing(1, 1, false);
    public static final Pacing FAST_PACING = new Pacing(4, 1, true);

    /** The pacing this profile stands for; {@code custom} only counts for {@link #CUSTOM}. */
    public Pacing resolve(Pacing custom) {
        return switch (this) {
            case VANILLA_LEGIT -> LEGIT;
            case FAST -> FAST_PACING;
            case CUSTOM -> custom;
        };
    }

    /** Chat warning when this profile is switched on or a run starts with it. */
    public Optional<String> warning() {
        if (this != FAST) return Optional.empty();
        return Optional.of("Profile FAST: " + FAST_PACING.blocksPerTick() + " blocks per tick with spoofed rotations. "
            + "Anti-cheat plugins (Grim, Vulcan and the like) check rotation consistency and may flag or kick you; "
            + "use VANILLA_LEGIT on servers you do not run yourself.");
    }
}
