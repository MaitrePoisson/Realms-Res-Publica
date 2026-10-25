package rrp.core.player;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class PlayerLedgerTest {

    private final UUID id = UUID.randomUUID();

    @Test
    void defaultIsObserver() {
        assertEquals(PlayerRole.OBSERVER, new PlayerLedger().role(id));
    }

    @Test
    void switchHonoursCooldownButObserverIsAlwaysFree() {
        PlayerLedger l = new PlayerLedger();
        assertEquals(PlayerLedger.Switch.OK, l.switchRole(id, PlayerRole.MERCHANT, 0, 100));
        assertEquals(PlayerLedger.Switch.SAME, l.switchRole(id, PlayerRole.MERCHANT, 1, 100));
        assertEquals(PlayerLedger.Switch.COOLDOWN, l.switchRole(id, PlayerRole.SOVEREIGN, 50, 100));
        assertEquals(50, l.cooldownLeft(id, 50, 100), 1e-9);
        assertEquals(PlayerLedger.Switch.OK, l.switchRole(id, PlayerRole.OBSERVER, 51, 100));
        assertEquals(PlayerLedger.Switch.OK, l.switchRole(id, PlayerRole.SOVEREIGN, 200, 100));
    }

    @Test
    void purseRejectsOverdraft() {
        PlayerLedger l = new PlayerLedger();
        l.deposit(id, 10);
        assertFalse(l.withdraw(id, 11));
        assertTrue(l.withdraw(id, 4));
        assertEquals(6, l.purse(id));
        l.deposit(id, -5);
        assertEquals(6, l.purse(id));
    }

    @Test
    void snapshotRestoreRoundTrip() {
        PlayerLedger l = new PlayerLedger();
        l.switchRole(id, PlayerRole.MERCENARY, 10, 0);
        l.deposit(id, 7);
        PlayerLedger b = new PlayerLedger();
        l.snapshot().forEach((k, v) -> b.restore(k, (PlayerRole) v[0], (double) v[1], (long) v[2]));
        assertEquals(PlayerRole.MERCENARY, b.role(id));
        assertEquals(7, b.purse(id));
        assertEquals(PlayerLedger.Switch.COOLDOWN, b.switchRole(id, PlayerRole.MERCHANT, 11, 100));
    }
}
