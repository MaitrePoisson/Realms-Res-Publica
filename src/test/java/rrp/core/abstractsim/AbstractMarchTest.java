package rrp.core.abstractsim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractMarchTest {

    @Test
    void etaIsDistanceOverSpeed() {
        AbstractMarch m = new AbstractMarch(0, 0, 300, 400, 5, 1);
        assertEquals(500, m.distance(), 1e-9);
        assertEquals(100, m.etaSeconds(), 1e-9);
    }

    @Test
    void roadFactorShortensTheTrip() {
        AbstractMarch open = new AbstractMarch(0, 0, 200, 0, 4, 1);
        AbstractMarch road = new AbstractMarch(0, 0, 200, 0, 4, 2);
        assertEquals(open.etaSeconds() / 2, road.etaSeconds(), 1e-9);
    }

    @Test
    void positionInterpolatesAndArrivalIsReachedOnce() {
        AbstractMarch m = new AbstractMarch(0, 0, 100, 0, 10, 1);
        m.advance(4);
        assertEquals(40, m.x(), 1e-9);
        assertEquals(6, m.remainingSeconds(), 1e-9);
        assertFalse(m.arrived());
        m.advance(100);
        assertTrue(m.arrived());
        assertEquals(100, m.x(), 1e-9);
        assertEquals(1, m.progress(), 1e-9);
    }

    @Test
    void zeroLengthRouteArrivesAtOnce() {
        assertTrue(new AbstractMarch(5, 5, 5, 5, 3, 1).arrived());
    }
}
