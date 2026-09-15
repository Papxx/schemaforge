package dev.tore.schemaforge.compat;

import dev.tore.schemaforge.SchemaForgeAddon;
import dev.tore.schemaforge.compat.ProbeReport.Line;
import dev.tore.schemaforge.compat.SignatureCheck.Result;
import dev.tore.schemaforge.compat.SignatureCheck.Spec;
import dev.tore.schemaforge.core.MaterialRules;
import dev.tore.schemaforge.core.SchematicSnapshot;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The only class allowed to touch {@code fi.dy.masa.*}; all fragile accesses go through
 * MethodHandles verified at startup (hard rule 2).
 * Shell from P0-04; each method names the ticket that implements it.
 */
public final class LitematicaAdapter {
    private static final String PKG = "fi.dy.masa.litematica.";
    private static final String MAIN_CLASS = PKG + "Litematica";
    private static final String DATA_MANAGER = PKG + "data.DataManager";
    private static final String PLACEMENT_MANAGER = PKG + "schematic.placement.SchematicPlacementManager";
    private static final String PLACEMENT = PKG + "schematic.placement.SchematicPlacement";
    private static final String SUB_REGION = PKG + "schematic.placement.SubRegionPlacement";
    private static final String SCHEMATIC = PKG + "schematic.LitematicaSchematic";
    private static final String WORLD_HANDLER = PKG + "world.SchematicWorldHandler";
    private static final String WORLD_SCHEMATIC = PKG + "world.WorldSchematic";
    private static final String GENERIC_CONFIGS = PKG + "config.Configs$Generic";
    private static final String PLACEMENT_HANDLER = PKG + "util.PlacementHandler";
    private static final String PROTOCOL = PKG + "util.EasyPlaceProtocol";
    private static final String OPTION_LIST = "fi.dy.masa.malilib.config.options.ConfigOptionList";

    private static final String BLOCK_POS = "net.minecraft.core.BlockPos";
    private static final String ROTATION = "net.minecraft.world.level.block.Rotation";
    private static final String MIRROR = "net.minecraft.world.level.block.Mirror";
    private static final String LEVEL = "net.minecraft.world.level.Level";

    private static final Spec GET_PLACEMENT_MANAGER = Spec.staticMethod(DATA_MANAGER, PLACEMENT_MANAGER, "getSchematicPlacementManager");
    // Litematica has switched between both names (NOTES-litematica-api.md row 3, defect F1).
    private static final Spec GET_ALL_PLACEMENTS = new Spec(PLACEMENT_MANAGER, false, "java.util.List",
        List.of("getAllSchematicsPlacements", "getAllSchematicPlacements"), List.of());
    private static final Spec GET_PLACEMENT_NAME = Spec.virtual(PLACEMENT, "java.lang.String", "getName");
    private static final Spec GET_ORIGIN = Spec.virtual(PLACEMENT, BLOCK_POS, "getOrigin");
    private static final Spec GET_ROTATION = Spec.virtual(PLACEMENT, ROTATION, "getRotation");
    private static final Spec GET_MIRROR = Spec.virtual(PLACEMENT, MIRROR, "getMirror");
    private static final Spec GET_ENABLED_REGIONS = Spec.virtual(PLACEMENT, "com.google.common.collect.ImmutableMap", "getEnabledRelativeSubRegionPlacements");
    private static final Spec GET_SCHEMATIC = Spec.virtual(PLACEMENT, SCHEMATIC, "getSchematic");
    private static final Spec GET_AREA_SIZE = Spec.virtual(SCHEMATIC, BLOCK_POS, "getAreaSize", "java.lang.String");
    private static final Spec GET_REGION_POS = Spec.virtual(SUB_REGION, BLOCK_POS, "getPos");
    private static final Spec GET_REGION_ROTATION = Spec.virtual(SUB_REGION, ROTATION, "getRotation");
    private static final Spec GET_REGION_MIRROR = Spec.virtual(SUB_REGION, MIRROR, "getMirror");
    private static final Spec GET_SCHEMATIC_WORLD = Spec.staticMethod(WORLD_HANDLER, WORLD_SCHEMATIC, "getSchematicWorld");

    /** Every method from docs/NOTES-litematica-api.md, in the order of that table (rows 2–14). */
    private static final List<Spec> BARITONE_SIGNATURES = List.of(
        GET_PLACEMENT_MANAGER,
        GET_ALL_PLACEMENTS,
        GET_PLACEMENT_NAME,
        GET_ORIGIN,
        GET_ROTATION,
        GET_MIRROR,
        GET_ENABLED_REGIONS,
        GET_SCHEMATIC,
        GET_AREA_SIZE,
        GET_REGION_POS,
        GET_REGION_ROTATION,
        GET_REGION_MIRROR,
        GET_SCHEMATIC_WORLD
    );

    /** Not used by Baritone: a disabled placement is missing from the schematic world (NOTES-litematica-api.md row 16, P1-02). */
    private static final Spec IS_PLACEMENT_ENABLED = Spec.virtual(PLACEMENT, "boolean", "isEnabled");

    /** Resolves Litematica's AUTO setting (Servux → V3, Carpet → V2, else SLAB_ONLY). */
    private static final Spec EFFECTIVE_PROTOCOL = Spec.staticMethod(PLACEMENT_HANDLER, PROTOCOL, "getEffectiveProtocolVersion");
    private static final Spec OPTION_LIST_VALUE = Spec.virtual(OPTION_LIST, "java.lang.String", "getStringValue");

    private LitematicaAdapter() {
    }

    /** True if Litematica is installed (its main class loads). Says nothing about signature compatibility, see {@link #probe()}. */
    public static boolean isPresent() {
        return Resolved.PRESENT;
    }

    /**
     * Which Litematica signatures resolved: main class, every method Baritone uses,
     * the schematic world type and the EasyPlace protocol (configured and effective).
     * Never throws; without Litematica the section holds a single MISSING line. Implemented in P0-05.
     */
    public static ProbeReport.Section probe() {
        ClassLoader loader = loader();
        if (!isPresent()) {
            return new ProbeReport.Section("Litematica", List.of(Line.missing("Litematica", "missing")));
        }

        List<Line> lines = new ArrayList<>();
        lines.add(Line.ok("class Litematica", "found"));
        for (Spec spec : BARITONE_SIGNATURES) lines.add(signatureLine(loader, spec));
        lines.add(signatureLine(loader, IS_PLACEMENT_ENABLED));
        lines.add(worldSchematicLine(loader));
        lines.add(configuredProtocolLine(loader));
        lines.add(effectiveProtocolLine(loader));
        return new ProbeReport.Section("Litematica", lines);
    }

    /**
     * Names of all loaded placements in Litematica's order; names may repeat.
     * Empty if Litematica is missing, incompatible or not ready yet; never throws (P1-01).
     */
    public static List<String> placementNames() {
        Optional<PlacementHandles> handles = Resolved.PLACEMENTS;
        if (handles.isEmpty()) return List.of();
        try {
            Object manager = handles.get().getManager().invoke();
            if (manager == null) return List.of();
            List<?> placements = (List<?>) handles.get().getAll().invoke(manager);
            if (placements == null) return List.of();

            List<String> names = new ArrayList<>(placements.size());
            for (Object placement : placements) {
                String name = (String) handles.get().getName().invoke(placement);
                if (name != null) names.add(name);
            }
            return List.copyOf(names);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable t) {
            SchemaForgeAddon.LOG.warn("Reading Litematica placements failed", t);
            return List.of();
        }
    }

    /**
     * Target state of the first placement called {@code name}: every enabled sub-region, with placement and sub-region
     * mirror/rotation applied. Block states are read from Litematica's schematic world, which already holds them
     * transformed; positions in schematic chunks that are not loaded are left out (and logged).
     * Client thread only. Empty if Litematica is missing or incompatible, the name is unknown, the placement is
     * disabled or has no enabled sub-region; never throws (P1-02).
     */
    public static Optional<SchematicSnapshot> snapshot(String name) {
        Optional<PlacementHandles> placementHandles = Resolved.PLACEMENTS;
        Optional<SnapshotHandles> snapshotHandles = Resolved.SNAPSHOT;
        if (placementHandles.isEmpty() || snapshotHandles.isEmpty()) return Optional.empty();
        PlacementHandles p = placementHandles.get();
        SnapshotHandles s = snapshotHandles.get();
        try {
            Object placement = findPlacement(p, name);
            if (placement == null) {
                SchemaForgeAddon.LOG.info("No Litematica placement named '{}'", name);
                return Optional.empty();
            }
            if (!(boolean) s.isEnabled().invoke(placement)) {
                SchemaForgeAddon.LOG.info("Litematica placement '{}' is disabled", name);
                return Optional.empty();
            }
            if (!(s.getSchematicWorld().invoke() instanceof Level world)) {
                SchemaForgeAddon.LOG.warn("Litematica's schematic world is not available");
                return Optional.empty();
            }

            BlockPos origin = (BlockPos) s.getOrigin().invoke(placement);
            Mirror mirror = (Mirror) s.getMirror().invoke(placement);
            Rotation rotation = (Rotation) s.getRotation().invoke(placement);
            Object schematic = s.getSchematic().invoke(placement);
            Map<?, ?> regions = (Map<?, ?>) s.getEnabledRegions().invoke(placement);

            PlacementTransform.Box bounds = null;
            Long2ObjectMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
            long unloaded = 0;
            for (Map.Entry<?, ?> entry : regions.entrySet()) {
                String regionName = (String) entry.getKey();
                Object region = entry.getValue();
                BlockPos areaSize = (BlockPos) s.getAreaSize().invoke(schematic, regionName);
                if (areaSize == null) {
                    SchemaForgeAddon.LOG.warn("Placement '{}': sub-region '{}' has no size, skipped", name, regionName);
                    continue;
                }
                PlacementTransform.Box box = PlacementTransform.subRegionBox(origin, mirror, rotation,
                    (BlockPos) s.getRegionPos().invoke(region),
                    (Mirror) s.getRegionMirror().invoke(region),
                    (Rotation) s.getRegionRotation().invoke(region),
                    areaSize);
                bounds = bounds == null ? box : bounds.union(box);
                unloaded += readBox(world, box, blocks);
            }
            if (bounds == null) {
                SchemaForgeAddon.LOG.info("Litematica placement '{}' has no enabled sub-region", name);
                return Optional.empty();
            }
            if (unloaded > 0) {
                SchemaForgeAddon.LOG.warn("Placement '{}': {} positions lie in schematic chunks that are not loaded and are missing from the snapshot",
                    name, unloaded);
            }
            return Optional.of(new SchematicSnapshot(name, bounds.min(), bounds.max(),
                Long2ObjectMaps.unmodifiable(blocks), Collections.unmodifiableMap(MaterialRules.totals(blocks.values()))));
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable t) {
            SchemaForgeAddon.LOG.warn("Reading Litematica placement '{}' failed", name, t);
            return Optional.empty();
        }
    }

    /** Selection bounds for the buildOnlySelection setting. No ticket yet, see Backlog in docs/TASKS.md. */
    public static Optional<BlockPos[]> selectionBounds() {
        throw new UnsupportedOperationException("Backlog: buildOnlySelection");
    }

    /**
     * Protocol Litematica would use right now (its AUTO setting already resolved).
     * Empty if Litematica is missing or the lookup fails. Implemented in P0-05.
     */
    public static Optional<EasyPlaceProtocol> detectedProtocol() {
        return effectiveProtocolName(loader()).map(LitematicaAdapter::mapProtocol);
    }

    private static Object findPlacement(PlacementHandles handles, String name) throws Throwable {
        Object manager = handles.getManager().invoke();
        if (manager == null) return null;
        List<?> placements = (List<?>) handles.getAll().invoke(manager);
        if (placements == null) return null;
        for (Object placement : placements) {
            if (name.equals(handles.getName().invoke(placement))) return placement;
        }
        return null;
    }

    /** Copies every block of {@code box} into {@code blocks}; returns how many positions were skipped as unloaded. */
    private static long readBox(Level world, PlacementTransform.Box box, Long2ObjectMap<BlockState> blocks) {
        long unloaded = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.min().getX(); x <= box.max().getX(); x++) {
            for (int z = box.min().getZ(); z <= box.max().getZ(); z++) {
                if (!world.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                    unloaded += box.max().getY() - box.min().getY() + 1;
                    continue;
                }
                for (int y = box.min().getY(); y <= box.max().getY(); y++) {
                    pos.set(x, y, z);
                    blocks.put(pos.asLong(), world.getBlockState(pos));
                }
            }
        }
        return unloaded;
    }

    private static Line signatureLine(ClassLoader loader, Spec spec) {
        Result result = SignatureCheck.resolve(loader, spec);
        if (!result.isFound()) return Line.fail(spec.describe(), result.problem());
        boolean renamed = !result.resolvedName().equals(spec.names().getFirst());
        return Line.ok(spec.describe(), renamed ? "found as " + result.resolvedName() : "found");
    }

    private static Line worldSchematicLine(ClassLoader loader) {
        String label = "class WorldSchematic extends Level";
        Optional<Class<?>> world = SignatureCheck.load(loader, WORLD_SCHEMATIC);
        Optional<Class<?>> level = SignatureCheck.load(loader, LEVEL);
        if (world.isEmpty()) return Line.fail(label, "class " + WORLD_SCHEMATIC + " not found");
        if (level.isEmpty() || !level.get().isAssignableFrom(world.get())) return Line.fail(label, "does not extend Level");
        return Line.ok(label, "found");
    }

    private static Line configuredProtocolLine(ClassLoader loader) {
        String label = "EasyPlace protocol (config)";
        Result field = SignatureCheck.resolveStaticField(loader, GENERIC_CONFIGS, "EASY_PLACE_PROTOCOL", OPTION_LIST);
        if (!field.isFound()) return Line.fail(label, field.problem());
        Result value = SignatureCheck.resolve(loader, OPTION_LIST_VALUE);
        if (!value.isFound()) return Line.fail(label, value.problem());
        try {
            Object option = field.handle().orElseThrow().invoke();
            return Line.ok(label, String.valueOf(value.handle().orElseThrow().invoke(option)));
        } catch (Throwable t) {
            SchemaForgeAddon.LOG.warn("Reading Litematica's EASY_PLACE_PROTOCOL failed", t);
            return Line.fail(label, t.getClass().getSimpleName());
        }
    }

    private static Line effectiveProtocolLine(ClassLoader loader) {
        String label = "EasyPlace protocol (effective)";
        Result result = SignatureCheck.resolve(loader, EFFECTIVE_PROTOCOL);
        if (!result.isFound()) return Line.fail(label, result.problem());
        return effectiveProtocolName(loader)
            .map(name -> Line.ok(label, name + " -> " + mapProtocol(name)))
            .orElseGet(() -> Line.fail(label, "lookup failed, see log"));
    }

    /** Name of Litematica's {@code EasyPlaceProtocol} constant, e.g. {@code V3}. */
    private static Optional<String> effectiveProtocolName(ClassLoader loader) {
        Result result = SignatureCheck.resolve(loader, EFFECTIVE_PROTOCOL);
        Optional<MethodHandle> handle = result.handle();
        if (handle.isEmpty()) return Optional.empty();
        try {
            Object protocol = handle.get().invoke();
            return protocol instanceof Enum<?> e ? Optional.of(e.name()) : Optional.empty();
        } catch (Throwable t) {
            SchemaForgeAddon.LOG.warn("Litematica's getEffectiveProtocolVersion failed", t);
            return Optional.empty();
        }
    }

    /** V3 and V2 carry block properties; SLAB_ONLY and NONE do not (AUTO is resolved before). */
    private static EasyPlaceProtocol mapProtocol(String litematicaName) {
        return switch (litematicaName) {
            case "V3" -> EasyPlaceProtocol.V3_SERVUX;
            case "V2" -> EasyPlaceProtocol.V2_CARPET;
            default -> EasyPlaceProtocol.NONE;
        };
    }

    private static ClassLoader loader() {
        return LitematicaAdapter.class.getClassLoader();
    }

    private record PlacementHandles(MethodHandle getManager, MethodHandle getAll, MethodHandle getName) {
    }

    private record SnapshotHandles(
        MethodHandle isEnabled, MethodHandle getOrigin, MethodHandle getRotation, MethodHandle getMirror,
        MethodHandle getEnabledRegions, MethodHandle getSchematic, MethodHandle getAreaSize,
        MethodHandle getRegionPos, MethodHandle getRegionRotation, MethodHandle getRegionMirror,
        MethodHandle getSchematicWorld
    ) {
    }

    /** Resolved once on first use (holder idiom); signature problems are logged a single time. */
    private static final class Resolved {
        static final boolean PRESENT = SignatureCheck.load(loader(), MAIN_CLASS).isPresent();
        static final Optional<PlacementHandles> PLACEMENTS = resolve("placements",
            List.of(GET_PLACEMENT_MANAGER, GET_ALL_PLACEMENTS, GET_PLACEMENT_NAME),
            h -> new PlacementHandles(h.get(0), h.get(1), h.get(2)));
        static final Optional<SnapshotHandles> SNAPSHOT = resolve("snapshots",
            List.of(IS_PLACEMENT_ENABLED, GET_ORIGIN, GET_ROTATION, GET_MIRROR, GET_ENABLED_REGIONS, GET_SCHEMATIC,
                GET_AREA_SIZE, GET_REGION_POS, GET_REGION_ROTATION, GET_REGION_MIRROR, GET_SCHEMATIC_WORLD),
            h -> new SnapshotHandles(h.get(0), h.get(1), h.get(2), h.get(3), h.get(4), h.get(5),
                h.get(6), h.get(7), h.get(8), h.get(9), h.get(10)));

        /** All specs or nothing: one missing handle disables the feature and is logged once. */
        private static <T> Optional<T> resolve(String feature, List<Spec> specs, Function<List<MethodHandle>, T> factory) {
            if (!PRESENT) return Optional.empty();
            List<Result> results = specs.stream().map(spec -> SignatureCheck.resolve(loader(), spec)).toList();
            List<String> problems = results.stream().filter(r -> !r.isFound()).map(Result::problem).toList();
            if (!problems.isEmpty()) {
                SchemaForgeAddon.LOG.warn("Litematica is installed but incompatible, {} unavailable: {}", feature, problems);
                return Optional.empty();
            }
            return Optional.of(factory.apply(results.stream().map(r -> r.handle().orElseThrow()).toList()));
        }
    }
}
