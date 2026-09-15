# Architektur (verbindlich)

Änderungen an Schnittstellen hier zuerst eintragen, dann implementieren.

## 1. Paketstruktur

```
dev.tore.schemaforge
├── SchemaForgeAddon                 MeteorAddon-Einstieg; registriert Module, Commands, HUD; hält LOG
├── compat/
│   ├── VersionProbe                 sammelt Versionen + Signatur-Checks → ProbeReport
│   ├── ProbeReport                  Ergebnis von VersionProbe, Abschnitte je Mod (siehe §4)
│   ├── SignatureCheck               löst Methoden per Name/Typ-Strings als MethodHandle auf (ohne Klassen-Import)
│   ├── EasyPlaceProtocol            enum NONE / V2_CARPET / V3_SERVUX
│   ├── LitematicaAdapter            einzige Klasse mit fi.dy.masa.*-Zugriff (MethodHandles)
│   ├── PlacementTransform           package-private, reine Mathematik: Mirror/Rotation, Sub-Region-Box (P1-02)
│   └── BaritoneBridge               einzige Klasse mit baritone.api.*-Zugriff
├── core/
│   ├── ActionBudget                 Pakete pro Tick begrenzen
│   ├── SchematicSnapshot            immutable Soll-Zustand
│   ├── WorkPlanner                  Diff → Tasks → Cluster → Reihenfolge
│   ├── PlacementSolver              BlockState → PlacementPlan (Klick-Seite, Blick, Hand-Item)
│   ├── Printer                      Tick-Loop, platziert innerhalb Reichweite
│   ├── Navigator                    Cluster-/Container-Ziele an BaritoneBridge
│   ├── MaterialManager              Bedarf, Hotbar-Swap, Restock-Trigger
│   ├── ContainerIndex               gelernte Kisteninhalte + Persistenz
│   ├── RestockProcess               State-Machine für 3.4 im Plan
│   ├── PlacementLog                 eigene Platzierungen (für Undo, Temp-Blöcke)
│   ├── MaterialRules                BlockState → benötigtes Item + Anzahl; materialTotals (P1-02)
│   ├── ContainerType                enum CHEST / BARREL / SHULKER / ENDER_CHEST (WorldView, ContainerIndex)
│   └── view/  WorldView, InventoryView, PlayerView   (kleine Interfaces für Testbarkeit)
├── modules/
│   ├── SchemaPrinter                Hauptmodul + alle Settings
│   ├── ContainerRestock             Restock-Settings
│   └── BuildResume                  Checkpoint-Persistenz
├── commands/  SfCommand             .sf <sub> – Sub-Commands als eigene Klassen (DoctorCommand, …)
├── hud/       BuildProgressHud
└── mixins/                          leer bis ein Ticket einen Mixin verlangt
```

## 2. Kern-Datentypen (`core/`)

```java
// Soll-Zustand einer Litematica-Platzierung, bereits transformiert in Weltkoordinaten.
public record SchematicSnapshot(
    String placementName,
    BlockPos min, BlockPos max,                    // Bounding Box aller aktivierten Sub-Regionen in Weltkoordinaten (inklusiv)
    Long2ObjectMap<BlockState> blocks,             // key = BlockPos.asLong(); Air-Einträge enthalten, wenn Schematic Luft verlangt
    Map<Item, Integer> materialTotals              // Item → Anzahl, aus blocks abgeleitet (MaterialRules.totals)
) {}
// P1-02: blocks enthält nur Positionen, deren Chunk in Litematicas Schematic-World geladen ist.
// Fehlende Keys heißen „unbekannt“, nicht „Luft“ – der Planner darf dort nichts tun.

// P1-02. Regeln wie Litematicas Materialliste (MaterialCache), eigenständig implementiert.
public final class MaterialRules {
    public record Requirement(Item item, int count) {}
    public static List<Requirement> required(BlockState state);       // leer: Luft, obere Tür-/Pflanzenhälfte, Bett-Kopf, fließende Flüssigkeit, Portale …
                                                                      // zwei Einträge: bepflanzter Blumentopf, gefüllter Kessel
    public static Map<Item, Integer> totals(Iterable<BlockState> states);
}

public enum TaskKind { PLACE, BREAK, FLUID, SKIP }

public record BlockTask(BlockPos pos, BlockState target, BlockState current, TaskKind kind, int priority) {}

// Cluster = räumlich zusammenhängende Task-Gruppe, Anlaufpunkt für Baritone.
public record Cluster(int index, BlockPos center, List<BlockTask> tasks) {}

public record PlanConfig(
    int clusterSize,                 // Kantenlänge in Blöcken, Default 5
    Axis layerAxis, boolean layerAscending,
    boolean additiveOnly, boolean ignoreAir,
    Set<Block> skipIfWorldIs, Set<Block> treatAsAir, Set<Block> neverPlace,
    Map<Block, List<Block>> substitutes,
    Set<String> ignoreProperties
) {}

// Ergebnis des PlacementSolvers – was der Printer tatsächlich tun muss.
public record PlacementPlan(
    BlockPos clickPos, Direction clickFace, Vec3 hitVec,
    float yaw, float pitch, boolean requiresRealRotation,   // true bei Rotationsblöcken ohne Accurate-Placement
    Item handItem, boolean sneak
) {}

public sealed interface SolveResult permits SolveResult.Ok, SolveResult.NeedsSupport, SolveResult.Unsupported {
    record Ok(PlacementPlan plan) implements SolveResult {}
    record NeedsSupport(BlockPos missingSupport) implements SolveResult {}
    record Unsupported(String reason) implements SolveResult {}
}
```

## 3. Interfaces für Testbarkeit (`core/view/`)

```java
public interface WorldView {
    BlockState getBlockState(BlockPos pos);
    boolean isChunkLoaded(BlockPos pos);
    Optional<ContainerType> containerAt(BlockPos pos);   // chest/barrel/shulker/ender_chest
}
public interface InventoryView {
    int count(Item item);
    OptionalInt hotbarSlotWith(Item item);
    int freeSlots();
    List<ItemStack> shulkersContaining(Item item);
}
public interface PlayerView {
    Vec3 eyePos(); float yaw(); float pitch(); double reach();
    boolean hasLineOfSight(Vec3 target);
}
```

Produktiv-Implementierungen liegen in `compat/` bzw. `modules/` (z. B. `McWorldView`), Tests nutzen Fakes.

## 4. Schnittstellen der Bausteine

```java
// P0-05. Reine Daten, keine Minecraft-Abhängigkeit; Chat-Darstellung in commands/DoctorCommand.
public record ProbeReport(List<Section> sections) {
    public enum Status { OK, MISSING, FAIL }          // grün / gelb (Soft-Dependency fehlt) / rot (Signatur bricht)
    public record Line(String label, String value, Status status) {}
    public record Section(String title, List<Line> lines) { public Status status(); }   // schlechtester Line-Status
    public Status status();
}

public final class VersionProbe {
    public static ProbeReport run();   // Abschnitte: Minecraft, Meteor, Server, Baritone, MaLiLib, Litematica
}

// Mod-IDs (FabricLoader): minecraft, meteor-client, baritone-meteor (Meteor-Fork) bzw. baritone, malilib, litematica
```

```java
public final class LitematicaAdapter {
    public static boolean isPresent();
    public static ProbeReport.Section probe();                       // welche Signaturen gefunden wurden
    public static List<String> placementNames();                     // alle geladenen Placements
    public static Optional<SchematicSnapshot> snapshot(String name); // Sub-Regionen aufgelöst, Mirror/Rotation angewandt;
                                                                     // nur Client-Thread; erstes Placement mit dem Namen;
                                                                     // leer: Litematica fehlt/inkompatibel, Name unbekannt,
                                                                     // Placement deaktiviert, keine aktivierte Sub-Region
    public static Optional<BlockPos[]> selectionBounds();            // für buildOnlySelection
    public static Optional<EasyPlaceProtocol> detectedProtocol();    // NONE, V2_CARPET, V3_SERVUX
}

public final class BaritoneBridge {
    public static boolean isPresent();
    public static void gotoNear(BlockPos pos, int radius);           // GoalNear
    public static void gotoBlock(BlockPos pos);                      // GoalGetToBlock
    public static boolean isPathing();
    public static void stop();
    public static void setAvoidBreaking(Set<BlockPos> protectedArea);// Schutzone um Placement
    public static void restoreSettings();                            // beim Deaktivieren
}

public final class ActionBudget {
    public ActionBudget(IntSupplier limitPerTick);
    public boolean tryConsume();                                     // false → in diesem Tick nichts mehr
    public void resetTick();
}

public final class WorkPlanner {
    public WorkPlanner(PlanConfig cfg);
    public List<Cluster> plan(SchematicSnapshot snap, WorldView world);
    public List<BlockTask> refresh(Cluster c, WorldView world);      // Ist-Zustand neu einlesen
}

public final class PlacementSolver {
    public SolveResult solve(BlockTask task, WorldView world, PlayerView player, EasyPlaceProtocol proto);
}

public final class ContainerIndex {
    public record Entry(BlockPos pos, ContainerType type, long lastSeenEpochMs, Map<Item,Integer> items, boolean stale) {}
    public void learn(BlockPos pos, ContainerType type, Map<Item,Integer> items);
    public List<Entry> sourcesFor(Item item, Vec3 from);             // nach Distanz sortiert, stale zuletzt
    public void markStale(Duration olderThan);
    public void save(Path file); public static ContainerIndex load(Path file);
}

public final class RestockProcess {
    public enum State { IDLE, PICK_SOURCE, TRAVEL, OPEN, WAIT_SCREEN, TAKE, CLOSE, RETURN, FAILED }   // public: state() ist public (P0-04)
    public void start(Map<Item,Integer> demand);
    public void tick();
    public State state();
}
```

## 5. Zustandsautomat `SchemaPrinter`

```
IDLE ─start─▶ PLANNING ─▶ BUILDING ⇄ TRAVELING
                              │  (Material fehlt) ▶ RESTOCKING ─▶ BUILDING
                              │  (Schaden/Hunger/Spieler nah/Chunk fehlt) ▶ PAUSED ─▶ BUILDING
                              └─ alle Cluster leer ▶ VERIFYING ─▶ DONE
Jeder Zustand: onEnter(), tick(), onExit(). Zustandswechsel loggen (Debug-Setting).
```

## 6. Persistenz

- `meteor-client/schemaforge/containers-<serverHash>-<dimension>.json` – ContainerIndex
- `meteor-client/schemaforge/resume-<placementName>.json` – `{ clusterIndex, placedCount, startedAt, planConfigHash }`
- `meteor-client/schemaforge/placementlog-<placementName>.jsonl` – eine Zeile pro Platzierung `{pos, block, temp, t}`
- Format Gson; Schema-Version als erstes Feld (`"v": 1`).

## 7. Settings des Moduls `SchemaPrinter` (Meteor `SettingGroup`s)

| Gruppe | Setting | Typ | Default |
|---|---|---|---|
| General | placement | String (Dropdown aus `placementNames()`) | aktives Placement |
| General | additiveOnly | bool | true |
| General | ignoreAir | bool | true |
| General | buildOnlySelection | bool | false |
| Order | layerAxis / layerAscending | enum / bool | Y / true |
| Order | clusterSize | int 3–16 | 5 |
| Filters | skipIfWorldIs / treatAsAir / neverPlace | BlockList | leer / grass,tall_grass / tnt |
| Filters | substitutes | StringList `a->b,c` | leer |
| Filters | ignoreProperties | StringList | waterlogged |
| Placement | profile | enum VANILLA_LEGIT / FAST / CUSTOM | VANILLA_LEGIT |
| Placement | blocksPerTick / tickInterval | int / int | 1 / 1 |
| Placement | reach | double ≤ 4.5 | 4.5 |
| Placement | lineOfSight | bool | true |
| Placement | clickAdjacentOnly | bool | true |
| Placement | rotationSpoof | bool | false |
| Placement | allowedHotbarSlots | String `2-8` | 2-8 |
| Safety | pauseOnDamage / minFood / pausePlayerRadius | bool / int / int | true / 6 / 16 |
| Debug | logStateChanges / renderClusters | bool / bool | false / true |

## 8. Commands

```
.sf start [placement]   .sf pause   .sf resume   .sf stop
.sf status              .sf preview [placement]
.sf verify              .sf materials
.sf restock [item]      .sf scan [radius]      .sf containers
.sf undo <n>            .sf doctor
```

## 9. Bewusst offen (nicht ohne Ticket bauen)

Multi-Account, Mining/Crafting-Beschaffung, Servux-Handshake selbst implementieren, Forge/NeoForge.
