# SchemaForge – Meteor-Client-Addon

Litematica-Printer + Container-Restock + Baritone-Navigation als Meteor-Addon für Minecraft 26.2.
Dieses Dokument ist die Arbeitsanweisung für Claude Code. Lies es vollständig, bevor du etwas änderst.

## Pflichtlektüre (in dieser Reihenfolge)

1. `CLAUDE.md` (diese Datei) – Regeln
2. `docs/ARCHITECTURE.md` – Schnittstellen, Datenmodelle, Paketstruktur (verbindlich)
3. `docs/TASKS.md` – Tickets mit Akzeptanzkriterien; immer nur **ein** Ticket auf einmal
4. `docs/PLAN.md` – Hintergrund, Recherche, Fehleranalyse (nur bei Bedarf nachschlagen)

## Stack (nicht ändern ohne Rückfrage)

| Komponente | Version | Quelle |
|---|---|---|
| Minecraft | 26.2 | `gradle/libs.versions.toml` |
| Fabric Loader | 0.19.3 | ebd. |
| Meteor Client | 26.2-SNAPSHOT | `https://maven.meteordev.org/snapshots` |
| JDK | 25 | Toolchain in `build.gradle.kts` |
| Loom | 1.17-SNAPSHOT | ebd. |
| Mappings | **Mojmap** (nicht Yarn!) | Meteor 26.x |
| Litematica / MaLiLib | 26.2, `compileOnly` (Loom 1.17 hat kein `modCompileOnly`) | Modrinth-Maven |
| Baritone | `baritone.api` aus Meteor-Fork (aktuell 26.1-SNAPSHOT gepinnt) | `maven.meteordev.org` |

Der Stack ist zu neu für Trainingswissen. **Rate niemals API-Namen.** Wenn du eine Klasse oder Methode aus Minecraft, Meteor, Baritone oder Litematica brauchst, schlage sie in `refs/` nach (siehe unten) oder im dekompilierten Loom-Output unter `.gradle/loom-cache` bzw. dem IDE-Sourcejar.

## Referenzquellen lokal (`refs/`, gitignored)

Vor dem ersten Ticket einmalig ausführen (Ticket P0-02):

```
git clone --depth 1 https://github.com/MeteorDevelopment/meteor-client refs/meteor-client
git clone --depth 1 -b 26.2 https://github.com/cabaletta/baritone refs/baritone
git clone --depth 1 https://github.com/aleksilassila/litematica-printer refs/litematica-printer   # NUR LESEN, AGPL!
```

Vorbild-Code in Meteor (gleiche Lizenz GPL-3.0, darf adaptiert werden):
- `refs/meteor-client/src/main/java/meteordevelopment/meteorclient/systems/modules/world/HighwayBuilder.java` – Platzieren, Rotation, Slot-Wechsel, Ender-Chest-Restock, State-Machine
- `.../modules/world/InfinityMiner.java` – Arbeiten→Lager→zurück-Loop mit Baritone
- `.../modules/misc/InventoryTweaks.java` (Setting-Gruppe „Auto Steal“; eine eigene `AutoSteal.java` existiert in 26.2 nicht) – Container-Slot-Klicks mit Delay
- `.../utils/world/BlockUtils.java`, `.../utils/player/Rotations.java`, `.../utils/player/InvUtils.java`, `.../utils/player/FindItemResult.java`
- `.../pathing/PathManagers.java`, `.../pathing/BaritonePathManager.java` – wie Meteor Baritone kapselt
- `refs/baritone/src/main/java/baritone/utils/schematic/litematica/LitematicaHelper.java` – zeigt, welche Litematica-Interna gebraucht werden (und warum das bricht)
- `refs/litematica-printer/` – **nur** Konzepte für Facing/Rails/Fluids lesen. Kein Copy-Paste, keine Umformulierung von Code. AGPL-3.0.

## Harte Regeln

1. **Nur `baritone.api.*`** verwenden. Kein Import aus `baritone.*` ohne `api`.
2. **Litematica nur über `compat/LitematicaAdapter`.** Kein direkter Import von `fi.dy.masa.*` außerhalb des Pakets `compat`. Der Adapter kapselt alle fragilen Zugriffe (`getBlockState`, Placement-Listen, Sub-Regionen) über `MethodHandle`s mit Signaturprüfung beim Start.
3. **Soft-Dependencies:** `fabric.mod.json` → `suggests`, nie `depends` für litematica/baritone. Das Addon muss starten, wenn beide fehlen, und dann klare Chat-Meldungen liefern.
4. **Keine Mixins in Litematica oder Baritone.** Mixins in Minecraft-Klassen nur, wenn `docs/ARCHITECTURE.md` sie vorsieht; jeder neue Mixin braucht einen Kommentar mit Begründung und Ticket-Nummer.
5. **Kein Code aus AGPL-Quellen.** Wenn du dich beim Schreiben an `refs/litematica-printer` orientierst, implementiere aus der Beschreibung des Verhaltens neu.
6. **Additive-only ist Default.** Kein Codepfad darf Blöcke brechen, solange `SchemaPrinter.additiveOnly == true`. Das gilt auch für Baritone-Aufrufe (`avoidBreaking`-Liste setzen).
7. **Paketpacing überall.** Jede Aktion, die ein Paket auslöst (Platzieren, Klick, Öffnen), läuft über `core/ActionBudget` mit Limit pro Tick. Keine `for`-Schleife, die n Pakete in einem Tick feuert, ohne das Budget zu fragen.
8. **Ein Ticket pro Session-Abschnitt.** Ticket in `docs/TASKS.md` auf `in_progress` setzen, umsetzen, Akzeptanzkriterien einzeln prüfen, `done` setzen, committen. Nächstes Ticket erst danach.
9. **Kein Scope-Creep.** Ideen, die nicht im aktuellen Ticket stehen, kommen als neue Zeile unter `## Backlog` in `docs/TASKS.md`, nicht in den Code.
10. **Vor dem Antworten „fertig“:** `./gradlew build` grün, alle Tests grün, kein neuer `TODO` ohne Ticket-Referenz.

## Build & Test

```
./gradlew build                 # Jar in build/libs/
./gradlew test                  # JUnit 5 – reine Logik (WorkPlanner, PlacementSolver, ContainerIndex) muss ohne Minecraft-Runtime testbar sein
./gradlew runClient             # Dev-Client mit Meteor + Addon (Litematica/MaLiLib-Jars liegen in run/mods/)
```

- Logik-Klassen in `core/` dürfen **keine** Minecraft-Runtime-Abhängigkeit über `BlockState`/`BlockPos` hinaus haben; alles, was `Minecraft.getInstance()` braucht, gehört in `modules/`, `compat/` oder `Printer`/`Navigator`.
- Für Tests: `core/` gegen kleine Interfaces programmieren (`WorldView`, `InventoryView`), die im Test gemockt werden. Siehe `docs/ARCHITECTURE.md`.
- Manuelle In-Game-Tests: Prüfschritte stehen im jeweiligen Ticket. Ergebnis als kurze Zeile in `docs/TESTLOG.md` (Datum, Ticket, Ergebnis).

## Code-Konventionen

- Java 25, Records für Datenklassen, `final` wo möglich, keine `null`-Rückgaben in `core/` (Optional oder leere Collection).
- Paket-Root: `dev.tore.schemaforge` (statt `com.example.addon`).
- Settings-Namen im Meteor-GUI englisch, Chat-Meldungen englisch (Server-Umfeld), Kommentare und Commit-Messages deutsch oder englisch – konsistent pro Datei.
- Commit-Message: `P2-04: PlacementSolver für Slabs/Stairs` (Ticket-ID voran).
- Logging über `SchemaForgeAddon.LOG` (SLF4J), nie `System.out`.

## Wenn etwas nicht wie erwartet ist

- API-Signatur in Meteor/Baritone/Litematica anders als in `docs/ARCHITECTURE.md` beschrieben → **erst** `refs/` prüfen, dann ARCHITECTURE.md korrigieren und im Commit erwähnen. Nicht stillschweigend anders bauen.
- Build bricht wegen Versionskonflikt → in `gradle/libs.versions.toml` nur die eine Version anfassen, die den Fehler verursacht, und im Ticket notieren.
- Unsicher, ob Verhalten gewollt ist → Frage an den Nutzer formulieren, Ticket auf `blocked` setzen, nicht raten.
