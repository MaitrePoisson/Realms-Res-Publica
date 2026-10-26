package rrp.core.tactics;

import org.junit.jupiter.api.Test;
import rrp.core.SimConfig;
import rrp.core.tactics.CivilianCycle.Phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivilianCycleTest {

    @Test
    void releaseDefaultIsFiveSeconds() {
        assertEquals(100, SimConfig.defaults().get(SimConfig.Key.CIVILIAN_RELEASE_TICKS), 1e-9);
    }

    @Test
    void calmCivilianStaysCalmWithoutDanger() {
        var c = new CivilianCycle(5, 100);
        for (int i = 0; i < 20; i++) {
            assertEquals(Phase.CALM, c.tick(false, false));
        }
        assertFalse(c.sheltered());
    }

    @Test
    void dangerAlertsThenShelterThenWaitsInside() {
        var c = new CivilianCycle(5, 100);
        assertEquals(Phase.ALERT, c.tick(true, false));
        assertTrue(c.sheltered());
        assertEquals(Phase.SHELTER, c.tick(true, false));
        assertEquals(Phase.SHELTER, c.tick(true, false));
        assertEquals(Phase.WAIT_SECURED, c.tick(true, true));
        assertTrue(c.sheltered());
    }

    @Test
    void alertAtTheRefugeSkipsTheWalk() {
        var c = new CivilianCycle(5, 100);
        c.tick(true, true);
        assertEquals(Phase.WAIT_SECURED, c.tick(true, true));
    }

    @Test
    void notReleasedWhileTheThreatRemains() {
        var c = new CivilianCycle(5, 100);
        c.tick(true, false);
        c.tick(true, true);
        c.tick(true, true);
        for (int i = 0; i < 50; i++) {
            assertEquals(Phase.WAIT_SECURED, c.tick(true, true));
        }
    }

    @Test
    void releasedOnlyAfterTheThreatIsGoneForReleaseTicksInARow() {
        var c = new CivilianCycle(5, 100);
        c.tick(true, false);
        c.tick(false, true);                       // ALERT -> WAIT_SECURED, nothing counted yet
        for (int i = 0; i < 4; i++) {
            assertEquals(Phase.WAIT_SECURED, c.tick(false, true));
        }
        assertEquals(Phase.CALM, c.tick(false, true));
        assertTrue(c.released());
        assertFalse(c.sheltered());
        c.tick(false, true);
        assertFalse(c.released());
    }

    @Test
    void aReturningThreatRestartsTheCount() {
        var c = new CivilianCycle(5, 100);
        c.tick(true, true);
        c.tick(false, true);
        for (int i = 0; i < 3; i++) {
            c.tick(false, true);
        }
        assertEquals(Phase.WAIT_SECURED, c.tick(true, true));
        assertEquals(0, c.clearFor());
        for (int i = 0; i < 4; i++) {
            assertEquals(Phase.WAIT_SECURED, c.tick(false, true));
        }
        assertEquals(Phase.CALM, c.tick(false, true));
    }

    @Test
    void anUnreachableRefugeEndsInHidingWhereItStands() {
        var c = new CivilianCycle(5, 10);
        c.tick(true, false);
        Phase p;
        for (int i = 0; i < 10; i++) {
            p = c.tick(true, false);
            assertEquals(Phase.SHELTER, p);
        }
        assertEquals(Phase.WAIT_SECURED, c.tick(true, false));
    }

    @Test
    void threatClearedOnTheWayHomeReleasesWithoutArriving() {
        var c = new CivilianCycle(5, 100);
        c.tick(true, false);
        c.tick(true, false);
        for (int i = 0; i < 4; i++) {
            assertEquals(Phase.SHELTER, c.tick(false, false));
        }
        assertEquals(Phase.CALM, c.tick(false, false));
        assertTrue(c.released());
    }

    @Test
    void resetReturnsToWork() {
        var c = new CivilianCycle(5, 100);
        c.tick(true, false);
        c.reset();
        assertEquals(Phase.CALM, c.phase());
    }

    @Test
    void invalidParametersAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new CivilianCycle(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new CivilianCycle(5, 0));
    }
}
