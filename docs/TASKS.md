# Tickets

Status: `todo` · `in_progress` · `blocked` · `done`. Immer nur ein Ticket `in_progress`.
Akzeptanzkriterien (AK) einzeln abhaken. „Manuell“ = In-Game-Test, Ergebnis nach `docs/TESTLOG.md`.

---

## Phase 0 – Setup

### P0-01 · Template übernehmen `done`
Template `MeteorDevelopment/meteor-addon-template` in dieses Repo, Paket `com.example.addon` → `dev.tore.schemaforge`, Addon-Name/ID `schemaforge`, Icon-Ordner umbenennen, Lizenz GPL-3.0.
- AK1 `./gradlew build` grün
- AK2 `./gradlew runClient` startet, Addon erscheint in Meteor unter Addons
- AK3 `fabric.mod.json`: `suggests` für `litematica`, `malilib`, `baritone`; kein `depends` darauf

### P0-02 · Referenzquellen klonen `done`
`refs/` anlegen, Klone laut CLAUDE.md, `refs/` in `.gitignore`.
- AK1 Die vier in CLAUDE.md genannten Meteor-Dateien existieren lokal und wurden gelesen; Notiz mit 5 Stichpunkten „so platziert/rotiert/klickt Meteor in 26.2“ nach `docs/NOTES-meteor-api.md`
- AK2 Aus `refs/baritone/.../LitematicaHelper.java` die Liste aller genutzten Litematica-Methoden (Klasse + Signatur) nach `docs/NOTES-litematica-api.md`

### P0-03 · Gradle-Abhängigkeiten `done`
Modrinth-Maven ergänzen; Litematica + MaLiLib 26.2 als `compileOnly` (Loom 1.17 hat kein `modCompileOnly`); Baritone-API wie Meteor (`meteordevelopment:baritone`, gleiche Version wie in Meteors `libs.versions.toml`); JUnit 5.
- AK1 Build grün, `import fi.dy.masa.litematica.data.DataManager` kompiliert in `compat/`
- AK2 `import baritone.api.BaritoneAPI` kompiliert
- AK3 Leerer JUnit-Test läuft mit `./gradlew test`
- AK4 Genaue Litematica-/MaLiLib-Versionsnummern in `docs/PLAN.md` Abschnitt 2.1 nachgetragen

### P0-04 · Skeleton + Logger `done`
Alle Pakete/Klassen aus ARCHITECTURE.md als leere Hüllen (Signaturen, `throw new UnsupportedOperationException("P?-??")` im Body). `SchemaForgeAddon.LOG`.
- AK1 Build grün, keine Warnungen aus eigenem Code
- AK2 Jede Hülle referenziert im Kommentar das Ticket, das sie füllt

### P0-05 · `.sf doctor` + VersionProbe `done`
`VersionProbe` sammelt: MC-, Meteor-, Baritone-, Litematica-, MaLiLib-Version (via FabricLoader), Server-Brand, EasyPlace-Protokoll (Litematica-Config lesen), und pro Litematica-Methode aus `docs/NOTES-litematica-api.md` ob `MethodHandle` auflösbar. Ausgabe im Chat als Tabelle.
- AK1 Manuell: mit allen Mods → alle Zeilen grün
- AK2 Manuell: Litematica entfernt → Addon startet, `.sf doctor` meldet „Litematica missing“, kein Crash
- AK3 Manuell: Baritone entfernt → analog

Stand 2026-09-13: Code fertig (`VersionProbe`, `ProbeReport`, `SignatureCheck`, `LitematicaAdapter.probe()/detectedProtocol()`, `DoctorCommand`), Build + 11 Tests grün. Alle 13 Methoden aus NOTES-litematica-api.md per `javap` gegen das echte Litematica-0.28.8-Jar bestätigt. Auf Anweisung des Nutzers `done` gesetzt; AK1–3 wurden **nicht** in-game ausgeführt (siehe TESTLOG):
- Dev-Client mit Litematica 0.28.8 + MaLiLib 0.29.6 crasht direkt nach dem Start im Rendering (`IllegalStateException: Missing uniform Globals (should be UNIFORM_BUFFER)` in `TextureAtlas.cycleAnimationFrames`, AMD RX 9060 XT). Ohne diese beiden Mods kein Crash; SchemaForge ist zu dem Zeitpunkt schon initialisiert. Nicht Teil von P0-05 → Backlog.
- Baritone `26.1-SNAPSHOT` lädt unter MC 26.2 nicht (Fabric: „requires minecraft 26.1.x“) → in `libs.versions.toml` auf `26.2-SNAPSHOT` angehoben (Nutzerentscheidung); dasselbe Jar liegt in `run/mods/`. Ein Release `26.2` steht in Meteors Maven-Metadaten, hat aber keine Dateien (404).
- Meteors Baritone-Fork hat die Mod-ID `baritone-meteor`; `fabric.mod.json` `suggests` darum ergänzt.

Nachtrag 2026-09-15: Der Render-Crash ist geklärt (MaLiLib, Workaround `tools/patch_malilib_dev.py`). AK1 in-game **bestanden** (25/25 OK, TESTLOG). AK2/AK3 weiter offen (Backlog).

---

## Phase 1 – Adapter + Planner

### P1-01 · `LitematicaAdapter.placementNames()` + `isPresent()` `done`
- AK1 Manuell: zwei geladene Placements → beide Namen im `.sf preview`-Dropdown/Chat
- AK2 Ohne Litematica → leere Liste, keine Exception

Stand 2026-09-13: `isPresent()` (Hauptklasse ladbar) und `placementNames()` (Handles einmalig per Holder aufgelöst, beide Methodennamen für `getAll…Placements`, bei Inkompatibilität einmal Log-Warnung + leere Liste, nie Exception). AK2 per `LitematicaAdapterTest` erfüllt (Litematica ist `compileOnly`, fehlt also im Test-Runtime). AK1 **offen**: `.sf preview` kommt erst mit P1-05, und der Dev-Client crasht mit Litematica (siehe P0-05).

Stand 2026-09-15: Auf Anweisung des Nutzers `done` gesetzt. AK1 wurde **nicht** in-game geprüft, sondern als Backlog-Punkt übernommen (mit P1-05 nachholen).

Nachtrag 2026-09-15: AK1 in-game **bestanden** mit `.sf preview` (zwei Placements gelistet; gleichnamige Placements seitdem als „name (2x)“, siehe P1-05 und TESTLOG).

### P1-02 · `LitematicaAdapter.snapshot()` `done`
Alle aktivierten Sub-Regionen, Mirror/Rotation von Placement **und** Sub-Region anwenden (Transformationslogik wie in `LitematicaHelper.transform`, eigenständig implementiert), Blöcke via MethodHandle aus der Schematic-World lesen, `materialTotals` berechnen.
- AK1 Unit-Test: Transformation für alle 4 Rotationen × 3 Mirror-Zustände gegen handverifizierte Tabelle
- AK2 Manuell: Test-Schematic (P1-04) unrotiert → `materialTotals` == Litematica-Materialliste (Abweichung 0)
- AK3 Manuell: gleiches Placement um 90° gedreht + gespiegelt → Bounding Box stimmt mit Litematica-Rendering überein
- AK4 Manuell: eine Sub-Region deaktiviert → deren Blöcke fehlen im Snapshot

Stand 2026-09-15: `compat/PlacementTransform` (Mirror/Rotation, Sub-Region-Box), `core/MaterialRules` (Regeln von Litematicas `MaterialCache`), `LitematicaAdapter.snapshot()` (erstes Placement mit dem Namen; deaktiviertes Placement → leer; Positionen in nicht geladenen Schematic-Chunks werden weggelassen und geloggt; neue Signatur `SchematicPlacement.isEnabled()` auch in `.sf doctor`). ARCHITECTURE.md §1/§2/§4 und NOTES-litematica-api.md ergänzt. Build + 43 Tests grün.
- AK1 erfüllt: `PlacementTransformTest` (12er-Tabelle + Box-Fälle). Zusätzlich einmalig per Reflection gegen Litematica-0.28.8-`PositionUtils` abgeglichen: 3084 Fälle, 0 Abweichungen (nicht Teil der Test-Suite, weil Litematica im Test-Runtime fehlen muss).
- AK2–AK4 **nicht** in-game geprüft: Test-Schematic (P1-04) fehlt noch, `.sf preview` (P1-05) auch, und der Dev-Client crasht mit Litematica (siehe P0-05). `MaterialRulesTest` deckt die Blockklassen der Test-Schematic ab (Stufen, Tür, Wandfackel, Wasser …), ersetzt aber nicht den Abgleich mit der Litematica-Liste. Auf Anweisung des Nutzers `done`; AK2–4 → Backlog.

Nachtrag 2026-09-15: AK2 **teilweise** in-game: `.sf preview` liefert für die Test-Schematic exakt die erwartete Liste (15 Typen / 337 Items); der direkte Vergleich mit Litematicas Materiallisten-GUI steht aus. AK3/AK4 offen (Backlog).

### P1-03 · `WorkPlanner.plan()` `done`
Diff Soll/Ist über `WorldView`; Filterregeln; Priorität: Support-Blöcke (voll) vor abhängigen (Torch, Button, Rail, Carpet, Door, Sign, Ladder, Vine, Slab-Top ohne Träger); Clustering in Würfel `clusterSize`; Reihenfolge nach `layerAxis`/`layerAscending`, innerhalb einer Schicht Nearest-Neighbor ab Spielerposition.
- AK1 Unit-Test: 3×3×3-Snapshot, Welt leer → alle 27 Tasks, korrekte Cluster-Anzahl
- AK2 Unit-Test: Welt hat bereits 10 korrekte Blöcke → 17 Tasks
- AK3 Unit-Test: `additiveOnly=true`, Welt hat falschen Block → Task `SKIP` mit Grund, kein `BREAK`
- AK4 Unit-Test: Torch über fehlendem Boden → Torch-Task hat niedrigere Priorität als Boden-Task
- AK5 Unit-Test: `substitutes` stone→[andesite] + Welt hat Andesit → kein Task

Stand 2026-09-15: `WorkPlanner.plan()` mit Diff-Regeln, Filtern, Prioritäten, Würfel-Clustern und Reihenfolge; neues Enum `SkipReason`, `BlockTask.skipReason`. Schnittstelle geändert: `plan(snap, world, start)` – für „Nearest-Neighbor ab Spielerposition“ fehlte die Startposition (ARCHITECTURE.md §2/§4 vorher angepasst). Build + 56 Tests grün.
- AK1–AK5 erfüllt: `WorkPlannerTest` (27 Tasks / 1 bzw. 8 Cluster · 17 Tasks · SKIP `MISMATCH_ADDITIVE_ONLY`, ohne additiveOnly BREAK · Fackel 100 < Boden 300 und danach einsortiert · Andesit als Ersatz → kein Task). Zusätzlich: `ignoreProperties`, `ignoreAir`, alle Skip-Gründe, `treatAsAir`/ersetzbare Blöcke, FLUID, Schicht-Richtung, Nearest-Neighbor, obere Stufe mit/ohne Träger.

### P1-04 · Test-Schematic per litemapy `done`
`tools/gen_test_schematic.py` erzeugt `test/blockclasses.litematic`: 12×12×6 mit je einer Zeile pro Blockklasse (Vollblock, Slab unten/oben, Stairs 4 Richtungen, Log 3 Achsen, Wall-Torch 4 Seiten, Button, Lever, Trapdoor offen/zu, Door, Rail gerade/Kurve, Carpet, Glazed Terracotta 4 Richtungen, Water source, Fence).
- AK1 Skript läuft mit `pip install litemapy`, Datei lädt in Litematica ohne Fehler
- AK2 Skript druckt Materialliste (Item → Anzahl) für Vergleich in P1-02

Stand 2026-09-15: Skript + `tools/requirements.txt` (`litemapy==0.11.0b0`), erzeugte Datei `test/blockclasses.litematic` (965 B) ist eingecheckt, damit In-Game-Tests kein Python brauchen. Layout (im Docstring des Skripts): Steinsockel y=0–1 mit eingefasstem Wasser-Quellblock, Reihen auf y=2, alle abhängigen Blöcke mit echtem Träger (Wandfackeln an Brettern/Terrakotta, Wand-Knopf/-Hebel an Terrakotta/Stamm, obere Stufe neben Stamm, Schienenkurve mit zwei Nachbarn). Abweichungen vom Ticket: Carpet und Fence teilen sich Reihe z=11 (13 Klassen passen nicht in 12 Reihen, Wasser liegt im Sockel), das Schienen-Endstück der Kurve steht in der Tür-Reihe. Datenversion 4903 (MC 26.2) und Litematic-Version 7 (Litematica 0.28.8) gesetzt, damit Litematica keinen Datafixer anwirft.
- AK1 erfüllt, aber **außerhalb des Spiels** geprüft: Skript lief in einem venv nach `pip install litemapy`; die Datei wurde in einer Scratch-JVM mit Litematicas eigenem Parser (`LitematicaSchematic.readFromData`, 0.28.8, MC-Registries per Bootstrap) fehlerfrei gelesen – Version 7, Datenversion 4903, alle Properties erhalten, 339 Nicht-Luft-Blöcke. Laden im echten Client steht noch aus (Render-Crash, P0-05) → mit P1-02 AK2 nachholen.
- AK2 erfüllt: Skript druckt 15 Items / 337 Stück (u. a. stone 287, oak_planks 13, rail 6, torch 4, water_bucket 1). `MaterialRules.totals` über die von Litematica gelesenen Blockzustände ergibt exakt dieselbe Liste.
- Nachtrag 2026-09-15: AK1 jetzt auch im echten Client **bestanden** – Datei lädt in Litematica 0.28.8 und lässt sich platzieren (TESTLOG).

### P1-05 · `.sf preview` `done`
Blockanzahl, Cluster-Anzahl, Materialliste (gesamt / fehlend im Inventar), Warnungen (Y<-64, Bounding Box > Renderdistanz, Blöcke mit `Unsupported`-SolveResult).
- AK1 Manuell: Ausgabe ≤ 25 Zeilen für Test-Schematic; bei > 30 Materialien nur Top 30 + „…“

Stand 2026-09-15: `commands/PreviewCommand` (`.sf preview [placement]`, Tab-Vorschläge aus `placementNames()`, ohne Argument das einzige Placement, sonst Namensliste), `core/PreviewReport` (Zeilen als reine Daten), `compat/McWorldView`, `PlanConfig.defaults()` (§7-Defaults, bis P2-07 Settings liefert). Geplant wird gegen die echte Welt ab Spielerposition; „missing“ = Bedarf der noch offenen PLACE/FLUID-Tasks minus Inventar (`InvUtils.find`), „gesamt“ = ganze Schematic wie Litematicas Liste. Warnungen: unter Weltboden / über Bauhöhe (aus dem Level statt fest −64), breiter als Renderdistanz, Positionen ohne geladenen Schematic-Chunk, Skip-Gründe. ARCHITECTURE.md §1/§2/§4 ergänzt. Build + 62 Tests grün.
- **Abweichung:** Warnung „Blöcke mit `Unsupported`-SolveResult“ fehlt – `PlacementSolver` wirft bis P2-02 nur `UnsupportedOperationException`. In P2-02 nachrüsten (siehe dort). → mit P2-02 nachgerüstet.
- AK1 per `PreviewReportTest` abgesichert (15 Materialien → ≤ 25 Zeilen; 41 Materialien → 30 Zeilen + „… 11 more types“). Die Zeilenzahl ist höchstens 4 + Materialien + 5 Warnzeilen, für die Test-Schematic (15 Materialien) also ≤ 24.
- AK1 in-game **bestanden** (Dev-Client, `blockclasses.litematic` platziert): 19 Zeilen, 339 Blöcke / 338 place + 1 fluid, 9 Cluster, 15 Materialien / 337 Items – identisch mit der Skript-Liste. Keine Warnungen (Placement kompakt, Y 79–84).
- Dabei gefunden und behoben: zwei Placements mit gleichem Namen erschienen als „blockclasses, blockclasses“, das zweite war nicht wählbar. Jetzt: Namensliste als „blockclasses (2x)“, Tab-Vorschläge ohne Doppelte, beim Preview gelber Hinweis „2 placements are named …; showing the first one. Rename them in Litematica …“. Der Fix selbst ist nur per Build geprüft, nicht erneut in-game.

---

## Phase 2 – Printer MVP

### P2-01 · `ActionBudget` + Tick-Hook `done`
- AK1 Unit-Test: Limit 2 → dritter `tryConsume()` im selben Tick false, nach `resetTick()` wieder true
- AK2 Modul-Tick registriert via Meteor `EventHandler` auf `TickEvent.Pre` (Name aus refs prüfen)

Stand 2026-09-15: `ActionBudget` zählt verbrauchte Aktionen, liest das Limit bei jedem `tryConsume()` (geänderte Settings greifen sofort, Limit ≤ 0 erlaubt nichts), nur Client-Thread. `SchemaPrinter` hält ein Budget (fest 1, bis `blocksPerTick` in P2-07 kommt) und setzt es in `@EventHandler onTickPre(TickEvent.Pre)` zurück. Build + 66 Tests grün.
- AK1 erfüllt: `ActionBudgetTest` (Limit 2 → dritter Aufruf false, nach `resetTick()` wieder zwei; Limit-Änderung im Tick; 0/negativ).
- AK2 erfüllt: `meteordevelopment.meteorclient.events.world.TickEvent.Pre` und `meteordevelopment.orbit.EventHandler` in `refs/meteor-client` nachgeschlagen (Verwendung wie in `HighwayBuilder.onTick`); `SchemaPrinterTest` prüft per Reflection, dass der Hook annotiert ist. In-game nicht prüfbar, solange das Modul nicht registriert ist (P2-07); Meteor abonniert Handler erst beim Aktivieren eines Moduls.

### P2-02 · `PlacementSolver` Grundklassen `done`
Vollblock, Slab (half), Stairs (facing+half), Pillar (axis), HorizontalFacing (Glazed Terracotta, Furnace), Fence/Wall (Nachbar egal). Klick-Seite + `hitVec` so wählen, dass Vanilla-Placement den Zielstate erzeugt; `requiresRealRotation` wo Facing vom Blick abhängt.
- AK1 Unit-Test pro Blockklasse: gegebener Zielstate → erwarteter `clickFace`/`hitVec`-Halbraum/Yaw-Quadrant
- AK2 Kein Nachbar zum Anklicken → `NeedsSupport(pos)`
- AK3 `clickAdjacentOnly=true` → nie `clickPos == task.pos`
- Nachtrag aus P1-05: `.sf preview` um die Warnung „n blocks unsupported“ (Tasks mit `SolveResult.Unsupported`, Grund gruppiert) ergänzen

Stand 2026-09-15: `PlacementSolver` mit Regeln aus Vanilla-26.2-`getStateForPlacement` (Vineflower gelesen: `SlabBlock`, `StairBlock`, `RotatedPillarBlock`, `GlazedTerracottaBlock`, `AbstractFurnaceBlock`, `FenceBlock`, `WallBlock`, `BlockPlaceContext`): Slab-/Stufen-Hälfte über Klick-Seite und Treffer-Y, Stufen-Facing = Blickrichtung, Terrakotta/Ofen = Gegenrichtung, Pillar-Achse = Klick-Seite. Kandidaten sind Nachbarn mit voller Fläche zum Ziel; bewertet nach „Fläche zeigt zum Auge + in Reichweite + Sicht“, dann Nachbar vor Airplace, dann Abstand. Neu: `SolverConfig(clickAdjacentOnly, lineOfSight)` als Konstruktor-Parameter und `PlacementSolver.unsupportedReason(BlockState)` (ARCHITECTURE.md §1/§4 vorher ergänzt). `sneak` ist immer true. Build + 81 Tests grün.
- AK1 erfüllt: `PlacementSolverTest` je Klasse (Vollblock, Slab unten/oben/doppelt, Stairs alle 4 Richtungen + oben, Pillar Y/X, Terrakotta + Ofen alle 4 Richtungen, Fence/Wall). Erwartung gegen die Vanilla-Formeln (`Direction.fromYRot(yaw)`, Hälften-Bedingung aus `SlabBlock`), nicht gegen Solver-Konstanten; zusätzlich Blickwinkel zeigt auf `hitVec`.
- AK2 erfüllt: kein Nachbar (oder nur ersetzbarer / nicht voller Nachbar) → `NeedsSupport` (unten; obere Hälfte oben; X/Z-Pillar westlich/nördlich).
- AK3 erfüllt: 5 Zielzustände × 3 Welten, nie `clickPos == task.pos`; mit `clickAdjacentOnly=false` Airplace nur als Ausweichlösung.
- Nachtrag P1-05 erledigt: `.sf preview` meldet „n blocks the printer cannot place yet: …“ (`PreviewReportTest`). Für die Test-Schematic kommt damit eine Zeile dazu (Wandfackeln, Knöpfe, Hebel, Falltüren, Türen, Schienen, Teppiche → P2-04); Zeilenzahl ≤ 4 + Materialien + 6 = 25.
- Nicht in-game geprüft (erst mit dem Printer in P2-03 sichtbar). Doppelstufen sind `Unsupported`, siehe Backlog.

### P2-03 · `Printer` Tick-Loop `done`
Für aktuellen Cluster: Tasks in Reihenfolge, Reichweite (`reach`, `lineOfSight`), Solve, Hotbar-Swap via `MaterialManager`, Rotation (echt via Meteor `Rotations` oder Spoof je Profil), Platzieren via `BlockUtils`-Äquivalent, `PlacementLog` schreiben, Budget beachten.
- AK1 Manuell: Test-Schematic-Zeile Vollblöcke/Slabs/Stairs/Logs in Singleplayer → Litematica-Verifier 0 Fehler
- AK2 Manuell: Profil VANILLA_LEGIT, 1 Block/Tick → keine Ghost-Blocks auf lokalem Paper-Server
- AK3 Blöcke außer Reichweite werden übersprungen, nicht endlos versucht (max 3 Versuche pro Task pro Cluster-Besuch)

Stand 2026-09-17: `Printer` arbeitet einen Cluster in Durchläufen ab: `WorkPlanner.refresh()` (jetzt implementiert, gleiche Regeln wie `plan`, Reihenfolge/Priorität bleiben), dann PLACE-Tasks ab Cursor, höchstens ein Durchlauf pro Tick. Jeder Blick auf einen Task zählt einen Versuch (Solver liefert kein `Ok`, Plan außer Reichweite/ohne Sicht/Fläche abgewandt, Item nicht in der Hotbar, gesendet); nach 3 Versuchen ist der Task für diesen Besuch erledigt, `clusterDone()` wird true. Budget leer → Tick endet ohne Versuch. Gesendete Platzierungen gehen ins `PlacementLog` (JSONL `{"v":1,pos,block,temp,t}`, Datei optional). Produktiv: `compat/McPrintActions` (Meteor `Rotations.rotate` mit Callback, `InvUtils.swap`, Sneak per `ServerboundPlayerInputPacket` um `useItemOn` herum, weil `BlockUtils.interact` Sneak löst), `McPlayerView` (Reichweite = min(Setting, `blockInteractionRange()`), Sicht per `Level.clip`), `McInventoryView`. Hotbar-Wahl vorerst `InventoryView.hotbarSlotWith` (bevorzugt den gewählten Slot) bis P2-05. Rotation immer (VANILLA_LEGIT); `rotationSpoof` ist Konstruktor-Parameter von `McPrintActions`, bis P2-07 das Setting liefert. ARCHITECTURE.md §1/§3/§4 vorher ergänzt (`PrintActions`, `Printer`, `PlacementLog`, `refresh`, `PlacementSolver.config()`). Vanilla-Signaturen per javap/Vineflower geprüft (`LocalPlayer.getLastSentInput`, `ClientInput.keyPresses`, `Input`, `MultiPlayerGameMode.useItemOn`, `ClipContext`, `BlockStateParser.serialize`). Build + 94 Tests grün.
- AK1/AK2 **offen**: nicht in-game prüfbar, solange kein Modul den Printer tickt (P2-07 registriert `SchemaPrinter`). Nachholen mit P2-07, siehe Backlog.
- AK3 erfüllt: `PrinterTest` – außer Reichweite nach 3 Ticks aufgegeben, nie gesendet; abgelehnte Platzierung höchstens 3× gesendet; neuer Besuch (`startCluster`) setzt Versuche zurück; fehlendes Item, fehlende Regel (Fackel) und fehlende Sicht werden nie gesendet. Außerdem: 1 Platzierung pro Tick bei Limit 1, Reihenfolge = Task-Reihenfolge, leeres Budget zählt keine Versuche, Log-Zeilenformat und Datei-Append. `WorkPlannerTest` um zwei `refresh`-Tests ergänzt.

### P2-04 · `PlacementSolver` abhängige Blöcke `done`
Wall-Torch, Torch, Button, Lever, Trapdoor, Door (untere Hälfte, hinge), Carpet, Ladder, Sign, Glazed Terracotta.
- AK1 Unit-Tests wie P2-02
- AK2 Manuell: entsprechende Zeilen der Test-Schematic → Verifier 0 Fehler

Stand 2026-09-17: Regeln aus Vanilla-26.2 gelesen (Vineflower: `StandingAndWallBlockItem`, `BlockPlaceContext.getNearestLookingDirections`, `TorchBlock`/`WallTorchBlock`/`RedstoneWallTorchBlock`, `FaceAttachedHorizontalDirectionalBlock`, `TrapDoorBlock`, `DoorBlock.getHinge`, `CarpetBlock`, `LadderBlock`, `StandingSignBlock`/`WallSignBlock`). Kernpunkt: Klick auf einen Nachbarn stellt dessen Richtung an die erste Stelle der Blickrichtungen, daher sind Wandblöcke ohne Rotation planbar; Klick auf die Zielposition selbst hängt vom Blick ab, deshalb kein Airplace für abhängige Blöcke (außer Teppich). Stehende Fackel/Schild nur von oben, Wandfackel/Leiter/Wandschild/Wandknopf auf die Wand hinter FACING, Boden-/Deckenknopf und -hebel mit Yaw-Quadrant, Falltür seitlich (Hälfte über Treffer-Y) oder von oben/unten mit Gegen-Yaw, Tür von oben mit Yaw und Treffer um 0,25 zur Scharnierseite verschoben; Scharnier, das die Nachbarn erzwingen, wird wie in `getHinge` nachgerechnet. Schild-Rotation über `RotationSegment` (±11°). Neu `Unsupported`: offene Tür/Falltür ohne Strom („opened by hand“), eingeschalteter Hebel/Knopf („switched on“), Tür mit belegtem Block darüber, erzwungenes falsches Scharnier. Obere Türhälfte → `NeedsSupport(unten)`. Glazed Terracotta war schon in P2-02 erledigt. ARCHITECTURE.md §4 vorher ergänzt. Build + 103 Tests grün.
- AK1 erfüllt: `PlacementSolverTest` +9 Tests (stehende Fackeln, Wandblöcke × 4 Richtungen × 5 Arten, Boden-/Deckenknopf/-hebel, Falltür seitlich/Boden/Decke, Tür 4 Richtungen × 2 Scharniere, Tür oben/blockiert/erzwungenes Scharnier, Teppich, Schild 16 Rotationen, kein Airplace). Erwartungen gegen die Vanilla-Formeln (`Direction.fromYRot`, `RotationSegment.convertToSegment(yaw+180)`, Scharnier-Ausdruck aus `DoorBlock.getHinge`), nicht gegen Solver-Konstanten. Bisherige Tests, die Fackeln als „nicht unterstützt“ nutzten, verwenden jetzt Schienen (P5-04).
- AK2 **offen**: in-game erst mit P2-07 prüfbar, siehe Backlog.

### P2-05 · `MaterialManager` Basis `done`
Bedarf aus Cluster, Hotbar-Swap nur in `allowedHotbarSlots`, Fehlbestand-Event.
- AK1 Unit-Test: Item in Slot 1 (nicht erlaubt) + Slot 4 → Slot 4 gewählt
- AK2 Unit-Test: Item fehlt → Event mit Item + Menge

Stand 2026-09-17: `MaterialManager` berechnet beim Cluster-Start den Bedarf (`MaterialRules` über PLACE-/FLUID-Tasks) und wählt pro Platzierung: Item in erlaubtem Hotbar-Slot → `Ready` (gewählter Slot zuerst, sonst kleinster), sonst Item im Inventar oder in nicht erlaubtem Hotbar-Slot → `Swap` (größter Stapel; Ziel: leerer erlaubter Slot, sonst einer ohne benötigtes Item, sonst kleinster), sonst `Missing` mit Fehlbestand-Event (`Consumer<Shortage>`, höchstens einmal je Item pro Cluster-Besuch; Menge = Bedarf − Inventar, mind. 1). `checkShortages(inv)` meldet alle Fehlbestände vorab (für RESTOCKING in P2-07/P4-04). Neu `HotbarSlots.parse("2-8")`: Nummern wie die Tasten 1–9, intern Index 0–8; Setting wird bei jedem Aufruf gelesen. `Printer` nutzt jetzt den MaterialManager: `Swap` kostet eine Budget-Einheit und einen Versuch, platziert wird im nächsten Durchlauf. `PrintActions.swapToHotbar` → `McPrintActions` per Meteor `InvUtils.quickSwap()` (ein SWAP-Paket), nur wenn das Spielerinventar-Menü aktiv ist. `InventoryView` um `selectedSlot()`, `itemAt(slot)`, `countAt(slot)` erweitert – Item + Anzahl statt `ItemStack`, weil `ItemStack` im Unit-Test ohne gebundene Item-Komponenten nicht erzeugbar ist. ARCHITECTURE.md §1/§3/§4 vorher ergänzt. Build + 113 Tests grün.
- AK1 erfüllt: `MaterialManagerTest.picksAllowedSlotOverDisallowedOne` – Stein in Slot 1 (Index 0, von „2-8“ nicht erlaubt) und Slot 4 (Index 3) → `Ready(3)`. Dazu: gewählter Slot bevorzugt, Swap-Quelle/-Ziel, geänderte Settings greifen sofort, Parser.
- AK2 erfüllt: `MaterialManagerTest.missingItemFiresEventWithAmountOncePerVisit` – 3× Stein im Cluster, Inventar leer → Event `Shortage(STONE, 3)`, beim zweiten `select` kein weiteres, nach neuem Cluster-Besuch wieder. `PrinterTest` +2 (Swap aus dem Inventar vor dem Platzieren, ein Event bei fehlendem Item).

### P2-06 · `AdditiveOnlyGuard` `done`
Bei `additiveOnly` keine Break-Aktion aus Printer; `BaritoneBridge.setAvoidBreaking(bbox)` beim Start, `restoreSettings()` beim Stop.
- AK1 Manuell: Fremder Block im Baubereich bleibt stehen, erscheint in `.sf status` unter „mismatched“
- AK2 Manuell: Modul aus → Baritone-Settings wieder wie vorher (`#modified` zeigt keine SchemaForge-Änderung)

Stand 2026-09-17: `baritone.api` hat keine positionsbezogene Break-Sperre (Jar 26.2-SNAPSHOT per javap geprüft: nur `allowBreak`, `allowBreakAnyway`, Blocktyp-Listen `blocksToAvoidBreaking`/`blocksToDisallowBreaking`). Statt `setAvoidBreaking(bbox)` verbietet `core/AdditiveOnlyGuard` Baritone deshalb das Brechen während des ganzen Laufs: `engage(additiveOnly)` merkt `allowBreak` + `allowBreakAnyway` und schreibt `false` + leere Liste, `release()` schreibt die gemerkten Werte zurück – als **dieselben Objekte**, weil Baritones `modifiedSettings` per `value == defaultValue` vergleicht (eine Listen-Kopie stünde sonst in `#modified`). Ohne Baritone oder ohne additiveOnly passiert nichts; zweites `engage` überschreibt die gemerkten Werte nicht. `BaritoneBridge.isPresent()` (Klasse laden ohne Initialisierung, wie `VersionProbe`) und `BaritoneBridge.BREAK_SETTINGS` implementiert; `restoreSettings()` entfällt (liegt im Guard). `SchemaPrinter` ruft `engage(true)` in `onActivate`, `release()` in `onDeactivate` (additiveOnly fest, bis das Setting in P2-07 kommt). Printer: keine Änderung nötig – er bearbeitet nur PLACE-Tasks, `PrintActions` hat keine Break-Aktion. ARCHITECTURE.md §1/§4 vorher angepasst. Build + 120 Tests grün.
- AK1 per Unit-Test abgesichert: `PrinterTest.wrongBlockInWorldIsNeverTouched` – Erde an einer Zielposition: mit additiveOnly `SKIP MISMATCH_ADDITIVE_ONLY`, ohne `BREAK`; in beiden Fällen kein Paket an diese Position, Block bleibt, Cluster wird fertig. In-game (`.sf status` „mismatched“) erst mit P2-07 → Backlog.
- AK2 per Unit-Test abgesichert: `AdditiveOnlyGuardTest` (5) – nach engage/release dieselben Objekte (`assertSame`), Nutzerwerte vor dem Start bleiben, doppeltes engage, ohne Baritone/ohne additiveOnly keine Zugriffe. `SchemaPrinterTest` prüft die überschriebenen Hooks. In-game (`#modified` nach Modul aus) erst, wenn P2-07 das Modul registriert → Backlog.

### P2-07 · Modul `SchemaPrinter` + Zustandsautomat + `.sf start/pause/resume/stop/status` `done`
- AK1 Manuell: kompletter Durchlauf Test-Schematic in Reichweite ohne Baritone → DONE, Verifier 0 Fehler
- AK2 `.sf status` zeigt State, Cluster i/n, Blöcke gesetzt, Blöcke/min, mismatched

Stand 2026-09-17: Zustandsautomat als testbares `core/BuildSession` (IDLE→PLANNING→BUILDING→VERIFYING→DONE, dazu PAUSED; TRAVELING/RESTOCKING existieren, werden erst in P3-02/P4-04 betreten). BUILDING läuft Cluster mit PLACE-Tasks der Reihe nach an (Cluster ohne PLACE werden übersprungen), VERIFYING plant neu und startet bis zu `MAX_ROUNDS` = 3 Runden, damit Blöcke nachgezogen werden, deren Träger erst in einem späteren Cluster entstand; Ende, sobald nichts mehr offen ist, eine Runde nichts gesendet hat oder Runde 3 vorbei ist. `SchemaPrinter` hält die Settings aus §7 (ohne `profile` → P5-05, `Safety` → P3-03, `buildOnlySelection`/`renderClusters` ohne Ticket → Backlog), baut je Tick `McWorldView`/`McPlayerView`/`McInventoryView`/`McPrintActions` und tickt die Session; Budget = `blocksPerTick` in Ticks mit `tick % tickInterval == 0`. Neu `core/Substitutes` (Setting-Zeilen „a->b,c“, fehlerhafte Zeilen werden gemeldet und übersprungen) und `core/StatusReport` (Text von `.sf status`). Commands: `StartCommand`, `ControlCommands` (pause/resume/stop), `StatusCommand`, dazu `commands/PlacementChoice` gemeinsam mit `.sf preview`. Modul in `SchemaForgeAddon` registriert. Gefunden und abgefangen: Meteor ruft `onActivate` für noch aktive Module beim Welt-Beitritt erneut auf (auch nach Neustart aus der Config) – ein Lauf startet deshalb nur aus `toggle()`, sonst schaltet sich das Modul im nächsten Tick ab. ARCHITECTURE.md §1/§5/§7 vorher angepasst. Build + 134 Tests grün.
- AK1 **offen**: kompletter Durchlauf ist ein In-game-Test (Dev-Client, Litematica-Verifier) und steht noch aus → Backlog, zusammen mit P2-03 AK1/AK2, P2-04 AK2 und P2-06 AK1/AK2.
- AK2 erfüllt: `StatusReportTest` (3) – State, Placement, Runde, Cluster i/n, gesetzte Blöcke, Blöcke/min, mismatched, offene Blöcke; ohne Lauf Hinweis auf `.sf start`, nach Stop/DONE nicht mehr „BUILDING“.
- Dazu `BuildSessionTest` (7): Zustandsfolge bis DONE, alle Cluster der Reihe nach, zweite Runde holt nach, Abbruch nach 3 Runden, mismatched bleibt liegen, pause/resume sendet nichts bzw. macht weiter, Blöcke/min zählt nur aktive Zeit. `SubstitutesTest` (3), `SchemaPrinterTest` +1 (toggle-Absicherung).

---

## Phase 3 – Navigator + Robustheit

### P3-01 · `BaritoneBridge` `done`
Nur `baritone.api`. `gotoNear`, `gotoBlock`, `isPathing`, `stop`, Settings-Backup/Restore.
- AK1 Manuell: `.sf` interner Testbefehl `.sf debug goto x y z` läuft zum Ziel
- AK2 Ohne Baritone: Methoden werfen nicht, `isPresent()==false`

Stand 2026-09-19: `gotoNear`/`gotoBlock` über `getCustomGoalProcess().setGoalAndPath(new GoalNear(pos, radius))` bzw.
`new GoalGetToBlock(pos)`, `isPathing()` über `getPathingBehavior().isPathing()`, `stop()` über
`getCustomGoalProcess().onLostControl()` + `getPathingBehavior().cancelEverything()` – Einstiegspunkt und Schreibweise wie in
Meteors `BaritonePathManager` (refs), Signaturen per `javap` gegen `baritone-26.2-SNAPSHOT.jar` geprüft
(`IBaritone`, `IPathingBehavior`, `ICustomGoalProcess`, `GoalNear(BlockPos,int)`, `GoalGetToBlock(BlockPos)`).
Alle Baritone-Referenzen liegen in der privaten `BaritoneBridge.Api`, die erst geladen wird, wenn ein Aufruf an
`isPresent()` vorbeikam; `isPresent()` cached das Ergebnis. `getPrimaryBaritone()` kann vor dem Welt-Beitritt `null` sein →
Log-Warnung statt NPE. Settings-Backup/Restore liegt seit P2-06 im `AdditiveOnlyGuard` (`BREAK_SETTINGS`), nicht hier.
Neu `commands/DebugCommands`: `.sf debug goto <x> <y> <z>` und `.sf debug stopgoto`. Build + 138 Tests grün.
- AK2 erfüllt: `BaritoneBridgeTest` (3) – Baritone fehlt im Test-Runtime (`compileOnly`), `isPresent()` false, alle vier
  Methoden werfen nicht, `isPathing()` false.
- AK1 in-game **bestanden** 2026-09-19 (Dev-Client, Nutzer): `.sf debug goto x y z` läuft zum Ziel.

### P3-02 · `Navigator` + TRAVELING-State `done`
Cluster ohne erreichbare Tasks → `gotoNear(center, 3)`; Timeout 20 s → Cluster ans Ende der Liste, nach 3 Fehlversuchen blacklisten und melden.
- AK1 Manuell: 20×20×3-Plattform → Printer baut, läuft, baut; kein Cluster wird > 3× angelaufen
- AK2 Manuell: unerreichbarer Cluster (eingemauert) → Blacklist-Meldung, Bau geht weiter

Stand 2026-09-19: `core/Navigator` hält ein Ziel, kennt Baritone nicht (Interface `Navigator.Pathing`, produktiv
`BaritoneBridge.PATHING`), so bleibt der Zustandsautomat ohne Minecraft-Runtime testbar. `start(center)` →
`gotoNear(center, GOAL_RADIUS=3)`; `tick(player)` liefert ARRIVED (Augen ≤ `ARRIVE_DISTANCE` = 5 vom Zielmittelpunkt,
Pfadfinder wird freigegeben), FAILED (Timeout 20 s **oder** nach 1,5 s Anlaufzeit meldet der Pfadfinder „läuft nicht“ –
sonst wäre jeder Cluster erst nach 20 s als unerreichbar erkannt) oder TRAVELING. FAILED zählt einen Versuch, ab
`MAX_ATTEMPTS` = 3 Blacklist (gilt für den Lauf, Schlüssel ist die Cluster-Mitte, übersteht also das Neuplanen).
`BuildSession`: neuer Zweig TRAVELING; beim Clusterwechsel wird geprüft, ob ein PLACE-Task des Clusters innerhalb
`player.reach()` liegt – wenn nicht, wird er angelaufen. FAILED → Meldung + Cluster ans Ende der Liste; beim dritten Mal
Blacklist-Meldung und der Cluster wird nicht mehr angelaufen. Ohne Baritone wird TRAVELING nie betreten (Verhalten wie
vor P3-02) und der Lauf meldet das einmal im Chat. Signatur geändert (ARCHITECTURE.md §4/§5 vorher angepasst):
`BuildSession(..., Navigator navigator, ..., Consumer<String> notes)`; `pause()` gilt jetzt für jeden laufenden Zustand
und gibt in TRAVELING den Pfadfinder frei, `resume()` läuft denselben Cluster neu an. `SchemaPrinter` baut je Lauf einen
neuen Navigator und ruft `cancel()` in `onDeactivate`. Build + 147 Tests grün.
- `NavigatorTest` (5): Ankunft gibt den Pfad frei und zählt keinen Versuch, „nicht am Laufen“ nach der Anlaufzeit,
  Timeout-Grenze exakt, dritter Fehlversuch blacklistet (danach `start` false, genau 3 Ziele beim Pfadfinder),
  ohne Pfadfinder passiert nichts.
- `BuildSessionTest` +4: Cluster außer Reichweite wird angelaufen (Ziel = Cluster-Mitte, Radius 3) und nach der Ankunft
  gebaut; eingemauerter Cluster → 3 Anläufe, Blacklist-Meldung, erreichbarer Teil bleibt gebaut, Lauf endet in DONE;
  ohne Pfadfinder nie TRAVELING; Pause während der Fahrt gibt den Pfad frei und zählt keinen Fehlversuch.
- AK1/AK2 in-game **bestanden** 2026-09-19 (Dev-Client, Nutzer): Printer baut, läuft, baut; unerreichbarer Cluster wird
  gemeldet und der Bau geht weiter. Ohne Messwerte zurückgemeldet – ob `ARRIVE_DISTANCE` = 5 und die 1,5 s Anlaufzeit
  auch in schwierigerem Gelände passen, zeigt erst der Dauerlauf (P3-06).

### P3-03 · Sicherheitsstopps → PAUSED `done`
Schaden, Hunger < `minFood`, Spieler im Radius, Chunk nicht geladen, Inventar leer für alle offenen Tasks.
- AK1 Manuell je Bedingung: Pause + Chat-Grund; Fortsetzen automatisch, wenn Bedingung weg (Spieler) bzw. `.sf resume` (Schaden)

Stand 2026-09-19: `core/SafetyMonitor` prüft in fester Reihenfolge DAMAGE → FOOD → PLAYER_NEARBY → CHUNK_UNLOADED →
NO_MATERIALS und liefert einen `Trigger(reason, message, autoResume)`; er entscheidet nur, pausiert nichts selbst.
Schaden = Leben unter dem Wert des letzten Ticks (nur bei `pauseOnDamage`); weil während der Pause nicht geprüft wird,
bleibt das Leben des Pausen-Ticks der Vergleichswert und ein `.sf resume` pausiert nicht sofort wieder. `autoResume` ist
nur bei DAMAGE false. Neu `core/view/SafetyView` (health/food/nächster anderer Spieler) mit `compat/McSafetyView`
(`getFoodData().getFoodLevel()`, `ClientLevel.players()` ohne Zuschauer – Signaturen per `javap` gegen das 26.2-Jar
geprüft) und `Printer.outOfMaterials(inv)` (Cluster braucht Items, keines davon im Inventar). `BuildSession` prüft vor
jedem Schritt eines laufenden Laufs und setzt in PAUSED nur fort, wenn der Grund weg ist; Signatur geändert
(ARCHITECTURE.md §3/§4/§5/§7 vorher angepasst): `BuildSession(..., SafetyMonitor safety, ...)`,
`tick(world, player, inv, safetyView, actions)`, neu `safetyPause()`. Settings-Gruppe `Safety` im Modul angelegt
(`pause-on-damage` true, `min-food` 6, `pause-player-radius` 16, 0 schaltet die Spielerprüfung ab); die Werte werden bei
jeder Prüfung gelesen. Build + 157 Tests grün.
- `SafetyMonitorTest` (7): erster Check merkt nur das Leben, Schaden → DAMAGE ohne autoResume, dauerhaft niedriges Leben
  pausiert nicht erneut, `pauseOnDamage` aus, Hunger (Grenze `minFood` selbst reicht zum Fortsetzen), Spieler genau auf
  dem Radius pausiert / knapp darüber nicht / niemand da, Chunk und leeres Inventar, Vorrang von DAMAGE.
- `BuildSessionTest` +3: Schaden pausiert, bleibt 10 Ticks pausiert, setzt nichts und läuft erst nach `.sf resume`
  weiter; Spieler in der Nähe pausiert und der Lauf geht von allein weiter, wenn er sich entfernt; leeres Inventar
  pausiert (sobald der Bedarf des ersten Clusters feststeht) und läuft weiter, wenn die Items wieder da sind.
- AK1 in-game **bestanden** 2026-09-19 (Dev-Client, Nutzer): Pause mit Chat-Grund je Bedingung, automatisches
  Fortsetzen bzw. `.sf resume` nach Schaden.

### P3-04 · `BuildResume` `done`
Checkpoint alle 30 s + bei Stop. `.sf start` mit vorhandenem Checkpoint fragt „resume? .sf resume“.
- AK1 Manuell: Disconnect mitten im Bau → Reconnect → `.sf resume` macht bei Cluster i weiter
- AK2 `planConfigHash` anders → Warnung, Checkpoint verworfen

Stand 2026-09-19: `core/BuildCheckpoint` (Record + Gson-Datei nach §6, `"v":1` als erstes Feld, `save`/`load`/`delete`,
Lese- und Schreibfehler werden geloggt statt geworfen) und `PlanConfig.fingerprint()`. Wichtig: der Fingerprint wird aus
**sortierten Registry-Namen** gebaut, nicht aus `hashCode` – `Block.hashCode` ist identitätsbasiert, jeder Spielstart
hätte sonst „Settings geändert“ gemeldet. `BuildSession.start(int fromCluster)` überspringt beim Planen die schon
erledigten Cluster. Modul `BuildResume` (Setting `interval`, 5–300 s, Default 30) hält Datei und Intervall; der
`SchemaPrinter` schreibt den Checkpoint im Tick, beim `onDeactivate` und löscht ihn bei DONE. `.sf start` mit passendem
Checkpoint **startet nicht**, sondern bietet ihn an (`.sf resume` = weiter bei Cluster i, nochmal `.sf start` = von vorn,
Checkpoint weg); ein Checkpoint mit anderem `planConfigHash` wird mit Warnung verworfen und der Lauf startet normal.
Modul in `SchemaForgeAddon` registriert. ARCHITECTURE.md §4/§5/§6/§8 vorher ergänzt. Build + 166 Tests grün.
- **Beim Testen gefunden und behoben:** zeigte der Checkpoint hinter den letzten Cluster (Schematic geschrumpft, Cluster
  neu geschnitten), endete der Lauf sofort in DONE – die Regel „Runde ohne Platzierung beendet den Bau“ griff auf eine
  Runde, die nur wegen des Resume nichts getan hatte. Eine solche Runde beendet den Lauf jetzt nicht mehr.
- `BuildCheckpointTest` (7): Roundtrip, `"v":1` zuerst + alle vier Felder aus §6, fehlende/kaputte/fremde Version →
  leer, Löschen ohne Datei, Fingerprint ignoriert Reihenfolge und Collection-Typ aber nicht den Inhalt, AK2
  (Cluster-Größe bzw. neue Substitute → passt nicht mehr), anderes Placement → anderer Fingerprint.
- `BuildSessionTest` +2: Resume startet beim angegebenen Cluster (übersprungene Reihe holt die Verify-Runde nach),
  Resume hinter dem letzten Cluster baut in der zweiten Runde trotzdem alles.
- AK1/AK2 **offen**: beides sind In-game-AKs (Disconnect/Reconnect) → TESTLOG.

### P3-05 · `BuildProgressHud` `done`
- AK1 HUD-Element in Meteor-HUD-Editor platzierbar, zeigt %, Blöcke/min, ETA, State, Fehlbestand (Top 3)

Stand 2026-09-19: Text und Rendering getrennt – `core/ProgressReport` liefert die Zeilen (Prozent = placed /
(placed + remaining), abgerundet, damit 99 % nie wie fertig aussieht; ETA "-" ohne Rest, "?" ohne Rate, sonst `5m` /
`2h30m`; Fehlbestand die größten drei plus „+n more"), `hud/BuildProgressHud` zeichnet sie. Meteor-HUD-API in `refs/`
nachgeschlagen (`HudElement`, `HudElementInfo`, `HudRenderer.text/textHeight/quad`, `isInEditor()`,
`Hud.get().getTextScale()`, Vorbild `LagNotifierHud`): Settings für `hide-when-idle`, Schatten, Textfarbe, eigene Farbe
für die Fehlbestandszeile, Scale und Hintergrund; im HUD-Editor zeigt das Element Beispieldaten, damit es ohne
laufenden Bau platziert werden kann. `SchemaPrinter.shortages()` liefert den Fehlbestand des **aktuellen** Clusters –
die Liste wird beim Clusterwechsel geleert, sonst stünden längst nachgefüllte Items noch im HUD. Registriert über
`Hud.get().register(BuildProgressHud.INFO)` in `SchemaForgeAddon`. ARCHITECTURE.md §1/§4 vorher ergänzt.
Build + 174 Tests grün.
- `ProgressReportTest` (8): Leerlauf, laufender Bau (2 Zeilen), Prozent inkl. Ab- und Randfällen, ETA-Fälle,
  mismatched-Zeile nur wenn > 0, Top-3-Fehlbestand mit „+1 more", einzelner Fehlbestand ohne Suffix,
  gestoppter Lauf zeigt STOPPED statt BUILDING.
- AK1 **offen**: Platzieren im HUD-Editor ist ein In-game-AK → TESTLOG.

### P3-06 · Dauerlauf-Test `blocked`
- AK1 Manuell: 60×60×30-Schematic, 30 min unbeaufsichtigt auf Testserver → kein Stillstand > 60 s ohne PAUSED-Grund; Ergebnis in TESTLOG

Stand 2026-09-19: **vorbereitet, nicht ausgeführt** – das Ticket besteht nur aus einem 30-Minuten-Lauf im Spiel, den
Claude nicht selbst fahren kann. Blockiert auf den Nutzer.
- `tools/gen_endurance_schematic.py` erzeugt `test/endurance.litematic` (eingecheckt, 833 B): 60×60×30, volle
  Steinfläche auf y=0, darüber hohle Wände auf einem 15er-Raster mit Brett-Bändern auf y=4/14/24. Hohl, damit der
  Printer laufen muss statt an einer Stelle zu stehen; nur Stein und Eichenbretter, damit ein Abbruch am Material
  liegt und nicht an einer fehlenden Platzierungsregel. **17 056 Blöcke** (15 664 Stein, 1 392 Bretter) ≈ 9,9
  Shulker – bei 1 Block/Tick sind das über 14 min reine Platzierungen, mit Laufwegen deutlich mehr als 30 min.
- Ablauf für den Lauf: Schematic in Litematica laden und platzieren · `BuildResume` an (P3-04) · Material in Kisten
  oder Kreativ · `.sf start endurance` · 30 min laufen lassen · danach `.sf status`, TESTLOG-Zeile, und im Log auf
  Stillstände ohne PAUSED-Grund achten.
- Die Datei wurde mit demselben litemapy-Setup wie `blockclasses.litematic` erzeugt, aber **nicht** gegen Litematicas
  Parser gegengeprüft (das war bei P1-04 ein eigener Schritt). Lädt sie nicht, ist das der erste Befund des Tickets.

---

## Phase 4 – Container-Restock

### P4-01 · `ContainerIndex` + Persistenz `done`
- AK1 Unit-Tests: learn/sourcesFor/markStale/save/load Roundtrip
- AK2 Gson-Datei entspricht ARCHITECTURE.md §6, `"v":1`

Stand 2026-09-19: `ContainerIndex` als Map Position → `Entry` (Einfügereihenfolge, damit die Datei zwischen Läufen
stabil bleibt). `learn` ersetzt den Eintrag und macht ihn wieder frisch, `sourcesFor` filtert auf Container, die das
Item wirklich führen, und sortiert erst nach `stale`, dann nach Abstand; `markStale` altert nur Einträge, die älter
sind als die Grenze. Uhr als `LongSupplier` im Konstruktor, damit Tests altern können ohne zu warten. Dazu
`entries()`, `at()`, `size()` und `forget()` (für P4-04, wenn eine Kiste leer oder verschwunden ist). Persistenz per
Gson über kleine DTO-Klassen statt direkter Serialisierung von `BlockPos`/`Item`: Positionen als `[x,y,z]`, Items als
Registry-Id. Unbekannte Item-Ids und Container-Typen werden übersprungen und geloggt, statt die ganze Datei zu
verwerfen – ein Mod weniger soll nicht den kompletten Kisten-Index kosten. `Identifier.tryParse` +
`BuiltInRegistries.ITEM.getValue` wie in `Substitutes` (Luft = unbekannt). ARCHITECTURE.md §4 vorher ergänzt.
Build + 183 Tests grün.
- AK1 erfüllt: `ContainerIndexTest` (9) – learn merkt Inhalt und Zeitpunkt, erneutes learn ersetzt statt zu mischen und
  löscht `stale`, `sourcesFor` nach Abstand mit stale zuletzt, `markStale` trifft nur alte Einträge, `forget`,
  save/load-Roundtrip mit `assertEquals` über alle Einträge.
- AK2 erfüllt: `theFileStartsWithTheSchemaVersionAndUsesItemIds` – `{"v":1,` zuerst, `"minecraft:stone": 64`,
  `"type": "CHEST"`, `"pos":[5,64,0]`. Dazu: fehlende/kaputte/fremde Version → leerer Index, unbekannte Ids werden
  übersprungen ohne den Rest zu verlieren.
- Der Dateiname `containers-<serverHash>-<dimension>.json` wird erst mit P4-02 gebildet; der Index selbst kennt nur
  den Pfad, den er bekommt.

### P4-02 · Passives Lernen `done`
Listener auf Container-Screen open/close (Meteor-Event oder eigener Hook – Vorbild: Auto-Steal in `InventoryTweaks` erkennt den Container über `InventoryEvent` + `containerMenu.getType()`, siehe `docs/NOTES-meteor-api.md` Punkt 5); Container-Position aus letztem Rechtsklick-Ziel; Inhalt beim Schließen in Index.
- AK1 Manuell: 3 Kisten manuell öffnen → `.sf containers` listet alle 3 mit korrekten Mengen
- AK2 Doppelkiste → eine Position, alle 54 Slots gezählt
- AK3 Ender-Chest → Typ `ender_chest`, positionsunabhängig

Stand 2026-09-19: Modul `ContainerRestock` (Settings `learn-passively`, `stale-after-hours` Default 24) hängt an
`InventoryEvent` (wie Auto-Steal: `packet.containerId() == containerMenu.containerId`, eigenes Inventar-Menü
ausgenommen) und an `InteractBlockEvent` für die Position – der Screen selbst sagt nicht, wo der Container steht,
deshalb gilt der letzte Rechtsklick, solange er höchstens 40 Ticks her ist. Gelernt wird **beim Eintreffen des
Inhalts**, nicht beim Schließen: das Paket ist der einzige Zeitpunkt, an dem der Inhalt sicher vollständig ist.
Gezählt werden `menu.slots` ohne die letzten 36 (Spielerinventar) – damit stimmt die Zahl für jede Containergröße,
auch für die 54 Slots einer Doppelkiste (AK2). Doppelkisten werden über `ChestBlock.getConnectedBlockPos` auf **eine**
Hälfte normiert (`ContainerKey.canonical`, kleinstes x/y/z), Enderkisten auf einen Sentinel mit
y = `Integer.MIN_VALUE`, den kein echter Block haben kann (AK3, positionsunabhängig). Persistenz je Welt:
`containers-<serverHash>-<dimension>.json`, der Hash sind 4 Byte SHA-256 der Serveradresse – die rohe Adresse landet
nie im Dateinamen. Geladen beim Aktivieren bzw. beim ersten Tick mit Welt, gespeichert beim Deaktivieren.
Neu `.sf containers` (`commands/ContainersCommand` + `core/ContainerReport`). Modul in `SchemaForgeAddon` registriert –
früher als in der Addon-Notiz geplant (dort stand P4-04), weil passives Lernen sonst nie liefe.
ARCHITECTURE.md §1/§4/§8 vorher ergänzt. Build + 197 Tests grün.
- **Beim Testen gefunden und behoben:** `fileName` ersetzte Sonderzeichen vor der Leer-Prüfung, dadurch wurde aus einem
  leeren Dimensionsnamen `_` statt `unknown` – der Fallback war toter Code.
- `ContainerKeyTest` (7): beide Hälften einer Doppelkiste ergeben denselben Eintrag, Ordnung über alle drei Achsen,
  Einzelkiste, Sentinel liegt unter jeder Welt, Dateiname enthält nur den Hash, gleiche Adresse → gleicher Hash,
  ohne Server `local`, kaputte Dimensionsnamen können nicht aus dem Dateinamen ausbrechen.
- `ContainerReportTest` (6): leerer Index (mit Hinweis, wenn das Modul aus ist), Sortierung nächste zuerst mit stale
  zuletzt, Enderkiste ohne Sentinel-Position, vier Item-Sorten plus „+n more", leerer Container, lange Liste gekürzt.
- AK1/AK2/AK3 **offen**: alle drei sind In-game-AKs → TESTLOG.

### P4-03 · `.sf scan [radius]` `done`
Container-BlockEntities im Radius aus geladenen Chunks; nacheinander anlaufen (Navigator), öffnen, lernen, schließen; Budget für Öffnungen (1 pro 10 Ticks).
- AK1 Manuell: 6 Kisten in 20 Blöcken → alle im Index, Dauer < 2 min

Stand 2026-09-19: `core/ScanSession` ist die Zustandsmaschine (TRAVELING → OPENING → WAITING → nächster Container),
programmiert gegen `ScanSession.Actions` statt gegen Minecraft-Screens, damit sie testbar bleibt. `route()` ordnet die
Container per Nearest-Neighbor ab Spielerposition und wirft Doppelte weg. Öffnen ist auf **eine Aktion pro 10 Ticks**
begrenzt (Regel 7); wird der Inhalt nicht innerhalb von 60 Ticks gelernt, gilt der Container als übersprungen und es
gibt eine Chat-Meldung. Ohne Baritone wird jeder Container von der Stelle aus versucht, statt den Scan abzubrechen.
Im Modul: `containersWithin(radius)` sammelt Block-Entities aus geladenen Chunks
(`ClientLevel.getChunkSource().getChunk(x, z, false)` + `LevelChunk.getBlockEntities()`, per `javap` geprüft), filtert
über `McWorldView.containerAt` und normiert Doppelkisten wie beim passiven Lernen. Geöffnet wird mit einem
`useItemOn` auf die Blockmitte; die Position wird dabei direkt gesetzt, statt sich darauf zu verlassen, dass das
Interact-Event für den eigenen Aufruf feuert. Neu `.sf scan [radius]` und `.sf scan stop` (`commands/ScanCommand`,
Default-Radius 32, 1–128). ARCHITECTURE.md §1/§4/§8 vorher ergänzt. Build + 205 Tests grün.
- **Beim Testen gefunden und behoben:** `tick - lastOpenAt` lief mit dem Startwert `Integer.MIN_VALUE` in einen
  Integer-Überlauf, dadurch war der Abstand immer negativ und es wurde **nie** ein Container geöffnet. Startwert ist
  jetzt ein Intervall in der Vergangenheit.
- `ScanSessionTest` (8): Route nimmt immer den nächsten Container (auch umgekehrt vom anderen Ende), Doppelte fallen
  weg, jeder Container wird genau einmal geöffnet und der Screen wieder geschlossen, Öffnen ist auf ein Paket je
  Intervall begrenzt, Container der nicht aufgeht wird nach dem Timeout übersprungen, unerreichbarer Container wird
  übersprungen und der Scan läuft weiter, Abbruch gibt Pfad und Screen zurück, leere Liste ist sofort fertig.
- AK1 **offen**: In-game-AK (6 Kisten, Dauer < 2 min) → TESTLOG.

### P4-04 · `RestockProcess` `done`
State-Machine laut ARCHITECTURE.md; Entnahme nur Bedarf der nächsten N Cluster (Setting `lookaheadClusters`, Default 3); Klicks über `ActionBudget` (Default 2/Tick); Screen-Timeout 60 Ticks; Inventar voll → Müll-Liste ablegen, sonst FAILED mit Meldung; Index nach Entnahme korrigieren.
- AK1 Manuell: Inventar leer, Lager mit 6 Kisten → Bau startet, holt, baut weiter ohne Eingriff (Meilenstein M4)
- AK2 Manuell: Kiste im Index inzwischen leer → nächste Quelle, Index korrigiert, kein Endlos-Öffnen
- AK3 Manuell: Inventar voll, Müll-Liste leer → FAILED + Meldung, kein Item-Verlust

Stand 2026-09-19: `core/RestockProcess` als Zustandsmaschine mit den Namen aus §4
(PICK_SOURCE → TRAVEL → OPEN → WAIT_SCREEN → TAKE → CLOSE → RETURN), gegen ein `Actions`-Interface programmiert und
damit ohne Minecraft testbar. Quellenwahl über `ContainerIndex.sourcesFor` (nächste zuerst, stale zuletzt), jeder
Container wird pro Lauf höchstens **einmal** geöffnet. Beim Öffnen wird der **echte** Inhalt in den Index geschrieben –
damit korrigiert sich eine inzwischen leere Kiste selbst und der Lauf geht zur nächsten Quelle (AK2). Alle Klicks
(Öffnen, Entnehmen, Zurücklegen, Schließen) laufen über das `ActionBudget` mit `clicks-per-tick` (Default 2).
Inventar voll → ein Item der Müll-Liste **zurück in den Container**, nie auf den Boden; ist die Liste leer, endet der
Lauf mit FAILED und einer Meldung, ohne dass etwas verloren geht (AK3).
- **Bewusst anders als geplant:** wie viel angekommen ist, wird aus dem Inventar zurückgelesen statt aus
  `getDefaultMaxStackSize()` geraten. Der Client aktualisiert sein Inventar beim Klick sofort, und eine geratene
  Stapelgröße wäre für jedes Item mit 16er-Stapeln falsch. (Die Stapelgröße ist im Unit-Test ohnehin nicht abrufbar –
  `Components not bound yet`, dieselbe Grenze wie bei `ItemStack` in P2-05.)
- Anbindung an den Bau (AK1/M4): der Sicherheitsstopp pausiert den Lauf ohnehin mit NO_MATERIALS und setzt von allein
  fort, sobald die Items da sind. Der `SchemaPrinter` startet deshalb in genau dieser Pause einen Restock mit dem
  Bedarf der nächsten `lookahead-clusters` Cluster (`BuildSession.upcomingDemand`) und dem aktuellen Cluster als
  Rückweg. **Der Zustand RESTOCKING in `BuildSession` bleibt damit unbenutzt** – der Ablauf ist derselbe wie in §5
  beschrieben, liegt aber im PAUSED-Zweig; so muss der Zustandsautomat den Restock nicht kennen.
- Im Modul: `RestockActions` öffnet per `useItemOn` und verschiebt Stapel per `InvUtils.shiftClick().slotId(i)`,
  getrennt nach Container- und Spielerhälfte des Menüs. Neue Settings `lookahead-clusters` (3), `clicks-per-tick` (2)
  und `trash` (ItemList, leer). ARCHITECTURE.md §4/§5/§7/§8 vorher ergänzt. Build + 214 Tests grün.
- `RestockProcessTest` (9): nächste Quelle wird angelaufen und geleert, leere Kiste korrigiert den Index und die
  nächste Quelle wird genommen (AK2), dieselbe Kiste wird nie zweimal geöffnet, ohne Quelle FAILED mit Meldung,
  volles Inventar legt Müll zurück, volles Inventar ohne Müll-Liste → FAILED ohne etwas wegzuwerfen (AK3), was das
  Inventar schon hat wird nicht geholt, Klicks laufen durchs Budget, Abbruch gibt Pfad und Screen zurück.
- AK1/AK2/AK3 **offen** als In-game-Tests → TESTLOG; die Unit-Tests decken AK2 und AK3 logisch ab.

### P4-05 · Shulker im Inventar (Option) `done`
Nur wenn `useInventoryShulkers`. Platzieren → öffnen → entnehmen → abbauen → aufnehmen.
- AK1 Manuell: Ablauf funktioniert; Shulker landet wieder im Inventar; bei `additiveOnly` erlaubt (eigener temp-Block)

Stand 2026-09-19: `core/ShulkerProcess` als Zustandsmaschine (PLACING → OPENING → WAIT_SCREEN → TAKE → CLOSE →
BREAKING), gegen ein `Actions`-Interface programmiert und damit ohne Minecraft testbar. Standardmäßig **aus**
(`use-inventory-shulkers`), weil Platzieren und Abbauen auf Servern auffällt. Jeder Schritt kostet eine Einheit des
Klick-Budgets. Das ist die einzige Stelle, an der SchemaForge einen Block bricht – und immer nur den, den es selbst
gerade gesetzt hat; damit bleibt Regel 6 (additive-only) gewahrt. Beim Abbrechen bleibt eine schon stehende Kiste
bewusst stehen und es gibt eine Meldung, statt sie stillschweigend liegen zu lassen. Im Modul sucht `freeSpot()` einen
ersetzbaren Nachbarblock mit festem Boden und Luft darüber, platziert wird über Meteors `BlockUtils.place`, abgebaut
über `BlockUtils.breakBlock`. Ein passender Shulker im Inventar wird **vor** jedem Laufweg zu einer Kiste probiert
(`InventoryView.shulkersContaining`). ARCHITECTURE.md §1/§4 vorher ergänzt. Build + 221 Tests grün.
- `ShulkerProcessTest` (7): kompletter Ablauf in der richtigen Reihenfolge, kein freier Platz → FAILED bevor etwas
  passiert, Kiste erscheint nicht → Timeout, Kiste lässt sich nicht abbauen → Meldung, Shulker ohne das gesuchte Item
  wird trotzdem wieder aufgenommen, Abbrechen warnt solange sie steht, leeres Budget sendet gar nichts.
- AK1 **offen**: In-game-AK → TESTLOG.

### P4-06 · `.sf materials` Vier-Spalten `done`
Item | Bedarf gesamt | nächste N Cluster | Inventar | in bekannten Kisten.
- AK1 Manuell: Zahlen stimmen mit Preview + `.sf containers` überein

Stand 2026-09-19: `core/MaterialsReport` baut die Zeilen (Kopfzeile, je Item die fünf Spalten, Summenzeile), das
Kommando `commands/MaterialsCommand` holt die Zahlen aus denselben Quellen wie die anderen Befehle – `materialTotals`
des Snapshots (wie `.sf preview`), der Bedarf der nächsten `lookahead-clusters` Cluster (wie der Restock),
`InvUtils.find(item).count()` (wie `.sf preview`) und die Summe über den `ContainerIndex` (wie `.sf containers`).
`missing` = nächste Cluster − Inventar − Kisten, nie negativ; sortiert wird nach fehlender Menge, damit oben steht,
was eine Entscheidung braucht. Über 30 Item-Sorten werden gekürzt. ARCHITECTURE.md §1/§4/§8 vorher ergänzt.
Build + 229 Tests grün.
- `MaterialsReportTest` (8): jede Spalte kommt aus ihrer eigenen Quelle, Gedecktes ist nicht „missing" und wird nie
  negativ, Items die nur die nächsten Cluster brauchen bekommen trotzdem eine Zeile, fehlende Zeilen zuerst,
  Kopf- und Summenzeile, gedeckter Bau meldet das, lange Listen werden gekürzt, leerer Bau.
- AK1 **offen**: der Abgleich mit `.sf preview` und `.sf containers` ist ein In-game-AK → TESTLOG.

---

## Phase 5 – Feinschliff

### P5-01 · `.sf undo <n>` `done` — aus `PlacementLog`, nur eigene Blöcke, Budget beachten

Stand 2026-09-19: `core/UndoSession` arbeitet die Log-Einträge **neueste zuerst** ab und bricht nur ab, wo die Welt
noch **genau** den geloggten BlockState hat – hat jemand anderes dort etwas geändert, bleibt es stehen. Nicht geladene
Chunks, Positionen außer Reichweite und Blöcke, die sich nach 200 Ticks nicht abbauen lassen, werden gemeldet und
übersprungen. Jeder Abbauschritt kostet eine Budget-Einheit, das Ganze tickt im `SchemaPrinter`, damit es dasselbe
Paketbudget wie der Drucker nutzt. Neu `PlacementLog.readFrom(file, blocks)` – damit funktioniert Undo auch nach einem
Neustart, nicht nur für die laufende Sitzung; kaputte Zeilen und Blöcke, die dieses Spiel nicht kennt, werden
übersprungen statt das ganze Log zu verlieren. `.sf undo <n>` (1–4096) und `.sf undo stop`; während eines laufenden
Baus wird abgelehnt, weil sich Drucker und Undo sonst gegenseitig bekämpfen. ARCHITECTURE.md §1/§4/§8 vorher ergänzt.
Build + 235 Tests grün.
- `UndoSessionTest` (6): neueste zuerst und nur so viele wie verlangt, fremd gewordener Block bleibt unangetastet,
  nicht geladen/außer Reichweite wird gemeldet und übersprungen, Abbau läuft über mehrere Ticks mit einem Schritt je
  Budget-Einheit, nicht abbaubarer Block wird nach dem Timeout aufgegeben, mehr verlangt als geloggt.
- AK: das Ticket nennt keine eigenen AKs; der In-game-Test (Blöcke setzen, `.sf undo 10`) steht aus → TESTLOG.
### P5-02 · Temporäre Stützblöcke `todo` — Whitelist, Log-Flag `temp`, Entfernen am Cluster-Ende, nur `additiveOnly=false`
### P5-03 · Fluids `todo` — Bucket vom Nachbarblock, Quellblock-Check, Setting `handleFluids`
### P5-04 · Rails `todo` — Reihenfolge gerade→Kurve, Verifier-Gegencheck
### P5-05 · Profil FAST `todo` — `blocksPerTick` > 1, `rotationSpoof`, Warnung im Chat beim Aktivieren
### P5-06 · EasyPlace-Protokoll nutzen `todo` — wenn V2/V3 erkannt: Property-Encoding im `hitVec` laut Carpet-Protokoll (Spezifikation aus `refs/meteor-client`? nein – aus Litematica-Quelle / PaperAccurateBlockPlacement-README ableiten, in ARCHITECTURE.md dokumentieren)
### P5-07 · Release `todo` — README, Modrinth-Metadaten, CI `dev_build.yml` grün, GPL-Header

---

## Backlog (nur sammeln)
- ~~Dev-Client-Crash mit Litematica 0.28.8/MaLiLib 0.29.6~~ **geklärt 2026-09-15:** MaLiLibs `test.MixinSharedConstants` schaltet im Dev-Umfeld Vanilla-GPU-Validierung ein, Minecraft 26.2 scheitert daran beim ersten Textur-Tick. Nicht Treiber, nicht Litematica, nicht SchemaForge; normale Instanzen nicht betroffen. Workaround `python tools/patch_malilib_dev.py`, Details `docs/NOTES-devclient-crash.md`
- P0-05 AK1 in-game **bestanden** (2026-09-15, siehe TESTLOG); AK2 (Litematica entfernt) und AK3 (Baritone entfernt) noch nachholen – Jar in `run/mods` umbenennen, `.sf doctor`
- Baritone (Meteor-Fork 26.2-SNAPSHOT) hält beim Beenden nicht-daemon Threads (`CachedWorld`) offen → Minecraft-Watchdog schreibt nach ~15 s einen „Client shutdown“-Crash-Report. Harmlos (alles gespeichert), aber Crash-Reports verwirren; nur beobachten, SchemaForge kann das nicht beheben (nur `baritone.api`)
- ~~P1-01 AK1 in-game~~ **bestanden** 2026-09-15 mit `.sf preview` (zwei Placements → beide gelistet; beide hießen gleich, siehe P1-05)
- ~~P1-04 AK1 im echten Client~~ **bestanden** 2026-09-15: `test/blockclasses.litematic` lädt und lässt sich platzieren
- P1-02 AK2–4 in-game nachholen: AK2 teilweise – `.sf preview` liefert exakt die Skript-Liste (15 / 337), der Abgleich mit Litematicas eigener Materialliste (GUI) steht noch aus; AK3 (gedreht+gespiegelt → Box in der Preview-Kopfzeile vs. Litematica) und AK4 (Sub-Region deaktiviert) offen. Hinweis: die Test-Schematic hat nur eine Sub-Region, für AK4 braucht es eine zweite
- `LitematicaAdapter.snapshot(name)` kann bei gleichnamigen Placements nur das erste ansprechen; ggf. Auswahl über Index oder Litematicas ausgewähltes Placement
- `snapshot()` bei großen Placements: eine Map-Zeile pro Position inkl. Luft, kein Größenlimit → Speicherbedarf messen, ggf. Limit oder Luft nur bei `ignoreAir=false` speichern
- `WorkPlanner`: Nearest-Neighbor ist O(n²) je Schicht – bei sehr großen Flächen (z. B. 1000×1000, 40 000 Cluster pro Schicht) spürbar langsam; ggf. Gitter-Ringsuche
- `WorkPlanner`: halbe Stufe in der Welt + Ziel Doppelstufe wird als falscher Block gewertet (SKIP/BREAK) statt als „zweite Hälfte platzieren“; `PlacementSolver` meldet Doppelstufen deshalb vorerst `Unsupported` (P2-02) – beides zusammen lösen
- `PlacementSolver`: volle Blöcke mit nachbarabhängigen Properties (`snowy` bei Gras/Myzel/Podsol, Laub `distance`/`persistent`, Glasscheiben/Eisengitter) sind `Unsupported`; wie Fence/Wall behandeln, sobald `ignoreProperties`/Verifier das abdecken
- `PlacementSolver`: `hitVec` für Airplace liegt auf der Fläche der Zielposition; wenn dort ein ersetzbarer Block mit Outline steht (Gras), trifft ein echter Strahl dessen Box – mit `lineOfSight`-Raycast im Printer (P2-03) prüfen
- `snapshot()` sieht nur Schematic-Chunks in Spielernähe; Chunks, die Litematicas Daemon schon geladen, aber noch nicht befüllt hat (`ChunkSchematicState`), liefern evtl. Luft – prüfen, ob das in-game vorkommt

- P2-03 AK1 (Vollblöcke/Slabs/Stairs/Logs → Verifier 0 Fehler, Singleplayer) und AK2 (VANILLA_LEGIT, 1 Block/Tick, keine Ghost-Blocks auf lokalem Paper) in-game nachholen, sobald P2-07 den Printer ans Modul hängt; dabei auch prüfen, dass Sneak-Klick an Kisten/Türen keine GUI öffnet und der Server danach nicht schleichend bleibt
- `Printer`: eine Platzierung = eine Budget-Einheit; mehrere Rotationen im selben Tick schicken bei Meteor je ein Zusatzpaket (NOTES-meteor-api Punkt 3), dazu 2 Input-Pakete für Sneak – bei `blocksPerTick` > 1 (P5-05) Paketzahl messen und ggf. mitzählen
- `Printer`: gesendete Platzierung wird geloggt, auch wenn der Server sie ablehnt; `PlacementLog` ggf. erst nach Bestätigung (nächster refresh) schreiben – für Undo (P5-01) klären

- P2-04 AK2 in-game nachholen (Zeilen Fackeln/Knöpfe/Hebel/Falltüren/Türen/Teppiche/Leitern/Schilder der Test-Schematic → Verifier 0 Fehler), zusammen mit P2-03 AK1/AK2 nach P2-07
- `PlacementSolver`: Schild platzieren öffnet beim Server den Schild-Editor (Client-Screen) – Printer muss den Screen schließen oder `.sf`-Setting „sign text“ bekommen; in-game mit P2-07 prüfen
- `PlacementSolver`: offene Türen/Falltüren und eingeschaltete Hebel sind `Unsupported`; nach dem Platzieren per Rechtsklick umschalten wäre möglich (Pakete über ActionBudget)
- `PlacementSolver`: Hängeschilder, Schienen (P5-04), Druckplatten, Pflanzen, Redstone-Staub/Repeater/Comparator, Banner, Ranken haben noch keine Regel

- P2-05: Hotbar-Swap in-game prüfen (mit P2-07): SWAP-Klick per `InvUtils.quickSwap()` aus Slot 9–35 und aus nicht erlaubtem Hotbar-Slot; auf Paper prüfen, dass kein Desync entsteht. Bei offenem Container-Screen wird nicht getauscht (Versuch verfällt)
- `MaterialManager`: Restock-Schwelle (PLAN 3.1 Punkt 5 „bei Unterschreiten einer Schwelle“) gehört zu P4-04; bisher nur Event bei komplettem Fehlen bzw. `checkShortages`

- P2-06 AK1/AK2 in-game nachholen (mit P2-07): fremder Block im Baubereich bleibt und steht in `.sf status` unter „mismatched“; nach Modul aus zeigt `#modified` kein `allowBreak`/`allowBreakAnyway`. Dabei P0-05 AK3 mitprüfen: Addon lädt ohne Baritone, `onActivate` wirft nicht
- `AdditiveOnlyGuard`: Brechen ist während des Laufs global verboten, nicht nur im Baubereich (baritone.api kann keine Positionen). Falls Baritone dadurch Cluster nicht erreicht: eigener `IBaritoneProcess` o. Ä. prüfen, nur `baritone.api`
- `AdditiveOnlyGuard`: speichert Baritone seine Settings (z. B. nach `#set` durch den Nutzer) während der Guard aktiv ist, landet `allowBreak false` in `settings.txt`; stürzt das Spiel vor `onDeactivate` ab, bleibt das bestehen. Ggf. beim Start einen Hinweis im Chat oder Wiederherstellen beim Beenden

- P2-07 AK1 in-game nachholen: kompletter Durchlauf der Test-Schematic in Reichweite ohne Baritone → DONE und Litematica-Verifier 0 Fehler. Dabei in einem Rutsch prüfen: P2-03 AK1/AK2, P2-04 AK2, P2-06 AK1/AK2, P2-05 Hotbar-Swap, Schild-Editor-Screen, Sneak-Klick an Kisten/Türen
- `.sf status`/`BuildSession`: `placed` zählt gesendete Platzierungen, auch abgelehnte (gleiche Frage wie beim `PlacementLog`, P2-03). Erst nach Bestätigung zählen, sobald das geklärt ist
- `BuildSession`: PLANNING und jedes VERIFYING planen die ganze Schematic in einem Tick; bei großen Placements spürbarer Ruckler (siehe auch WorkPlanner-Nearest-Neighbor-Eintrag). Ggf. über mehrere Ticks verteilen
- Settings ohne Ticket: `buildOnlySelection` (§7, braucht `LitematicaAdapter.selectionBounds()`) und `renderClusters` (§7, braucht Render-Code) sind in P2-07 bewusst nicht angelegt
- `SchemaPrinter`: `blocksPerTick` > 1 ist einstellbar, aber die Zusatzpakete (Rotation, Sneak) zählt das Budget noch nicht – vor P5-05 messen
- `.sf verify` und `.sf materials` (§8) haben noch kein Ticket; `.sf status` deckt den Verifier-Teil bisher nur als Zählung „left to place / mismatched“ ab

- ~~P3-01 AK1, P3-02 AK1/AK2, P3-03 AK1 in-game~~ **bestanden 2026-09-19** (siehe TESTLOG)
- `Navigator`: ob `ARRIVE_DISTANCE` = 5 und die 1,5 s Anlaufzeit auch in schwierigem Gelände reichen, ist noch nicht
  gemessen – im Dauerlauf-Test (P3-06) mitbeobachten
- `.sf status` zeigt den Grund einer automatischen Pause noch nicht an (`BuildSession.safetyPause()` gibt es), nur die
  Chat-Meldung – `StatusReport` müsste den Grund mitbekommen
- `SafetyMonitor`: Schadenserkennung vergleicht nur den Lebensbalken; Rüstungsschaden ohne Lebensverlust oder Gift, das
  genau bis 1 zieht, lösen nichts aus. Ggf. an Meteors Damage-Event hängen
- `Printer.outOfMaterials` nutzt den Bedarf vom Beginn des Cluster-Besuchs, zählt also schon gesetzte Blöcke mit
- P3-04 AK1/AK2 in-game nachholen: Disconnect mitten im Bau → Reconnect → `.sf resume` macht bei Cluster i weiter;
  Setting ändern → `.sf start` warnt und verwirft den Checkpoint
- `BuildResume`: der Checkpoint speichert nur den Cluster-Index, nicht die Runde. Nach dem Fortsetzen beginnt der Lauf
  wieder bei Runde 1 – bei einem Abbruch in Runde 2/3 wird also mehr nachgeprüft als nötig
- `BuildResume`: Cluster-Indizes gelten nur für denselben Plan; ändert sich die Welt stark, zeigt der Index woandershin.
  Der `planConfigHash` fängt nur Setting-Änderungen ab, nicht Weltänderungen
- P3-05 AK1 in-game nachholen: Element im Meteor-HUD-Editor platzieren, Werte während eines Laufs gegenprüfen
- `BuildProgressHud`: die ETA rechnet mit der Durchschnittsrate des ganzen Laufs; nach einer langen Pause ist sie
  zu pessimistisch. Ggf. gleitendes Mittel der letzten Minute
- P4-01: `ContainerIndex` ist gebaut, aber noch nirgends angeschlossen – Dateiname (`containers-<serverHash>-<dimension>`),
  Laden beim Welt-Beitritt und Speichern beim Verlassen kommen mit P4-02
- P4-02 AK1/AK2/AK3 in-game nachholen: 3 Kisten öffnen → `.sf containers`; Doppelkiste → ein Eintrag mit 54 Slots;
  Enderkiste → Typ `ender_chest`. Dabei prüfen, ob der 40-Tick-Merker für den Rechtsklick in der Praxis reicht
- `ContainerRestock`: die Position kommt aus dem letzten Rechtsklick. Öffnet etwas anderes einen Container-Screen
  (Kommandoblock, Plugin-GUI, Minecart mit Kiste), wird die Position falsch zugeordnet – Menütyp wird noch nicht geprüft
- `ContainerRestock`: der Index wird beim Deaktivieren gespeichert, nicht beim Verlassen der Welt. Stürzt das Spiel ab,
  ist das Gelernte weg
- P4-03 AK1 in-game nachholen: 6 Kisten in 20 Blöcken → alle im Index, Dauer < 2 min. Dabei messen, ob 10 Ticks
  zwischen zwei Öffnungen und 60 Ticks Screen-Timeout in der Praxis passen
- `ScanSession`: der Scan öffnet nur, was in geladenen Chunks ein Block-Entity hat – Kisten hinter der Renderdistanz
  findet er nicht. Ein Lauf über größere Lager braucht mehrere Scans von verschiedenen Standorten
- P4-04 AK1 in-game nachholen (Meilenstein M4): Inventar leer, Lager mit 6 Kisten → Bau startet, holt, baut weiter
  ohne Eingriff. Dabei AK2 (leere Kiste) und AK3 (volles Inventar ohne Müll-Liste) gegenprüfen
- `RestockProcess`: `BuildSession.State.RESTOCKING` existiert, wird aber nicht betreten – der Restock läuft im
  PAUSED-Zweig. Entweder den Zustand nutzen oder ihn aus §5 streichen
- `RestockProcess`: die Müll-Liste legt in **denselben** Container zurück; ist der voll, hilft das nicht. PLAN 3.4
  nennt als ersten Schritt Wegwerfen nach Liste, das gibt es bewusst nicht (kein Item-Verlust)
- P4-05 AK1 in-game nachholen: Shulker platzieren → öffnen → entnehmen → abbauen → wieder im Inventar. Dabei prüfen,
  ob `freeSpot()` in engen Baustellen etwas findet und ob der Abbau mit dem Werkzeug in der Hand schnell genug ist
- P4-06 AK1 in-game nachholen: Zahlen gegen `.sf preview` und `.sf containers` prüfen
- P5-01 in-game nachholen: ein paar Blöcke drucken, `.sf undo 10`, prüfen dass fremde Blöcke stehen bleiben
- `.sf materials` plant die ganze Schematic neu, um die nächsten Cluster zu kennen – bei großen Placements dauert das
  einen Moment (gleiche Frage wie beim Ruckler in PLANNING/VERIFYING)
- `UndoSession`: der Undo läuft nur von der Stelle aus, an der man steht – kein Navigator. Für verstreute Blöcke
  müsste er die Cluster anlaufen wie der Printer
- `.sf undo` liest das Log der **aktuell eingestellten** Schematic; wer das Placement-Setting ändert, undoet ein
  anderes Log
- Multi-Account-Aufteilung von Clustern
- Servux-Handshake selbst sprechen
- Mining/Crafting-Beschaffung (Alto-Clef-Stil)
- `buildOnlySelection` / `LitematicaAdapter.selectionBounds()`: steht in ARCHITECTURE.md §4 und §7, hat aber kein Ticket. Die Hülle aus P0-04 wirft `UnsupportedOperationException("Backlog: buildOnlySelection")`
