# Notiz: Von Baritone genutzte Litematica-API

Quelle: `refs/baritone` @ `9fadf7c` (2026-08-31, Branch `26.2`).
- Aufrufe: `src/main/java/baritone/utils/schematic/litematica/LitematicaHelper.java`
- Signaturen: Baritones Compile-Stubs unter `src/schematica_api/java/fi/dy/masa/litematica/` (Methoden-Bodies werfen `LinkageError`)

**Stand der Prüfung:** Die Liste zeigt, *wogegen Baritone kompiliert*. In P0-05 per `javap` gegen das echte Jar `litematica-0.28.8` abgeglichen: alle Zeilen 2–14 existieren mit exakt diesen Signaturen (Zeile 3 heißt dort `getAllSchematicsPlacements`), `WorldSchematic extends Level` stimmt. Zur Laufzeit prüft `.sf doctor` jede Zeile erneut als `MethodHandle`.

EasyPlace (P0-05): `config.Configs$Generic.EASY_PLACE_PROTOCOL` ist eine MaLiLib-`ConfigOptionList` mit den Werten `AUTO, V3, V2, SLAB_ONLY, NONE` (`util.EasyPlaceProtocol`). `static util.PlacementHandler.getEffectiveProtocolVersion()` löst `AUTO` auf: Singleplayer oder Servux → `V3`, Carpet → `V2`, sonst `SLAB_ONLY`.

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
| 16 | `schematic.placement.SchematicPlacement` | `boolean isEnabled()` | nein | *(nicht Baritone – SchemaForge P1-02: deaktivierte Placements fehlen in der Schematic-World)* |

⚠ **Zeile 3 hat einen instabilen Namen.** Der Stub-Kommentar lautet: *„in case of a java.lang.NoSuchMethodError try change the name of this method to getAllSchematicPlacements()“*. Litematica hat zwischen `getAllSchematicsPlacements` und `getAllSchematicPlacements` hin- und hergewechselt. Der `LitematicaAdapter` muss deshalb beide Namen versuchen. Das ist genau der Defekt F1 aus PLAN.md (`NoSuchMethodError`).

## Beobachtungen, die für P1-02 wichtig sind

- **Baritone transformiert keine BlockStates.** Es liest die Blöcke fertig gedreht und gespiegelt aus Litematicas Schematic-World (`SchematicWorldHandler.getSchematicWorld().getBlockState(origin.offset(x, y, z))`). Mirror und Rotation braucht Baritone nur für die Lage und Größe der Bounding Box.
- **`getAreaSize` kann negative Komponenten liefern.** Die Größe gibt die Richtung vom Region-Ursprung an. Baritone nimmt deshalb `Math.abs(size)` und verschiebt mit `min(size + 1, 0)` zur Minimum-Ecke.
- **Die Transformationen sind asymmetrisch.** Die Sub-Region-*Position* wird nur mit Mirror/Rotation des Placements transformiert (Z. 105). Die *Größe* bekommt erst die Transformation des Placements und dann die der Sub-Region (Z. 107–108). Ob das korrekt ist, prüft der Unit-Test in P1-02 AK1 und nicht diese Notiz.
  **Geklärt in P1-02:** Litematica 0.28.8 macht es in `SchematicPlacement.getSubRegionBoxes` genauso (per `javap -c` gelesen): `start = transform(regionPos, placement) + origin`, `end = transform(transform(getRelativeEndPositionFromAreaSize(size), placement), subRegion) + start`. `getRelativeEndPositionFromAreaSize` zieht pro Komponente 1 in Richtung 0. `PositionUtils.getTransformedBlockPos` = Baritones `transform`. Ein einmaliger Abgleich per Reflection gegen das echte Jar (alle Mirror × Rotation, Würfel −3…3) ergab 0 Abweichungen zu `compat/PlacementTransform`.
- **Die Schematic-World hat nur Chunks in Spielernähe.** `ChunkManagerSchematic.hasChunk` ist das Vanilla-`ChunkSource.hasChunk`; ist ein Chunk nicht geladen, liefert `getBlockState` Luft. `snapshot()` lässt solche Positionen darum weg.
- **Materialliste:** `materials.MaterialCache` – Override-Liste (Portale/Kolbenkopf → nichts, Farmland → Erde, Wasser/Lava nur Quellblock → Eimer, obere Tür-/Pflanzenhälfte und Bett-Kopf → nichts), sonst Pick-Stack; Anzahl: Doppelstufe 2, Schnee-Schichten, Schildkröteneier, Seegurken, Kerzen, Multiface-Seiten; Blumentopf/Kessel ergeben zwei Items. Nachgebaut in `core/MaterialRules`.
- **Die Reihenfolge in `transform` ist: erst Mirror, dann Rotation.** `LEFT_RIGHT` negiert z, `FRONT_BACK` negiert x. Danach folgt `CLOCKWISE_90: (x,z) → (−z, x)`, `CLOCKWISE_180: → (−x, −z)` und `COUNTERCLOCKWISE_90: → (z, −x)`.
