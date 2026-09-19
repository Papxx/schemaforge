package dev.tore.schemaforge.modules;

import dev.tore.schemaforge.SchemaForgeAddon;
import dev.tore.schemaforge.compat.McWorldView;
import dev.tore.schemaforge.core.ContainerIndex;
import dev.tore.schemaforge.core.ContainerKey;
import dev.tore.schemaforge.core.ContainerType;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Container index for the restock system (P4-02): every container opened by hand is learned, so the index
 * fills up during normal play without a scan run. Taking items out is P4-04.
 * The index is data, never an instruction: contents are checked again before anything is taken.
 */
public final class ContainerRestock extends Module {
    /** The player inventory is always the last 36 slots of a container menu; everything before it is the container. */
    private static final int PLAYER_SLOTS = 36;

    /** How long a right-click stays the explanation for a container that opens afterwards. */
    private static final int INTERACT_MEMORY_TICKS = 40;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> learnPassively = sgGeneral.add(new BoolSetting.Builder()
        .name("learn-passively")
        .description("Remember the contents of every container you open by hand.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> staleAfterHours = sgGeneral.add(new IntSetting.Builder()
        .name("stale-after-hours")
        .description("Entries older than this are used last and scanned again first.")
        .defaultValue(24)
        .range(1, 720)
        .sliderRange(1, 168)
        .build());

    private ContainerIndex index = new ContainerIndex();
    /** Where the index is loaded from and saved to; empty until a world is joined. */
    private Optional<Path> indexFile = Optional.empty();
    private BlockPos lastInteracted;
    private int lastInteractedAt;
    private int tick;

    public ContainerRestock() {
        super(SchemaForgeAddon.CATEGORY, "container-restock", "Learns container contents and restocks the printer from them.");
    }

    @Override
    public void onActivate() {
        loadIndex();
    }

    @Override
    public void onDeactivate() {
        saveIndex();
        lastInteracted = null;
    }

    /** The learned containers; also used by {@code .sf containers} and the restock process (P4-04). */
    public ContainerIndex index() {
        return index;
    }

    public Optional<Path> indexFile() {
        return indexFile;
    }

    /** Writes the index if a world was joined; safe to call at any time. */
    public void saveIndex() {
        indexFile.ifPresent(index::save);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tick++;
        // The file name needs the world, which is only known once one is joined.
        if (indexFile.isEmpty()) loadIndex();
    }

    /** Remembers what was right-clicked, because the container screen itself does not say where it stands. */
    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (event.result == null) return;
        lastInteracted = event.result.getBlockPos().immutable();
        lastInteractedAt = tick;
    }

    /** Fires with the contents of a container the server sent; the screen is open at this point. */
    @EventHandler
    private void onInventory(InventoryEvent event) {
        if (!learnPassively.get() || mc.player == null || mc.level == null) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || event.packet.containerId() != menu.containerId) return;
        if (menu == mc.player.inventoryMenu) return;

        Optional<BlockPos> target = containerPosition();
        if (target.isEmpty()) return;
        Optional<ContainerType> type = typeAt(target.get());
        if (type.isEmpty()) return;

        BlockPos pos = type.get() == ContainerType.ENDER_CHEST ? ContainerKey.ENDER_CHEST : canonical(target.get());
        index.learn(pos, type.get(), contentsOf(menu));
    }

    /** Counts the container half of the menu; the player's own inventory at the end is left out. */
    private Map<Item, Integer> contentsOf(AbstractContainerMenu menu) {
        Map<Item, Integer> items = new LinkedHashMap<>();
        int containerSlots = menu.slots.size() - PLAYER_SLOTS;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            items.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return items;
    }

    /** The block the open screen belongs to: the last right-click, as long as it was recent. */
    private Optional<BlockPos> containerPosition() {
        if (lastInteracted == null || tick - lastInteractedAt > INTERACT_MEMORY_TICKS) return Optional.empty();
        return Optional.of(lastInteracted);
    }

    /** Empty if the block is not a container we know; both halves of a double chest map to the same entry. */
    private Optional<ContainerType> typeAt(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.getBlock() instanceof EnderChestBlock) return Optional.of(ContainerType.ENDER_CHEST);
        return new McWorldView(mc.level).containerAt(pos);
    }

    /** The half of a double chest both sides agree on (AK2), or the position itself. */
    private BlockPos canonical(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return pos.immutable();
        }
        return ContainerKey.canonical(pos, Optional.of(ChestBlock.getConnectedBlockPos(pos, state)));
    }

    /** Loads the index for the world that is joined right now; keeps the current one if there is no world yet. */
    private void loadIndex() {
        if (mc.level == null) return;
        String server = ContainerKey.serverHash(Optional.ofNullable(mc.getCurrentServer()).map(data -> data.ip));
        String dimension = mc.level.dimension().identifier().getPath();
        Path file = MeteorClient.FOLDER.toPath().resolve("schemaforge")
            .resolve(ContainerKey.fileName(server, dimension));
        if (indexFile.map(file::equals).orElse(false)) return;
        indexFile = Optional.of(file);
        index = ContainerIndex.load(file);
        index.markStale(Duration.ofHours(staleAfterHours.get()));
        SchemaForgeAddon.LOG.info("Container index {}: {} entries", file.getFileName(), index.size());
    }
}
