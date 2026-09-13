# Projektplan: Meteor-Addon „SchemaForge“ – Litematica-Printer + Baritone-Fixes

Stand der Recherche: 13.09.2026 (Europe/Berlin). Arbeitstitel frei änderbar.

## TL;DR

- Zielplattform: **Minecraft 26.2 · Fabric Loader 0.19.3 · Meteor Client 26.2-SNAPSHOT · JDK 25 · Loom 1.17-SNAPSHOT** (direkt aus dem offiziellen Addon-Template gezogen).
- Baritone upstream ist seit **31.08.2026 auf v1.19.0 für 26.2**. Meteor hängt in seinem `libs.versions.toml` aber noch auf **`baritone = 26.1-SNAPSHOT`** aus dem MeteorDevelopment-Fork → Versionsdrift ist Risiko Nr. 1.
- Zusätzlich: **Container-Restock** – ein lernender Kisten-Index plus Baritone-Navigation holt fehlende Blöcke automatisch aus deinem Lager ins Inventar.
- Das Addon baut **keinen eigenen Pathfinder**, sondern kombiniert: eigener Printer (Blockplatzierung in Reichweite) + Baritone nur für Wegfindung zum nächsten Bau-Cluster + Fixes für den kaputten `#litematica`-Pfad.
- Fünf Phasen, ca. 8–11 Wochen Feierabend-Tempo. MVP (Phase 1+2) ist nach ~3 Wochen nutzbar.
- Lizenzfalle: Litematica-Printer ist **AGPL-3.0**. Kein Code kopieren, nur Konzepte übernehmen, sonst muss das Addon AGPL werden.

---

## 1. Ziel und Abgrenzung

**Ziel:** Ein Meteor-Addon, das eine Litematica-Platzierung zuverlässig, schnell und server-verträglich in die Welt bringt – auf einem Multiplayer-Server ohne OP – und dabei die bekannten Aussetzer der Kombination Meteor + Baritone + Litematica beseitigt.

**Nicht Ziel (v1):**
- Kein Ersatz für Litematica selbst (Rendern, Schematic-Verwaltung bleibt bei Litematica).
- Kein Umgehen von Anti-Cheat im Sinne von „unsichtbar“ – nur konfigurierbare Paketraten, damit man nicht sofort gekickt wird.
- Kein Forge/NeoForge.

**Annahmen (weil nicht spezifiziert):**
1. Du spielst weiter auf dem Drittanbieter-Server mit Meteor + gebündeltem Baritone (kein Creative).
2. Litematica + MaLiLib für 26.2 sind installiert; Servux ggf. serverseitig nicht vorhanden.
3. Java-Kenntnisse aus dem SnapTap-Mod reichen als Einstieg; Mixins sind Neuland.

---

## 2. Ist-Zustand (recherchiert)

### 2.1 Versionen

| Komponente | Version | Quelle / Datum |
|---|---|---|
| Minecraft | 26.2 | Meteor Template `libs.versions.toml` |
| Fabric Loader | 0.19.3 | ebd. |
| Fabric API (Meteor baut dagegen) | 0.154.2+26.2 | Meteor `libs.versions.toml` (master) |
| Meteor Client | 26.2-SNAPSHOT | Meteor Maven `maven.meteordev.org/snapshots` |
| Baritone (Meteor-Fork, gebündelt) | **26.1-SNAPSHOT** (!) | Meteor `libs.versions.toml` (master) |
| Baritone upstream | v1.19.0 für 26.2, Release 31.08.2026 | github.com/cabaletta/baritone/releases |
| Litematica Printer (aleksilassila) | 26.2-3.2.2, veröffentlicht 17.06.2026 | Modrinth |
| Litematica | 26.2-Build vorhanden, genaue Nummer **noch prüfen** (Modrinth/CurseForge, nicht litematica.com) | – |
| Mappings | Mojmap (Baritone-Quelle nutzt `net.minecraft.core.BlockPos`) | cabaletta/baritone Branch 26.2 |

**Korrektur zu „neueste Baritone-Version“:** Die „neueste“ Baritone, die tatsächlich in Meteor steckt, ist nicht die neueste upstream. Das Addon muss deshalb ausschließlich gegen `baritone.api.*` programmieren und zur Laufzeit prüfen, welche Baritone-Klassen da sind – nie gegen `baritone.*`-Interna.

### 2.2 Wie Baritone Litematica heute anspricht

`baritone.utils.schematic.litematica.LitematicaHelper` importiert **direkt** Litematica-Interna:
`fi.dy.masa.litematica.data.DataManager`, `SchematicPlacement`, `SubRegionPlacement`, `SchematicWorldHandler`, `WorldSchematic`. Es holt sich die Platzierung per Index, transformiert Sub-Regionen (Mirror/Rotation) und liest jeden `BlockState` einzeln aus der Schematic-Welt in ein `BlockState[][][]`.

Folge: Jede Signaturänderung bei Litematica bricht `#litematica` mit `NoSuchMethodError` (dokumentiert: cabaletta/baritone #4601, 03.01.2025: `WorldSchematic.getBlockState(BlockPos)` nicht gefunden). Genau das ist der Klassiker „Baritone #litematica not working“ (MeteorDevelopment/meteor-client #5186, 09.02.2025).

### 2.3 Bekannte Fehler, die das Addon adressiert

| # | Symptom | Quelle | Ursache (Analyse) | Fix-Ansatz |
|---|---|---|---|---|
| F1 | `#litematica` wirft „unhandled exception“ / `NoSuchMethodError` | baritone #4601, meteor #5186 | Harte Kompilierung gegen Litematica-Interna | Eigener **versionsadaptiver Adapter** (Reflection + MethodHandles), Baritone-Command wird nicht mehr genutzt |
| F2 | „Done building“ sofort, nichts passiert | meteor #4793 (31.07.2024), baritone #2689 | Leere/falsch transformierte Schematic (Sub-Region deaktiviert, Origin-Offset falsch, Index falsch) | Eigene Schematic-Extraktion mit Validierung + Vorschau der Blockanzahl vor Start |
| F3 | Bot läuft zu leeren Stellen, Endlosschleife | baritone #4546 (02.11.2024), #4342 | Baritone-Builder pathet zu Zielblöcken, die es nicht platzieren kann (kein Support, falsche Facing) → Blacklist/Retry-Loop | Printer platziert nur erreichbare Blöcke; Baritone bekommt nur **Cluster-Ziele**, keine Einzelblöcke |
| F4 | Scaffold platziert → abgebaut → Stillstand | baritone #3978 | Builder will „Air“ aus der Schematic erzwingen und zerstört eigenes Gerüst | Air in der Schematic optional als „egal“ behandeln (`ignoreAir`), Gerüstblöcke als temporär markieren |
| F5 | `#litematica` reißt trotz `allowBreak=false` Blöcke ab | baritone #4102 | Builder-Prozess hat eigene Break-Logik | **Additive-only-Modus**: nur platzieren, nie brechen, Fehlblöcke werden gelistet |
| F6 | Kein „ignore what's there“ pro Block | baritone #2323 | Feature fehlt | Filterregeln: „wenn Welt = X, dann überspringen“ |
| F7 | Printer (aleksilassila): Flüssigkeiten, `printInAir`, Rails unzuverlässig, kein Legit-Mode | README aleksilassila/litematica-printer | Bekannte offene Punkte des Originals | Eigene Strategien für Fluids (Bucket), Rails (Nachbar-Reihenfolge), Legit-Mode über Paketpacing |
| F8 | Versionsdrift Meteor-Baritone vs. upstream | Meteor `libs.versions.toml` | Meteor aktualisiert Baritone später als der Client | Soft-Dependency, Feature-Detection zur Laufzeit, klare Fehlermeldung statt Crash |

### 2.4 Lizenzen

- Meteor Client: GPL-3.0 · Baritone (beide Forks): LGPL-3.0 · Litematica/MaLiLib: LGPL-3.0 · Litematica Printer (aleksilassila): **AGPL-3.0** · Addon-Template: CC0.
- Konsequenz: Addon unter GPL-3.0 veröffentlichen (kompatibel zu Meteor). **Nichts** aus dem Printer-Repo kopieren.

---

## 3. Architektur

### 3.1 Grundprinzip: „Printer baut, Baritone läuft“

Der Baritone-Builder ist der fehleranfälligste Teil (F2–F5). Er wird **nicht** benutzt. Stattdessen:

1. **SchematicAdapter** liest die aktive Litematica-Platzierung → interne, transformierte Blockliste (`Long2ObjectMap<BlockState>`), Sub-Regionen bereits aufgelöst.
2. **WorkPlanner** vergleicht Soll (Schematic) mit Ist (Client-Welt), erzeugt eine Aufgabenliste, gruppiert in **Cluster** (z. B. 5×5×5 oder schichtweise), sortiert nach Reihenfolge (unten → oben, Support-Blöcke zuerst).
3. **Printer** platziert alle Blöcke des aktuellen Clusters, die in Reichweite (≤ 4,5 Blöcke, Sichtlinie optional) und legal platzierbar sind. Rotation/Facing/Half/Axis werden über die Klick-Seite und den Blickvektor erzeugt, nicht über „einfach draufsetzen“.
4. **Navigator** (Baritone via `BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()`) bekommt ein `GoalNear`/`GoalGetToBlock` zum nächsten Cluster, sobald im aktuellen nichts mehr erreichbar ist. Optional Fallback: Meteor-eigener `PathManagers.get()`.
5. **MaterialManager** hält passende Items im Hotbar (Swap), meldet Fehlbestand und löst bei Unterschreiten einer Schwelle den **Restock-Prozess** aus (siehe 3.4).
6. **Verifier-Bridge** nutzt Litematicas Schematic-Verifier-Logik (per Adapter) für den Abschluss-Check und „Fehlblöcke“-Liste.

### 3.2 Modulübersicht

```
com.tore.schemaforge
├── SchemaForgeAddon            (MeteorAddon: Module/Commands/HUD registrieren)
├── compat
│   ├── LitematicaAdapter       (Reflection/MethodHandles, versionsadaptiv, Feature-Flags)
│   ├── BaritoneBridge          (nur baritone.api, Null-safe wenn Baritone fehlt)
│   └── VersionProbe            (prüft beim Start, welche Signaturen vorhanden sind)
├── core
│   ├── SchematicSnapshot       (immutable, transformierte Blockliste + Bounds)
│   ├── WorkPlanner             (Diff, Clustering, Sortierung, Filterregeln)
│   ├── PlacementSolver         (Facing/Half/Axis/Rails/Fluids → Klick-Seite + Blickrichtung)
│   ├── Printer                 (Tick-Loop, Paketpacing, Rotation-Spoof optional)
│   ├── Navigator               (Cluster-Ziele an Baritone, Timeout/Blacklist)
│   ├── MaterialManager         (Bedarf, Hotbar-Swap, Restock-Trigger)
│   └── ContainerIndex          (gelernte Kisteninhalte, JSON-Persistenz, Stale-Handling)
├── modules
│   ├── SchemaPrinter           (Hauptmodul, alle Settings)
│   ├── AdditiveOnlyGuard       (verhindert jeden Break-Vorgang, auch von Baritone)
│   └── BuildResume             (Checkpoint in JSON: Cluster-Index, Zeit, Statistik)
├── commands
│   ├── .sf start|pause|stop|status|verify|materials
│   ├── .sf restock [item]       (manuell auslösen) · .sf scan [radius] · .sf containers
│   └── .sf preview              (Blockanzahl, Materialliste, geschätzte Zeit)
├── hud
│   └── BuildProgressHud         (Fortschritt %, Blöcke/min, ETA, Fehlbestand)
└── mixins                       (nur wenn nötig – siehe 3.4)
```

### 3.3 Warum Reflection statt `modImplementation "litematica"`?

- Baritones harte Kompilierung ist exakt der Grund für F1. Eine Reflection-Schicht mit **Signaturprüfung beim Start** liefert eine klare Chat-Meldung („Litematica 0.xx nicht unterstützt, Methode Y fehlt“) statt Crash.
- Litematica als `modCompileOnly` (Modrinth-Maven `maven.modrinth:litematica`) ist trotzdem sinnvoll für Typsicherheit der bekannten Klassen; die Reflection sichert nur die Stellen ab, die sich erfahrungsgemäß ändern (`getBlockState`, `getAllSchematicsPlacements`, `getEnabledRelativeSubRegionPlacements`).

### 3.4 Restock aus Containern („Container-Sourcing“)

**Problem:** Der Client kennt Kisteninhalte nur, während die Kiste geöffnet ist. Es gibt keinen Server-Paket-Feed für geschlossene Container. Deshalb braucht das Feature einen **Container-Index**, der Inhalte beim Öffnen lernt und lokal speichert.

**Ablauf:**

1. **Bedarf ermitteln** – `MaterialManager` berechnet aus dem `WorkPlanner` die nächsten N Cluster und deren Itembedarf, abzüglich Inventar. Ergebnis: `Map<Item, int fehlend>`.
2. **Quelle finden** – `ContainerIndex` liefert Container (Kiste, Fass, Shulker in der Welt, Trapped Chest) mit den passenden Items, sortiert nach Distanz. Fehlt ein Eintrag, wird optional der **Scan-Modus** gestartet: alle Container im konfigurierten Radius (Default 32 Blöcke, per `BlockEntity`-Iteration aus geladenen Chunks) einmal anlaufen und öffnen → Index füllen.
3. **Hinlaufen** – `Navigator` schickt Baritone mit `GoalGetToBlock` zum Container. Bei Doppelkisten wird der nähere Teil angesteuert.
4. **Öffnen + Entnehmen** – Rechtsklick auf den Block, warten bis `ContainerScreen` offen ist (Timeout 60 Ticks), dann Slot-Klicks über `MultiPlayerGameMode.handleInventoryMouseClick` (Quick-Move / Shift-Klick). Pro Tick maximal k Klicks (Setting, Default 2) – gleiches Paketpacing-Prinzip wie beim Printer. Entnommen wird nur die Menge, die laut Schritt 1 fehlt, gerundet auf Stacks.
5. **Inventar-Platz** – Wenn voll: erst „Müll“ nach konfigurierbarer Liste ablegen (Cobble, Dirt), sonst Einlagern in denselben Container; wenn nichts hilft → Pause + Chat-Meldung.
6. **Index aktualisieren** – nach jeder Entnahme wird der Indexstand korrigiert; Schließen des Screens.
7. **Zurück zum Bau** – Navigator setzt den letzten Cluster als Ziel.

**Datenmodell `ContainerIndex`** (JSON unter `meteor-client/schemaforge/containers-<server>-<dimension>.json`):

```
{ "pos": [x,y,z], "type": "chest|barrel|shulker", "lastSeen": <epoch ms>,
  "items": { "minecraft:stone_bricks": 384, ... }, "stale": false }
```

- Einträge älter als ein konfigurierbares Alter (Default 24 h) werden als `stale` markiert und bevorzugt neu gescannt.
- Passive Aktualisierung: jeder Container, den du **manuell** öffnest, wird ebenfalls indiziert (Listener auf `ContainerScreen`-Open/Close). So baut sich der Index im normalen Spiel auf, ohne Scan-Lauf.
- Der Index ist Datenquelle, nie Instruktion: Inhalte werden geprüft, bevor gehandelt wird (Container kann inzwischen leer sein).

**Shulker-Boxen im Inventar:** Shulker im Inventar mit passenden Items werden vor jedem Laufweg berücksichtigt – öffnen ist nur möglich, wenn platziert. Ablauf: freien Block neben Spieler finden → platzieren → öffnen → entnehmen → abbauen → wieder aufnehmen. Nur wenn Setting `useInventoryShulkers` aktiv, weil das auf manchen Servern Anti-Cheat-Auffälligkeiten erzeugt.

**Meteor-Wiederverwendung:** Meteors `InvUtils` (Slot-Suche, `FindItemResult`) und die Logik hinter `AutoSteal`/`InventoryTweaks` zeigen den erwarteten Klick-Pfad für 26.2 – nachlesen, nicht kopieren, GPL-Kompatibilität ist aber ohnehin gegeben.

**Abgrenzung zu Alto Clef:** Alto Clef (Meloweh-Fork) beschafft Materialien durch Mining/Crafting. Hier geht es bewusst nur um vorhandene Lagerbestände – deutlich weniger komplex und auf Servern unauffälliger.

### 3.5 Mixins – so wenig wie möglich

Mögliche Kandidaten, jeder einzeln zu begründen:
- `ClientPacketListener`/`MultiPlayerGameMode`: nur falls Rotation-Spoof ohne Kamerabewegung gewünscht (Meteor hat dafür bereits `Rotations`-Utility → erst prüfen, ob Mixin überhaupt nötig).
- Kein Mixin in Litematica oder Baritone – Konflikte mit deren eigenen Mixins sind vorprogrammiert.

---

## 4. Eigene Ideen (über Baritone/Printer hinaus)

1. **Cluster-Navigation statt Block-Navigation** – Baritone erhält nur ~1 Ziel pro 100–200 Blöcke; eliminiert das Herumlaufen aus F3 und spart massiv Pathing-Rechenzeit.
2. **Additive-only-Modus** mit Fehlblock-Report statt Zerstörung – ideal auf Servern mit fremden Bauten in der Nähe.
3. **Support-aware Reihenfolge**: Blöcke, die eine Stützfläche brauchen (Torches, Buttons, Rails, Carpets, Doors), werden erst geplant, wenn der Träger in der Welt steht. Löst einen Großteil der Printer-Fehlplatzierungen.
4. **Filterregeln** (löst F4/F6): `skipIfWorldIs: [air, water]`, `treatAsAir: [grass, tall_grass]`, `neverPlace: [tnt]`.
5. **Paketpacing-Profile**: `vanilla-legit` (1 Block/Tick, echte Blickrichtung, 4,5 Reichweite), `fast` (n Blöcke/Tick mit Rotation-Spoof), `custom`. Einfach umschaltbar, wenn der Server anfängt zu meckern.
6. **Resume/Checkpoint**: Bei Disconnect oder Neustart weitermachen, wo aufgehört wurde – Snapshot der Platzierung + Cluster-Index in `meteor-client/schemaforge/<name>.json`.
7. **Preview vor Start**: Blockanzahl, Materialliste (mit „fehlt in Inventar“-Markierung), geschätzte Bauzeit, Warnungen (z. B. „Schematic liegt teils unter Y=-64“).
8. **Fluid-Strategie**: Wasser/Lava per Bucket vom Nachbarblock aus, Quellblock-Erkennung; Fluids in der Schematic optional ignorieren.
9. **Rails-Solver**: Platzierungsreihenfolge, die die Vanilla-Auto-Verbindung ausnutzt (Ecken zuletzt), Ergebnis mit dem Verifier gegenprüfen.
10. **Sicherheitsstopps**: Pause bei Schaden, Hunger < 6, Inventar voll, Spieler in Nähe (Meteor-`EntityUtils`), Chunk nicht geladen.
11. **Baritone-Health-Check-Command** `.sf doctor`: listet Meteor-, Baritone-, Litematica-Version, erkannte API-Signaturen, und ob `#litematica` überhaupt funktionieren kann. Spart dir künftig Stunden Fehlersuche.
12. **Lernender Container-Index**: Jede Kiste, die du sowieso öffnest, landet im Index – der Restock kennt dein Lager nach kurzer Zeit, ohne dass du je einen Scan starten musst. Bonus: `.sf containers` zeigt dir, wo welches Material liegt, auch ohne Bauauftrag.
13. **Bedarfsgerechter Restock**: Es wird nur geholt, was die nächsten N Cluster brauchen – kein volles Inventar mit Blöcken, die erst in drei Stunden dran sind.
14. **Wiederverwendung deiner SnapTap-Lessons**: Cloth-Config/ModMenu weglassen – Meteor liefert Settings-GUI und Keybinds selbst.

---

## 5. Phasenplan

### Phase 0 – Setup (2–3 Tage)
- Template klonen (`git clone --depth 1 https://github.com/MeteorDevelopment/meteor-addon-template`), Paket/ID umbenennen, `fabric.mod.json` mit `suggests: litematica, baritone` (nicht `depends`).
- `libs.versions.toml`: Litematica + MaLiLib als `modCompileOnly` über Modrinth-Maven ergänzen; Baritone-API aus `maven.meteordev.org` (gleiche Koordinate wie Meteor).
- IntelliJ-Run-Config „Minecraft Client“ starten, leeres Modul sichtbar → Meilenstein M0.
- `.sf doctor` implementieren (Versionen ausgeben). Das ist zugleich der Smoke-Test für die Reflection-Schicht.

### Phase 1 – Adapter + Planner (1–1,5 Wochen)
- `LitematicaAdapter`: aktive Platzierung → `SchematicSnapshot` (Mirror/Rotation der Sub-Regionen korrekt, Tests mit gedrehten Platzierungen).
- `WorkPlanner`: Diff gegen Welt, Clustering, Reihenfolge, Filterregeln.
- `.sf preview` → Meilenstein M1: Blockanzahl stimmt mit Litematicas Materialliste überein (Abweichung 0).

### Phase 2 – Printer MVP (1,5–2 Wochen)
- `PlacementSolver` für die häufigsten Blockklassen: Vollblöcke, Slabs, Stairs, Pillar/Axis, HorizontalFacing, Wall-Torches/Buttons/Levers, Trapdoors, Doors (untere Hälfte).
- `Printer` mit Paketpacing-Profil `vanilla-legit`, Reichweiten-Check, Hotbar-Swap.
- `AdditiveOnlyGuard`.
- Test-Schematic (10×10×10 mit jeder Blockklasse) in Singleplayer + auf Testserver → Meilenstein M2: Verifier meldet 0 falsche Blöcke.

### Phase 3 – Navigator + Robustheit (1–1,5 Wochen)
- `BaritoneBridge`: Cluster-Ziele, Timeout, Blacklist unerreichbarer Cluster, sauberer `stop()` bei Modul-Deaktivierung.
- Sicherheitsstopps, Resume/Checkpoint, HUD.
- Test: 60×60×30-Schematic unbeaufsichtigt 30 min → Meilenstein M3: kein Stillstand, kein Herumlaufen (F3 reproduzierbar behoben).

### Phase 4 – Container-Restock (1–1,5 Wochen)
- `ContainerIndex` mit passivem Lernen (Screen-Listener) + JSON-Persistenz.
- `.sf scan`, `.sf containers`, `.sf restock`.
- Restock-Ablauf aus 3.4 inkl. Inventar-Platz-Handling; Shulker-Option zuletzt.
- Test: Lager mit 6 Kisten, Inventar leer, Bau starten → Meilenstein M4: Addon holt selbstständig Material und baut ohne Eingriff weiter.

### Phase 5 – Feinschliff (offen)
- Fluids, Rails, Paketprofile `fast`, Servux-Erkennung.
- Modrinth-Release, README, CI über Template-Workflow `dev_build.yml`.

---

## 6. Test- und Verifikationsplan

| Test | Erwartung | Deckt ab |
|---|---|---|
| Litematica-Version wechseln (26.1.2 ↔ 26.2) | `.sf doctor` meldet fehlende Signaturen statt Crash | F1, F8 |
| Platzierung mit Rotation 90° + Mirror, 2 Sub-Regionen (1 deaktiviert) | Preview zählt nur die aktive Region korrekt | F2 |
| Schematic mit Luft über Boden | Kein Gerüst-Ping-Pong; `ignoreAir` respektiert | F4 |
| Fremder Block im Baubereich, Additive-only an | Wird nicht gebrochen, taucht im Report auf | F5 |
| Baritone deinstalliert | Modul läuft im „Reichweite-only“-Modus, klare Meldung | F8 |
| 30-min-Dauerlauf | Blöcke/min stabil, keine Leerlaufschleife | F3 |
| Kiste im Index inzwischen leer | Kein Endlos-Öffnen; Eintrag wird korrigiert, nächste Quelle gewählt | Restock |
| Inventar voll, Müll-Liste leer | Pause + Meldung statt Item-Verlust | Restock |
| Doppelkiste, Items in der zweiten Hälfte | Komplette Ansicht wird ausgewertet | Restock |

Jede Testrunde: Log-Auszug + Verifier-Ergebnis in `docs/testlog.md` festhalten.

---

## 7. Risiken und Gegenmaßnahmen

| Risiko | Wahrscheinlichkeit | Gegenmaßnahme |
|---|---|---|
| Meteor-Baritone bleibt auf 26.1-SNAPSHOT, API-Lücken | mittel | Nur `baritone.api`; Feature-Detection; Fallback ohne Baritone |
| Litematica ändert Interna erneut | hoch (historisch mehrfach) | Reflection + Signaturprüfung, kleiner Adapter je Version |
| Server-Anti-Cheat kickt bei Printer-Raten | mittel | Profil `vanilla-legit` als Default, Rate per Setting |
| Mojmap-Umstellung stolpert über alte Tutorials (Yarn-Namen) | hoch | Immer gegen Meteor-master-Quellcode lesen, nicht gegen 1.20-Guides |
| Container-Klicks zu schnell → Kick/Desync | mittel | Klicks/Tick konfigurierbar, Default 2; Screen-Bestätigung abwarten |
| Index veraltet (Mitspieler leert Kiste) | hoch | Stale-Alter, Prüfung beim Öffnen, kein blindes Vertrauen in den Index |
| AGPL-Kontamination | niedrig, aber fatal | Kein Code aus litematica-printer; nur eigene Implementierung |
| Umschulung/Praktikum frisst Zeit | hoch | MVP = Phase 1+2; Phase 3–5 sind Bonus |

---

## 8. Gegenargumente, die du kennen solltest

1. **„Warum nicht einfach den fertigen Litematica Printer + Baritone nutzen?“** – Funktioniert für kleine Builds im Umkreis. Für dein großes Projekt fehlt aber Navigation, Resume und Fehlertoleranz; genau da brechen beide Tools (F2–F5). Das Addon ist trotzdem deutlich Aufwand – wenn dein Build < 5.000 Blöcke ist, lohnt es sich nicht.
2. **„Baritone-Builder fixen statt umgehen“** – Wäre sauberer für die Community, aber du müsstest im MeteorDevelopment-Fork arbeiten, dessen Update-Zyklus du nicht kontrollierst (aktuell 26.1-SNAPSHOT). Eigener Printer gibt dir Kontrolle, kostet aber Facing-Logik für viele Blockklassen.

---

## 9. Quellen (mit Bewertung 0–10)

| Quelle | Datum | Bewertung | Begründung |
|---|---|---|---|
| github.com/MeteorDevelopment/meteor-addon-template – `libs.versions.toml`, `build.gradle.kts`, README | abgerufen 13.09.2026 | 10 | Primärquelle, exakte Versionen |
| github.com/MeteorDevelopment/meteor-client – `gradle/libs.versions.toml` (master) | abgerufen 13.09.2026 | 10 | Primärquelle; zeigt Baritone 26.1-SNAPSHOT-Pin |
| github.com/cabaletta/baritone – Release v1.19.0 | 31.08.2026 | 9 | Offizielles Release; Datum aus Release-Seite |
| github.com/cabaletta/baritone – `LitematicaHelper.java` (Branch 26.2) | abgerufen 13.09.2026 | 10 | Quellcode, belegt harte Kopplung |
| cabaletta/baritone Issue #4601 | 03.01.2025 | 8 | Stacktrace mit `NoSuchMethodError` |
| cabaletta/baritone Issues #4546, #4342, #3978, #4102, #2323, #2689 | 2021–2024 | 6 | Nutzerberichte, teils ohne Logs, aber konsistentes Muster |
| MeteorDevelopment/meteor-client Issues #5186, #4793 | 09.02.2025 / 31.07.2024 | 6 | Nutzerberichte, bestätigen Symptome auf Meteor-Seite |
| modrinth.com/mod/litematica-printer – Version 26.2-3.2.2 | 17.06.2026 | 9 | Offizielle Verteilseite, Lizenz AGPL-3.0 sichtbar |
| github.com/aleksilassila/litematica-printer – README | abgerufen 13.09.2026 | 8 | Primärquelle für offene Probleme (Fluids, printInAir, Rails) |
| litematica.com („0.28.8“) | unklar | 2 | Inoffizielle Fan-/SEO-Seite mit VPN-Werbung; Versionsangabe nicht übernommen |

**Unsicherheiten:** Exakte Litematica-/MaLiLib-Versionsnummer für 26.2 und der aktuelle Stand des MeteorDevelopment/baritone-Forks (GitHub-API-Ratelimit beim Abruf) sind offen – beides in Phase 0 per `.sf doctor` bzw. Modrinth prüfen.

---

## 10. Nächste konkrete Schritte

1. Template klonen, Paket umbenennen, `Minecraft Client`-Run-Config starten (M0).
2. Litematica 26.2 + MaLiLib von Modrinth in den Dev-Mods-Ordner; Versionsnummern hier in Abschnitt 2.1 nachtragen.
3. `.sf doctor` schreiben – 1 Command, 1 Reflection-Probe. Damit hast du Phase 0 in einem Abend durch.

---

## 11. Weitere Inspirationen (recherchiert 13.09.2026)

Was andere Projekte gut lösen und was davon in welchen Baustein des Plans wandert. Konzepte übernehmen, keinen Code (Lizenzen beachten – siehe 2.4).

### 11.1 Blockplatzierung

| Quelle | Was sie gut macht | Übernahme in den Plan | Bewertung |
|---|---|---|---|
| **Litematica Easy Place + Carpet/Servux „Accurate Block Placement“-Protokoll (V2/V3)** – github.com/maruohon/litematica/wiki/Easy-Place; PaperAccurateBlockPlacement (26.2) | Server-Protokoll, mit dem der Client die gewünschte Rotation/Property mitschickt; funktioniert nur, wenn der Server Carpet Extra, Litemoretica oder das Paper-Plugin hat. Ohne Protokoll: Ghost-Blocks bei Rotationsblöcken | **Protokoll-Erkennung** in `.sf doctor`: Ist V2/V3 verfügbar → `PlacementSolver` nutzt es (deutlich weniger Blickrichtungs-Gefummel). Sonst Fallback auf eigene Klick-Seiten-/Blick-Logik. Vanilla-only-Server ist dein wahrscheinlicher Fall | 9 (Wiki + aktuelle Server-Implementierung für 26.2) |
| **eatmyvenom/litematica-printer** | Ersetzt Easy Place durch Printer; nutzt bewusst **kein** Accurate-Placement und rotiert stattdessen den Spieler. Setting `easyPlaceClickAdjacent` für Spigot-Server, die Klicks auf Luft ablehnen | `clickAdjacentOnly`-Setting im Printer (Klick immer auf existierenden Nachbarblock) – wichtig für Paper/Spigot-Server | 6 (kleines Repo, aber die Spigot-Erkenntnis ist belegt) |
| **Litematica `pickBlockAuto` / `pickBlockableSlots`** | Automatischer Hotbar-Swap vor jeder Platzierung, aber nur in erlaubten Slots | `MaterialManager`: erlaubte Hotbar-Slots als Setting (z. B. 2–8), damit Schwert/Essen/Werkzeug nie verdrängt werden | 8 |
| **Wurst AutoBuild** – wurst.wiki/autobuild | Range + Line-of-Sight-Check als getrennte Settings; „Legit Mode“ = LoS erzwungen + Reichweite begrenzt | Genau diese zwei Schalter statt eines pauschalen Legit-Toggles ins Paketprofil `vanilla-legit` | 7 |
| **AutoBuild (oDilan, Modrinth, Update 09.08.2026)** | Build-Modus *per Layer / per Block*, Geschwindigkeit auch **< 1 Block/Tick**, **Undo** der letzten Platzierungen, Live-Materialliste im Seitenpanel | (a) Geschwindigkeit als Blöcke pro **n Ticks** erlauben, nicht nur pro Tick; (b) `.sf undo <n>` auf Basis eines Platzierungs-Logs (Additive-only macht Undo trivial: nur eigene Blöcke abbauen) | 7 |
| **Effortless Structure** | Undo/Redo-Stack für Bauoperationen | Bestätigt: Undo ist erwartetes Feature, nicht Luxus | 5 |
| **mineflayer-schem / mineflayer-builder** | Sortierung nach Blockklassen: erst normale Blöcke, dann *directional*, dann Stairs/Slabs, dann Spezialblöcke; Multi-Bot-Aufteilung | Support-aware Reihenfolge (Idee 3) um eine **Klassen-Priorität** ergänzen; Multi-Account später denkbar | 6 |

### 11.2 Navigation & Bau-Reihenfolge

| Quelle | Was sie gut macht | Übernahme | Bewertung |
|---|---|---|---|
| **Baritone `Settings.java`** – `buildInLayers`, `layerOrder`, `startAtLayer`, `skipFailedLayers`, `buildOnlySelection`, `buildSubstitutes`, `buildValidSubstitutes`, `buildIgnoreProperties`, `buildIgnoreDirection`, `okIfWater`, `mapArtMode`, `schematicOrientationX/Y/Z`, `buildRepeat` | Über Jahre gewachsener Settings-Katalog; jedes Setting steht für ein reales Nutzerproblem | Als **Checkliste für Settings** übernehmen. Besonders: `buildSubstitutes` (Schematic verlangt Stein, du hast nur Andesit → erlaubte Ersatzliste), `buildIgnoreProperties` (z. B. `waterlogged` ignorieren), `buildOnlySelection` (nur Litematica-Auswahlbereich bauen → perfekt zum Testen), `startAtLayer` (Resume-Alternative), `mapArtMode` (nur oberster Block zählt) | 10 (Primärquelle) |
| baritone Issue #2051 (X/Z-Layer für Mapart) | Nutzer wollen Reihenfolge entlang X oder Z, nicht nur Y | `WorkPlanner`-Setting `layerAxis: X|Y|Z` + `layerOrder: ASC|DESC` – bei 1-Block-dicken Wänden entscheidend | 6 |
| baritone Issue #822 (temporäre Blöcke) | Baritone platziert keine Hilfsblöcke → schwebende Blöcke/obere Slabs unbaubar | Idee: **temporäre Stützblöcke** aus einer Whitelist (z. B. Dirt) platzieren, in Platzierungs-Log als „temp“ markieren, am Cluster-Ende wieder entfernen. Nur im Nicht-Additive-Modus | 6 |
| **Meteor `HighwayBuilder`-Modul** (im Client enthalten) | Kombination aus Platzieren, Abbauen, Inventar-Check, Restock aus Ender-Chest, Pause bei fehlendem Material – alles in einer State-Machine, mit `Rotations`-Utility und Paketpacing | Bester **In-House-Referenzcode** für 26.2-API: Wie Meteor Blöcke platziert (`BlockUtils.place`), rotiert und Slots wechselt. Lies das, bevor du `Printer` schreibst. GPL-3.0, also sogar kopierbar | 9 (gleiche Codebasis, gleiche Lizenz) |
| **Meteor `InfinityMiner`** | Loop „arbeiten → Inventar voll → zur Basis → einlagern → zurück“ mit gespeicherten Koordinaten | Spiegelbild deines Restock-Loops; die Baritone-Aufrufe daraus sind direkt wiederverwendbar | 8 |
| **Alto Clef (Meloweh-Fork)** | Materialbeschaffung per Mining/Crafting, „Mine Protection Zones“, damit der Bot eigene Bauten nicht wieder abbaut | (a) **Schutzone** um die Platzierung: Baritone darf beim Pathing dort nichts abbauen (`avoidBreaking`-Liste dynamisch setzen); (b) Beschaffung per Mining bleibt bewusst außen vor | 6 |

### 11.3 Restock & Lager

| Quelle | Was sie gut macht | Übernahme | Bewertung |
|---|---|---|---|
| **mineflayer-schem** | „Automatic item retrieval from the nearest chest“ – Bot holt Material aus der nächsten Kiste, sobald es fehlt | Bestätigt das Konzept aus 3.4; dort wird allerdings blind geöffnet. Dein Index-Ansatz ist die Verbesserung | 6 |
| **Meteor `AutoSteal` / `InventoryTweaks`** | Klick-Pfad für Container-Slots in 26.2 inkl. Delay-Settings | Referenz für Schritt 4 in 3.4 (Slot-Klicks, Delay) | 8 |
| **Litematica Material List → „Item Scroller“-Workflow** | Materialliste zeigt *fehlend im Inventar*; Item Scroller verschiebt per Massenklick | `.sf materials` sollte exakt das liefern: Bedarf gesamt / nächste Cluster / im Inventar / in bekannten Kisten (aus `ContainerIndex`) – eine Tabelle, vier Spalten | 7 |
| **Meteor `HighwayBuilder` Ender-Chest-Restock** | Ender-Chest als tragbares Lager | `ContainerIndex` kennt einen Sondertyp `ender_chest` (Inhalt spielerbezogen, überall gleich) – erstes Ziel beim Restock, wenn Material dort liegt | 8 |

### 11.4 Server-Verträglichkeit

| Quelle | Erkenntnis | Übernahme | Bewertung |
|---|---|---|---|
| Easy-Place-Wiki | Vanilla-Server ohne Protokoll → Rotationsblöcke werden Ghost-Blocks, wenn man nicht wirklich hinschaut | Printer muss bei Rotationsblöcken die **echte** Blickrichtung setzen (kein Spoof) oder den Block überspringen und melden | 8 |
| eatmyvenom-Printer (`easyPlaceClickAdjacent`) | Spigot/Paper lehnen Klicks auf Luft ab | Setting `clickAdjacentOnly` (siehe 11.1) | 6 |
| Wurst AutoBuild („bypass NoCheat+“) | Alte Anti-Cheats prüfen v. a. Reichweite und Rate | Moderne (Grim, Vulcan) prüfen zusätzlich Rotation-Konsistenz → Rotation-Spoof im Profil `fast` als **riskant** kennzeichnen, Default aus | 5 (Wurst-Wiki 2024; Grim-Details nicht recherchiert) |

### 11.5 Tooling außerhalb des Spiels

| Quelle | Nutzen | Bewertung |
|---|---|---|
| **litemapy** (Python, `pip install litemapy`) | `.litematic` lesen/schreiben, Regionen, Blockzählung | Für Test-Schematics per Skript erzeugen (10×10×10 mit jeder Blockklasse aus Phase 2) und Materiallisten offline gegenprüfen. Passt zu deinem Python-Berichtsheft-Setup | 8 |
| Baritone Javadoc – baritone.leijurv.com | Vollständige API-Referenz `baritone.api.*` | Pflichtlektüre für `BaritoneBridge`; nur diese Pakete sind stabil | 9 |

### 11.6 Daraus abgeleitete Plan-Ergänzungen

1. `.sf doctor` prüft zusätzlich: Accurate-Placement-Protokoll (V2/V3) verfügbar? Server-Brand (Paper/Spigot/Vanilla/Fabric)?
2. Neue Settings im `SchemaPrinter`: `layerAxis`, `layerOrder`, `substitutes` (Map), `ignoreProperties` (Liste), `allowedHotbarSlots`, `clickAdjacentOnly`, `lineOfSight`, `blocksPerNTicks`, `buildOnlySelection`.
3. Neues Command `.sf undo <n>` auf Basis eines Platzierungs-Logs (im Additive-only-Modus verlustfrei).
4. `ContainerIndex`: Sondertyp `ender_chest`; `.sf materials` als Vier-Spalten-Tabelle.
5. Phase 0 erweitert: `HighwayBuilder`, `InfinityMiner`, `AutoSteal` im Meteor-Quellcode lesen, **bevor** eigener Code entsteht – spart die halbe Phase 2.
6. Testdaten: Test-Schematic per litemapy generieren statt von Hand bauen.
