package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.InventoryView;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Material demand of the current cluster, hotbar choice limited to the allowed slots, and the shortage event that
 * later triggers restocking (P2-05, rules in ARCHITECTURE.md §4). Sends nothing itself; the Printer turns a
 * {@link Selection.Swap} into a packet. Client thread only.
 */
public final class MaterialManager {
    /** Number of player inventory slots (hotbar 0–8, main inventory 9–35). */
    public static final int INVENTORY_SLOTS = 36;
    private static final int HOTBAR_SLOTS = 9;

    /** {@code missing} = demand minus what the inventory holds, at least 1. */
    public record Shortage(Item item, int missing) {
    }

    public sealed interface Selection {
        /** The item is in an allowed hotbar slot. */
        record Ready(int hotbarSlot) implements Selection {
        }

        /** The item has to be swapped from {@code fromSlot} (inventory index) into the allowed {@code toHotbarSlot}. */
        record Swap(int fromSlot, int toHotbarSlot) implements Selection {
        }

        record Missing(Shortage shortage) implements Selection {
        }
    }

    private final Supplier<HotbarSlots> allowed;
    private final Consumer<Shortage> onShortage;
    private Map<Item, Integer> demand = Map.of();
    private final Set<Item> reported = new HashSet<>();

    /** @param allowed read on every call, so a changed setting applies at once */
    public MaterialManager(Supplier<HotbarSlots> allowed, Consumer<Shortage> onShortage) {
        this.allowed = allowed;
        this.onShortage = onShortage;
    }

    /** Computes the demand of the PLACE and FLUID tasks of {@code c} and forgets which shortages were reported. */
    public void startCluster(Cluster c) {
        Map<Item, Integer> totals = new LinkedHashMap<>();
        for (BlockTask task : c.tasks()) {
            if (task.kind() != TaskKind.PLACE && task.kind() != TaskKind.FLUID) continue;
            for (MaterialRules.Requirement r : MaterialRules.required(task.target())) totals.merge(r.item(), r.count(), Integer::sum);
        }
        demand = Collections.unmodifiableMap(totals);
        reported.clear();
    }

    public Map<Item, Integer> demand() {
        return demand;
    }

    /** Every item of the demand the inventory cannot cover, ordered by item id; reports those not yet reported. */
    public List<Shortage> checkShortages(InventoryView inv) {
        List<Shortage> shortages = new ArrayList<>();
        demand.entrySet().stream()
            .sorted(Comparator.comparing(e -> BuiltInRegistries.ITEM.getKey(e.getKey()).toString()))
            .forEach(e -> {
                int have = inv.count(e.getKey());
                if (have < e.getValue()) shortages.add(new Shortage(e.getKey(), e.getValue() - have));
            });
        shortages.forEach(this::report);
        return shortages;
    }

    /** Where to take {@code item} from for the next placement. */
    public Selection select(Item item, InventoryView inv) {
        HotbarSlots slots = allowed.get();
        int selected = inv.selectedSlot();
        if (slots.allows(selected) && holds(inv, selected, item)) return new Selection.Ready(selected);
        for (int slot : slots.indices()) {
            if (holds(inv, slot, item)) return new Selection.Ready(slot);
        }

        int from = -1;
        int biggest = 0;
        for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
            if (slot < HOTBAR_SLOTS && slots.allows(slot)) continue;
            if (inv.itemAt(slot) == item && inv.countAt(slot) > biggest) {
                from = slot;
                biggest = inv.countAt(slot);
            }
        }
        if (from >= 0) return new Selection.Swap(from, swapTarget(slots, inv));

        Shortage shortage = new Shortage(item, Math.max(1, demand.getOrDefault(item, 0) - inv.count(item)));
        report(shortage);
        return new Selection.Missing(shortage);
    }

    /** An empty allowed slot, else one without a demanded item, else the lowest allowed slot. */
    private int swapTarget(HotbarSlots slots, InventoryView inv) {
        for (int slot : slots.indices()) {
            if (isEmpty(inv, slot)) return slot;
        }
        for (int slot : slots.indices()) {
            if (!demand.containsKey(inv.itemAt(slot))) return slot;
        }
        return slots.indices().iterator().next();
    }

    private static boolean holds(InventoryView inv, int slot, Item item) {
        return !isEmpty(inv, slot) && inv.itemAt(slot) == item;
    }

    private static boolean isEmpty(InventoryView inv, int slot) {
        return inv.itemAt(slot) == Items.AIR || inv.countAt(slot) <= 0;
    }

    /** Fires the event at most once per item per cluster visit. */
    private void report(Shortage shortage) {
        if (reported.add(shortage.item())) onShortage.accept(shortage);
    }
}
