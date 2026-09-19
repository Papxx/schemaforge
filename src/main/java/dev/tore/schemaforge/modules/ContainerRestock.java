package dev.tore.schemaforge.modules;

import dev.tore.schemaforge.SchemaForgeAddon;
import dev.tore.schemaforge.compat.McWorldView;
import dev.tore.schemaforge.core.ContainerIndex;
import dev.tore.schemaforge.core.ContainerKey;
import dev.tore.schemaforge.core.ContainerType;
import dev.tore.schemaforge.core.Navigator;
import dev.tore.schemaforge.core.ActionBudget;
import dev.tore.schemaforge.core.RestockProcess;
import dev.tore.schemaforge.core.ScanSession;
import dev.tore.schemaforge.core.ShulkerProcess;
import dev.tore.schemaforge.core.view.InventoryView;
import dev.tore.schemaforge.compat.McInventoryView;
import dev.tore.schemaforge.compat.BaritoneBridge;
import dev.tore.schemaforge.compat.McPlayerView;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

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

    private final Setting<Integer> lookaheadClusters = sgGeneral.add(new IntSetting.Builder()
        .name("lookahead-clusters")
        .description("How many upcoming clusters the restock fetches material for.")
        .defaultValue(3)
        .range(1, 20)
        .sliderRange(1, 10)
        .build());

    private final Setting<Integer> clicksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("clicks-per-tick")
        .description("Container clicks per tick; the same pacing idea as the printer's blocks per tick.")
        .defaultValue(2)
        .range(1, 8)
        .sliderRange(1, 4)
        .build());

    private final Setting<List<Item>> trash = sgGeneral.add(new ItemListSetting.Builder()
        .name("trash")
        .description("Items that may be put back into the container to make room; empty means the restock fails instead.")
        .build());

    private final Setting<Boolean> useInventoryShulkers = sgGeneral.add(new BoolSetting.Builder()
        .name("use-inventory-shulkers")
        .description("Place a carried shulker box to take from it, then break it and pick it up again. Off by default: it looks unusual to anti-cheat.")
        .defaultValue(false)
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
    /** Running {@code .sf scan}, if any (P4-03). */
    private Optional<ScanSession> scan = Optional.empty();
    /** Running restock, if any (P4-04). */
    private Optional<RestockProcess> restock = Optional.empty();
    /** Running shulker flow, if any (P4-05). */
    private Optional<ShulkerProcess> shulker = Optional.empty();
    private final ActionBudget clickBudget = new ActionBudget(clicksPerTick::get);
    /** Containers learned during the running scan, so the session knows when a visit worked. */
    private final Set<BlockPos> learnedByScan = new LinkedHashSet<>();

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
        cancelScan();
        cancelRestock();
        cancelShulker();
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
        clickBudget.resetTick();
        tickScan();
        tickShulker();
        tickRestock();
    }

    public int lookaheadClusters() {
        return lookaheadClusters.get();
    }

    /**
     * Starts fetching {@code demand}; false if a restock or a scan is already going on (P4-04).
     * {@code returnTo} is walked back to once everything is found.
     */
    public boolean startRestock(Map<Item, Integer> demand, BlockPos returnTo, Consumer<String> notes) {
        if (mc.player == null) return false;
        if (restock.map(RestockProcess::running).orElse(false)) return false;
        if (shulker.map(ShulkerProcess::running).orElse(false)) return false;
        if (scan.isPresent()) return false;
        // A shulker in the inventory is quicker than any walk, so it is tried first (P4-05).
        if (startShulker(demand, notes)) return true;
        RestockProcess process = new RestockProcess(index,
            new Navigator(BaritoneBridge.PATHING, System::currentTimeMillis), new RestockActions(),
            clickBudget, new RestockProcess.Config(60, List.copyOf(trash.get())), notes);
        restock = Optional.of(process);
        process.start(demand, returnTo);
        return process.running();
    }

    public Optional<RestockProcess> restock() {
        return restock;
    }

    public Optional<ShulkerProcess> shulkerRun() {
        return shulker;
    }

    public void cancelShulker() {
        shulker.ifPresent(ShulkerProcess::cancel);
        shulker = Optional.empty();
    }

    /** True if a carried shulker box holds something of the demand and the flow was started (P4-05). */
    private boolean startShulker(Map<Item, Integer> demand, Consumer<String> notes) {
        if (!useInventoryShulkers.get() || mc.player == null) return false;
        Optional<Item> box = shulkerWith(demand);
        if (box.isEmpty()) return false;
        ShulkerProcess process = new ShulkerProcess(new ShulkerActions(), clickBudget,
            ShulkerProcess.Config.defaults(), notes);
        shulker = Optional.of(process);
        process.start(box.get(), demand);
        return process.running();
    }

    /** The first shulker box in the inventory that carries one of the wanted items. */
    private Optional<Item> shulkerWith(Map<Item, Integer> demand) {
        McInventoryView inv = new McInventoryView(mc.player);
        for (Item item : demand.keySet()) {
            for (ItemStack stack : inv.shulkersContaining(item)) {
                if (!stack.isEmpty()) return Optional.of(stack.getItem());
            }
        }
        return Optional.empty();
    }

    private void tickShulker() {
        if (shulker.isEmpty() || mc.player == null) return;
        ShulkerProcess process = shulker.get();
        if (!process.running()) {
            shulker = Optional.empty();
            return;
        }
        process.tick(new McInventoryView(mc.player));
    }

    /** The game side of the shulker flow; the only place SchemaForge breaks a block, and only its own (rule 6). */
    private final class ShulkerActions implements ShulkerProcess.Actions {
        @Override
        public Optional<BlockPos> freeSpot() {
            if (mc.player == null || mc.level == null) return Optional.empty();
            BlockPos feet = mc.player.blockPosition();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = feet.relative(side);
                if (!mc.level.getBlockState(candidate).canBeReplaced()) continue;
                if (!mc.level.getBlockState(candidate.below()).isSolidRender()) continue;
                // A shulker needs the space above it to open.
                if (!mc.level.getBlockState(candidate.above()).canBeReplaced()) continue;
                return Optional.of(candidate);
            }
            return Optional.empty();
        }

        @Override
        public boolean place(BlockPos pos, Item shulkerItem) {
            FindItemResult found = InvUtils.find(shulkerItem);
            if (!found.found() || mc.player == null) return false;
            return BlockUtils.place(pos, found, true, 50, true, true, true);
        }

        @Override
        public boolean isShulkerAt(BlockPos pos) {
            return mc.level != null && mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock;
        }

        @Override
        public void open(BlockPos pos) {
            openContainer(pos);
        }

        @Override
        public void close() {
            if (mc.player != null) mc.player.closeContainer();
        }

        @Override
        public boolean screenOpen() {
            return mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu;
        }

        @Override
        public Map<Item, Integer> openContents() {
            return screenOpen() ? contentsOf(mc.player.containerMenu) : Map.of();
        }

        @Override
        public boolean take(Item item) {
            if (!screenOpen() || mc.player == null) return false;
            AbstractContainerMenu menu = mc.player.containerMenu;
            int containerSlots = menu.slots.size() - PLAYER_SLOTS;
            for (int i = 0; i < containerSlots; i++) {
                ItemStack stack = menu.slots.get(i).getItem();
                if (stack.isEmpty() || stack.getItem() != item) continue;
                InvUtils.shiftClick().slotId(i);
                return true;
            }
            return false;
        }

        @Override
        public boolean breakBlock(BlockPos pos) {
            return BlockUtils.breakBlock(pos, true);
        }

        @Override
        public boolean isAir(BlockPos pos) {
            return mc.level != null && mc.level.getBlockState(pos).isAir();
        }
    }

    public void cancelRestock() {
        restock.ifPresent(RestockProcess::cancel);
        restock = Optional.empty();
    }

    private void tickRestock() {
        if (restock.isEmpty() || mc.player == null) return;
        RestockProcess process = restock.get();
        if (!process.running()) {
            saveIndex();
            restock = Optional.empty();
            return;
        }
        process.tick(new McPlayerView(mc.player, 4.5), new McInventoryView(mc.player));
    }

    /** The screen side of a restock: open, quick-move stacks, close. */
    private final class RestockActions implements RestockProcess.Actions {
        @Override
        public void open(BlockPos pos) {
            openContainer(pos);
        }

        @Override
        public void close() {
            if (mc.player != null) mc.player.closeContainer();
        }

        @Override
        public boolean screenOpen() {
            return mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu;
        }

        @Override
        public Map<Item, Integer> openContents() {
            if (!screenOpen()) return Map.of();
            return contentsOf(mc.player.containerMenu);
        }

        /** Shift-click the first matching stack out of the container half of the menu. */
        @Override
        public boolean take(Item item) {
            return quickMove(item, true);
        }

        /** Shift-click the first matching stack out of the player half back into the container. */
        @Override
        public boolean store(Item item) {
            return quickMove(item, false);
        }

        private boolean quickMove(Item item, boolean fromContainer) {
            if (!screenOpen() || mc.player == null) return false;
            AbstractContainerMenu menu = mc.player.containerMenu;
            int containerSlots = menu.slots.size() - PLAYER_SLOTS;
            int first = fromContainer ? 0 : containerSlots;
            int last = fromContainer ? containerSlots : menu.slots.size();
            for (int i = first; i < last; i++) {
                ItemStack stack = menu.slots.get(i).getItem();
                if (stack.isEmpty() || stack.getItem() != item) continue;
                InvUtils.shiftClick().slotId(i);
                return true;
            }
            return false;
        }
    }

    /** One right-click on a container; the position is remembered so the contents can be attributed to it. */
    private void openContainer(BlockPos pos) {
        if (mc.player == null || mc.gameMode == null) return;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        lastInteracted = pos.immutable();
        lastInteractedAt = tick;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /** Containers with a block entity in loaded chunks around the player (P4-03). */
    public List<BlockPos> containersWithin(int radius) {
        List<BlockPos> found = new ArrayList<>();
        if (mc.level == null || mc.player == null) return found;
        BlockPos centre = mc.player.blockPosition();
        McWorldView world = new McWorldView(mc.level);
        int chunkRadius = (radius >> 4) + 1;
        int centreX = SectionPos.blockToSectionCoord(centre.getX());
        int centreZ = SectionPos.blockToSectionCoord(centre.getZ());
        for (int x = centreX - chunkRadius; x <= centreX + chunkRadius; x++) {
            for (int z = centreZ - chunkRadius; z <= centreZ + chunkRadius; z++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, false);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    if (pos.distSqr(centre) > (double) radius * radius) continue;
                    if (world.containerAt(pos).isEmpty()) continue;
                    found.add(canonical(pos));
                }
            }
        }
        return found.stream().distinct().toList();
    }

    /** Starts a scan of the containers in {@code radius}; the number of targets, or empty if one is already running. */
    public Optional<Integer> startScan(int radius, Consumer<String> notes) {
        if (scan.isPresent() && scan.get().state() != ScanSession.State.DONE) return Optional.empty();
        List<BlockPos> targets = ScanSession.route(containersWithin(radius), mc.player.position());
        learnedByScan.clear();
        ScanSession session = new ScanSession(targets, new Navigator(BaritoneBridge.PATHING, System::currentTimeMillis),
            new ScanActions(), ScanSession.Config.defaults(), notes);
        scan = Optional.of(session);
        session.start();
        return Optional.of(targets.size());
    }

    public Optional<ScanSession> scan() {
        return scan;
    }

    public void cancelScan() {
        scan.ifPresent(ScanSession::cancel);
        scan = Optional.empty();
        learnedByScan.clear();
    }

    private void tickScan() {
        if (scan.isEmpty() || mc.player == null) return;
        ScanSession session = scan.get();
        if (session.state() == ScanSession.State.DONE) {
            info("Scan done: %d of %d containers learned%s.", session.learnedCount(), session.total(),
                session.failedContainers().isEmpty() ? "" : ", " + session.failedContainers().size() + " skipped");
            saveIndex();
            scan = Optional.empty();
            learnedByScan.clear();
            return;
        }
        session.tick(new McPlayerView(mc.player, 4.5));
    }

    /** The screen side of a scan; opening is one right-click, closing is the vanilla close. */
    private final class ScanActions implements ScanSession.Actions {
        @Override
        public void open(BlockPos pos) {
            openContainer(pos);
        }

        @Override
        public void close() {
            if (mc.player != null) mc.player.closeContainer();
        }

        @Override
        public boolean screenOpen() {
            return mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu;
        }

        @Override
        public boolean learned(BlockPos pos) {
            return learnedByScan.contains(pos);
        }
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
        learnedByScan.add(pos);
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
