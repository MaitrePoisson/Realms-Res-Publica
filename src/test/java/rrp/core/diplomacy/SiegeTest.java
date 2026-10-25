package rrp.core.diplomacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SiegeTest {

    @Test
    void ramComesFirstThenCatapultThenNothing() {
        assertEquals(Siege.Kind.RAM, Siege.next(false, false));
        assertEquals(Siege.Kind.RAM, Siege.next(false, true));
        assertEquals(Siege.Kind.CATAPULT, Siege.next(true, false));
        assertNull(Siege.next(true, true));
    }

    @Test
    void engineIsDestroyedWhenHpRunsOut() {
        var e = new Siege.Engine(Siege.Kind.RAM, 30);
        e.hit(10);
        assertFalse(e.destroyed());
        e.hit(25);
        assertTrue(e.destroyed());
        assertEquals(0, e.hp());
    }

    @Test
    void budgetNeverGoesOverItsCap() {
        var b = new Siege.Budget(5);
        assertEquals(3, b.take(3));
        assertEquals(2, b.take(4));
        assertEquals(0, b.take(1));
        assertEquals(5, b.used());
        assertEquals(0, b.remaining());
    }

    @Test
    void defendersAddUp() {
        assertEquals(30, Siege.defenderDamage(3, 10));
        assertEquals(0, Siege.defenderDamage(-1, 10));
    }
}
