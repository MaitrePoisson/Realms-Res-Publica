package rrp.neoforge.urban;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefensePlanTest {

    private static final DefensePlan.Rules RULES = new DefensePlan.Rules(12, 3, 30, 80, 14);

    @Test
    void startsOnPopulationOrThreats() {
        assertFalse(DefensePlan.shouldStart(11, 2, RULES));
        assertTrue(DefensePlan.shouldStart(12, 0, RULES));
        assertTrue(DefensePlan.shouldStart(3, 3, RULES));
    }

    @Test
    void ringIsClosedAndWithoutDuplicates() {
        int r = 9;
        Set<Long> seen = new HashSet<>();
        int[] prev = DefensePlan.ringAt(100, -50, r, DefensePlan.ringLength(r) - 1);
        for (int i = 0; i < DefensePlan.ringLength(r); i++) {
            int[] c = DefensePlan.ringAt(100, -50, r, i);
            assertEquals(r, Math.max(Math.abs(c[0] - 100), Math.abs(c[1] + 50)));
            assertTrue(seen.add(((long) c[0] << 32) ^ (c[1] & 0xffffffffL)), "duplicate column");
            assertEquals(1, Math.abs(c[0] - prev[0]) + Math.abs(c[1] - prev[1]), "ring must be 4-connected");
            prev = c;
        }
        assertEquals(DefensePlan.ringLength(r), seen.size());
    }

    @Test
    void gatesOpenAtRoadCrossingsNeverOnCorners() {
        List<int[]> roads = List.of(new int[] {0, 0, 0, -1, 0, -2, 0, -3, 0, -4, 0, -5, 0, -6, 0, -7, 0, -8, 0, -9, 0, -10});
        assertTrue(DefensePlan.isGate(roads, 0, 0, 9, 0, -9));
        assertTrue(DefensePlan.isGate(roads, 0, 0, 9, 1, -9));
        assertFalse(DefensePlan.isGate(roads, 0, 0, 9, 3, -9));
        assertFalse(DefensePlan.isGate(List.of(new int[] {9, -9}), 0, 0, 9, 9, -9));
    }

    @Test
    void upgradeNeedsFinishedPalisadeStoneAndPopulation() {
        Defense d = new Defense();
        d.start(5);
        assertFalse(DefensePlan.shouldUpgrade(d, 500, 50, RULES));
        d.advanceLaid(d.length());
        assertTrue(d.palisadeDone());
        assertFalse(DefensePlan.shouldUpgrade(d, 79, 50, RULES));
        assertFalse(DefensePlan.shouldUpgrade(d, 80, 13, RULES));
        assertTrue(DefensePlan.shouldUpgrade(d, 80, 14, RULES));
        d.startUpgrade();
        assertFalse(DefensePlan.shouldUpgrade(d, 500, 50, RULES));
    }

    @Test
    void towersAreSpacedOnTheBorders() {
        assertEquals(4, DefensePlan.towerTarget(12));
        assertEquals(8, DefensePlan.towerTarget(24));
        assertArrayEquals(new int[] {25, 25}, DefensePlan.towerSite(0, 0, 30, 0));
        Set<String> sites = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            int[] s = DefensePlan.towerSite(0, 0, 30, i);
            assertTrue(sites.add(s[0] + "," + s[1]));
        }
    }
}
