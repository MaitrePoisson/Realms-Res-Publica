package rrp.core.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.SimConfig.Key;
import rrp.core.logistics.ChestTrip.Phase;
import rrp.core.logistics.WaitPolicy.Choice;

class LogisticsCoreTest {

    @Test
    void defaultsAreHalfAStackAndTwoBlocks() {
        SimConfig c = SimConfig.defaults();
        assertEquals(32, c.getInt(Key.CARRY_CAPACITY));
        assertEquals(2.0, c.get(Key.CHEST_REACH), 1e-9);
        assertEquals(20, c.getInt(Key.RUMMAGE_MIN_TICKS));
        assertEquals(40, c.getInt(Key.RUMMAGE_MAX_TICKS));
    }

    @Test
    void carryLoadIsBoundedByCapacity() {
        CarryLoad l = new CarryLoad(32);
        assertEquals(20, l.load(Resource.STONE, 20));
        assertEquals(12, l.load(Resource.WOOD, 50));
        assertTrue(l.isFull());
        assertEquals(0, l.load(Resource.STONE, 1));
        assertEquals(32, l.total());
        assertEquals(5, l.take(Resource.STONE, 5));
        assertEquals(15, l.count(Resource.STONE));
        assertEquals(5, l.free());
        assertEquals(15, l.unload(Resource.STONE));
        assertEquals(12, l.total());
        assertEquals(0, l.take(Resource.ORE, 3));
        assertEquals(0, l.load(Resource.ORE, -4));
        assertFalse(l.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new CarryLoad(0));
    }

    @Test
    void reachRuleIsTwoBlocksInclusive() {
        assertTrue(ChestTrip.inReach(2.0, 2.0));
        assertTrue(ChestTrip.inReach(0.5, 2.0));
        assertFalse(ChestTrip.inReach(2.01, 2.0));
    }

    @Test
    void rummageDurationStaysInTheConfiguredRange() {
        assertEquals(20, ChestTrip.rummageDuration(20, 40, 0.0));
        assertEquals(40, ChestTrip.rummageDuration(20, 40, 0.999999));
        assertEquals(40, ChestTrip.rummageDuration(20, 40, 5.0));
        assertEquals(20, ChestTrip.rummageDuration(20, 40, -1.0));
        assertEquals(30, ChestTrip.rummageDuration(40, 20, 0.5));
        assertEquals(25, ChestTrip.rummageDuration(25, 25, 0.7));
    }

    @Test
    void tripGoesThroughAllPhasesInOrder() {
        ChestTrip t = new ChestTrip(2.0, 3);
        assertEquals(Phase.GO_TO_CHEST, t.phase());
        assertEquals(Phase.GO_TO_CHEST, t.tick(10, true, false));
        assertEquals(Phase.GO_TO_CHEST, t.tick(4, true, false));
        assertEquals(Phase.RUMMAGE, t.tick(1.5, true, false));
        assertEquals(Phase.RUMMAGE, t.tick(1.5, true, false));
        assertEquals(Phase.RUMMAGE, t.tick(1.5, true, false));
        assertFalse(t.takeNow());
        assertEquals(Phase.CARRY, t.tick(1.5, true, false));
        assertTrue(t.takeNow());
        assertEquals(Phase.CARRY, t.tick(8, true, false));
        assertFalse(t.takeNow(), "items are taken on one tick only");
        assertEquals(Phase.WORK, t.tick(20, true, true));
        assertEquals(Phase.WORK, t.tick(20, true, true));
        t.restart();
        assertEquals(Phase.GO_TO_CHEST, t.phase());
        assertEquals(0, t.rummaged());
    }

    @Test
    void rummageIsInterruptedWhenPushedAwayAndResumes() {
        ChestTrip t = new ChestTrip(2.0, 4);
        t.tick(1, true, false);
        t.tick(1, true, false);
        t.tick(1, true, false);
        assertEquals(2, t.rummaged());
        assertEquals(Phase.GO_TO_CHEST, t.tick(5, true, false));
        assertEquals(Phase.RUMMAGE, t.tick(1, true, false));
        assertEquals(Phase.RUMMAGE, t.tick(1, true, false));
        assertEquals(Phase.CARRY, t.tick(1, true, false));
        assertTrue(t.takeNow());
    }

    @Test
    void nothingIsTakenFromAFarChest() {
        ChestTrip t = new ChestTrip(2.0, 2);
        for (int i = 0; i < 100; i++) {
            t.tick(2.5, true, false);
            assertFalse(t.takeNow());
        }
        assertEquals(Phase.GO_TO_CHEST, t.phase());
    }

    @Test
    void emptyChestLeadsToActiveWaitThenRetry() {
        ChestTrip t = new ChestTrip(2.0, 2);
        t.tick(1, false, false);
        t.tick(1, false, false);
        assertEquals(Phase.ACTIVE_WAIT, t.tick(1, false, false));
        assertFalse(t.takeNow());
        for (int i = 0; i < 5; i++) {
            t.tick(30, false, false);
        }
        assertEquals(5, t.waited());
        assertTrue(WaitPolicy.shouldRetry(t.waited(), 5));
        assertFalse(WaitPolicy.shouldRetry(t.waited(), 6));
        t.restart();
        assertEquals(Phase.GO_TO_CHEST, t.phase());
        assertEquals(0, t.waited());
    }

    @Test
    void waitPolicyComplainsOnceThenClearsOrSits() {
        assertEquals(Choice.COMPLAIN, WaitPolicy.choose(false, true, true));
        assertEquals(Choice.CLEAR, WaitPolicy.choose(true, true, true));
        assertEquals(Choice.SIT, WaitPolicy.choose(true, true, false));
        assertEquals(Choice.CLEAR, WaitPolicy.choose(false, false, true));
        assertEquals(Choice.SIT, WaitPolicy.choose(false, false, false));
    }

    @Test
    void invalidTripParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ChestTrip(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new ChestTrip(2, 0));
    }
}
