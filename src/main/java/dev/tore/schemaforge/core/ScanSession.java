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

import dev.tore.schemaforge.core.view.PlayerView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Walks a list of containers, opens each one and lets the passive learning record it (P4-03).
 * Knows nothing about Minecraft screens: opening, closing and "has it been learned" come in through
 * {@link Actions}. One container at a time; the caller ticks it.
 */
public final class ScanSession {
    /** What the scan needs from the game; the module implements it, tests fake it. */
    public interface Actions {
        /** Right-click the container to open its screen. */
        void open(BlockPos pos);

        /** Close whatever screen is open. */
        void close();

        boolean screenOpen();

        /** True once the index holds an entry learned during this visit. */
        boolean learned(BlockPos pos);
    }

    public enum State { IDLE, TRAVELING, OPENING, WAITING, DONE }

    /**
     * @param openIntervalTicks ticks between two open attempts, so opening stays paced like every other packet
     * @param screenTimeoutTicks how long to wait for the contents after opening
     * @param reachDistance      how close the player has to be before trying to open
     */
    public record Config(int openIntervalTicks, int screenTimeoutTicks, double reachDistance) {
        public static Config defaults() {
            return new Config(10, 60, 4.0);
        }
    }

    private final List<BlockPos> targets;
    private final Navigator navigator;
    private final Actions actions;
    private final Config config;
    private final Consumer<String> notes;

    private State state = State.IDLE;
    private int current = -1;
    private int tick;
    private int waitingSince;
    /** One interval in the past, so the first container is opened right away. Integer.MIN_VALUE would overflow. */
    private int lastOpenAt;
    private int learned;
    private final Set<BlockPos> failed = new LinkedHashSet<>();

    public ScanSession(List<BlockPos> targets, Navigator navigator, Actions actions, Config config, Consumer<String> notes) {
        this.targets = List.copyOf(targets);
        this.navigator = navigator;
        this.actions = actions;
        this.config = config;
        this.notes = notes;
        this.lastOpenAt = -Math.max(1, config.openIntervalTicks());
    }

    /**
     * Containers ordered so the walk is short: always the nearest one that is left (P4-03).
     * The same order the WorkPlanner uses for clusters, for the same reason.
     */
    public static List<BlockPos> route(List<BlockPos> containers, Vec3 from) {
        List<BlockPos> left = new ArrayList<>(new LinkedHashSet<>(containers));
        List<BlockPos> ordered = new ArrayList<>(left.size());
        Vec3 at = from;
        while (!left.isEmpty()) {
            BlockPos next = left.getFirst();
            double best = Vec3.atCenterOf(next).distanceToSqr(at);
            for (BlockPos candidate : left) {
                double distance = Vec3.atCenterOf(candidate).distanceToSqr(at);
                if (distance < best) {
                    best = distance;
                    next = candidate;
                }
            }
            left.remove(next);
            ordered.add(next);
            at = Vec3.atCenterOf(next);
        }
        return List.copyOf(ordered);
    }

    public void start() {
        if (state != State.IDLE) return;
        if (targets.isEmpty()) {
            state = State.DONE;
            return;
        }
        nextTarget();
    }

    public void tick(PlayerView player) {
        tick++;
        switch (state) {
            case TRAVELING -> travel(player);
            case OPENING -> openIfDue(player);
            case WAITING -> waitForContents();
            case IDLE, DONE -> {
            }
        }
    }

    /** Stops the scan wherever it is; the pathfinder and any open screen are handed back. */
    public void cancel() {
        navigator.cancel();
        if (actions.screenOpen()) actions.close();
        state = State.DONE;
    }

    public State state() {
        return state;
    }

    /** The container being visited, or empty when the scan is over. */
    public Optional<BlockPos> target() {
        return current >= 0 && current < targets.size() && state != State.DONE
            ? Optional.of(targets.get(current))
            : Optional.empty();
    }

    public int learnedCount() {
        return learned;
    }

    public List<BlockPos> failedContainers() {
        return List.copyOf(failed);
    }

    public int total() {
        return targets.size();
    }

    private void travel(PlayerView player) {
        if (inReach(player)) {
            navigator.cancel();
            state = State.OPENING;
            return;
        }
        switch (navigator.tick(player)) {
            case ARRIVED -> state = State.OPENING;
            case FAILED -> {
                notes.accept(describe("cannot be reached"));
                failed.add(targets.get(current));
                nextTarget();
            }
            case TRAVELING -> {
            }
        }
    }

    /** Opening is paced like any other packet: at most one attempt per interval (rule 7). */
    private void openIfDue(PlayerView player) {
        if (!inReach(player)) {
            // Baritone let go too early, or we were pushed away.
            startTravel();
            return;
        }
        if (tick - lastOpenAt < config.openIntervalTicks()) return;
        lastOpenAt = tick;
        actions.open(targets.get(current));
        waitingSince = tick;
        state = State.WAITING;
    }

    private void waitForContents() {
        BlockPos pos = targets.get(current);
        if (actions.learned(pos)) {
            learned++;
            actions.close();
            nextTarget();
            return;
        }
        if (tick - waitingSince < config.screenTimeoutTicks()) return;
        notes.accept(describe("did not open"));
        failed.add(pos);
        if (actions.screenOpen()) actions.close();
        nextTarget();
    }

    private void nextTarget() {
        current++;
        if (current >= targets.size()) {
            state = State.DONE;
            return;
        }
        startTravel();
    }

    private void startTravel() {
        state = State.TRAVELING;
        if (!navigator.start(targets.get(current))) {
            // No pathfinder, or the target is blacklisted: try it from where we stand.
            state = State.OPENING;
        }
    }

    private boolean inReach(PlayerView player) {
        return player.eyePos().distanceTo(Vec3.atCenterOf(targets.get(current))) <= config.reachDistance();
    }

    private String describe(String what) {
        BlockPos pos = targets.get(current);
        return "Container at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + what + "; skipping it.";
    }
}
