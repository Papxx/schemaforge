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
package dev.tore.schemaforge.core.view;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.OptionalInt;

/** Read-only inventory access for testable core logic. Consumers: Printer (P2-03), MaterialManager (P2-05). */
public interface InventoryView {
    int count(Item item);

    OptionalInt hotbarSlotWith(Item item);

    int freeSlots();

    List<ItemStack> shulkersContaining(Item item);

    /** Selected hotbar slot, 0–8 (P2-05). */
    int selectedSlot();

    /** Item at inventory index 0–35 (0–8 hotbar), {@code Items.AIR} if the slot is empty (P2-05). */
    Item itemAt(int slot);

    /**
     * Stack size at inventory index 0–35, 0 if empty (P2-05). Item and count instead of an {@code ItemStack}, because
     * stacks cannot be created in unit tests (item components are only bound with loaded data).
     */
    int countAt(int slot);
}
