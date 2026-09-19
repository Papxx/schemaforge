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

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2-01 AK1 plus limit changes. */
class ActionBudgetTest {
    @Test
    void thirdConsumeInSameTickFailsUntilReset() {
        ActionBudget budget = new ActionBudget(() -> 2);

        assertTrue(budget.tryConsume());
        assertTrue(budget.tryConsume());
        assertFalse(budget.tryConsume());
        assertFalse(budget.tryConsume());

        budget.resetTick();
        assertTrue(budget.tryConsume());
        assertTrue(budget.tryConsume());
        assertFalse(budget.tryConsume());
    }

    @Test
    void limitIsReadOnEveryCall() {
        AtomicInteger limit = new AtomicInteger(1);
        ActionBudget budget = new ActionBudget(limit::get);

        assertTrue(budget.tryConsume());
        assertFalse(budget.tryConsume());
        limit.set(3);
        assertTrue(budget.tryConsume());
        assertTrue(budget.tryConsume());
        assertFalse(budget.tryConsume());
    }

    @Test
    void zeroOrNegativeLimitAllowsNothing() {
        assertFalse(new ActionBudget(() -> 0).tryConsume());
        assertFalse(new ActionBudget(() -> -5).tryConsume());
    }
}
