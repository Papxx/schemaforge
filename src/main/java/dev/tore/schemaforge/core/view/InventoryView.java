package dev.tore.schemaforge.core.view;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.OptionalInt;

/** Read-only inventory access for testable core logic. First consumer and test fake: P2-03 (Printer); MaterialManager follows in P2-05. */
public interface InventoryView {
    int count(Item item);

    OptionalInt hotbarSlotWith(Item item);

    int freeSlots();

    List<ItemStack> shulkersContaining(Item item);
}
