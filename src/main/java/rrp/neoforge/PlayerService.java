package rrp.neoforge;

import rrp.core.player.PlayerLedger;
import rrp.core.player.PlayerRole;

import java.util.UUID;

/** Holder of the player ledger (roles and purses); free of Minecraft types so persistence tests can use it. */
public final class PlayerService {

    private static final PlayerLedger LEDGER = new PlayerLedger();

    private PlayerService() {
    }

    public static PlayerLedger ledger() {
        return LEDGER;
    }

    public static PlayerRole roleOf(UUID id) {
        return LEDGER.role(id);
    }
}
