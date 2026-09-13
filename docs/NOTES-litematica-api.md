# Notiz: Von Baritone genutzte Litematica-API

Quelle: `refs/baritone` @ `9fadf7c` (2026-08-31, Branch `26.2`).
- Aufrufe: `src/main/java/baritone/utils/schematic/litematica/LitematicaHelper.java`
- Signaturen: Baritones Compile-Stubs unter `src/schematica_api/java/fi/dy/masa/litematica/` (Methoden-Bodies werfen `LinkageError`)

**Stand der Prüfung:** Die Liste zeigt, *wogegen Baritone kompiliert*. Gegen das echte Litematica-26.2-Jar ist sie noch **nicht** abgeglichen. Das passiert in P0-03 (Jar als `modCompileOnly`) und P0-05 (`VersionProbe` löst jede Zeile als `MethodHandle` auf).

Paket-Präfix `fi.dy.masa.litematica.` unten weggelassen. MC-Typen (Mojmap): `net.minecraft.core.BlockPos`, `net.minecraft.world.level.block.Rotation`, `net.minecraft.world.level.block.Mirror`, `net.minecraft.world.level.Level`. `ImmutableMap` ist `com.google.common.collect.ImmutableMap`.

| # | Klasse | Signatur | static | Zeile in LitematicaHelper |
|---|---|---|---|---|
| 1 | `Litematica` | *(nur Klasse: Präsenz-Check per `Class.forName(Litematica.class.getName())`)* | – | 54 |
| 2 | `data.DataManager` | `SchematicPlacementManager getSchematicPlacementManager()` | ja | 65, 69 |
| 3 | `schematic.placement.SchematicPlacementManager` | `List<SchematicPlacement> getAllSchematicsPlacements()` ⚠ | nein | 65, 69 |
| 4 | `schematic.placement.SchematicPlacement` | `String getName()` | nein | 127 |
| 5 | `schematic.placement.SchematicPlacement` | `BlockPos getOrigin()` | nein | 115, 132 |
| 6 | `schematic.placement.SchematicPlacement` | `Rotation getRotation()` | nein | 105, 107 |
| 7 | `schematic.placement.SchematicPlacement` | `Mirror getMirror()` | nein | 105, 107 |
| 8 | `schematic.placement.SchematicPlacement` | `ImmutableMap<String, SubRegionPlacement> getEnabledRelativeSubRegionPlacements()` | nein | 103 |
| 9 | `schematic.placement.SchematicPlacement` | `LitematicaSchematic getSchematic()` | nein | 106 |
| 10 | `schematic.LitematicaSchematic` | `BlockPos getAreaSize(String regionName)` | nein | 106 |
| 11 | `schematic.placement.SubRegionPlacement` | `BlockPos getPos()` | nein | 105 |
| 12 | `schematic.placement.SubRegionPlacement` | `Rotation getRotation()` | nein | 108 |
| 13 | `schematic.placement.SubRegionPlacement` | `Mirror getMirror()` | nein | 108 |
| 14 | `world.SchematicWorldHandler` | `WorldSchematic getSchematicWorld()` | ja | 102 |
| 15 | `world.WorldSchematic` (`extends Level`) | *(nur Typ; gelesen wird über das Vanilla-`Level.getBlockState(BlockPos)`)* | – | 120 |

⚠ **Zeile 3 hat einen instabilen Namen.** Der Stub-Kommentar lautet: *„in case of a java.lang.NoSuchMethodError try change the name of this method to getAllSchematicPlacements()“*. Litematica hat zwischen `getAllSchematicsPlacements` und `getAllSchematicPlacements` hin- und hergewechselt. Der `LitematicaAdapter` muss deshalb beide Namen versuchen. Das ist genau der Defekt F1 aus PLAN.md (`NoSuchMethodError`).

## Beobachtungen, die für P1-02 wichtig sind

- **Baritone transformiert keine BlockStates.** Es liest die Blöcke fertig gedreht und gespiegelt aus Litematicas Schematic-World (`SchematicWorldHandler.getSchematicWorld().getBlockState(origin.offset(x, y, z))`). Mirror und Rotation braucht Baritone nur für die Lage und Größe der Bounding Box.
- **`getAreaSize` kann negative Komponenten liefern.** Die Größe gibt die Richtung vom Region-Ursprung an. Baritone nimmt deshalb `Math.abs(size)` und verschiebt mit `min(size + 1, 0)` zur Minimum-Ecke.
- **Die Transformationen sind asymmetrisch.** Die Sub-Region-*Position* wird nur mit Mirror/Rotation des Placements transformiert (Z. 105). Die *Größe* bekommt erst die Transformation des Placements und dann die der Sub-Region (Z. 107–108). Ob das korrekt ist, prüft der Unit-Test in P1-02 AK1 und nicht diese Notiz.
- **Die Reihenfolge in `transform` ist: erst Mirror, dann Rotation.** `LEFT_RIGHT` negiert z, `FRONT_BACK` negiert x. Danach folgt `CLOCKWISE_90: (x,z) → (−z, x)`, `CLOCKWISE_180: → (−x, −z)` und `COUNTERCLOCKWISE_90: → (z, −x)`.
