# Testlog

| Datum | Ticket | Ergebnis | Notiz |
|---|---|---|---|
| 2026-09-13 | P0-01 | OK | `runClient`: Fabric lädt `schemaforge 0.1.0`, Log `Initializing SchemaForge`, Addon erscheint in Meteor unter Addons; kein Crash, keine ERROR-Zeilen (einzige Warnung: Meteors Baritone-Mixin `ComeCommand`, Baritone nicht installiert) |
| 2026-09-13 | P0-05 | NICHT GETESTET | Auf Nutzeranweisung `done`. `runClient` mit Baritone 26.2-SNAPSHOT allein: Start ok. Mit Litematica 0.28.8 + MaLiLib 0.29.6: Render-Crash `Missing uniform Globals` nach Init (unabhängig von SchemaForge-Code). Baritone 26.1-SNAPSHOT: Fabric verweigert Start unter MC 26.2. `.sf doctor` selbst nicht in-game ausgeführt |
