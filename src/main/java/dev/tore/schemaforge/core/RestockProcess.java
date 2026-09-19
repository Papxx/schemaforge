package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.InventoryView;
import dev.tore.schemaforge.core.view.PlayerView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Fetches missing items out of known containers (P4-04, flow in docs/PLAN.md 3.4).
 * Knows nothing about Minecraft screens: opening, taking and the inventory come in through {@link Actions}.
 * Every click goes through the {@link ActionBudget}, so the packet pacing is the same as everywhere else (rule 7).
 */
public final class RestockProcess {
    /** State names are the ones from ARCHITECTURE.md §4. */
    public enum State { IDLE, PICK_SOURCE, TRAVEL, OPEN, WAIT_SCREEN, TAKE, CLOSE, RETURN, FAILED }

    /** What the restock needs from the game. */
    public interface Actions {
        void open(BlockPos pos);

        void close();

        boolean screenOpen();

        /** What the open container really holds; empty while no container screen is open. */
        Map<Item, Integer> openContents();

        /** Move one stack of {@code item} from the open container into the inventory; false = nothing sent. */
        boolean take(Item item);

        /** Put one stack of {@code item} from the inventory into the open container; false = nothing sent. */
        boolean store(Item item);
    }

    /**
     * @param screenTimeoutTicks how long to wait for the contents after opening
     * @param trash              items that may be given away to make room, in that order
     */
    public record Config(int screenTimeoutTicks, List<Item> trash) {
        public static Config defaults() {
            return new Config(60, List.of());
        }
    }

    private final ContainerIndex index;
    private final Navigator navigator;
    private final Actions actions;
    private final ActionBudget budget;
    private final Config config;
    private final Consumer<String> notes;

    private State state = State.IDLE;
    /** What is still missing; shrinks as items arrive. */
    private final Map<Item, Integer> demand = new LinkedHashMap<>();
    /** Containers already visited in this run, so an empty one is not opened again (AK2). */
    private final Set<BlockPos> visited = new LinkedHashSet<>();
    private Optional<BlockPos> source = Optional.empty();
    private Optional<BlockPos> returnTo = Optional.empty();
    private int tick;
    private int waitingSince;
    private int taken;

    public RestockProcess(ContainerIndex index, Navigator navigator, Actions actions, ActionBudget budget,
                          Config config, Consumer<String> notes) {
        this.index = index;
        this.navigator = navigator;
        this.actions = actions;
        this.budget = budget;
        this.config = config;
        this.notes = notes;
    }

    /**
     * Starts fetching {@code demand}; {@code returnTo} is walked back to afterwards.
     * A run that is already going on is not restarted.
     */
    public void start(Map<Item, Integer> demand, BlockPos returnTo) {
        if (running()) return;
        this.demand.clear();
        demand.forEach((item, count) -> {
            if (count > 0) this.demand.put(item, count);
        });
        this.returnTo = Optional.ofNullable(returnTo);
        visited.clear();
        source = Optional.empty();
        taken = 0;
        state = this.demand.isEmpty() ? State.IDLE : State.PICK_SOURCE;
    }

    public void tick(PlayerView player, InventoryView inv) {
        tick++;
        switch (state) {
            case PICK_SOURCE -> pickSource(player, inv);
            case TRAVEL -> travel(player);
            case OPEN -> open(player);
            case WAIT_SCREEN -> waitForScreen(inv);
            case TAKE -> take(inv);
            case CLOSE -> close();
            case RETURN -> goBack(player);
            case IDLE, FAILED -> {
            }
        }
    }

    /** True while the process is still working; IDLE and FAILED mean it is over. */
    public boolean running() {
        return state != State.IDLE && state != State.FAILED;
    }

    public State state() {
        return state;
    }

    /** Items still missing after the run; empty means everything was found. */
    public Map<Item, Integer> missing() {
        return Map.copyOf(demand);
    }

    public int takenCount() {
        return taken;
    }

    /** Stops wherever it is and hands back pathfinder and screen. */
    public void cancel() {
        navigator.cancel();
        if (actions.screenOpen()) actions.close();
        state = State.IDLE;
    }

    /** The nearest container the index says holds something that is still missing. */
    private void pickSource(PlayerView player, InventoryView inv) {
        dropSatisfied(inv);
        if (demand.isEmpty()) {
            state = returnTo.isPresent() ? State.RETURN : State.IDLE;
            return;
        }
        Optional<ContainerIndex.Entry> best = demand.keySet().stream()
            .flatMap(item -> index.sourcesFor(item, player.eyePos()).stream())
            .filter(entry -> !visited.contains(entry.pos()))
            .min(Comparator.comparing(ContainerIndex.Entry::stale)
                .thenComparingDouble(entry -> Vec3.atCenterOf(entry.pos()).distanceToSqr(player.eyePos())));
        if (best.isEmpty()) {
            fail("No container in the index holds " + names(demand.keySet()) + ".");
            return;
        }
        source = Optional.of(best.get().pos());
        state = State.TRAVEL;
        if (!navigator.start(source.get())) state = State.OPEN;
    }

    private void travel(PlayerView player) {
        if (near(player)) {
            navigator.cancel();
            state = State.OPEN;
            return;
        }
        switch (navigator.tick(player)) {
            case ARRIVED -> state = State.OPEN;
            case FAILED -> {
                notes.accept("Cannot reach the container at " + describe(source) + "; trying another one.");
                visited.add(source.orElseThrow());
                state = State.PICK_SOURCE;
            }
            case TRAVELING -> {
            }
        }
    }

    private void open(PlayerView player) {
        if (!near(player)) {
            notes.accept("Too far from the container at " + describe(source) + "; trying another one.");
            visited.add(source.orElseThrow());
            state = State.PICK_SOURCE;
            return;
        }
        if (!budget.tryConsume()) return;
        actions.open(source.orElseThrow());
        waitingSince = tick;
        state = State.WAIT_SCREEN;
    }

    /** Corrects the index with what is really in there, then decides whether it is worth taking from (AK2). */
    private void waitForScreen(InventoryView inv) {
        BlockPos pos = source.orElseThrow();
        Map<Item, Integer> contents = actions.openContents();
        if (!actions.screenOpen() || contents.isEmpty() && tick - waitingSince < config.screenTimeoutTicks()) {
            if (tick - waitingSince < config.screenTimeoutTicks()) return;
            notes.accept("The container at " + describe(source) + " did not open; trying another one.");
            visited.add(pos);
            if (actions.screenOpen()) actions.close();
            state = State.PICK_SOURCE;
            return;
        }

        // The index is data, never an instruction: what the container really holds wins.
        index.learn(pos, index.at(pos).map(ContainerIndex.Entry::type).orElse(ContainerType.CHEST), contents);
        visited.add(pos);
        dropSatisfied(inv);
        state = wanted(contents).isEmpty() ? State.CLOSE : State.TAKE;
    }

    /**
     * Takes one stack per click until the inventory covers the demand or the container has nothing left.
     * How much arrived is read back from the inventory rather than guessed from the stack size: the client
     * updates its inventory as soon as the click is sent, and a guess would be wrong for every non-64 item.
     */
    private void take(InventoryView inv) {
        dropSatisfied(inv);
        List<Item> wanted = wanted(actions.openContents());
        if (wanted.isEmpty()) {
            state = State.CLOSE;
            return;
        }
        if (inv.freeSlots() == 0 && !makeRoom(inv)) return;

        while (!wanted.isEmpty() && budget.tryConsume()) {
            if (!actions.take(wanted.getFirst())) break;
            taken++;
            dropSatisfied(inv);
            wanted = wanted(actions.openContents());
            if (!wanted.isEmpty() && inv.freeSlots() == 0 && !makeRoom(inv)) return;
        }
        if (wanted.isEmpty()) state = State.CLOSE;
    }

    /**
     * Gives one trash item back to the container to free a slot (AK3).
     * False means there is nothing to give away - the run fails rather than dropping anything on the floor.
     */
    private boolean makeRoom(InventoryView inv) {
        for (Item item : config.trash()) {
            if (inv.count(item) <= 0) continue;
            if (!budget.tryConsume()) return false;
            if (actions.store(item)) return true;
        }
        fail("The inventory is full and the trash list is empty; nothing was dropped.");
        if (actions.screenOpen()) actions.close();
        return false;
    }

    private void close() {
        if (actions.screenOpen()) {
            if (!budget.tryConsume()) return;
            actions.close();
        }
        state = demand.isEmpty() && returnTo.isPresent() ? State.RETURN : State.PICK_SOURCE;
    }

    private void goBack(PlayerView player) {
        BlockPos target = returnTo.orElseThrow();
        if (player.eyePos().distanceTo(Vec3.atCenterOf(target)) <= Navigator.ARRIVE_DISTANCE) {
            navigator.cancel();
            state = State.IDLE;
            return;
        }
        if (navigator.target() == null && !navigator.start(target)) {
            state = State.IDLE;
            return;
        }
        switch (navigator.tick(player)) {
            case ARRIVED, FAILED -> state = State.IDLE;
            case TRAVELING -> {
            }
        }
    }

    /** Items of the demand this container can actually serve, biggest shortage first. */
    private List<Item> wanted(Map<Item, Integer> contents) {
        List<Item> items = new ArrayList<>();
        demand.forEach((item, missing) -> {
            if (missing > 0 && contents.getOrDefault(item, 0) > 0) items.add(item);
        });
        items.sort(Comparator.comparingInt((Item item) -> demand.getOrDefault(item, 0)).reversed());
        return items;
    }

    /** Drops what the inventory already covers, so a full stack in hand does not send us to a chest. */
    private void dropSatisfied(InventoryView inv) {
        demand.entrySet().removeIf(entry -> inv.count(entry.getKey()) >= entry.getValue());
    }

    private boolean near(PlayerView player) {
        return source.filter(pos -> player.eyePos().distanceTo(Vec3.atCenterOf(pos)) <= player.reach()).isPresent();
    }

    private void fail(String message) {
        notes.accept(message);
        navigator.cancel();
        state = State.FAILED;
    }

    private static String describe(Optional<BlockPos> pos) {
        return pos.map(p -> p.getX() + " " + p.getY() + " " + p.getZ()).orElse("?");
    }

    private static String names(Set<Item> items) {
        return items.stream().map(item -> BuiltInRegistries.ITEM.getKey(item).getPath()).reduce((a, b) -> a + ", " + b).orElse("anything");
    }
}
