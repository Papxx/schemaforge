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

---

## Phase 1 – Adapter + Planner

### P1-01 · `LitematicaAdapter.placementNames()` + `isPresent()` `done`
- AK1 Manuell: zwei geladene Placements → beide Namen im `.sf preview`-Dropdown/Chat
- AK2 Ohne Litematica → leere Liste, keine Exception

Stand 2026-09-13: `isPresent()` (Hauptklasse ladbar) und `placementNames()` (Handles einmalig per Holder aufgelöst, beide Methodennamen für `getAll…Placements`, bei Inkompatibilität einmal Log-Warnung + leere Liste, nie Exception). AK2 per `LitematicaAdapterTest` erfüllt (Litematica ist `compileOnly`, fehlt also im Test-Runtime). AK1 **offen**: `.sf preview` kommt erst mit P1-05, und der Dev-Client crasht mit Litematica (siehe P0-05).

Stand 2026-09-15: Auf Anweisung des Nutzers `done` gesetzt. AK1 wurde **nicht** in-game geprüft, sondern als Backlog-Punkt übernommen (mit P1-05 nachholen).

### P1-02 · `LitematicaAdapter.snapshot()` `done`
Alle aktivierten Sub-Regionen, Mirror/Rotation von Placement **und** Sub-Region anwenden (Transformationslogik wie in `LitematicaHelper.transform`, eigenständig implementiert), Blöcke via MethodHandle aus der Schematic-World lesen, `materialTotals` berechnen.
- AK1 Unit-Test: Transformation für alle 4 Rotationen × 3 Mirror-Zustände gegen handverifizierte Tabelle
- AK2 Manuell: Test-Schematic (P1-04) unrotiert → `materialTotals` == Litematica-Materialliste (Abweichung 0)
- AK3 Manuell: gleiches Placement um 90° gedreht + gespiegelt → Bounding Box stimmt mit Litematica-Rendering überein
- AK4 Manuell: eine Sub-Region deaktiviert → deren Blöcke fehlen im Snapshot

Stand 2026-09-15: `compat/PlacementTransform` (Mirror/Rotation, Sub-Region-Box), `core/MaterialRules` (Regeln von Litematicas `MaterialCache`), `LitematicaAdapter.snapshot()` (erstes Placement mit dem Namen; deaktiviertes Placement → leer; Positionen in nicht geladenen Schematic-Chunks werden weggelassen und geloggt; neue Signatur `SchematicPlacement.isEnabled()` auch in `.sf doctor`). ARCHITECTURE.md §1/§2/§4 und NOTES-litematica-api.md ergänzt. Build + 43 Tests grün.
- AK1 erfüllt: `PlacementTransformTest` (12er-Tabelle + Box-Fälle). Zusätzlich einmalig per Reflection gegen Litematica-0.28.8-`PositionUtils` abgeglichen: 3084 Fälle, 0 Abweichungen (nicht Teil der Test-Suite, weil Litematica im Test-Runtime fehlen muss).
- AK2–AK4 **nicht** in-game geprüft: Test-Schematic (P1-04) fehlt noch, `.sf preview` (P1-05) auch, und der Dev-Client crasht mit Litematica (siehe P0-05). `MaterialRulesTest` deckt die Blockklassen der Test-Schematic ab (Stufen, Tür, Wandfackel, Wasser …), ersetzt aber nicht den Abgleich mit der Litematica-Liste. Auf Anweisung des Nutzers `done`; AK2–4 → Backlog.

### P1-03 · `WorkPlanner.plan()` `todo`
Diff Soll/Ist über `WorldView`; Filterregeln; Priorität: Support-Blöcke (voll) vor abhängigen (Torch, Button, Rail, Carpet, Door, Sign, Ladder, Vine, Slab-Top ohne Träger); Clustering in Würfel `clusterSize`; Reihenfolge nach `layerAxis`/`layerAscending`, innerhalb einer Schicht Nearest-Neighbor ab Spielerposition.
- AK1 Unit-Test: 3×3×3-Snapshot, Welt leer → alle 27 Tasks, korrekte Cluster-Anzahl
- AK2 Unit-Test: Welt hat bereits 10 korrekte Blöcke → 17 Tasks
- AK3 Unit-Test: `additiveOnly=true`, Welt hat falschen Block → Task `SKIP` mit Grund, kein `BREAK`
- AK4 Unit-Test: Torch über fehlendem Boden → Torch-Task hat niedrigere Priorität als Boden-Task
- AK5 Unit-Test: `substitutes` stone→[andesite] + Welt hat Andesit → kein Task

### P1-04 · Test-Schematic per litemapy `todo`
`tools/gen_test_schematic.py` erzeugt `test/blockclasses.litematic`: 12×12×6 mit je einer Zeile pro Blockklasse (Vollblock, Slab unten/oben, Stairs 4 Richtungen, Log 3 Achsen, Wall-Torch 4 Seiten, Button, Lever, Trapdoor offen/zu, Door, Rail gerade/Kurve, Carpet, Glazed Terracotta 4 Richtungen, Water source, Fence).
- AK1 Skript läuft mit `pip install litemapy`, Datei lädt in Litematica ohne Fehler
- AK2 Skript druckt Materialliste (Item → Anzahl) für Vergleich in P1-02

### P1-05 · `.sf preview` `todo`
Blockanzahl, Cluster-Anzahl, Materialliste (gesamt / fehlend im Inventar), Warnungen (Y<-64, Bounding Box > Renderdistanz, Blöcke mit `Unsupported`-SolveResult).
- AK1 Manuell: Ausgabe ≤ 25 Zeilen für Test-Schematic; bei > 30 Materialien nur Top 30 + „…“

---

## Phase 2 – Printer MVP

### P2-01 · `ActionBudget` + Tick-Hook `todo`
- AK1 Unit-Test: Limit 2 → dritter `tryConsume()` im selben Tick false, nach `resetTick()` wieder true
- AK2 Modul-Tick registriert via Meteor `EventHandler` auf `TickEvent.Pre` (Name aus refs prüfen)

### P2-02 · `PlacementSolver` Grundklassen `todo`
Vollblock, Slab (half), Stairs (facing+half), Pillar (axis), HorizontalFacing (Glazed Terracotta, Furnace), Fence/Wall (Nachbar egal). Klick-Seite + `hitVec` so wählen, dass Vanilla-Placement den Zielstate erzeugt; `requiresRealRotation` wo Facing vom Blick abhängt.
- AK1 Unit-Test pro Blockklasse: gegebener Zielstate → erwarteter `clickFace`/`hitVec`-Halbraum/Yaw-Quadrant
- AK2 Kein Nachbar zum Anklicken → `NeedsSupport(pos)`
- AK3 `clickAdjacentOnly=true` → nie `clickPos == task.pos`

### P2-03 · `Printer` Tick-Loop `todo`
Für aktuellen Cluster: Tasks in Reihenfolge, Reichweite (`reach`, `lineOfSight`), Solve, Hotbar-Swap via `MaterialManager`, Rotation (echt via Meteor `Rotations` oder Spoof je Profil), Platzieren via `BlockUtils`-Äquivalent, `PlacementLog` schreiben, Budget beachten.
- AK1 Manuell: Test-Schematic-Zeile Vollblöcke/Slabs/Stairs/Logs in Singleplayer → Litematica-Verifier 0 Fehler
- AK2 Manuell: Profil VANILLA_LEGIT, 1 Block/Tick → keine Ghost-Blocks auf lokalem Paper-Server
- AK3 Blöcke außer Reichweite werden übersprungen, nicht endlos versucht (max 3 Versuche pro Task pro Cluster-Besuch)

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
- Dev-Client-Crash mit Litematica 0.28.8/MaLiLib 0.29.6: `Missing uniform Globals (should be UNIFORM_BUFFER)` beim Textur-Atlas-Tick (siehe P0-05-Notiz); klären, ob Treiber-, MaLiLib- oder Meteor-Kombination
- P0-05 AK1–3 in-game nachholen, sobald der Litematica-Render-Crash gelöst ist (oder in einer Nicht-Dev-Instanz)
- P1-01 AK1 in-game nachholen (zwei Placements → beide Namen), sobald `.sf preview` (P1-05) existiert und Litematica im Client läuft
- P1-02 AK2–4 in-game nachholen (Materialliste == Litematica, gedreht+gespiegelt Bounding Box, deaktivierte Sub-Region), nach P1-04/P1-05
- `snapshot()` bei großen Placements: eine Map-Zeile pro Position inkl. Luft, kein Größenlimit → Speicherbedarf messen, ggf. Limit oder Luft nur bei `ignoreAir=false` speichern
- `snapshot()` sieht nur Schematic-Chunks in Spielernähe; Chunks, die Litematicas Daemon schon geladen, aber noch nicht befüllt hat (`ChunkSchematicState`), liefern evtl. Luft – prüfen, ob das in-game vorkommt

- Multi-Account-Aufteilung von Clustern
- Servux-Handshake selbst sprechen
- Mining/Crafting-Beschaffung (Alto-Clef-Stil)
- `buildOnlySelection` / `LitematicaAdapter.selectionBounds()`: steht in ARCHITECTURE.md §4 und §7, hat aber kein Ticket. Die Hülle aus P0-04 wirft `UnsupportedOperationException("Backlog: buildOnlySelection")`
