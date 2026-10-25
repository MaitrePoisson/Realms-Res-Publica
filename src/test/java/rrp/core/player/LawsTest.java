package rrp.core.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LawsTest {

    @Test
    void taxYieldAndClamp() {
        assertEquals(0, Taxation.yield(10, 0, 2));
        assertEquals(6, Taxation.yield(10, 0.3, 2));
        assertEquals(0, Taxation.yield(0, 0.5, 2));
        assertEquals(0.4, Taxation.clamp(0.9, 0.4));
        assertEquals(0, Taxation.clamp(-1, 0.4));
    }

    @Test
    void unrestOnlyAboveTheFreeBurden() {
        assertEquals(0, Taxation.unrest(0.3, 0.5, 20));
        assertEquals(4, Taxation.unrest(0.7, 0.5, 20), 1e-9);
    }

    @Test
    void embargoWorksBothWaysAndToggles() {
        Laws laws = new Laws();
        assertFalse(laws.embargo("a", "b"));
        assertTrue(laws.of("a").toggleEmbargo("b"));
        assertTrue(laws.embargo("a", "b"));
        assertTrue(laws.embargo("b", "a"));
        assertFalse(laws.of("a").toggleEmbargo("b"));
        assertFalse(laws.embargo("a", "b"));
        assertNull(laws.peek("zzz"));
    }

    @Test
    void conscriptionIsCappedAndDefaultsToGlobal() {
        VillageLaws l = new VillageLaws();
        assertEquals(0.25, l.conscriptionOr(0.25, 0.6));
        l.setConscription(0.9);
        assertEquals(0.6, l.conscriptionOr(0.25, 0.6));
        l.setConscription(0.4);
        assertEquals(0.4, l.conscriptionOr(0.25, 0.6));
        l.setTax("FOOD", 0.2);
        l.setTax("ORE", 0.1);
        assertEquals(0.3, l.totalTax(), 1e-9);
        l.setTax("FOOD", 0);
        assertEquals(0.1, l.totalTax(), 1e-9);
    }
}
