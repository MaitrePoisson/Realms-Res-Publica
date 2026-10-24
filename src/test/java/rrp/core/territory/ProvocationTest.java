package rrp.core.territory;

import org.junit.jupiter.api.Test;
import rrp.core.territory.Provocation.Stage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProvocationTest {

    @Test
    void escalatesThroughWarnExpelWarOnce() {
        Provocation p = new Provocation(3, 6, 10, 0.1);
        assertEquals(Stage.NONE, p.add("a", "b", 2));
        assertEquals(Stage.WARN, p.add("a", "b", 1));
        assertEquals(Stage.NONE, p.add("a", "b", 1));
        assertEquals(Stage.EXPEL, p.add("a", "b", 2));
        assertEquals(Stage.WAR, p.add("a", "b", 4));
        assertEquals(Stage.NONE, p.add("a", "b", 4));
        assertEquals(Stage.WAR, p.stage("a", "b"));
    }

    @Test
    void pairsAreIndependentAndDirected() {
        Provocation p = new Provocation(3, 6, 10, 0.1);
        p.add("a", "b", 5);
        assertEquals(0, p.score("b", "a"));
        assertEquals(0, p.score("a", "c"));
        assertEquals(Stage.WARN, p.stage("a", "b"));
    }

    @Test
    void decayLowersScoreAndAllowsReescalation() {
        Provocation p = new Provocation(3, 6, 10, 0.5);
        p.add("a", "b", 4);
        p.decay(4);
        assertEquals(2.0, p.score("a", "b"), 1e-9);
        assertEquals(Stage.NONE, p.stage("a", "b"));
        assertEquals(Stage.WARN, p.add("a", "b", 1));
        p.decay(1000);
        assertEquals(0, p.score("a", "b"));
    }

    @Test
    void resetClearsPair() {
        Provocation p = new Provocation(1, 2, 3, 0);
        p.add("a", "b", 5);
        p.reset("a", "b");
        assertEquals(Stage.NONE, p.stage("a", "b"));
        assertEquals(0, p.score("a", "b"));
    }
}
