package rrp.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import rrp.core.SimConfig;
import rrp.core.SimConfig.Key;
import rrp.core.tactics.ArcherCycle.Phase;
import rrp.core.tactics.GuardPosture.Posture;

class GuardCombatCoreTest {

    @Test
    void newKeysHaveTheRequestedDefaults() {
        SimConfig c = SimConfig.defaults();
        assertEquals(40, c.getInt(Key.GUARD_RELOAD_TICKS));
        assertEquals(120, c.get(Key.GUARD_SHIELD_ARC), 1e-9);
    }

    @Test
    void shieldBlocksTheFrontArcOnly() {
        // yaw 0 faces +z
        assertTrue(ShieldArc.blocks(0, 0, 10, 120));
        assertTrue(ShieldArc.blocks(0, 5, 10, 120));
        assertFalse(ShieldArc.blocks(0, 10, 0, 120)); // from the side (90 degrees)
        assertFalse(ShieldArc.blocks(0, 0, -10, 120)); // from behind
        // yaw 90 faces -x
        assertTrue(ShieldArc.blocks(90, -10, 0, 120));
        assertFalse(ShieldArc.blocks(90, 10, 0, 120));
    }

    @Test
    void shieldArcEdgesAndDegenerateCases() {
        assertTrue(ShieldArc.blocks(0, Math.sin(Math.toRadians(59)), Math.cos(Math.toRadians(59)), 120));
        assertFalse(ShieldArc.blocks(0, Math.sin(Math.toRadians(61)), Math.cos(Math.toRadians(61)), 120));
        assertFalse(ShieldArc.blocks(0, 0, 0, 120));
        assertTrue(ShieldArc.blocks(0, 5, 0.1, 360)); // an arc above 180 is clamped to 180: the sides are covered
        assertFalse(ShieldArc.blocks(0, 0, -5, 360)); // but never the back
    }

    @Test
    void yawTowardFacesThePoint() {
        double yaw = ShieldArc.yawToward(0, 10);
        assertEquals(0, yaw, 1e-9);
        Vec2 f = ShieldArc.facing(ShieldArc.yawToward(-6, 8));
        assertEquals(-0.6, f.x(), 1e-9);
        assertEquals(0.8, f.z(), 1e-9);
    }

    @Test
    void archerDrawsLoosesThenReloads() {
        ArcherCycle a = new ArcherCycle(40);
        assertEquals(Phase.IDLE, a.phase());
        assertEquals(0, a.advance(10, true));
        assertEquals(Phase.DRAW, a.phase());
        assertEquals(1, a.advance(10, true));
        assertEquals(Phase.RELOAD, a.phase());
        assertTrue(a.ducking());
        assertEquals(0, a.advance(39, true));
        assertTrue(a.ducking());
        assertEquals(0, a.advance(1, true));
        assertEquals(Phase.IDLE, a.phase());
        assertFalse(a.ducking());
    }

    @Test
    void archerNeverShootsDuringTheReload() {
        ArcherCycle a = new ArcherCycle(40);
        int shots = 0;
        for (int t = 0; t < 120; t++) {
            shots += a.advance(1, true);
        }
        assertEquals(2, shots); // one cycle is 20 + 40 ticks
    }

    @Test
    void losingTheTargetAbandonsTheDraw() {
        ArcherCycle a = new ArcherCycle(40);
        a.advance(10, true);
        assertEquals(0, a.advance(5, false));
        assertEquals(Phase.IDLE, a.phase());
        assertEquals(0, a.advance(100, false));
    }

    @Test
    void aLongStepCoversSeveralCycles() {
        assertEquals(3, new ArcherCycle(40).advance(180, true));
        assertThrows(IllegalArgumentException.class, () -> new ArcherCycle(0));
    }

    @Test
    void postureFollowsWeaponRangeAndPost() {
        assertEquals(Posture.SHOOT, GuardPosture.choose(true, false, 20, 24));
        assertEquals(Posture.SHIELD_ADVANCE, GuardPosture.choose(true, false, 30, 24));
        assertEquals(Posture.SHIELD_ADVANCE, GuardPosture.choose(false, true, 5, 24));
        assertEquals(Posture.SENTINEL, GuardPosture.choose(false, true, -1, 24));
        assertEquals(Posture.PATROL, GuardPosture.choose(true, false, -1, 24));
    }

    @Test
    void patrolsAreInPairsAlongTheBorder() {
        assertEquals(0, PatrolRoute.pairOf(1));
        assertEquals(1, PatrolRoute.pairOf(2));
        assertEquals(1, PatrolRoute.partnerOf(0, 3));
        assertEquals(0, PatrolRoute.partnerOf(1, 3));
        assertEquals(-1, PatrolRoute.partnerOf(2, 3)); // odd one out walks alone
        List<Vec2> ring = PatrolRoute.ring(100, 200, 20);
        assertEquals(PatrolRoute.WAYPOINTS, ring.size());
        for (Vec2 p : ring) {
            assertEquals(20, p.dist(new Vec2(100, 200)), 1e-9);
        }
    }

    @Test
    void pairsStartApartAndPartnersFollowOneWaypointBehind() {
        List<Vec2> ring = PatrolRoute.ring(0, 0, 20);
        Vec2 a0 = PatrolRoute.waypoint(ring, 0, 2, 0);
        Vec2 a1 = PatrolRoute.waypoint(ring, 1, 2, 0);
        Vec2 b0 = PatrolRoute.waypoint(ring, 2, 2, 0);
        assertFalse(a0.equals(a1));
        assertFalse(a0.equals(b0));
        assertEquals(a0, PatrolRoute.waypoint(ring, 1, 2, 1)); // the partner reaches the leader's old waypoint
    }
}
