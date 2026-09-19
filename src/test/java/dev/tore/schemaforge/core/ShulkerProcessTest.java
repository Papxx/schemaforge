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

import dev.tore.schemaforge.core.view.InventoryView;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4-05: place a carried shulker box, take from it, break it again. */
class ShulkerProcessTest {
    private static final BlockPos SPOT = new BlockPos(1, 64, 0);

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theWholeFlowEndsWithTheShulkerBrokenAgain() {
        Fixture f = new Fixture();
        f.actions.contents.put(Items.STONE, 64);

        f.process.start(Items.SHULKER_BOX, Map.of(Items.STONE, 30));
        f.run(200);

        assertEquals(State.order(), f.actions.log, "placed, opened, taken, closed, broken - in that order");
        assertEquals(ShulkerProcess.State.DONE, f.process.state());
        assertEquals(1, f.process.takenCount());
        assertEquals(Map.of(), f.process.missing());
    }

    @Test
    void withoutAFreeSpotItFailsBeforePlacingAnything() {
        Fixture f = new Fixture();
        f.actions.spot = Optional.empty();

        f.process.start(Items.SHULKER_BOX, Map.of(Items.STONE, 30));
        f.run(50);

        assertEquals(ShulkerProcess.State.FAILED, f.process.state());
        assertEquals(List.of(), f.actions.log);
        assertTrue(f.notes.getLast().contains("No free block"), f.notes.toString());
    }

    @Test
    void aShulkerThatNeverAppearsFailsAfterTheTimeout() {
        Fixture f = new Fixture();
        f.actions.placeWorks = false;

        f.process.start(Items.SHULKER_BOX, Map.of(Items.STONE, 30));
        f.run(200);

        assertEquals(ShulkerProcess.State.FAILED, f.process.state());
        assertTrue(f.notes.getLast().contains("did not appear"), f.notes.toString());
    }

    @Test
    void aShulkerThatWillNotBreakIsReported() {
        Fixture f = new Fixture();
        f.actions.contents.put(Items.STONE, 64);
        f.actions.breakWorks = false;

        f.process.start(Items.SHULKER_BOX, Map.of(Items.STONE, 30));
        f.run(400);

        assertEquals(ShulkerProcess.State.FAILED, f.process.state());
        assertTrue(f.notes.getLast().contains("still standing"), f.notes.toString());
    }

    @Test
    void aShulkerWithoutTheWantedItemIsPickedUpAgainAtOnce() {
        Fixture f = new Fixture();
        f.actions.contents.put(Items.DIRT, 64);

        f.process.start(Items.SHULKER_BOX, Map.of(Items.STONE, 30));
        f.run(200);

        assertEquals(ShulkerProcess.State.DONE, f.process.state());
        assertEquals(0, f.process.takenCount(), "nothing to take");
        assertTrue(f.actions.log.contains("break"), "but it is still picked up again");
        assertEquals(Map.of(Items.STONE, 30), f.process.missing());
    }

    @Test
    void cancellingWarnsWhileTheShulkerIsStillStanding() {
        Fixture f = new Fixture();
        f.actions.contents.put(Items.STONE, 64);
        f.process.start(Items.SHULKER_BOX, Map.of(Items.STONE, 30));
        f.tickOnce();

        f.process.cancel();

        assertFalse(f.process.running());
        assertTrue(f.notes.getLast().contains("still standing"), f.notes.toString());
    }

    @Test
    void everyStepGoesThroughTheBudget() {
        Fixture f = new Fixture();
        f.actions.contents.put(Items.STONE, 64);
        f.limit = 0;

        f.process.start(Items.SHULKER_BOX, Map.of(Items.STONE, 30));
        for (int i = 0; i < 20; i++) f.tickOnce();

        assertEquals(List.of(), f.actions.log, "an empty budget sends nothing at all");
        assertEquals(ShulkerProcess.State.PLACING, f.process.state());
    }

    /** The order the actions are expected in; kept here so the test reads like the flow. */
    private static final class State {
        static List<String> order() {
            return List.of("place", "open", "take", "close", "break");
        }
    }

    private static final class Fixture {
        final FakeActions actions = new FakeActions();
        final FakeInv inventory = new FakeInv();
        final List<String> notes = new ArrayList<>();
        final ActionBudget budget;
        final ShulkerProcess process;
        int limit = 4;

        Fixture() {
            actions.inventory = inventory;
            budget = new ActionBudget(() -> limit);
            process = new ShulkerProcess(actions, budget, ShulkerProcess.Config.defaults(), notes::add);
        }

        void tickOnce() {
            budget.resetTick();
            process.tick(inventory);
        }

        void run(int maxTicks) {
            for (int i = 0; i < maxTicks && process.running(); i++) tickOnce();
        }
    }

    /** A shulker that appears where it is placed, hands out its contents and breaks on the first hit. */
    private static final class FakeActions implements ShulkerProcess.Actions {
        FakeInv inventory;
        final Map<Item, Integer> contents = new LinkedHashMap<>();
        final List<String> log = new ArrayList<>();
        Optional<BlockPos> spot = Optional.of(SPOT);
        boolean placeWorks = true;
        boolean breakWorks = true;
        boolean placed;
        boolean broken;
        boolean screenOpen;

        @Override
        public Optional<BlockPos> freeSpot() {
            return spot;
        }

        @Override
        public boolean place(BlockPos pos, Item shulker) {
            log.add("place");
            if (placeWorks) placed = true;
            return placeWorks;
        }

        @Override
        public boolean isShulkerAt(BlockPos pos) {
            return placed && !broken;
        }

        @Override
        public void open(BlockPos pos) {
            log.add("open");
            screenOpen = true;
        }

        @Override
        public void close() {
            log.add("close");
            screenOpen = false;
        }

        @Override
        public boolean screenOpen() {
            return screenOpen;
        }

        @Override
        public Map<Item, Integer> openContents() {
            return screenOpen ? Map.copyOf(contents) : Map.of();
        }

        @Override
        public boolean take(Item item) {
            if (contents.getOrDefault(item, 0) <= 0) return false;
            log.add("take");
            inventory.counts.merge(item, contents.remove(item), Integer::sum);
            return true;
        }

        @Override
        public boolean breakBlock(BlockPos pos) {
            log.add("break");
            if (breakWorks) broken = true;
            return breakWorks;
        }

        @Override
        public boolean isAir(BlockPos pos) {
            return broken;
        }
    }

    private static final class FakeInv implements InventoryView {
        final Map<Item, Integer> counts = new LinkedHashMap<>();

        @Override
        public int count(Item item) {
            return counts.getOrDefault(item, 0);
        }

        @Override
        public OptionalInt hotbarSlotWith(Item item) {
            return OptionalInt.empty();
        }

        @Override
        public int freeSlots() {
            return 10;
        }

        @Override
        public List<ItemStack> shulkersContaining(Item item) {
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
