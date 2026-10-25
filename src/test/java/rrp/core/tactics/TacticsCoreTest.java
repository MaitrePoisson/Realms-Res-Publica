package rrp.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rrp.core.SimConfig;
import rrp.core.SimConfig.Key;
import rrp.core.tactics.Squad.Member;
import rrp.core.tactics.Squad.Role;
import rrp.core.tactics.ThreatRanking.Kind;
import rrp.core.tactics.ThreatRanking.Threat;

class TacticsCoreTest {

    private static final double CREEPER = 16;
    private static final double RANGE = 24;

    @Test
    void defaultsMatchTheRequest() {
        SimConfig c = SimConfig.defaults();
        assertEquals(25, c.get(Key.GUARD_RETREAT_PCT), 1e-9);
        assertEquals(24, c.get(Key.GUARD_RANGED_RANGE), 1e-9);
        assertEquals(3, c.getInt(Key.GUARD_ENCIRCLE_MIN));
    }

    @Test
    void closeCreeperComesFirstThenSkeletonThenMelee() {
        List<Threat> ranked = ThreatRanking.rank(List.of(
                new Threat("zombie", Kind.MELEE, 3),
                new Threat("skeleton", Kind.RANGED, 20),
                new Threat("creeper", Kind.CREEPER, 10)), CREEPER, RANGE);
        assertEquals(List.of("creeper", "skeleton", "zombie"), ranked.stream().map(Threat::id).toList());
    }

    @Test
    void farCreeperDoesNotOutrankASkeleton() {
        Threat top = ThreatRanking.top(List.of(
                new Threat("creeper", Kind.CREEPER, 30),
                new Threat("skeleton", Kind.RANGED, 22)), CREEPER, RANGE);
        assertEquals("skeleton", top.id());
    }

    @Test
    void skeletonOutOfRangeFallsBackToMeleeTierAndNearestWins() {
        List<Threat> ranked = ThreatRanking.rank(List.of(
                new Threat("skeleton", Kind.RANGED, 40),
                new Threat("zombie", Kind.MELEE, 5),
                new Threat("spider", Kind.MELEE, 4)), CREEPER, RANGE);
        assertEquals(List.of("spider", "zombie", "skeleton"), ranked.stream().map(Threat::id).toList());
        assertNull(ThreatRanking.top(List.of(), CREEPER, RANGE));
    }

    @Test
    void retreatTriggersAtTwentyFivePercentAndBelow() {
        assertTrue(Retreat.shouldWithdraw(5, 20, 25));
        assertTrue(Retreat.shouldWithdraw(4, 20, 25));
        assertFalse(Retreat.shouldWithdraw(6, 20, 25));
        assertFalse(Retreat.shouldWithdraw(20, 20, 25));
        assertFalse(Retreat.shouldWithdraw(1, 0, 25));
    }

    @Test
    void withdrawnGuardStaysOutUntilHealed() {
        assertTrue(Retreat.stillWithdrawing(true, 8, 20, 25));
        assertFalse(Retreat.stillWithdrawing(true, 12, 20, 25));
        assertFalse(Retreat.stillWithdrawing(false, 8, 20, 25));
        assertTrue(Retreat.stillWithdrawing(false, 5, 20, 25));
    }

    @Test
    void retreatPointIsBehindEveryComrade() {
        Vec2 enemy = new Vec2(0, 0);
        Vec2 self = new Vec2(0, 5);
        List<Vec2> comrades = List.of(new Vec2(-2, 7), new Vec2(2, 6));
        Vec2 p = Retreat.pointBehindLine(self, enemy, comrades, 3);
        assertEquals(0, p.x(), 1e-9);
        assertEquals(10, p.z(), 1e-9);
        assertTrue(p.dist(enemy) > comrades.get(0).dist(enemy));
        assertTrue(p.dist(enemy) > self.dist(enemy));
    }

    @Test
    void retreatPointWithoutComradesIsBehindSelf() {
        Vec2 p = Retreat.pointBehindLine(new Vec2(4, 0), new Vec2(0, 0), List.of(), 2);
        assertEquals(6, p.x(), 1e-9);
        assertEquals(0, p.z(), 1e-9);
    }

    @Test
    void flankPointIsBehindTheEnemy() {
        Vec2 enemy = new Vec2(10, 0);
        Vec2 p = Flank.point(enemy, new Vec2(5, 1), new Vec2(5, -1), 4);
        assertEquals(14, p.x(), 1e-9);
        assertEquals(0, p.z(), 1e-9);
    }

    @Test
    void flankWaypointKeepsToTheFlankersSide() {
        Vec2 enemy = new Vec2(10, 0);
        Vec2 a = new Vec2(5, 1);
        Vec2 b = new Vec2(5, -1);
        Vec2 north = Flank.waypoint(new Vec2(4, 6), enemy, a, b, 4);
        Vec2 south = Flank.waypoint(new Vec2(4, -6), enemy, a, b, 4);
        assertEquals(10, north.x(), 1e-9);
        assertEquals(-south.z(), north.z(), 1e-9);
        assertTrue(Math.abs(north.z()) == 4);
        assertTrue(north.z() * south.z() < 0);
    }

    @Test
    void squadWithThreeGuardsAgainstAGroupHasTwoFixersAndOneFlanker() {
        Vec2 enemy = new Vec2(0, 0);
        Map<String, Role> roles = Squad.assign(List.of(
                new Member("c", new Vec2(0, 12)),
                new Member("a", new Vec2(0, 4)),
                new Member("b", new Vec2(0, 6))), enemy, 3, 3);
        assertEquals(Role.FIXER, roles.get("a"));
        assertEquals(Role.FIXER, roles.get("b"));
        assertEquals(Role.FLANKER, roles.get("c"));
    }

    @Test
    void noEncirclementWithTooFewHostilesOrGuards() {
        Vec2 enemy = new Vec2(0, 0);
        List<Member> three = List.of(new Member("a", new Vec2(0, 4)), new Member("b", new Vec2(0, 5)),
                new Member("c", new Vec2(0, 6)));
        assertFalse(Squad.assign(three, enemy, 2, 3).containsValue(Role.FLANKER));
        assertFalse(Squad.assign(three.subList(0, 2), enemy, 5, 3).containsValue(Role.FLANKER));
        assertTrue(Squad.assign(List.of(), enemy, 5, 3).isEmpty());
    }

    @Test
    void squadAssignmentIsDeterministicOnTies() {
        Vec2 enemy = new Vec2(0, 0);
        List<Member> ms = List.of(new Member("z", new Vec2(0, 5)), new Member("y", new Vec2(5, 0)),
                new Member("x", new Vec2(-5, 0)), new Member("w", new Vec2(0, -5)));
        Map<String, Role> r1 = Squad.assign(ms, enemy, 4, 3);
        Map<String, Role> r2 = Squad.assign(List.of(ms.get(3), ms.get(1), ms.get(2), ms.get(0)), enemy, 4, 3);
        assertEquals(r1, r2);
        assertEquals(Role.FLANKER, r1.get("y"));
    }
}
