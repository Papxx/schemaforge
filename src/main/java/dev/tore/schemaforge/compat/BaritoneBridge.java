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

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalNear;
import dev.tore.schemaforge.SchemaForgeAddon;
import dev.tore.schemaforge.core.AdditiveOnlyGuard;
import dev.tore.schemaforge.core.Navigator;
import net.minecraft.core.BlockPos;

import java.util.function.Consumer;

/**
 * The only class allowed to touch {@code baritone.api.*} (hard rule 1).
 * Every Baritone reference sits in the nested {@link Api}, which is only loaded once a call got past
 * {@link #isPresent()}; without Baritone no method here throws (P3-01 AK2).
 * Shell from P0-04; navigation implemented in P3-01, presence check and break settings in P2-06.
 */
public final class BaritoneBridge {
    private static final String API_CLASS = "baritone.api.BaritoneAPI";

    /** allowBreak and allowBreakAnyway for the AdditiveOnlyGuard (P2-06). Values are passed through as the same objects. */
    public static final AdditiveOnlyGuard.PathfinderSettings BREAK_SETTINGS = new AdditiveOnlyGuard.PathfinderSettings() {
        @Override
        public boolean isPresent() {
            return BaritoneBridge.isPresent();
        }

        @Override
        public AdditiveOnlyGuard.BreakSettings read() {
            Settings settings = BaritoneAPI.getSettings();
            return new AdditiveOnlyGuard.BreakSettings(settings.allowBreak.value, settings.allowBreakAnyway.value);
        }

        @Override
        public void write(AdditiveOnlyGuard.BreakSettings values) {
            Settings settings = BaritoneAPI.getSettings();
            // Boxing true/false yields the cached Boolean instances, which are the defaults' instances as well.
            settings.allowBreak.value = values.allowBreak();
            settings.allowBreakAnyway.value = values.allowBreakAnyway();
        }
    };

    /** Pathing side of Baritone for the {@link Navigator} (P3-02); every method is a no-op without Baritone. */
    public static final Navigator.Pathing PATHING = new Navigator.Pathing() {
        @Override
        public boolean isPresent() {
            return BaritoneBridge.isPresent();
        }

        @Override
        public void gotoNear(BlockPos pos, int radius) {
            BaritoneBridge.gotoNear(pos, radius);
        }

        @Override
        public boolean isPathing() {
            return BaritoneBridge.isPathing();
        }

        @Override
        public void stop() {
            BaritoneBridge.stop();
        }
    };

    /** Result of the one class lookup; Baritone cannot appear or vanish while the game runs. */
    private static Boolean present;

    private BaritoneBridge() {
    }

    /** True if baritone.api can be loaded; checked without initializing any Baritone class (P2-06). */
    public static boolean isPresent() {
        if (present == null) present = SignatureCheck.load(BaritoneBridge.class.getClassLoader(), API_CLASS).isPresent();
        return present;
    }

    /** Walks until {@code pos} is within {@code radius}; does nothing without Baritone (P3-01). */
    public static void gotoNear(BlockPos pos, int radius) {
        if (!isPresent()) return;
        Api.gotoNear(pos, radius);
    }

    /** Walks onto {@code pos} itself; does nothing without Baritone (P3-01). */
    public static void gotoBlock(BlockPos pos) {
        if (!isPresent()) return;
        Api.gotoBlock(pos);
    }

    /** True while Baritone follows a path; false without Baritone (P3-01). */
    public static boolean isPathing() {
        if (!isPresent()) return false;
        return Api.isPathing();
    }

    /** Cancels goal and path; does nothing without Baritone (P3-01). */
    public static void stop() {
        if (!isPresent()) return;
        Api.stop();
    }

    /**
     * Everything that names a Baritone type. Loading this class fails without Baritone, which is why the callers
     * above check {@link #isPresent()} first.
     */
    private static final class Api {
        private Api() {
        }

        static void gotoNear(BlockPos pos, int radius) {
            // Same entry point as Meteor's BaritonePathManager.moveTo: the custom goal process, not the command system.
            withBaritone(b -> b.getCustomGoalProcess().setGoalAndPath(new GoalNear(pos, radius)));
        }

        static void gotoBlock(BlockPos pos) {
            withBaritone(b -> b.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(pos)));
        }

        static boolean isPathing() {
            IBaritone baritone = primary();
            return baritone != null && baritone.getPathingBehavior().isPathing();
        }

        static void stop() {
            withBaritone(b -> {
                b.getCustomGoalProcess().onLostControl();
                b.getPathingBehavior().cancelEverything();
            });
        }

        private static void withBaritone(Consumer<IBaritone> action) {
            IBaritone baritone = primary();
            if (baritone != null) action.accept(baritone);
        }

        /** Null before Baritone has a primary instance (no world joined yet). */
        private static IBaritone primary() {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone == null) SchemaForgeAddon.LOG.warn("Baritone has no primary instance yet; navigation skipped");
            return baritone;
        }
    }
}
