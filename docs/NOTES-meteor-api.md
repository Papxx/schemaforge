# Notiz: So platziert/rotiert/klickt Meteor in 26.2

Quelle: `refs/meteor-client` @ `3128d5d` (2026-09-11, `master`, `libs.versions.toml`: minecraft 26.2, baritone 26.1-SNAPSHOT).
Gelesen (P0-02): `utils/world/BlockUtils.java`, `utils/player/Rotations.java`, `utils/player/InvUtils.java`, `utils/player/FindItemResult.java`.
Alle Namen Mojmap. Paket-Präfix `meteordevelopment.meteorclient.` unten weggelassen.

1. **Platzieren: `BlockUtils.place(...)` klickt in die Luft, wenn es keinen Nachbarn gibt.**
   Die volle Überladung ist `place(BlockPos, FindItemResult, boolean rotate, int rotationPriority, boolean swingHand, boolean checkEntities, boolean swapBack)`. Sie delegiert an `place(BlockPos, InteractionHand, int slot, …)` und akzeptiert nur Hotbar-Slots 0–8 oder die Offhand.
   Vorab prüft `canPlaceBlock` drei Dinge: `Level.isInSpawnableBounds(pos)`, `level.getBlockState(pos).canBeReplaced()` und `level.isUnobstructed(state, pos, CollisionContext.empty())`.
   Die Klickseite wählt `getPlaceSide(pos)`. Von den 6 Nachbarn kommen nur die in Frage, die nicht Luft, kein Fluid und nicht `isClickable` sind. Davon gewinnt der Nachbar mit der größten Blickvektor-Komponente in Seitenrichtung, also der Block *hinter* dem Ziel.
   Geklickt wird mit `new BlockHitResult(hitPos, side.getOpposite(), neighbour, false)`. `hitPos` ist die Mitte der Nachbarfläche (`Vec3.atCenterOf(pos) + side·0.5`).
   **Gibt es keinen Nachbarn, wird `side = UP, neighbour = pos` gesetzt, also Airplace.** Das verstößt gegen `clickAdjacentOnly`. Für SchemaForge heißt das: nicht direkt übernehmen, sondern im `PlacementSolver` `NeedsSupport` liefern.

2. **Interagieren: `BlockUtils.interact(BlockHitResult, InteractionHand, boolean swing)`.**
   Die Methode schaltet Sneak vorübergehend aus (`setShiftKeyDown(false)`) und ruft `mc.gameMode.useItemOn(mc.player, hand, bhr)` auf. Bei `InteractionResult.consumesAction()` folgt `mc.player.swing(hand)`, bei `swing=false` stattdessen ein `ServerboundSwingPacket`. Danach wird der alte Sneak-Zustand wiederhergestellt.
   Sneak-Placement an klickbaren Blöcken gibt es damit nicht. Stattdessen schließt `isClickable` solche Blöcke als Klickziel aus: `BaseEntityBlock` (Kisten usw.), Door, TrapDoor, FenceGate, Button, BasePressurePlate, Bed, NoteBlock, Crafting Table, Anvil, Loom, Cartography Table, Grindstone, Stonecutter.
   Eine Platzierung erzeugt mindestens einen Use-Paket und einen Swing-Paket. Hinzu kommen eventuell Slot-Sync (Punkt 4) und Rotation (Punkt 3).

3. **Rotieren: `Rotations.rotate(double yaw, double pitch, int priority, boolean clientSide, Runnable callback)` läuft asynchron über eine Queue.**
   Rotationen landen in einer nach `priority` sortierten Liste und werden nicht sofort ausgeführt.
   Die erste Rotation eines Ticks wird in `events.entity.player.SendMovementPacketsEvent.Pre` in das normale Bewegungspaket eingesetzt. Ihr Callback läuft in `.Post`.
   **Jede weitere Rotation im selben Tick schickt ein eigenes `ServerboundMovePlayerPacket.Rot`** und führt dann ihren Callback aus. n Platzierungen mit Rotation pro Tick bedeuten also n−1 Zusatzpakete, die das `ActionBudget` mitzählen muss.
   Die letzte Rotation bleibt `Config.get().rotationHoldTicks` Ticks lang aktiv.
   `BlockUtils.place(rotate=true)` führt Swap und Interact im Callback aus, also **später** und nicht innerhalb des `place`-Aufrufs. Der Rückgabewert `true` bedeutet nur „eingeplant“.
   Winkel liefern `Rotations.getYaw(Vec3)` und `getPitch(Vec3)`. Yaw wird ab der XZ-Position des Spielers gerechnet, Pitch ab Augenhöhe (`getY() + getEyeHeight(getPose())`).

4. **Items finden und Slot wechseln: `InvUtils` und `FindItemResult`.**
   `InvUtils.findInHotbar(Item...)` bzw. `(Predicate<ItemStack>)` sucht zuerst in der Offhand, dann in der Main-Hand, dann in den Slots 0–8. `InvUtils.find(...)` durchsucht das ganze Inventar.
   Das Ergebnis ist `record FindItemResult(int slot, int count)` mit `found()` (`slot != -1`), `isHotbar()`, `isOffhand()`, `isMainHand()` und `getHand()`. `count` ist die Summe über alle passenden Slots, `slot` der erste Treffer.
   `InvUtils.swap(int slot, boolean swapBack)` setzt `getInventory().setSelectedSlot(slot)` und ruft `((mixininterface.IMultiPlayerGameMode) mc.gameMode).meteor$syncSelected()` auf. Das ist per Mixin das Vanilla-`MultiPlayerGameMode.ensureHasSentCarriedItem()` und synchronisiert den Slot mit dem Server.
   `swapBack()` kehrt zu `previousSlot` zurück. **`previousSlot` ist globaler statischer Zustand**, den sich alle Meteor-Module teilen.
   Der Offhand-Slot hat die Konstante `SlotUtils.OFFHAND`. Für die Umrechnung zwischen Inventar-Index und Container-Slot-ID gibt es `SlotUtils.indexToId`.

5. **Container-Klicks: `InvUtils.click()/move()/quickSwap()/shiftClick()/drop()/dropOne()` ohne eingebautes Pacing.**
   Die Aufrufe liefern eine Fluent-`Action`. `.from*(…)` legt die Quelle fest, `.to*(…)` bzw. `.slot*(…)` das Ziel und lösen den Klick aus.
   Jeder Klick wird zu `mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slotId, data, ContainerInput, mc.player)`.
   **In 26.2 heißt der Klicktyp `net.minecraft.world.inventory.ContainerInput`** (`PICKUP`, `SWAP`, `QUICK_MOVE`, `THROW`).
   `move()` erzeugt zwei `PICKUP`-Klicks und einen dritten Rückklick, falls der Cursor danach nicht leer ist. Das sind bis zu 3 Pakete pro Aufruf.
   Pausen zwischen Klicks setzt `InvUtils` nicht, das muss der Aufrufer tun.
   Beispiel Auto-Steal (eigene Klasse `AutoSteal` gibt es nicht mehr, es ist eine Setting-Gruppe in `systems/modules/misc/InventoryTweaks`):
   - **Auslöser** ist `events.packets.InventoryEvent`, das `ClientboundContainerSetContentPacket` kapselt. Geprüft wird, ob `packet.containerId() == mc.player.containerMenu.containerId` ist und `containerMenu.getType()` in der `MenuType`-Liste `stealScreens` steht.
   - **Die Klicks** macht `moveSlots` auf dem **`MeteorExecutor`-Thread** und wartet dazwischen per `Thread.sleep(autoStealDelay / autoStealInitDelay / autoStealRandomDelay)` in Millisekunden. Das ist nicht tickbasiert und darf **nicht so übernommen werden**, weil Regel 7 verlangt, dass Pakete über `ActionBudget` im Tick laufen.
   Den Tick-Hook für P2-01 liefert `events.world.TickEvent.Pre` bzw. `.Post`, abonniert mit `@EventHandler` aus `meteordevelopment.orbit`.

## Was SchemaForge davon übernommen hat (Stand P2-02)

- **P2-01:** `SchemaPrinter.onTickPre(TickEvent.Pre)` mit `@EventHandler` setzt das `ActionBudget` zurück. Meteor abonniert die Handler erst, wenn das Modul aktiv ist.
- **P1-05:** `.sf preview` zählt Inventar-Items mit `InvUtils.find(item).count()` und nutzt dafür die ganze Inventarschleife aus Punkt 4.
- **P2-02, bewusst anders als Punkt 1:** Airplace gibt es nur mit `clickAdjacentOnly=false` und nur als Ausweichlösung; mit `true` liefert der Solver `NeedsSupport`.
- **P2-02, bewusst anders als Punkt 2:** Klickbare Nachbarn werden nicht ausgeschlossen. `PlacementPlan.sneak` ist stattdessen immer `true`. Der Printer (P2-03) darf deshalb nicht `BlockUtils.interact` verwenden, weil das Sneak ausschaltet. Er muss Sneak während des Klicks halten.
