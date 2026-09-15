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
