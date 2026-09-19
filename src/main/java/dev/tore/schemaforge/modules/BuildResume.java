package dev.tore.schemaforge.modules;

import dev.tore.schemaforge.SchemaForgeAddon;
import dev.tore.schemaforge.core.BuildCheckpoint;
import dev.tore.schemaforge.core.PlanConfig;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Checkpoint persistence (P3-04): while this module is on, the SchemaPrinter saves where the build stands
 * every {@code interval} seconds and when it stops, so a disconnect can be picked up with {@code .sf resume}.
 * The module only holds the setting and the file handling; the printer decides when to call it.
 */
public final class BuildResume extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
        .name("interval")
        .description("Seconds between checkpoints; the build also saves one when it stops.")
        .defaultValue(30)
        .range(5, 300)
        .sliderRange(5, 120)
        .build());

    public BuildResume() {
        super(SchemaForgeAddon.CATEGORY, "build-resume", "Saves build checkpoints and resumes after a disconnect.");
    }

    /** Checkpoint file of one placement (ARCHITECTURE.md §6). */
    public static Path file(Path folder, String placement) {
        return folder.resolve("resume-" + placement.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json");
    }

    public long intervalMs() {
        return interval.get() * 1000L;
    }

    /** Saves only while the module is on, so switching it off really stops writing files. */
    public void save(Path folder, String placement, int clusterIndex, int placedCount, long startedAt, PlanConfig config) {
        if (!isActive()) return;
        new BuildCheckpoint(clusterIndex, placedCount, startedAt, BuildCheckpoint.fingerprint(config, placement))
            .save(file(folder, placement));
    }

    /** The saved position, or empty if there is none, it is broken or it was written for other settings (AK2). */
    public Resume check(Path folder, String placement, PlanConfig config) {
        Optional<BuildCheckpoint> saved = BuildCheckpoint.load(file(folder, placement));
        if (saved.isEmpty()) return new Resume.None();
        if (!saved.get().fits(config, placement)) return new Resume.Stale(saved.get());
        return new Resume.Usable(saved.get());
    }

    public static void discard(Path folder, String placement) {
        BuildCheckpoint.delete(file(folder, placement));
    }

    /** What {@code .sf start} found: nothing, a checkpoint from other settings, or one it can continue from. */
    public sealed interface Resume {
        record None() implements Resume {
        }

        record Stale(BuildCheckpoint checkpoint) implements Resume {
        }

        record Usable(BuildCheckpoint checkpoint) implements Resume {
        }
    }
}
