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

### P2-04 · `PlacementSolver` abhängige Blöcke `todo`
Wall-Torch, Torch, Button, Lever, Trapdoor, Door (untere Hälfte, hinge), Carpet, Ladder, Sign, Glazed Terracotta.
- AK1 Unit-Tests wie P2-02
- AK2 Manuell: entsprechende Zeilen der Test-Schematic → Verifier 0 Fehler

### P2-05 · `MaterialManager` Basis `todo`
Bedarf aus Cluster, Hotbar-Swap nur in `allowedHotbarSlots`, Fehlbestand-Event.
- AK1 Unit-Test: Item in Slot 1 (nicht erlaubt) + Slot 4 → Slot 4 gewählt
- AK2 Unit-Test: Item fehlt → Event mit Item + Menge

### P2-06 · `AdditiveOnlyGuard` `todo`
Bei `additiveOnly` keine Break-Aktion aus Printer; `BaritoneBridge.setAvoidBreaking(bbox)` beim Start, `restoreSettings()` beim Stop.
- AK1 Manuell: Fremder Block im Baubereich bleibt stehen, erscheint in `.sf status` unter „mismatched“
- AK2 Manuell: Modul aus → Baritone-Settings wieder wie vorher (`#modified` zeigt keine SchemaForge-Änderung)

### P2-07 · Modul `SchemaPrinter` + Zustandsautomat + `.sf start/pause/resume/stop/status` `todo`
- AK1 Manuell: kompletter Durchlauf Test-Schematic in Reichweite ohne Baritone → DONE, Verifier 0 Fehler
- AK2 `.sf status` zeigt State, Cluster i/n, Blöcke gesetzt, Blöcke/min, mismatched

---

## Phase 3 – Navigator + Robustheit

### P3-01 · `BaritoneBridge` `todo`
Nur `baritone.api`. `gotoNear`, `gotoBlock`, `isPathing`, `stop`, Settings-Backup/Restore.
- AK1 Manuell: `.sf` interner Testbefehl `.sf debug goto x y z` läuft zum Ziel
- AK2 Ohne Baritone: Methoden werfen nicht, `isPresent()==false`

### P3-02 · `Navigator` + TRAVELING-State `todo`
Cluster ohne erreichbare Tasks → `gotoNear(center, 3)`; Timeout 20 s → Cluster ans Ende der Liste, nach 3 Fehlversuchen blacklisten und melden.
- AK1 Manuell: 20×20×3-Plattform → Printer baut, läuft, baut; kein Cluster wird > 3× angelaufen
- AK2 Manuell: unerreichbarer Cluster (eingemauert) → Blacklist-Meldung, Bau geht weiter

### P3-03 · Sicherheitsstopps → PAUSED `todo`
Schaden, Hunger < `minFood`, Spieler im Radius, Chunk nicht geladen, Inventar leer für alle offenen Tasks.
- AK1 Manuell je Bedingung: Pause + Chat-Grund; Fortsetzen automatisch, wenn Bedingung weg (Spieler) bzw. `.sf resume` (Schaden)

### P3-04 · `BuildResume` `todo`
Checkpoint alle 30 s + bei Stop. `.sf start` mit vorhandenem Checkpoint fragt „resume? .sf resume“.
- AK1 Manuell: Disconnect mitten im Bau → Reconnect → `.sf resume` macht bei Cluster i weiter
- AK2 `planConfigHash` anders → Warnung, Checkpoint verworfen

### P3-05 · `BuildProgressHud` `todo`
- AK1 HUD-Element in Meteor-HUD-Editor platzierbar, zeigt %, Blöcke/min, ETA, State, Fehlbestand (Top 3)

### P3-06 · Dauerlauf-Test `todo`
- AK1 Manuell: 60×60×30-Schematic, 30 min unbeaufsichtigt auf Testserver → kein Stillstand > 60 s ohne PAUSED-Grund; Ergebnis in TESTLOG

---

## Phase 4 – Container-Restock

### P4-01 · `ContainerIndex` + Persistenz `todo`
- AK1 Unit-Tests: learn/sourcesFor/markStale/save/load Roundtrip
- AK2 Gson-Datei entspricht ARCHITECTURE.md §6, `"v":1`

### P4-02 · Passives Lernen `todo`
Listener auf Container-Screen open/close (Meteor-Event oder eigener Hook – Vorbild: Auto-Steal in `InventoryTweaks` erkennt den Container über `InventoryEvent` + `containerMenu.getType()`, siehe `docs/NOTES-meteor-api.md` Punkt 5); Container-Position aus letztem Rechtsklick-Ziel; Inhalt beim Schließen in Index.
- AK1 Manuell: 3 Kisten manuell öffnen → `.sf containers` listet alle 3 mit korrekten Mengen
- AK2 Doppelkiste → eine Position, alle 54 Slots gezählt
- AK3 Ender-Chest → Typ `ender_chest`, positionsunabhängig

### P4-03 · `.sf scan [radius]` `todo`
Container-BlockEntities im Radius aus geladenen Chunks; nacheinander anlaufen (Navigator), öffnen, lernen, schließen; Budget für Öffnungen (1 pro 10 Ticks).
- AK1 Manuell: 6 Kisten in 20 Blöcken → alle im Index, Dauer < 2 min

### P4-04 · `RestockProcess` `todo`
State-Machine laut ARCHITECTURE.md; Entnahme nur Bedarf der nächsten N Cluster (Setting `lookaheadClusters`, Default 3); Klicks über `ActionBudget` (Default 2/Tick); Screen-Timeout 60 Ticks; Inventar voll → Müll-Liste ablegen, sonst FAILED mit Meldung; Index nach Entnahme korrigieren.
- AK1 Manuell: Inventar leer, Lager mit 6 Kisten → Bau startet, holt, baut weiter ohne Eingriff (Meilenstein M4)
- AK2 Manuell: Kiste im Index inzwischen leer → nächste Quelle, Index korrigiert, kein Endlos-Öffnen
- AK3 Manuell: Inventar voll, Müll-Liste leer → FAILED + Meldung, kein Item-Verlust

### P4-05 · Shulker im Inventar (Option) `todo`
Nur wenn `useInventoryShulkers`. Platzieren → öffnen → entnehmen → abbauen → aufnehmen.
- AK1 Manuell: Ablauf funktioniert; Shulker landet wieder im Inventar; bei `additiveOnly` erlaubt (eigener temp-Block)

### P4-06 · `.sf materials` Vier-Spalten `todo`
Item | Bedarf gesamt | nächste N Cluster | Inventar | in bekannten Kisten.
- AK1 Manuell: Zahlen stimmen mit Preview + `.sf containers` überein

---

## Phase 5 – Feinschliff

### P5-01 · `.sf undo <n>` `todo` — aus `PlacementLog`, nur eigene Blöcke, Budget beachten
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

- Multi-Account-Aufteilung von Clustern
- Servux-Handshake selbst sprechen
- Mining/Crafting-Beschaffung (Alto-Clef-Stil)
- `buildOnlySelection` / `LitematicaAdapter.selectionBounds()`: steht in ARCHITECTURE.md §4 und §7, hat aber kein Ticket. Die Hülle aus P0-04 wirft `UnsupportedOperationException("Backlog: buildOnlySelection")`
