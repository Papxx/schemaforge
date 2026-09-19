package dev.tore.schemaforge.core;

import dev.tore.schemaforge.core.view.InventoryView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Takes items out of a shulker box carried in the inventory (P4-05): place it, open it, take, break it, pick it up.
 * Off unless {@code use-inventory-shulkers} is on, because placing and breaking looks unusual to anti-cheat.
 *
 * <p>This is the one place where SchemaForge breaks a block, and it is always a block it has just placed itself -
 * which is why it is allowed while {@code additiveOnly} is on (rule 6).
 */
public final class ShulkerProcess {
    public enum State { IDLE, PLACING, OPENING, WAIT_SCREEN, TAKE, CLOSE, BREAKING, DONE, FAILED }

    /** What the flow needs from the game. */
    public interface Actions {
        /** A free block next to the player the shulker may go on; empty if there is nowhere to put it. */
        Optional<BlockPos> freeSpot();

        /** Place the shulker from the inventory; false = nothing sent. */
        boolean place(BlockPos pos, Item shulker);

        boolean isShulkerAt(BlockPos pos);

        void open(BlockPos pos);

        void close();

        boolean screenOpen();

        Map<Item, Integer> openContents();

        boolean take(Item item);

        /** One mining step on the block; false = nothing sent. */
        boolean breakBlock(BlockPos pos);

        boolean isAir(BlockPos pos);
    }

    /**
     * @param placeTimeoutTicks how long to wait for the shulker to appear
     * @param screenTimeoutTicks how long to wait for its contents
     * @param breakTimeoutTicks how long to keep mining before giving up
     */
    public record Config(int placeTimeoutTicks, int screenTimeoutTicks, int breakTimeoutTicks) {
        public static Config defaults() {
            return new Config(40, 60, 200);
        }
    }

    private final Actions actions;
    private final ActionBudget budget;
    private final Config config;
    private final Consumer<String> notes;

    private State state = State.IDLE;
    private final Map<Item, Integer> demand = new LinkedHashMap<>();
    private Item shulker;
    private Optional<BlockPos> spot = Optional.empty();
    private int tick;
    private int waitingSince;
    private int taken;

    public ShulkerProcess(Actions actions, ActionBudget budget, Config config, Consumer<String> notes) {
        this.actions = actions;
        this.budget = budget;
        this.config = config;
        this.notes = notes;
    }

    /** Starts the flow for one shulker box; ignored while another one is running. */
    public void start(Item shulker, Map<Item, Integer> demand) {
        if (running()) return;
        this.shulker = shulker;
        this.demand.clear();
        demand.forEach((item, count) -> {
            if (count > 0) this.demand.put(item, count);
        });
        spot = Optional.empty();
        taken = 0;
        state = this.demand.isEmpty() ? State.IDLE : State.PLACING;
    }

    public void tick(InventoryView inv) {
        tick++;
        switch (state) {
            case PLACING -> place();
            case OPENING -> open();
            case WAIT_SCREEN -> waitForScreen(inv);
            case TAKE -> take(inv);
            case CLOSE -> close();
            case BREAKING -> breakIt();
            case IDLE, DONE, FAILED -> {
            }
        }
    }

    public boolean running() {
        return state != State.IDLE && state != State.DONE && state != State.FAILED;
    }

    public State state() {
        return state;
    }

    public int takenCount() {
        return taken;
    }

    /** Items still missing after the run. */
    public Map<Item, Integer> missing() {
        return Map.copyOf(demand);
    }

    /**
     * Stops the flow. A shulker that is already standing is left where it is on purpose:
     * breaking it is what gets it back, and that has to stay a deliberate step, never a cleanup side effect.
     */
    public void cancel() {
        if (actions.screenOpen()) actions.close();
        if (spot.isPresent() && actions.isShulkerAt(spot.get())) {
            notes.accept("The shulker box is still standing; pick it up by hand.");
        }
        state = State.IDLE;
    }

    private void place() {
        if (spot.isEmpty()) {
            spot = actions.freeSpot();
            if (spot.isEmpty()) {
                fail("No free block next to you to put the shulker box on.");
                return;
            }
            waitingSince = tick;
        }
        if (actions.isShulkerAt(spot.get())) {
            state = State.OPENING;
            return;
        }
        if (tick - waitingSince > config.placeTimeoutTicks()) {
            fail("The shulker box did not appear where it was placed.");
            return;
        }
        if (!budget.tryConsume()) return;
        actions.place(spot.get(), shulker);
    }

    private void open() {
        if (!budget.tryConsume()) return;
        actions.open(spot.orElseThrow());
        waitingSince = tick;
        state = State.WAIT_SCREEN;
    }

    private void waitForScreen(InventoryView inv) {
        if (actions.screenOpen() && !actions.openContents().isEmpty()) {
            dropSatisfied(inv);
            state = wanted().isEmpty() ? State.CLOSE : State.TAKE;
            return;
        }
        if (tick - waitingSince < config.screenTimeoutTicks()) return;
        notes.accept("The shulker box did not open; picking it up again.");
        state = State.CLOSE;
    }

    private void take(InventoryView inv) {
        dropSatisfied(inv);
        List<Item> wanted = wanted();
        while (!wanted.isEmpty() && budget.tryConsume()) {
            if (!actions.take(wanted.getFirst())) break;
            taken++;
            dropSatisfied(inv);
            wanted = wanted();
        }
        if (wanted.isEmpty()) state = State.CLOSE;
    }

    private void close() {
        if (actions.screenOpen()) {
            if (!budget.tryConsume()) return;
            actions.close();
        }
        waitingSince = tick;
        state = State.BREAKING;
    }

    /** Mining runs over several ticks; the block turning to air is what ends it. */
    private void breakIt() {
        BlockPos pos = spot.orElseThrow();
        if (actions.isAir(pos)) {
            state = State.DONE;
            return;
        }
        if (tick - waitingSince > config.breakTimeoutTicks()) {
            fail("Could not break the shulker box at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + "; it is still standing.");
            return;
        }
        if (!budget.tryConsume()) return;
        actions.breakBlock(pos);
    }

    /** Items of the demand this shulker can serve. */
    private List<Item> wanted() {
        Map<Item, Integer> contents = actions.openContents();
        return demand.keySet().stream().filter(item -> contents.getOrDefault(item, 0) > 0).toList();
    }

    private void dropSatisfied(InventoryView inv) {
        demand.entrySet().removeIf(entry -> inv.count(entry.getKey()) >= entry.getValue());
    }

    private void fail(String message) {
        notes.accept(message);
        if (actions.screenOpen()) actions.close();
        state = State.FAILED;
    }
}
