package dev.tore.schemaforge.modules;

import dev.tore.schemaforge.SchemaForgeAddon;
import dev.tore.schemaforge.compat.BaritoneBridge;
import dev.tore.schemaforge.compat.LitematicaAdapter;
import dev.tore.schemaforge.compat.McInventoryView;
import dev.tore.schemaforge.compat.McPlayerView;
import dev.tore.schemaforge.compat.McPrintActions;
import dev.tore.schemaforge.compat.McSafetyView;
import dev.tore.schemaforge.compat.McWorldView;
import dev.tore.schemaforge.core.ActionBudget;
import dev.tore.schemaforge.core.BuildCheckpoint;
import dev.tore.schemaforge.core.AdditiveOnlyGuard;
import dev.tore.schemaforge.core.BuildSession;
import dev.tore.schemaforge.core.HotbarSlots;
import dev.tore.schemaforge.core.MaterialManager;
import dev.tore.schemaforge.core.Navigator;
import dev.tore.schemaforge.core.PacingProfile;
import dev.tore.schemaforge.core.PlacementLog;
import dev.tore.schemaforge.core.PlacementSolver;
import dev.tore.schemaforge.core.PlanConfig;
import dev.tore.schemaforge.core.RestockProcess;
import dev.tore.schemaforge.core.SafetyMonitor;
import dev.tore.schemaforge.core.Printer;
import dev.tore.schemaforge.core.SchematicSnapshot;
import dev.tore.schemaforge.core.SolverConfig;
import dev.tore.schemaforge.core.TempSupports;
import dev.tore.schemaforge.core.UndoSession;
import dev.tore.schemaforge.core.Substitutes;
import dev.tore.schemaforge.core.WorkPlanner;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ProvidedStringSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Main module (P2-07): holds all settings, builds the views for one run and ticks the {@link BuildSession}.
 * Started by toggling the module or {@code .sf start}; the state machine itself lives in core (ARCHITECTURE.md §5).
 */
public final class SchemaPrinter extends Module {
    /** Directory for the placement log (ARCHITECTURE.md §6). */
    private static final Path FOLDER = MeteorClient.FOLDER.toPath().resolve("schemaforge");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgOrder = settings.createGroup("Order");
    private final SettingGroup sgFilters = settings.createGroup("Filters");
    private final SettingGroup sgPlacement = settings.createGroup("Placement");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgSupports = settings.createGroup("Supports");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    private final Setting<String> placement = sgGeneral.add(new ProvidedStringSetting.Builder()
        .name("placement")
        .description("Name of the Litematica placement to print; empty uses the only loaded one.")
        .defaultValue("")
        .supplier(() -> LitematicaAdapter.placementNames().stream().distinct().toArray(String[]::new))
        .build());

    private final Setting<Boolean> additiveOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("additive-only")
        .description("Never break blocks: wrong blocks are reported as mismatched and Baritone may not break either.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> ignoreAir = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-air")
        .description("Ignore positions where the schematic wants air.")
        .defaultValue(true)
        .build());

    private final Setting<Direction.Axis> layerAxis = sgOrder.add(new EnumSetting.Builder<Direction.Axis>()
        .name("layer-axis")
        .description("Axis the build advances along.")
        .defaultValue(Direction.Axis.Y)
        .build());

    private final Setting<Boolean> layerAscending = sgOrder.add(new BoolSetting.Builder()
        .name("layer-ascending")
        .description("Build layers from low to high along the layer axis.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> clusterSize = sgOrder.add(new IntSetting.Builder()
        .name("cluster-size")
        .description("Edge length in blocks of one work cluster.")
        .defaultValue(5)
        .range(3, 16)
        .sliderRange(3, 16)
        .build());

    private final Setting<List<Block>> skipIfWorldIs = sgFilters.add(new BlockListSetting.Builder()
        .name("skip-if-world-is")
        .description("Positions holding one of these blocks are left alone.")
        .build());

    private final Setting<List<Block>> treatAsAir = sgFilters.add(new BlockListSetting.Builder()
        .name("treat-as-air")
        .description("Blocks counted as air, in the world and in the schematic.")
        .defaultValue(Blocks.SHORT_GRASS, Blocks.TALL_GRASS)
        .build());

    private final Setting<List<Block>> neverPlace = sgFilters.add(new BlockListSetting.Builder()
        .name("never-place")
        .description("Blocks the printer never places.")
        .defaultValue(Blocks.TNT)
        .build());

    private final Setting<List<String>> substitutes = sgFilters.add(new StringListSetting.Builder()
        .name("substitutes")
        .description("Accepted replacements, one line per target block: stone->cobblestone,andesite")
        .build());

    private final Setting<List<String>> ignoreProperties = sgFilters.add(new StringListSetting.Builder()
        .name("ignore-properties")
        .description("Block state properties that may differ from the schematic.")
        .defaultValue("waterlogged")
        .build());

    private final Setting<PacingProfile> profile = sgPlacement.add(new EnumSetting.Builder<PacingProfile>()
        .name("profile")
        .description("VANILLA_LEGIT: 1 block per tick, real rotation. FAST: 4 per tick, spoofed rotation (risky). CUSTOM: the settings below.")
        .defaultValue(PacingProfile.VANILLA_LEGIT)
        .onChanged(this::onProfileChanged)
        .build());

    private final Setting<Integer> blocksPerTick = sgPlacement.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("Actions per tick; above 1 sends several placements per tick. Profile CUSTOM only.")
        .defaultValue(1)
        .range(1, 8)
        .sliderRange(1, 4)
        .visible(() -> profile.get() == PacingProfile.CUSTOM)
        .build());

    private final Setting<Integer> tickInterval = sgPlacement.add(new IntSetting.Builder()
        .name("tick-interval")
        .description("Act only on every nth tick. Profile CUSTOM only.")
        .defaultValue(1)
        .range(1, 20)
        .sliderRange(1, 10)
        .visible(() -> profile.get() == PacingProfile.CUSTOM)
        .build());

    private final Setting<Double> reach = sgPlacement.add(new DoubleSetting.Builder()
        .name("reach")
        .description("Upper bound for the placement distance; the server's own range still applies.")
        .defaultValue(4.5)
        .range(1, 4.5)
        .sliderRange(1, 4.5)
        .build());

    private final Setting<Boolean> lineOfSight = sgPlacement.add(new BoolSetting.Builder()
        .name("line-of-sight")
        .description("Only place where the player can see the hit point.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> clickAdjacentOnly = sgPlacement.add(new BoolSetting.Builder()
        .name("click-adjacent-only")
        .description("Only click existing neighbour blocks, never the target position itself (no airplace).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> rotationSpoof = sgPlacement.add(new BoolSetting.Builder()
        .name("rotation-spoof")
        .description("Only the server sees the rotation; the camera does not turn. Profile CUSTOM only.")
        .defaultValue(false)
        .visible(() -> profile.get() == PacingProfile.CUSTOM)
        .build());

    private final Setting<String> allowedHotbarSlots = sgPlacement.add(new StringSetting.Builder()
        .name("allowed-hotbar-slots")
        .description("Hotbar slots the printer may use, as on the keyboard: 2-8 or 1,3,5-7")
        .defaultValue("2-8")
        .build());

    private final Setting<Boolean> handleFluids = sgPlacement.add(new BoolSetting.Builder()
        .name("handle-fluids")
        .description("Place water and lava sources from the schematic with buckets, clicking a neighbour block.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> pauseOnDamage = sgSafety.add(new BoolSetting.Builder()
        .name("pause-on-damage")
        .description("Pause the build when the player takes damage; continue with .sf resume.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> minFood = sgSafety.add(new IntSetting.Builder()
        .name("min-food")
        .description("Pause below this food level; the build continues on its own after eating.")
        .defaultValue(6)
        .range(0, 20)
        .sliderRange(0, 20)
        .build());

    private final Setting<Integer> pausePlayerRadius = sgSafety.add(new IntSetting.Builder()
        .name("pause-player-radius")
        .description("Pause while another player is this close; 0 turns the check off.")
        .defaultValue(16)
        .range(0, 128)
        .sliderRange(0, 64)
        .build());

    private final Setting<Boolean> tempSupports = sgSupports.add(new BoolSetting.Builder()
        .name("temp-supports")
        .description("Place a temporary block where a target has nothing to be clicked against, remove it at the end of the cluster. Needs additive-only off.")
        .defaultValue(false)
        .visible(() -> !additiveOnly.get())
        .build());

    private final Setting<List<Block>> supportBlocks = sgSupports.add(new BlockListSetting.Builder()
        .name("support-blocks")
        .description("Blocks used as temporary supports, first one in the inventory wins.")
        .defaultValue(Blocks.DIRT, Blocks.COBBLESTONE, Blocks.NETHERRACK)
        .visible(() -> !additiveOnly.get() && tempSupports.get())
        .build());

    private final Setting<Boolean> logStateChanges = sgDebug.add(new BoolSetting.Builder()
        .name("log-state-changes")
        .description("Print every state change of the build to chat.")
        .defaultValue(false)
        .build());

    private final AdditiveOnlyGuard guard = new AdditiveOnlyGuard(BaritoneBridge.BREAK_SETTINGS);
    private final ActionBudget budget = new ActionBudget(this::budgetLimit);
    /** Items already reported as missing in this run; the chat warning fires once per item. */
    private final Set<Item> reportedShortages = new HashSet<>();
    /** Shortages of the cluster being worked on, for the progress HUD (P3-05). */
    private final Map<Item, Integer> currentShortages = new LinkedHashMap<>();

    private Optional<BuildSession> session = Optional.empty();
    /** Running {@code .sf undo}, if any (P5-01); it ticks here so it shares the packet budget. */
    private Optional<UndoSession> undo = Optional.empty();
    /** Plan settings of the running build; the checkpoint fingerprint is taken from these (P3-04). */
    private Optional<PlanConfig> planInRun = Optional.empty();
    /** Cluster the next activation starts at, set by {@code .sf resume} without a running build (P3-04). */
    private int resumeFromCluster;
    private long runStartedAt;
    private long lastCheckpointAt;
    /** Cluster the shortage list belongs to; a new cluster clears it so the HUD cannot show stale items. */
    private int shortagesOfCluster = -1;
    /** Pathfinder of the running build; a new one per run, so blacklisted clusters do not carry over (P3-02). */
    private Navigator navigator = new Navigator(BaritoneBridge.PATHING, System::currentTimeMillis);
    /** Status of the last run, kept for {@code .sf status} after it ended. */
    private Optional<BuildSession.Status> lastStatus = Optional.empty();
    private HotbarSlots hotbarSlots = HotbarSlots.parse("2-8");
    private boolean rotationSpoofInRun;
    private long tickCounter;
    /** True while {@link #toggle()} runs: tells a real toggle from Meteor re-activating the module on world join. */
    private boolean inToggle;
    private boolean stopNextTick;

    public SchemaPrinter() {
        super(SchemaForgeAddon.CATEGORY, "schema-printer", "Prints the active Litematica placement into the world.");
    }

    @Override
    public void toggle() {
        inToggle = true;
        try {
            super.toggle();
        } finally {
            inToggle = false;
        }
    }

    @Override
    public void onActivate() {
        // Meteor re-activates still-active modules on world join (also after a restart from the config).
        // A run only ever starts from a toggle, never on its own.
        if (!inToggle) {
            stopNextTick = true;
            return;
        }
        if (mc.level == null || mc.player == null) {
            error("Join a world first.");
            toggle();
            return;
        }
        Optional<BuildSession> started = startRun();
        if (started.isEmpty()) {
            toggle();
            return;
        }
        session = started;
        guard.engage(additiveOnly.get());
        runStartedAt = System.currentTimeMillis();
        lastCheckpointAt = runStartedAt;
        started.get().start(resumeFromCluster);
        resumeFromCluster = 0;
    }

    @Override
    public void onDeactivate() {
        Modules.get().get(ContainerRestock.class).cancelRestock();
        session.map(BuildSession::status).ifPresent(status -> lastStatus = Optional.of(status));
        // A checkpoint on stop as well, so a deliberate .sf stop can be picked up too (P3-04).
        session.ifPresent(run -> saveCheckpoint(run.status()));
        session = Optional.empty();
        planInRun = Optional.empty();
        resumeFromCluster = 0;
        navigator.cancel();
        reportedShortages.clear();
        currentShortages.clear();
        shortagesOfCluster = -1;
        stopNextTick = false;
        guard.release();
    }

    /** Builds everything one run needs; empty after an error message in chat. */
    private Optional<BuildSession> startRun() {
        if (!LitematicaAdapter.isPresent()) {
            error("Litematica is missing (see .sf doctor).");
            return Optional.empty();
        }
        List<String> names = LitematicaAdapter.placementNames();
        if (names.isEmpty()) {
            error("No Litematica placements loaded (or Litematica incompatible, see .sf doctor).");
            return Optional.empty();
        }
        String name = placement.get().isBlank() ? names.getFirst() : placement.get();
        if (!names.contains(name)) {
            error("Placement '%s' not found. Loaded: %s", name, String.join(", ", names.stream().distinct().toList()));
            return Optional.empty();
        }
        if (placement.get().isBlank() && names.size() > 1) {
            error("Several placements loaded, choose one with .sf start <name> or the placement setting.");
            return Optional.empty();
        }

        Optional<HotbarSlots> slots = parseHotbarSlots();
        if (slots.isEmpty()) return Optional.empty();
        hotbarSlots = slots.get();

        Substitutes.Parsed parsed = Substitutes.parse(substitutes.get());
        parsed.errors().forEach(e -> warning("Ignoring substitute %s", e));

        Optional<SchematicSnapshot> snapshot = LitematicaAdapter.snapshot(name);
        if (snapshot.isEmpty()) {
            error("Could not read placement '%s' (disabled, no enabled sub-region or Litematica incompatible; see log).", name);
            return Optional.empty();
        }

        PlanConfig plan = new PlanConfig(clusterSize.get(), layerAxis.get(), layerAscending.get(),
            additiveOnly.get(), ignoreAir.get(),
            Set.copyOf(skipIfWorldIs.get()), Set.copyOf(treatAsAir.get()), Set.copyOf(neverPlace.get()),
            parsed.map(), Set.copyOf(ignoreProperties.get()));
        planInRun = Optional.of(plan);

        WorkPlanner planner = new WorkPlanner(plan);
        MaterialManager materials = new MaterialManager(this::currentHotbarSlots, this::onShortage);
        Printer printer = new Printer(new PlacementSolver(new SolverConfig(clickAdjacentOnly.get(), lineOfSight.get())),
            planner, materials, budget, PlacementLog.toFile(FOLDER.resolve("placementlog-" + fileName(name) + ".jsonl")),
            new Printer.Options(supportsForRun(snapshot.get()), handleFluids.get(), note -> warning("%s", note)));

        PacingProfile.Pacing pacing = pacing();
        rotationSpoofInRun = pacing.rotationSpoof();
        profile.get().warning().ifPresent(text -> warning("%s", text));
        tickCounter = 0;
        reportedShortages.clear();
        navigator.cancel();
        navigator = new Navigator(BaritoneBridge.PATHING, System::currentTimeMillis);
        if (!navigator.available()) info("Baritone is missing; only clusters within reach are built (see .sf doctor).");
        SafetyMonitor safety = new SafetyMonitor(this::safetyConfig);
        return Optional.of(new BuildSession(snapshot.get(), planner, printer, navigator, safety,
            System::currentTimeMillis, this::onStateChange, note -> warning("%s", note)));
    }

    /**
     * Temporary supports for this run (P5-02); only with additive-only off, so an additive run has no way to break.
     */
    private Optional<TempSupports> supportsForRun(SchematicSnapshot snapshot) {
        if (!tempSupports.get()) return Optional.empty();
        if (additiveOnly.get()) {
            info("temp-supports is ignored while additive-only is on.");
            return Optional.empty();
        }
        if (supportBlocks.get().isEmpty()) {
            warning("temp-supports is on but support-blocks is empty; no supports are placed.");
            return Optional.empty();
        }
        return Optional.of(new TempSupports(supportBlocks.get(), TempSupports.reservedBy(snapshot),
            pos -> BlockUtils.breakBlock(pos, true), note -> warning("%s", note)));
    }

    /** Refills the budget and runs one step of the state machine (P2-01, P2-07). */
    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (stopNextTick) {
            // Deferred so the module is not toggled while Meteor iterates its modules on world join.
            stopNextTick = false;
            toggle();
            return;
        }
        tickCounter++;
        budget.resetTick();
        if (mc.level == null || mc.player == null) return;
        tickUndo();
        if (session.isEmpty()) return;

        BuildSession run = session.get();
        run.tick(new McWorldView(mc.level), new McPlayerView(mc.player, reach.get()),
            new McInventoryView(mc.player), new McSafetyView(mc.player, mc.level),
            new McPrintActions(mc, rotationSpoofInRun));
        if (run.state() == BuildSession.State.DONE) {
            finish(run);
            return;
        }
        restockIfOutOfMaterials(run);
        forgetShortagesOfFinishedCluster(run);
        checkpointIfDue(run);
    }

    private void finish(BuildSession run) {
        BuildSession.Status status = run.status();
        BuildResume.discard(FOLDER, status.placement());
        if (status.remaining() == 0 && status.mismatched() == 0) {
            info("Done: %d blocks placed, nothing left.", status.placed());
        } else {
            info("Done: %d blocks placed, %d not placed, %d mismatched (.sf status).",
                status.placed(), status.remaining(), status.mismatched());
        }
        toggle();
    }

    /** Log file of the placement that is set right now (ARCHITECTURE.md §6). */
    public Path placementLogFile() {
        return FOLDER.resolve("placementlog-" + fileName(placement.get()) + ".jsonl");
    }

    /** Starts undoing the newest {@code count} entries; false if an undo is already running (P5-01). */
    public boolean startUndo(List<PlacementLog.Entry> entries, int count) {
        if (undo.map(UndoSession::running).orElse(false)) return false;
        undo = Optional.of(new UndoSession(entries, count,
            pos -> BlockUtils.breakBlock(pos, true), budget, note -> warning("%s", note)));
        return true;
    }

    public Optional<UndoSession> undo() {
        return undo;
    }

    public void cancelUndo() {
        undo = Optional.empty();
    }

    private void tickUndo() {
        if (undo.isEmpty()) return;
        UndoSession session = undo.get();
        if (!session.running()) {
            info("Undo done: %d of %d block%s removed%s.", session.removedCount(), session.requestedCount(),
                session.requestedCount() == 1 ? "" : "s",
                session.skippedCount() == 0 ? "" : ", " + session.skippedCount() + " left alone");
            undo = Optional.empty();
            return;
        }
        session.tick(new McWorldView(mc.level), new McPlayerView(mc.player, reach.get()));
    }

    /** Pause, resume and status for the {@code .sf} commands; empty while no run is going on. */
    public Optional<BuildSession> session() {
        return session;
    }

    /** Status of the running or the last finished run. */
    public Optional<BuildSession.Status> lastStatus() {
        return session.map(BuildSession::status).or(() -> lastStatus);
    }

    public Setting<String> placementSetting() {
        return placement;
    }

    /** Where the checkpoint files live (ARCHITECTURE.md §6). */
    public static Path folder() {
        return FOLDER;
    }

    /** Plan settings as {@code .sf start} would use them right now, for the checkpoint check (P3-04). */
    public PlanConfig currentPlanConfig() {
        return new PlanConfig(clusterSize.get(), layerAxis.get(), layerAscending.get(),
            additiveOnly.get(), ignoreAir.get(),
            Set.copyOf(skipIfWorldIs.get()), Set.copyOf(treatAsAir.get()), Set.copyOf(neverPlace.get()),
            Substitutes.parse(substitutes.get()).map(), Set.copyOf(ignoreProperties.get()));
    }

    /** Makes the next activation continue at {@code clusterIndex} (0-based) instead of the first cluster (P3-04). */
    public void resumeFrom(int clusterIndex) {
        resumeFromCluster = Math.max(0, clusterIndex);
    }

    /**
     * Fetches material while the build waits for it (P4-04, milestone M4).
     * The safety stop already paused the run with NO_MATERIALS and continues on its own once the items are
     * back, so the restock only has to fill the inventory - it never resumes the build itself.
     */
    private void restockIfOutOfMaterials(BuildSession run) {
        boolean waitingForMaterial = run.safetyPause()
            .filter(trigger -> trigger.reason() == SafetyMonitor.Reason.NO_MATERIALS).isPresent();
        if (!waitingForMaterial) return;

        ContainerRestock restock = Modules.get().get(ContainerRestock.class);
        if (!restock.isActive() || restock.restock().map(RestockProcess::running).orElse(false)) return;

        Map<Item, Integer> demand = run.upcomingDemand(restock.lookaheadClusters());
        if (demand.isEmpty()) return;
        if (restock.startRestock(demand, run.currentClusterCentre().orElse(null), note -> warning("%s", note))) {
            info("Out of material, fetching %d item type%s from the container index.",
                demand.size(), demand.size() == 1 ? "" : "s");
        }
    }

    /** What is missing for the cluster being worked on right now (P3-05). */
    public List<MaterialManager.Shortage> shortages() {
        return currentShortages.entrySet().stream()
            .map(e -> new MaterialManager.Shortage(e.getKey(), e.getValue()))
            .toList();
    }

    /** The shortage list belongs to one cluster; carrying it over would show items that have long since been restocked. */
    private void forgetShortagesOfFinishedCluster(BuildSession run) {
        int cluster = run.status().clusterIndex();
        if (cluster == shortagesOfCluster) return;
        shortagesOfCluster = cluster;
        currentShortages.clear();
    }

    private void checkpointIfDue(BuildSession run) {
        BuildResume resume = Modules.get().get(BuildResume.class);
        long now = System.currentTimeMillis();
        if (!resume.isActive() || now - lastCheckpointAt < resume.intervalMs()) return;
        lastCheckpointAt = now;
        saveCheckpoint(run.status());
    }

    private void saveCheckpoint(BuildSession.Status status) {
        if (planInRun.isEmpty()) return;
        Modules.get().get(BuildResume.class)
            .save(FOLDER, status.placement(), status.clusterIndex(), status.placed(), runStartedAt, planInRun.get());
    }

    private void onShortage(MaterialManager.Shortage shortage) {
        currentShortages.put(shortage.item(), shortage.missing());
        if (reportedShortages.add(shortage.item())) {
            warning("Missing %d x %s; the printer skips those blocks (restock comes with P4-04).",
                shortage.missing(), BuiltInRegistries.ITEM.getKey(shortage.item()).getPath());
        }
    }

    private void onStateChange(BuildSession.State from, BuildSession.State to) {
        SchemaForgeAddon.LOG.info("Build state {} -> {}", from, to);
        if (logStateChanges.get()) info("%s -> %s", from, to);
    }

    /** Read on every safety check, so a changed setting takes effect at once (P3-03). */
    private SafetyMonitor.Config safetyConfig() {
        return new SafetyMonitor.Config(pauseOnDamage.get(), minFood.get(), pausePlayerRadius.get());
    }

    /** Full budget only on acting ticks; profile and interval are read every tick. */
    private int budgetLimit() {
        PacingProfile.Pacing pacing = pacing();
        return tickCounter % pacing.tickInterval() == 0 ? pacing.blocksPerTick() : 0;
    }

    /** What the profile setting stands for right now (P5-05). */
    private PacingProfile.Pacing pacing() {
        return profile.get().resolve(new PacingProfile.Pacing(blocksPerTick.get(), tickInterval.get(), rotationSpoof.get()));
    }

    /** Warns when FAST is switched on; not while the config is loaded at start-up (no world then). */
    private void onProfileChanged(PacingProfile changed) {
        if (mc == null || mc.level == null) return;
        changed.warning().ifPresent(text -> warning("%s", text));
    }

    /** Read on every placement, so a changed setting takes effect at once; broken text keeps the last valid value. */
    private HotbarSlots currentHotbarSlots() {
        try {
            hotbarSlots = HotbarSlots.parse(allowedHotbarSlots.get());
        } catch (IllegalArgumentException _) {
            // Reported when the run started; keep printing with what was valid then.
        }
        return hotbarSlots;
    }

    /** The setting at the start of a run; empty after an error message in chat. */
    private Optional<HotbarSlots> parseHotbarSlots() {
        try {
            return Optional.of(HotbarSlots.parse(allowedHotbarSlots.get()));
        } catch (IllegalArgumentException e) {
            error("Setting allowed-hotbar-slots: %s", e.getMessage());
            return Optional.empty();
        }
    }

    private static String fileName(String placementName) {
        return placementName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
