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
package dev.tore.schemaforge.compat;

import dev.tore.schemaforge.core.view.InventoryView;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;
import java.util.OptionalInt;

/** {@link InventoryView} over the local player's main inventory (hotbar + 27 slots, P2-03); client thread only. */
public final class McInventoryView implements InventoryView {
    private final LocalPlayer player;

    public McInventoryView(LocalPlayer player) {
        this.player = player;
    }

    @Override
    public int count(Item item) {
        return player.getInventory().getNonEquipmentItems().stream()
            .filter(stack -> stack.getItem() == item)
            .mapToInt(ItemStack::getCount)
            .sum();
    }

    /** The selected slot if it holds {@code item} (no slot change needed), otherwise the first hotbar slot with it. */
    @Override
    public OptionalInt hotbarSlotWith(Item item) {
        Inventory inventory = player.getInventory();
        if (inventory.getItem(inventory.getSelectedSlot()).getItem() == item) return OptionalInt.of(inventory.getSelectedSlot());
        for (int slot = 0; slot < Inventory.SELECTION_SIZE; slot++) {
            if (inventory.getItem(slot).getItem() == item) return OptionalInt.of(slot);
        }
        return OptionalInt.empty();
    }

    @Override
    public int selectedSlot() {
        return player.getInventory().getSelectedSlot();
    }

    @Override
    public Item itemAt(int slot) {
        return player.getInventory().getItem(slot).getItem();
    }

    @Override
    public int countAt(int slot) {
        return player.getInventory().getItem(slot).getCount();
    }

    @Override
    public int freeSlots() {
        return (int) player.getInventory().getNonEquipmentItems().stream().filter(ItemStack::isEmpty).count();
    }

    @Override
    public List<ItemStack> shulkersContaining(Item item) {
        return player.getInventory().getNonEquipmentItems().stream()
            .filter(stack -> stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock)
            .filter(stack -> stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .nonEmptyItemCopyStream().anyMatch(content -> content.getItem() == item))
            .toList();
    }
}
