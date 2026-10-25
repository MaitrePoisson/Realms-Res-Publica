package rrp.core.player;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractsTest {

    private final UUID merc = UUID.randomUUID();

    @Test
    void postRefusesDuplicatesAndEmptyPay() {
        Contracts b = new Contracts();
        assertNotNull(b.post("a", Contract.Type.CLEAR_CAMP, "camp1", 5, 0));
        assertNull(b.post("a", Contract.Type.CLEAR_CAMP, "camp1", 5, 1), "same objective twice");
        assertNull(b.post("a", Contract.Type.WAR_ATTACK, "b", 0, 1), "no pay");
        assertNotNull(b.post("a", Contract.Type.WAR_ATTACK, "b", 3, 1));
        assertEquals(2, b.liveOf("a"));
    }

    @Test
    void acceptRespectsStateAndLimit() {
        Contracts b = new Contracts();
        Contract c1 = b.post("a", Contract.Type.CLEAR_CAMP, "x", 5, 0);
        Contract c2 = b.post("a", Contract.Type.CLEAR_CAMP, "y", 5, 0);
        assertEquals(Contracts.Accept.UNKNOWN, b.accept(99, merc, 0, 1));
        assertEquals(Contracts.Accept.OK, b.accept(c1.id(), merc, 10, 1));
        assertEquals(Contracts.Accept.NOT_OPEN, b.accept(c1.id(), UUID.randomUUID(), 11, 1));
        assertEquals(Contracts.Accept.TOO_MANY, b.accept(c2.id(), merc, 12, 1));
        assertEquals(merc, c1.mercenary());
    }

    @Test
    void completeEarnsReputationFailureCostsIt() {
        Contracts b = new Contracts();
        Contract c1 = b.post("a", Contract.Type.CLEAR_CAMP, "x", 5, 0);
        Contract c2 = b.post("a", Contract.Type.CLEAR_CAMP, "y", 5, 0);
        b.accept(c1.id(), merc, 0, 2);
        b.accept(c2.id(), merc, 0, 2);
        assertTrue(b.complete(c1.id()));
        assertFalse(b.complete(c1.id()), "only once");
        assertEquals(1, b.reputation(merc));
        assertTrue(b.fail(c2.id()));
        assertEquals(0, b.reputation(merc));
        assertTrue(c1.state() == Contract.State.DONE && !c1.state().refundable());
        assertTrue(c2.state().refundable());
    }

    @Test
    void tickExpiresOpenAndFailsLateOnes() {
        Contracts b = new Contracts();
        Contract open = b.post("a", Contract.Type.CLEAR_CAMP, "x", 5, 0);
        Contract taken = b.post("a", Contract.Type.CLEAR_CAMP, "y", 5, 0);
        b.accept(taken.id(), merc, 100, 2);
        assertTrue(b.tick(50, 1000, 500).isEmpty());
        assertEquals(1, b.tick(650, 1000, 500).size());
        assertEquals(Contract.State.FAILED, taken.state());
        assertEquals(Contract.State.OPEN, open.state());
        assertEquals(-1, b.reputation(merc));
        assertEquals(1, b.tick(1000, 1000, 500).size());
        assertEquals(Contract.State.EXPIRED, open.state());
        assertEquals(2, b.unsettled().size());
    }

    @Test
    void abandonReopensWithPenaltyAndCancelRefundsWithoutBlame() {
        Contracts b = new Contracts();
        Contract c = b.post("a", Contract.Type.WAR_DEFEND, "e", 5, 0);
        b.accept(c.id(), merc, 0, 1);
        c.addKill();
        assertFalse(b.abandon(c.id(), UUID.randomUUID()), "only the holder");
        assertTrue(b.abandon(c.id(), merc));
        assertEquals(Contract.State.OPEN, c.state());
        assertEquals(0, c.kills());
        assertEquals(-1, b.reputation(merc));
        assertTrue(b.cancel(c.id()));
        assertEquals(Contract.State.CANCELLED, c.state());
        assertEquals(-1, b.reputation(merc));
        assertFalse(b.cancel(c.id()));
    }

    @Test
    void pruneKeepsUnsettledAndLiveOnes() {
        Contracts b = new Contracts();
        for (int i = 0; i < 4; i++) {
            Contract c = b.post("a", Contract.Type.CLEAR_CAMP, "x" + i, 5, 0);
            if (i < 3) {
                b.cancel(c.id());
            }
            if (i < 2) {
                c.markSettled();
            }
        }
        b.prune(1);
        assertEquals(3, b.all().size(), "one settled kept, one unsettled terminal, one live");
    }
}
