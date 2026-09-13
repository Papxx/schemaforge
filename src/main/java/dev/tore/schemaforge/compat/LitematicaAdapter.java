package dev.tore.schemaforge.compat;

import dev.tore.schemaforge.SchemaForgeAddon;
import dev.tore.schemaforge.compat.ProbeReport.Line;
import dev.tore.schemaforge.compat.SignatureCheck.Result;
import dev.tore.schemaforge.compat.SignatureCheck.Spec;
import dev.tore.schemaforge.core.SchematicSnapshot;
import net.minecraft.core.BlockPos;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    /** Every method from docs/NOTES-litematica-api.md, in the order of that table (rows 2–14). */
    private static final List<Spec> BARITONE_SIGNATURES = List.of(
        GET_PLACEMENT_MANAGER,
        GET_ALL_PLACEMENTS,
        GET_PLACEMENT_NAME,
        Spec.virtual(PLACEMENT, BLOCK_POS, "getOrigin"),
        Spec.virtual(PLACEMENT, ROTATION, "getRotation"),
        Spec.virtual(PLACEMENT, MIRROR, "getMirror"),
        Spec.virtual(PLACEMENT, "com.google.common.collect.ImmutableMap", "getEnabledRelativeSubRegionPlacements"),
        Spec.virtual(PLACEMENT, SCHEMATIC, "getSchematic"),
        Spec.virtual(SCHEMATIC, BLOCK_POS, "getAreaSize", "java.lang.String"),
        Spec.virtual(SUB_REGION, BLOCK_POS, "getPos"),
        Spec.virtual(SUB_REGION, ROTATION, "getRotation"),
        Spec.virtual(SUB_REGION, MIRROR, "getMirror"),
        Spec.staticMethod(WORLD_HANDLER, WORLD_SCHEMATIC, "getSchematicWorld")
    );

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

    /** Sub-regions resolved, mirror/rotation applied. Implemented in P1-02. */
    public static Optional<SchematicSnapshot> snapshot(String name) {
        throw new UnsupportedOperationException("P1-02");
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

    /** Resolved once on first use (holder idiom); signature problems are logged a single time. */
    private static final class Resolved {
        static final boolean PRESENT = SignatureCheck.load(loader(), MAIN_CLASS).isPresent();
        static final Optional<PlacementHandles> PLACEMENTS = resolvePlacements();

        private static Optional<PlacementHandles> resolvePlacements() {
            if (!PRESENT) return Optional.empty();
            List<Result> results = List.of(
                SignatureCheck.resolve(loader(), GET_PLACEMENT_MANAGER),
                SignatureCheck.resolve(loader(), GET_ALL_PLACEMENTS),
                SignatureCheck.resolve(loader(), GET_PLACEMENT_NAME)
            );
            List<String> problems = results.stream().filter(r -> !r.isFound()).map(Result::problem).toList();
            if (!problems.isEmpty()) {
                SchemaForgeAddon.LOG.warn("Litematica is installed but incompatible, placements unavailable: {}", problems);
                return Optional.empty();
            }
            return Optional.of(new PlacementHandles(
                results.get(0).handle().orElseThrow(),
                results.get(1).handle().orElseThrow(),
                results.get(2).handle().orElseThrow()
            ));
        }
    }
}
