package rrp.core.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CostShareTest {

    @Test
    void stepsPayExactlyTheTotalCost() {
        for (int cost : new int[] {0, 1, 7, 30, 80, 500}) {
            for (int steps : new int[] {1, 9, 35, 61}) {
                int paid = 0;
                for (int s = 0; s < steps; s++) {
                    int part = CostShare.ofStep(cost, steps, s);
                    assertTrue(part >= 0 && part <= cost / steps + 1, "uneven share " + part);
                    paid += part;
                }
                assertEquals(cost, paid, "cost " + cost + " over " + steps + " steps");
            }
        }
    }

    @Test
    void dueIsBoundedByTheTotal() {
        assertEquals(30, CostShare.due(30, 35, 99));
        assertEquals(0, CostShare.due(30, 35, 0));
        assertEquals(0, CostShare.due(30, 35, -4));
        assertEquals(12, CostShare.due(12, 0, 3));
    }
}
