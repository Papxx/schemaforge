# Testlog

| Datum | Ticket | Ergebnis | Notiz |
|---|---|---|---|
| 2026-09-13 | P0-01 | OK | `runClient`: Fabric lädt `schemaforge 0.1.0`, Log `Initializing SchemaForge`, Addon erscheint in Meteor unter Addons; kein Crash, keine ERROR-Zeilen (einzige Warnung: Meteors Baritone-Mixin `ComeCommand`, Baritone nicht installiert) |
| 2026-09-13 | P0-05 | NICHT GETESTET | Auf Nutzeranweisung `done`. `runClient` mit Baritone 26.2-SNAPSHOT allein: Start ok. Mit Litematica 0.28.8 + MaLiLib 0.29.6: Render-Crash `Missing uniform Globals` nach Init (unabhängig von SchemaForge-Code). Baritone 26.1-SNAPSHOT: Fabric verweigert Start unter MC 26.2. `.sf doctor` selbst nicht in-game ausgeführt |
| 2026-09-15 | P1-01 | NICHT GETESTET | Auf Nutzeranweisung `done`. AK2 per Unit-Test grün; AK1 braucht `.sf preview` (P1-05) und einen Client, in dem Litematica läuft → Backlog |
| 2026-09-15 | P1-02 | NICHT GETESTET | Auf Nutzeranweisung `done`. AK1 per Unit-Test grün, zusätzlich Reflection-Abgleich gegen Litematica-0.28.8-`PositionUtils` (3084 Fälle, 0 Abweichungen). AK2–4 brauchen Test-Schematic (P1-04), `.sf preview` (P1-05) und lauffähiges Litematica → Backlog |
| 2026-09-15 | P1-04 | TEILWEISE | Skript lief (venv, litemapy 0.11.0b0). `test/blockclasses.litematic` mit Litematica-0.28.8-Parser in Scratch-JVM ohne Fehler gelesen (v7, DataVersion 4903, 339 Blöcke). Materialliste Skript == `MaterialRules` (337). Laden im echten Client nicht geprüft (Render-Crash) |
