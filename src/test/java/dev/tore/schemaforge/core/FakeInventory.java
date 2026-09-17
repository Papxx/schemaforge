package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.InventoryView;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

/** Player inventory for tests: 36 slots (0–8 hotbar) as item + count, selected slot 0 unless set. */
final class FakeInventory implements InventoryView {
    private final Item[] items = new Item[MaterialManager.INVENTORY_SLOTS];
    private final int[] counts = new int[MaterialManager.INVENTORY_SLOTS];
    int selected;

    FakeInventory() {
        clear();
    }

    FakeInventory put(int slot, Item item, int count) {
        items[slot] = item;
        counts[slot] = count;
        return this;
    }

    void clear() {
        Arrays.fill(items, Items.AIR);
        Arrays.fill(counts, 0);
    }

    /** What a SWAP click does: exchange the two stacks. */
    void swap(int a, int b) {
        Item item = items[a];
        int count = counts[a];
        put(a, items[b], counts[b]);
        put(b, item, count);
    }

    @Override
    public int count(Item item) {
        int sum = 0;
        for (int i = 0; i < items.length; i++) if (items[i] == item) sum += counts[i];
        return sum;
    }

    @Override
    public OptionalInt hotbarSlotWith(Item item) {
        for (int i = 0; i < 9; i++) {
            if (items[i] == item && counts[i] > 0) return OptionalInt.of(i);
        }
        return OptionalInt.empty();
    }

    @Override
    public int freeSlots() {
        return (int) Arrays.stream(counts).filter(c -> c == 0).count();
    }

    @Override
    public List<ItemStack> shulkersContaining(Item item) {
        return List.of();
    }

    @Override
    public int selectedSlot() {
        return selected;
    }

    @Override
    public Item itemAt(int slot) {
        return items[slot];
    }

    @Override
    public int countAt(int slot) {
        return counts[slot];
    }
}
