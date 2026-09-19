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
│   ├── McWorldView                  WorldView über ein echtes Level, nur Client-Thread (P1-05)
│   ├── McPlayerView / McInventoryView  PlayerView/InventoryView über den lokalen Spieler, nur Client-Thread (P2-03)
│   ├── McPrintActions               PrintActions über Meteor Rotations/InvUtils + Vanilla-Klick, nur Client-Thread (P2-03)
│   └── BaritoneBridge               einzige Klasse mit baritone.api.*-Zugriff
├── core/
│   ├── ActionBudget                 Pakete pro Tick begrenzen
│   ├── SchematicSnapshot            immutable Soll-Zustand
│   ├── WorkPlanner                  Diff → Tasks → Cluster → Reihenfolge
│   ├── PlacementSolver              BlockState → PlacementPlan (Klick-Seite, Blick, Hand-Item)
│   ├── SolverConfig                 clickAdjacentOnly, lineOfSight für den PlacementSolver (P2-02)
│   ├── Printer                      Tick-Loop, platziert innerhalb Reichweite
│   ├── BuildSession                 Zustandsautomat eines Laufs (§5): Planen, Cluster abarbeiten, Nachprüfen, Status (P2-07)
│   ├── Substitutes                  Setting-Zeilen „a->b,c“ → Map<Block, List<Block>> (P2-07)
│   ├── AdditiveOnlyGuard            verbietet Baritone das Brechen während des Laufs, stellt Settings wieder her (P2-06)
│   ├── Navigator                    Cluster-/Container-Ziele an einen Pfadfinder (BaritoneBridge.PATHING), Timeout + Blacklist (P3-02)
│   ├── MaterialManager              Bedarf, Hotbar-Swap, Restock-Trigger
│   ├── HotbarSlots                  erlaubte Hotbar-Slots aus Setting-Text „2-8“ (P2-05)
│   ├── ContainerIndex               gelernte Kisteninhalte + Persistenz
│   ├── RestockProcess               State-Machine für 3.4 im Plan
│   ├── PlacementLog                 eigene Platzierungen (für Undo, Temp-Blöcke)
│   ├── MaterialRules                BlockState → benötigtes Item + Anzahl; materialTotals (P1-02)
│   ├── ContainerType                enum CHEST / BARREL / SHULKER / ENDER_CHEST (WorldView, ContainerIndex)
│   ├── SkipReason                   Grund eines SKIP-Tasks (P1-03)
│   ├── PreviewReport                Text von .sf preview als Zeilenliste, ohne Chat-Abhängigkeit (P1-05)
│   └── view/  WorldView, InventoryView, PlayerView, PrintActions   (kleine Interfaces für Testbarkeit)
├── modules/
│   ├── SchemaPrinter                Hauptmodul + alle Settings
│   ├── ContainerRestock             Restock-Settings
│   └── BuildResume                  Checkpoint-Persistenz
├── commands/  SfCommand             .sf <sub> – Sub-Commands als eigene Klassen (DoctorCommand, PreviewCommand, …)
│              PlacementChoice       Placement-Argument auflösen, gemeinsam für preview/start (P2-07)
│              StartCommand, ControlCommands (pause/resume/stop), StatusCommand   (P2-07)
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

// P1-03: Grund eines SKIP-Tasks; NONE bei allen anderen Arten.
public enum SkipReason { NONE, CHUNK_NOT_LOADED, NEVER_PLACE, WORLD_FILTER, MISMATCH_ADDITIVE_ONLY }

// priority: höher = früher. BREAK 400 > voller Block 300 > sonstiger Block 200 > abhängiger Block 100 > FLUID 50 > SKIP 0
// (Konstanten in WorkPlanner). Abhängig = braucht Träger: Fackel, Knopf/Hebel, Schiene, Teppich, Tür, Schild, Banner,
// Leiter, Ranke, Druckplatte, Redstone, Repeater/Comparator, Pflanzen, obere Stufe ohne Nachbar oben/seitlich.
public record BlockTask(BlockPos pos, BlockState target, BlockState current, TaskKind kind, int priority, SkipReason skipReason) {}

// Cluster = räumlich zusammenhängende Task-Gruppe, Anlaufpunkt für Baritone.
public record Cluster(int index, BlockPos center, List<BlockTask> tasks) {}

public record PlanConfig(
    int clusterSize,                 // Kantenlänge in Blöcken, Default 5
    Axis layerAxis, boolean layerAscending,
    boolean additiveOnly, boolean ignoreAir,
    Set<Block> skipIfWorldIs, Set<Block> treatAsAir, Set<Block> neverPlace,
    Map<Block, List<Block>> substitutes,
    Set<String> ignoreProperties
) {
    public static PlanConfig defaults();   // P1-05: Defaults aus §7; genutzt von .sf preview, bis die Settings existieren (P2-07)
}

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
    int selectedSlot();                                  // P2-05: 0–8
    Item itemAt(int slot);                               // P2-05: Inventar-Index 0–35 (0–8 Hotbar), Items.AIR wenn leer
    int countAt(int slot);                               // P2-05: Stapelgröße, 0 wenn leer (kein ItemStack: im Unit-Test nicht erzeugbar)
}
public interface PlayerView {
    Vec3 eyePos(); float yaw(); float pitch(); double reach();
    boolean hasLineOfSight(Vec3 target);
}
// P2-03: einziger Weg, auf dem core/ Pakete auslöst. Der Aufrufer hat vorher ActionBudget.tryConsume() gefragt.
public interface PrintActions {
    boolean place(PlacementPlan plan, int hotbarSlot);   // Slot wählen, rotieren, schleichend klicken; false = nichts gesendet
    boolean swapToHotbar(int inventorySlot, int hotbarSlot);   // P2-05: ein SWAP-Klick im Spielerinventar; false = nichts gesendet
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
    public static boolean isPresent();                               // P2-06: baritone.api.BaritoneAPI ladbar (ohne Initialisierung)
    public static void gotoNear(BlockPos pos, int radius);           // GoalNear
    public static void gotoBlock(BlockPos pos);                      // GoalGetToBlock
    public static boolean isPathing();
    public static void stop();
    public static final AdditiveOnlyGuard.PathfinderSettings BREAK_SETTINGS; // P2-06: allowBreak + allowBreakAnyway lesen/schreiben
    public static final Navigator.Pathing PATHING;                   // P3-02: Pfadfinder-Seite für den Navigator
    // P3-01: gotoNear/gotoBlock über getCustomGoalProcess().setGoalAndPath(new GoalNear(pos,radius) / new GoalGetToBlock(pos)),
    //  isPathing über getPathingBehavior().isPathing(), stop über onLostControl() + cancelEverything() (wie Meteors
    //  BaritonePathManager). Alle baritone-Referenzen liegen in der privaten Nested-Klasse Api, die erst nach
    //  isPresent() geladen wird; getPrimaryBaritone() kann vor dem Welt-Beitritt null sein → Log-Warnung, kein Wurf.
    // P2-06 statt setAvoidBreaking(Set<BlockPos>) / restoreSettings(): baritone.api kennt keine positionsbezogene Break-Sperre
    //  (Settings im Jar 26.2-SNAPSHOT: nur allowBreak, allowBreakAnyway und Blocktyp-Listen). Deshalb verbietet der
    //  AdditiveOnlyGuard Brechen während des Laufs ganz; Wiederherstellen liegt im Guard.
}

// P2-06. Additive-only-Schutz für Baritone. Printer bricht nie (nur PLACE-Tasks, PrintActions hat keine Break-Aktion);
// mit additiveOnly plant der WorkPlanner falsche Blöcke als SKIP MISMATCH_ADDITIVE_ONLY („mismatched“ in .sf status, P2-07).
public final class AdditiveOnlyGuard {
    // Referenzen wie gelesen: Baritones #modified vergleicht value == defaultValue, eine Kopie gälte als Änderung.
    public record BreakSettings(boolean allowBreak, List<Block> allowBreakAnyway) {}
    public interface PathfinderSettings { boolean isPresent(); BreakSettings read(); void write(BreakSettings s); }
    public AdditiveOnlyGuard(PathfinderSettings pathfinder);
    public void engage(boolean additiveOnly);   // Start: additiveOnly && Baritone da && nicht schon aktiv → merken, dann (false, leere Liste) schreiben
    public void release();                      // Stop: gemerkte Werte zurückschreiben (dieselben Objekte); ohne engage nichts
    public boolean engaged();
    // SchemaPrinter: engage in onActivate, release in onDeactivate. additiveOnly-Änderung während des Laufs greift beim nächsten Start.
}

// P3-02. Ein Ziel zur Zeit; kennt Baritone nicht (Pathing-Interface), damit der Zustandsautomat testbar bleibt.
public final class Navigator {
    public interface Pathing { boolean isPresent(); void gotoNear(BlockPos pos, int radius); boolean isPathing(); void stop(); }
    public enum Progress { TRAVELING, ARRIVED, FAILED }
    public static final int GOAL_RADIUS = 3;                         // Ziel: neben dem Cluster stehen, nicht darin
    public static final double ARRIVE_DISTANCE = GOAL_RADIUS + 2.0;  // Abstand Augen→Ziel, ab dem „angekommen“ gilt
    public static final long TIMEOUT_MS = 20_000;
    public static final int MAX_ATTEMPTS = 3;                        // danach Blacklist
    public Navigator(Pathing pathing, LongSupplier clockMs);
    public boolean available();                                      // false ohne Baritone → Cluster werden von der Stelle aus versucht
    public boolean start(BlockPos target);                           // false ohne Pfadfinder oder wenn geblacklistet
    public Progress tick(PlayerView player);
    // FAILED: Timeout, oder nach 1,5 s Anlaufzeit meldet der Pfadfinder „läuft nicht“ (kein Pfad gefunden / anderer
    //  Prozess hat übernommen). FAILED zählt einen Versuch und stoppt den Pfadfinder; ab MAX_ATTEMPTS Blacklist.
    public void cancel();                                            // Pause/Ankunft/Laufende: Pfadfinder freigeben, kein Versuch
    public boolean blacklisted(BlockPos target); public int attempts(BlockPos target); public BlockPos target();
}

public final class ActionBudget {
    public ActionBudget(IntSupplier limitPerTick);
    public boolean tryConsume();                                     // false → in diesem Tick nichts mehr; Limit bei jedem Aufruf gelesen, ≤ 0 → nichts
    public void resetTick();                                         // P2-01: SchemaPrinter @EventHandler TickEvent.Pre
}

public final class WorkPlanner {
    public WorkPlanner(PlanConfig cfg);
    public List<Cluster> plan(SchematicSnapshot snap, WorldView world, BlockPos start); // start = Spielerposition (Nearest-Neighbor)
    // P1-03 Regeln, in dieser Reihenfolge je Position:
    //  „Luft“ = Luft, Block aus treatAsAir oder fließende Flüssigkeit (gilt für Ziel und Welt)
    //  Ziel Luft + ignoreAir → kein Task · Chunk nicht geladen → SKIP (current = VOID_AIR) · Ziel in neverPlace → SKIP
    //  Welt passt (gleicher Block oder Ersatz aus substitutes, Properties bis auf ignoreProperties gleich) → kein Task
    //  Welt in skipIfWorldIs → SKIP · Welt Luft/treatAsAir/ersetzbar → PLACE bzw. FLUID (Wasser-/Lava-Quelle)
    //  sonst falscher Block → additiveOnly ? SKIP : BREAK
    // Cluster = Würfel clusterSize ab snap.min; center = gerundeter Mittelwert der Task-Positionen; leere Cluster entfallen.
    // Ein Cluster kann nur SKIP-Tasks enthalten (z. B. „mismatched“) – der Navigator (P3-02) muss solche nicht anlaufen.
    // Cluster-Reihenfolge: Würfel-Schicht entlang layerAxis/layerAscending, darin Nearest-Neighbor ab start.
    // Task-Reihenfolge im Cluster: priority absteigend, dann Schicht, dann Nearest-Neighbor (Cursor läuft über alle Cluster weiter).
    public List<BlockTask> refresh(Cluster c, WorldView world);      // Ist-Zustand neu einlesen
    // P2-03: dieselben Regeln je Task von c (target bleibt), erledigte Positionen entfallen, Reihenfolge von c bleibt.
    //  Priorität: war der Task schon PLACE, bleibt sie; sonst neu berechnet, Träger nur aus der Welt (kein Snapshot).
}

// P1-05. Reine Daten; Farben und Chat in commands/PreviewCommand.
public record PreviewReport(List<Line> lines) {
    public static final int MAX_MATERIAL_ROWS = 30;                  // Rest als eine Zeile „… n more types“
    public enum Level { HEADER, INFO, WARNING }
    public record Line(String text, Level level) {}
    public record Environment(int minBuildY, int maxBuildY, int renderDistance, ToIntFunction<Item> inventoryCount) {}
    public static PreviewReport of(SchematicSnapshot snap, List<Cluster> clusters, Environment env);
    // Zeilen: Kopf (Name, Größe, Box) · Blöcke/Task-Arten · Cluster · Skip-Gründe (falls vorhanden)
    //   · Materialien: gesamt aus snap.materialTotals, „missing“ = Bedarf der PLACE/FLUID-Tasks minus Inventar
    //   · Warnungen: unter minBuildY, über maxBuildY, breiter als 2·renderDistance·16, Positionen ohne geladenen Schematic-Chunk
    //   · (P2-02) PLACE-Tasks mit PlacementSolver.unsupportedReason, nach Grund gruppiert, max. 4 Gründe in einer Zeile
}

// .sf preview [placement]: ohne Argument das einzige geladene Placement, bei mehreren Namensliste; Vorschläge = placementNames().
// Plant mit PlanConfig.defaults() gegen die aktuelle Welt ab Spielerposition; sendet keine Pakete.

// P2-02: Settings, die die Klickwahl beeinflussen (Werte aus SchemaPrinter, bis P2-07 Defaults true/true).
public record SolverConfig(boolean clickAdjacentOnly, boolean lineOfSight) {}

public final class PlacementSolver {
    public PlacementSolver(SolverConfig cfg);                        // P2-02: Konstruktor ergänzt (clickAdjacentOnly braucht Konfiguration)
    public SolverConfig config();                                    // P2-03: Printer prüft lineOfSight erneut
    public SolveResult solve(BlockTask task, WorldView world, PlayerView player, EasyPlaceProtocol proto);
    // P2-02 Regeln (Vanilla getStateForPlacement, 26.2 nachgelesen):
    //  nur PLACE-Tasks; ohne Item → Unsupported · keine Regel für die Blockklasse → Unsupported (abhängige Blöcke: P2-04)
    //  schlicht (voller Kollisionsblock ohne Properties außer waterlogged; Fence, Wall): jede Seite
    //    nicht volle Blöcke ohne Properties (Fackel, Blume, Teppich) → Unsupported bis P2-04
    //  Slab BOTTOM: Seite ≠ DOWN, seitlich Treffer-Y unten (+0.25) · TOP: Seite ≠ UP, seitlich oben (+0.75) · DOUBLE → Unsupported
    //  Stairs: Hälfte wie Slab; FACING = Blickrichtung des Spielers → requiresRealRotation, Yaw im Quadranten von FACING
    //  RotatedPillarBlock: Klick-Seite auf der Achse AXIS
    //  GlazedTerracotta, AbstractFurnace: FACING = Gegenrichtung des Blicks → requiresRealRotation
    // Kandidaten: Nachbar N mit voller (sturdy) Fläche zum Ziel, nicht ersetzbar; clickPos = N, clickFace = Richtung N→Ziel,
    //  hitVec auf der gemeinsamen Fläche. clickAdjacentOnly=false: zusätzlich Klick auf die Zielposition selbst („Airplace“),
    //  nur wenn kein Nachbar-Kandidat nutzbar ist. Rangfolge: Fläche zum Auge + in Reichweite + (lineOfSight ? Sicht : egal),
    //  dann Nachbar vor Airplace, dann Abstand. Reichweite/Sicht des gewählten Plans prüft der Printer (P2-03) erneut.
    //  Kein Kandidat → NeedsSupport(unter dem Ziel; bei oberer Hälfte über dem Ziel; bei X/Z-Pillar westlich/nördlich).
    //  sneak immer true (verhindert, dass ein Klick eine GUI/Tür des Nachbarn bedient). yaw/pitch = Blick auf hitVec.
    //  proto wird erst in P5-06 ausgewertet.
    // P2-04 Regeln (abhängige Blöcke, Vanilla 26.2 nachgelesen). Kein Airplace außer Teppich (Klick auf die Zielposition
    //  ordnet getNearestLookingDirections nach Blick → nicht planbar). Klick auf einen Nachbarn setzt dessen Richtung zuerst.
    //  Torch/RedstoneTorch stehend: Seite UP · WallTorch/RedstoneWallTorch, Ladder, WallSign (FACING F): Seite F
    //  Button/Lever (FaceAttached): FLOOR → UP, CEILING → DOWN, beide FACING = Blickrichtung (requiresRealRotation) · WALL → Seite FACING
    //    POWERED=true → Unsupported („switched on“)
    //  TrapDoor: seitlich Seite = FACING, Hälfte über Treffer-Y · oder UP (BOTTOM) / DOWN (TOP) mit FACING = Gegenrichtung des Blicks
    //  Door untere Hälfte: Seite UP auf Block darunter, FACING = Blickrichtung; Scharnier: erzwingen Nachbarn (volle Blöcke/Türen
    //    links/rechts, wie DoorBlock.getHinge) ein anderes → Unsupported; sonst Treffer um 0.25 nach links (LEFT, gegen Uhrzeigersinn
    //    von FACING) bzw. rechts (RIGHT) verschoben · Block über dem Ziel nicht ersetzbar → Unsupported · obere Hälfte: kein Klick,
    //    NeedsSupport(unten) – entsteht mit der unteren
    //  Door/TrapDoor OPEN=true ohne POWERED → Unsupported („opened by hand“)
    //  Carpet: jede Seite, Airplace erlaubt; Block darunter Luft → NeedsSupport(unten)
    //  StandingSign ROTATION r: Seite UP, Yaw = r·22,5° − 180° (±11°, RotationSegment) · Hängeschilder, Schienen u. a. weiter Unsupported
    //  NeedsSupport bei Wandblöcken = Nachbar hinter FACING, bei CEILING/TrapDoor TOP = oben, sonst unten.
    public static Optional<String> unsupportedReason(BlockState target); // unabhängig von Welt/Spieler; leer = Regel vorhanden (für .sf preview)
}

// P2-03. Tick-Loop für genau einen Cluster; Navigation und Zustandsautomat liegen in SchemaPrinter (P2-07).
public final class Printer {
    public static final int MAX_ATTEMPTS = 3;                        // pro Task pro Cluster-Besuch (AK3)
    public Printer(PlacementSolver solver, WorkPlanner planner, MaterialManager materials, ActionBudget budget, PlacementLog log);  // P2-05: materials
    public void startCluster(Cluster c);                             // neuer Besuch: Versuche und Durchlauf zurücksetzen; materials.startCluster(c)
    public void tick(WorldView world, PlayerView player, InventoryView inv, PrintActions actions);
    public boolean clusterDone();                                    // kein offener PLACE-Task mit Versuchen < MAX_ATTEMPTS
    public int placedCount();                                        // gesendete Platzierungen in diesem Besuch
    // Durchlauf: zu Beginn refresh(), dann Tasks in Reihenfolge ab Cursor, höchstens ein Durchlauf pro Tick.
    //  Nur PLACE; Position nicht mehr ersetzbar → weiter ohne Versuch (nächster refresh entscheidet).
    //  Jeder Blick auf einen Task zählt einen Versuch: NeedsSupport/Unsupported, Plan außer Reichweite
    //  (eyePos→hitVec > reach), ohne Sicht (lineOfSight), Fläche nicht zum Auge, Item nicht in der Hotbar, gesendet.
    //  Budget leer → Tick endet, Cursor bleibt (kein Versuch). Gesendet → PlacementLog.append(pos, target, temp=false).
    //  Eine Platzierung = eine Budget-Einheit. Rotation immer (VANILLA_LEGIT). proto = NONE bis P5-06.
    //  P2-05: Hotbar über materials.select(item): Ready → platzieren · Swap → swapToHotbar (eine Budget-Einheit, zählt als
    //  Versuch; platziert wird im nächsten Durchlauf) · Missing → Versuch ohne Paket (Fehlbestand-Event feuert der MaterialManager).
}

// P2-05. Erlaubte Hotbar-Slots. Text wie im Spiel nummeriert (Tasten 1–9): „2-8“, „1,3,5-7“; intern Index 0–8.
public record HotbarSlots(Set<Integer> indices) {                   // aufsteigend, nicht leer
    public static HotbarSlots parse(String text);                    // IllegalArgumentException bei leer/ungültig/außerhalb 1–9
    public boolean allows(int index);
}

// P2-05. Bedarf eines Clusters, Hotbar-Wahl nur in erlaubten Slots, Fehlbestand-Event. Sendet selbst nichts.
public final class MaterialManager {
    public record Shortage(Item item, int missing) {}                // missing = Bedarf − Inventar, mindestens 1
    public sealed interface Selection {
        record Ready(int hotbarSlot) implements Selection {}         // Item liegt in erlaubtem Slot (gewählter Slot zuerst, sonst kleinster)
        record Swap(int fromSlot, int toHotbarSlot) implements Selection {}
        record Missing(Shortage shortage) implements Selection {}
    }
    public MaterialManager(Supplier<HotbarSlots> allowed, Consumer<Shortage> onShortage);   // allowed je Aufruf gelesen (Setting)
    public void startCluster(Cluster c);                             // Bedarf = MaterialRules.required der PLACE-/FLUID-Tasks; gemeldete Items zurücksetzen
    public Map<Item, Integer> demand();
    public List<Shortage> checkShortages(InventoryView inv);         // alle Items mit Inventar < Bedarf (nach Item-ID sortiert), meldet noch nicht gemeldete
    public Selection select(Item item, InventoryView inv);
    // select: Item in erlaubtem Hotbar-Slot → Ready. Sonst Item irgendwo im Inventar (nicht erlaubter Hotbar-Slot oder 9–35;
    //  größter Stapel) → Swap in: leeren erlaubten Slot, sonst erlaubten Slot ohne Item aus demand(), sonst kleinsten erlaubten.
    //  Sonst Missing; das Event feuert höchstens einmal je Item pro Cluster-Besuch.
}

// P2-03. Speichert jede Platzierung; Datei optional (Tests, Schreibfehler). Zeile: {"v":1,"pos":[x,y,z],"block":"<BlockStateParser.serialize>","temp":false,"t":<epoch ms>}
public final class PlacementLog {
    public record Entry(BlockPos pos, BlockState block, boolean temp, long t) {}
    public static PlacementLog inMemory();
    public static PlacementLog toFile(Path file);                    // hängt an; Verzeichnis wird angelegt
    public void append(BlockPos pos, BlockState block, boolean temp);
    public List<Entry> entries();                                    // Einträge dieser Sitzung
    public Optional<IOException> writeError();                       // erster Schreibfehler; danach nur noch im Speicher
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

P2-07: Der Automat liegt testbar in `core/BuildSession`; `SchemaPrinter` hält Settings, baut Views/Actions und reicht Ticks durch.
onEnter/tick/onExit sind Zweige eines `switch` je Zustand, nicht eigene Klassen. RESTOCKING (P4-04) und automatische
Pausen (P3-03) existieren als Zustände, werden aber noch nicht betreten; TRAVELING seit P3-02.

```java
public final class BuildSession {
    public enum State { IDLE, PLANNING, BUILDING, TRAVELING, RESTOCKING, PAUSED, VERIFYING, DONE }
    public static final int MAX_ROUNDS = 3;                          // BUILDING→VERIFYING-Runden, danach DONE mit Rest
    public record Status(State state, String placement, int round, int clusterIndex, int clusterCount,   // clusterIndex 1-basiert, 0 vor dem ersten
                         int placed, double blocksPerMinute, int mismatched, int remaining) {}
    public BuildSession(SchematicSnapshot snap, WorkPlanner planner, Printer printer, Navigator navigator,
                        LongSupplier clockMs, BiConsumer<State, State> onStateChange, Consumer<String> notes);
                                                                     // P3-02: navigator + notes (Chat-Einzeiler, z. B. „Cluster nicht erreichbar“)
    public void start();                                             // IDLE → PLANNING
    public void tick(WorldView world, PlayerView player, InventoryView inv, PrintActions actions);
    public boolean pause();                                          // aus jedem laufenden Zustand → PAUSED; false sonst
                                                                     // P3-02: aus TRAVELING zusätzlich navigator.cancel(), resume läuft den Cluster neu an
    public boolean resume();                                         // PAUSED → Zustand davor; false sonst
    public State state();
    public Status status();
    // PLANNING (1 Tick): planner.plan(snap, world, BlockPos.containing(player.eyePos())) → BUILDING
    // BUILDING: kein Cluster offen oder printer.clusterDone() → nächster Cluster mit PLACE-Task (printer.startCluster);
    //   Cluster ohne PLACE-Task (nur SKIP/BREAK/FLUID) und geblacklistete werden übersprungen; keiner mehr → VERIFYING.
    //   Sonst printer.tick.
    // P3-02: kein PLACE-Task des Clusters innerhalb player.reach() und navigator.start(center) → TRAVELING.
    // TRAVELING: navigator.tick. ARRIVED → printer.startCluster, BUILDING. FAILED → Meldung über notes und Cluster ans
    //   Ende der Liste; beim dritten Fehlversuch blacklistet der Navigator ihn, dann Meldung „cannot be reached“ und
    //   kein weiterer Anlauf. Ohne Baritone wird nie TRAVELING betreten.
    // VERIFYING (1 Tick): neu planen. remaining = PLACE-Tasks, mismatched = SKIP MISMATCH_ADDITIVE_ONLY.
    //   remaining == 0, in dieser Runde nichts gesendet oder round == MAX_ROUNDS → DONE; sonst round+1, neue Cluster, BUILDING.
    //   (Weitere Runden fangen Blöcke, deren Träger erst in einem späteren Cluster entstand.)
    // mismatched/remaining kommen aus dem letzten Plan (PLANNING oder VERIFYING). placed = gesendete Platzierungen aller Runden.
    // blocksPerMinute = placed / aktive Zeit (Zeit in PAUSED zählt nicht); 0 bei weniger als 1 s.
}

// P2-07. Zeilen „a->b,c“: Zielblock a darf in der Welt auch b oder c sein. Ids ohne Namespace = minecraft.
public final class Substitutes {
    public record Parsed(Map<Block, List<Block>> map, List<String> errors) {}   // fehlerhafte Zeilen werden übersprungen, Fehlertext je Zeile
    public static Parsed parse(List<String> lines);
}
```

`SchemaPrinter` (P2-07):
- `onActivate`: Placement aus Setting (leer → einziges geladenes), Snapshot, `PlanConfig`/`SolverConfig`/`HotbarSlots` aus Settings
  (fehlerhaft → Chat-Fehler, Modul aus), `PlacementLog.toFile(meteor-client/schemaforge/placementlog-<name>.jsonl)`,
  `AdditiveOnlyGuard.engage(additiveOnly)`, Session starten. Änderungen an Plan-/Solver-Settings greifen beim nächsten Start;
  `blocksPerTick`, `tickInterval`, `allowedHotbarSlots` (ungültig → letzter gültiger Wert) und `rotationSpoof` sofort.
- Meteor ruft `onActivate` beim Welt-Beitritt erneut für aktive Module auf (auch nach Neustart aus der Config). Ein Lauf
  startet nur aus `toggle()` (GUI, Keybind, `.sf start`); sonst wird das Modul im nächsten Tick ausgeschaltet.
- `onDeactivate`: Session verwerfen (Status bleibt für `.sf status` als „stopped“), `guard.release()`, `navigator.cancel()`.
- P3-02: je Lauf ein neuer `Navigator` (Blacklist gilt nur für den Lauf); ohne Baritone eine Chat-Zeile, dass nur
  Cluster in Reichweite gebaut werden.
- Budget: Limit `blocksPerTick` in Ticks mit `tick % tickInterval == 0`, sonst 0.
- DONE → Chat-Zusammenfassung, Modul aus. Fehlbestand → Chat-Warnung einmal je Item pro Lauf.

Commands (P2-07): `.sf start [placement]` setzt das Setting und (re)startet das Modul · `.sf pause` / `.sf resume` →
`BuildSession.pause/resume` · `.sf stop` → Modul aus · `.sf status` → Zeilen aus `Status` (State, Round, Cluster i/n,
placed, blocks/min, mismatched, remaining), ohne Lauf der letzte Status oder „idle“.

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
| General | buildOnlySelection | bool | false |  ← P2-07: nicht angelegt, kein Ticket (Backlog)
| Order | layerAxis / layerAscending | enum / bool | Y / true |
| Order | clusterSize | int 3–16 | 5 |
| Filters | skipIfWorldIs / treatAsAir / neverPlace | BlockList | leer / grass,tall_grass / tnt |
| Filters | substitutes | StringList `a->b,c` | leer |
| Filters | ignoreProperties | StringList | waterlogged |
| Placement | profile | enum VANILLA_LEGIT / FAST / CUSTOM | VANILLA_LEGIT |  ← P2-07: nicht angelegt, kommt mit P5-05
| Placement | blocksPerTick / tickInterval | int 1–8 / int 1–20 | 1 / 1 |
| Placement | reach | double ≤ 4.5 | 4.5 |
| Placement | lineOfSight | bool | true |
| Placement | clickAdjacentOnly | bool | true |
| Placement | rotationSpoof | bool | false |
| Placement | allowedHotbarSlots | String `2-8` | 2-8 |
| Safety | pauseOnDamage / minFood / pausePlayerRadius | bool / int / int | true / 6 / 16 |  ← P2-07: nicht angelegt, kommt mit P3-03
| Debug | logStateChanges / renderClusters | bool / bool | false / true |  ← P2-07: nur logStateChanges; renderClusters ohne Ticket (Backlog)

## 8. Commands

```
.sf start [placement]   .sf pause   .sf resume   .sf stop
.sf debug goto <x> <y> <z>   .sf debug stopgoto      (P3-01, intern)
.sf status              .sf preview [placement]
.sf verify              .sf materials
.sf restock [item]      .sf scan [radius]      .sf containers
.sf undo <n>            .sf doctor
```

## 9. Bewusst offen (nicht ohne Ticket bauen)

Multi-Account, Mining/Crafting-Beschaffung, Servux-Handshake selbst implementieren, Forge/NeoForge.
