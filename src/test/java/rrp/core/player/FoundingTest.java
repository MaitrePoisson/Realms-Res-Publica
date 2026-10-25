package rrp.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import rrp.core.player.Founding.Verdict;

class FoundingTest {

    @Test
    void checksInOrder() {
        assertEquals(Verdict.NOT_SOVEREIGN, Founding.check(PlayerRole.MERCHANT, 99, 5, 500, 96, 0, 1));
        assertEquals(Verdict.TOO_CLOSE, Founding.check(PlayerRole.SOVEREIGN, 99, 5, 50, 96, 0, 1));
        assertEquals(Verdict.TOO_MANY, Founding.check(PlayerRole.SOVEREIGN, 99, 5, 500, 96, 1, 1));
        assertEquals(Verdict.TOO_POOR, Founding.check(PlayerRole.SOVEREIGN, 4, 5, 500, 96, 0, 1));
        assertEquals(Verdict.OK, Founding.check(PlayerRole.SOVEREIGN, 5, 5, Double.MAX_VALUE, 96, 0, 1));
    }
}
