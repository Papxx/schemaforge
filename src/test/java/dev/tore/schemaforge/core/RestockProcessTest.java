/*
 * This file is part of SchemaForge (https://github.com/Papxx/schemaforge).
 * Copyright (C) 2026 Papxx
 *
 * SchemaForge is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, version 3 of the License.
 *
 * SchemaForge is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SchemaForge.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.tore.schemaforge.core;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4-04: fetching items out of known containers - source choice, index correction, full inventory, budget. */
class RestockProcessTest {
    private static final BlockPos NEAR = new BlockPos(2, 64, 0);
    private static final BlockPos FAR = new BlockPos(6, 64, 0);

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void itWalksToTheNearestSourceAndTakesWhatIsMissing() {
        Fixture f = new Fixture();
        f.chest(FAR, Map.of(Items.STONE, 64));
        f.chest(NEAR, Map.of(Items.STONE, 64));

        f.process.start(Map.of(Items.STONE, 30), null);
        f.run(200);

        assertEquals(List.of(NEAR), f.actions.opened, "the nearest container is enough");
        assertEquals(1, f.process.takenCount());
        assertEquals(Map.of(), f.process.missing());
        assertEquals(RestockProcess.State.IDLE, f.process.state());
        assertEquals(1, f.actions.closed, "the screen is closed again");
    }

    @Test
    void anEmptyChestIsCorrectedInTheIndexAndTheNextSourceIsTried() {
        Fixture f = new Fixture();
        // The index still claims stone, but the chest is empty by now (AK2).
        f.chest(NEAR, Map.of(Items.STONE, 64));
        f.chest(FAR, Map.of(Items.STONE, 64));
        f.actions.contents.put(NEAR, new LinkedHashMap<>());

        f.process.start(Map.of(Items.STONE, 10), null);
        f.run(300);

        assertEquals(List.of(NEAR, FAR), f.actions.opened, "empty one first, then the next source");
        assertEquals(Map.of(), f.index.at(NEAR).orElseThrow().items(), "the index was corrected");
        assertEquals(Map.of(), f.process.missing());
        assertEquals(1, f.actions.taken.size());
    }

    @Test
    void aContainerIsNeverOpenedTwiceInOneRun() {
        Fixture f = new Fixture();
        f.chest(NEAR, Map.of(Items.STONE, 64));
        f.actions.contents.put(NEAR, new LinkedHashMap<>());

        f.process.start(Map.of(Items.STONE, 10), null);
        f.run(300);

        assertEquals(List.of(NEAR), f.actions.opened, "no endless opening of the same empty chest");
        assertEquals(RestockProcess.State.FAILED, f.process.state());
        assertTrue(f.notes.stream().anyMatch(note -> note.contains("No container in the index")), f.notes.toString());
    }

    @Test
    void withoutAnySourceItFailsWithAMessage() {
        Fixture f = new Fixture();

        f.process.start(Map.of(Items.STONE, 10), null);
        f.run(50);

        assertEquals(RestockProcess.State.FAILED, f.process.state());
        assertEquals(List.of(), f.actions.opened);
        assertTrue(f.notes.getLast().contains("stone"), f.notes.toString());
    }

    @Test
    void aFullInventoryGivesTrashBackBeforeTaking() {
        Fixture f = new Fixture(new RestockProcess.Config(60, List.of(Items.COBBLESTONE)));
        f.chest(NEAR, Map.of(Items.STONE, 64));
        f.inventory.free = 0;
        f.inventory.counts.put(Items.COBBLESTONE, 64);

        f.process.start(Map.of(Items.STONE, 10), null);
        f.run(200);

        assertEquals(List.of(Items.COBBLESTONE), f.actions.stored, "trash goes back into the chest, not on the floor");
        assertEquals(List.of(Items.STONE), f.actions.taken);
    }

    @Test
    void aFullInventoryWithoutTrashFailsAndDropsNothing() {
        Fixture f = new Fixture();
        f.chest(NEAR, Map.of(Items.STONE, 64));
        f.inventory.free = 0;

        f.process.start(Map.of(Items.STONE, 10), null);
        f.run(200);

        assertEquals(RestockProcess.State.FAILED, f.process.state());
        assertEquals(List.of(), f.actions.taken, "nothing taken");
        assertEquals(List.of(), f.actions.stored, "and nothing given away either (AK3)");
        assertTrue(f.notes.stream().anyMatch(note -> note.contains("trash list is empty")), f.notes.toString());
    }

    @Test
    void whatTheInventoryAlreadyHoldsIsNotFetched() {
        Fixture f = new Fixture();
        f.chest(NEAR, Map.of(Items.STONE, 64));
        f.inventory.counts.put(Items.STONE, 64);

        f.process.start(Map.of(Items.STONE, 30), null);
        f.run(100);

        assertEquals(List.of(), f.actions.opened, "no walk for something we already have");
        assertEquals(RestockProcess.State.IDLE, f.process.state());
    }

    @Test
    void clicksGoThroughTheBudget() {
        Fixture f = new Fixture();
        f.chest(NEAR, Map.of(Items.STONE, 64, Items.OAK_PLANKS, 64));

        f.process.start(Map.of(Items.STONE, 64, Items.OAK_PLANKS, 64), null);
        // One action per tick: opening alone uses the first tick's budget.
        f.limit = 1;
        f.run(3);

        assertTrue(f.actions.opened.size() + f.actions.taken.size() <= 3,
            "at most one click per tick: " + f.actions.opened + " " + f.actions.taken);
    }

    @Test
    void cancellingHandsBackPathAndScreen() {
        Fixture f = new Fixture();
        f.chest(NEAR, Map.of(Items.STONE, 64));
        f.process.start(Map.of(Items.STONE, 10), null);
        f.tickOnce();
        f.actions.screenOpen = true;

        f.process.cancel();

        assertEquals(RestockProcess.State.IDLE, f.process.state());
        assertEquals(1, f.actions.closed);
        assertFalse(f.process.running());
    }

    private static final class Fixture {
        final AtomicLong clock = new AtomicLong(1_000_000);
        final NavigatorTest.FakePathing pathing = new NavigatorTest.FakePathing();
        final NavigatorTest.MutablePlayer player = new NavigatorTest.MutablePlayer(new Vec3(0, 64.5, 0));
        final ContainerIndex index = new ContainerIndex(() -> 1L);
        final FakeActions actions = new FakeActions();
        final FakeInv inventory = new FakeInv();
        final List<String> notes = new ArrayList<>();
        final ActionBudget budget;
        final RestockProcess process;
        int limit = 8;

        Fixture() {
            this(RestockProcess.Config.defaults());
        }

        Fixture(RestockProcess.Config config) {
            actions.inventory = inventory;
            budget = new ActionBudget(() -> limit);
            process = new RestockProcess(index, new Navigator(pathing, clock::get), actions, budget, config, notes::add);
        }

        void chest(BlockPos pos, Map<Item, Integer> items) {
            index.learn(pos, ContainerType.CHEST, items);
            actions.contents.put(pos, new LinkedHashMap<>(items));
        }

        void tickOnce() {
            budget.resetTick();
            process.tick(player, inventory);
        }

        /** Ticks until the process is over; the player follows wherever it wants to go. */
        void run(int maxTicks) {
            for (int i = 0; i < maxTicks && process.running(); i++) {
                clock.addAndGet(100);
                actions.currentTarget().ifPresent(pos -> player.eyePos = Vec3.atCenterOf(pos).add(0, 0.5, 0));
                tickOnce();
            }
        }
    }

    /** Containers with real contents; taking moves one stack into the fake inventory. */
    private static final class FakeActions implements RestockProcess.Actions {
        FakeInv inventory;
        final Map<BlockPos, Map<Item, Integer>> contents = new LinkedHashMap<>();
        final List<BlockPos> opened = new ArrayList<>();
        final List<Item> taken = new ArrayList<>();
        final List<Item> stored = new ArrayList<>();
        BlockPos open;
        boolean screenOpen;
        int closed;

        java.util.Optional<BlockPos> currentTarget() {
            return java.util.Optional.ofNullable(open);
        }

        @Override
        public void open(BlockPos pos) {
            opened.add(pos);
            open = pos;
            screenOpen = true;
        }

        @Override
        public void close() {
            closed++;
            screenOpen = false;
            open = null;
        }

        @Override
        public boolean screenOpen() {
            return screenOpen;
        }

        @Override
        public Map<Item, Integer> openContents() {
            return open == null ? Map.of() : Map.copyOf(contents.getOrDefault(open, Map.of()));
        }

        /** Like the client: the item lands in the inventory the moment the click goes out. */
        @Override
        public boolean take(Item item) {
            Map<Item, Integer> inside = contents.get(open);
            if (inside == null || inside.getOrDefault(item, 0) <= 0) return false;
            taken.add(item);
            int moved = inside.remove(item);
            inventory.counts.merge(item, moved, Integer::sum);
            return true;
        }

        @Override
        public boolean store(Item item) {
            stored.add(item);
            return true;
        }
    }

    /** Only the parts the restock uses; the rest is never called. */
    private static final class FakeInv implements dev.tore.schemaforge.core.view.InventoryView {
        final Map<Item, Integer> counts = new LinkedHashMap<>();
        int free = 10;

        @Override
        public int count(Item item) {
            return counts.getOrDefault(item, 0);
        }

        @Override
        public java.util.OptionalInt hotbarSlotWith(Item item) {
            return java.util.OptionalInt.empty();
        }

        @Override
        public int freeSlots() {
            return free;
        }

        @Override
        public List<net.minecraft.world.item.ItemStack> shulkersContaining(Item item) {
            return List.of();
        }

        @Override
        public int selectedSlot() {
            return 0;
        }

        @Override
        public Item itemAt(int slot) {
            return Items.AIR;
        }

        @Override
        public int countAt(int slot) {
            return 0;
        }
    }
}
