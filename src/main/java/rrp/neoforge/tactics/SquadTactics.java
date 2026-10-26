package rrp.neoforge.tactics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import rrp.core.SimConfig;
import rrp.core.tactics.Flank;
import rrp.core.tactics.Squad;
import rrp.core.tactics.Squad.Member;
import rrp.core.tactics.Squad.Role;
import rrp.core.tactics.Vec2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Encirclement (M74). Against a group of hostiles (at least {@code guard_encircle_min}) two guards pin the nearest
 * enemy behind their shields while a third walks round to the flank point and hits it from behind. The pure geometry
 * and the role assignment are in rrp.core.tactics (Squad, Flank); this class drives the guards.
 */
final class SquadTactics {

    /** Longest a pair of fixers waits for the flanker to come round (ticks). */
    static final int PIN_MAX = 200;
    /** A fixer within this many blocks of the enemy stops and holds its shield up while the flanker is on its way. */
    static final double PIN_DISTANCE = 4.5;
    /** The flanker has reached a waypoint when it is this close (blocks). */
    static final double ARRIVED = 1.8;

    /** The squad of a village: the enemy it fixes, the guard that goes round and the two that pin. */
    record Plan(Mob enemy, Villager flanker, Villager fixerA, Villager fixerB) { }

    private final Map<UUID, Integer> stage = new HashMap<>();
    private final Map<String, UUID> flankers = new HashMap<>();
    private final Map<String, Integer> pinned = new HashMap<>();
    private long maneuvers;

    /** Flanking maneuvers started since the start (tests, diagnostic). */
    long maneuvers() {
        return maneuvers;
    }

    /** The flank stage of a guard: 0 going round, 1 to the point behind, 2 in position (attacking). */
    int stageOf(Villager g) {
        return stage.getOrDefault(g.getUUID(), 0);
    }

    void reset() {
        stage.clear();
        flankers.clear();
        pinned.clear();
        maneuvers = 0;
    }

    /** The plan of this village's melee guards for this step, or {@code null} when nothing calls for an encirclement. */
    Plan plan(String siteId, List<GuardAi.Engagement> engaged, SimConfig cfg) {
        List<GuardAi.Engagement> melee = new ArrayList<>();
        int hostiles = 0;
        for (GuardAi.Engagement e : engaged) {
            if (!e.archer()) {
                melee.add(e);
                hostiles = Math.max(hostiles, e.hostiles());
            }
        }
        int min = cfg.getInt(SimConfig.Key.GUARD_ENCIRCLE_MIN);
        if (melee.size() < 3 || hostiles < min) {
            forget(siteId);
            return null;
        }
        double cx = 0;
        double cz = 0;
        for (GuardAi.Engagement e : melee) {
            cx += e.guard().getX() / melee.size();
            cz += e.guard().getZ() / melee.size();
        }
        Mob enemy = null;
        double best = Double.MAX_VALUE;
        for (GuardAi.Engagement e : melee) {
            double d = (e.target().getX() - cx) * (e.target().getX() - cx) + (e.target().getZ() - cz) * (e.target().getZ() - cz);
            if (d < best) {
                best = d;
                enemy = e.target();
            }
        }
        Vec2 enemyPos = new Vec2(enemy.getX(), enemy.getZ());
        Map<String, Villager> byId = new LinkedHashMap<>();
        List<Member> members = new ArrayList<>();
        for (GuardAi.Engagement e : melee) {
            byId.put(e.guard().getUUID().toString(), e.guard());
            members.add(new Member(e.guard().getUUID().toString(), pos(e.guard())));
        }
        Map<String, Role> roles = Squad.assign(members, enemyPos, hostiles, min);
        String flankerId = null;
        for (Map.Entry<String, Role> r : roles.entrySet()) {
            if (r.getValue() == Role.FLANKER) {
                flankerId = r.getKey();
            }
        }
        if (flankerId == null) {
            forget(siteId);
            return null;
        }
        UUID sticky = flankers.get(siteId);
        if (sticky != null && byId.containsKey(sticky.toString())) {
            flankerId = sticky.toString(); // the flanker keeps its job even when its way round brings it close
        }
        Villager flanker = byId.get(flankerId);
        if (!flanker.getUUID().equals(sticky)) {
            flankers.put(siteId, flanker.getUUID());
            stage.put(flanker.getUUID(), 0);
            maneuvers++;
        }
        List<Villager> fixers = new ArrayList<>();
        for (String id : roles.keySet()) {
            if (!id.equals(flankerId)) {
                fixers.add(byId.get(id));
            }
        }
        return new Plan(enemy, flanker, fixers.get(0), fixers.get(1));
    }

    private void forget(String siteId) {
        UUID f = flankers.remove(siteId);
        if (f != null) {
            stage.remove(f);
        }
        pinned.remove(siteId);
    }

    /** True while this fixer must keep its shield up and wait for the flanker rather than rush the enemy. */
    boolean pinning(String siteId, Plan plan, GuardAi.Engagement e, int ticks) {
        if (plan == null || e.guard() == plan.flanker() || stageOf(plan.flanker()) >= 2
                || e.guard().distanceTo(plan.enemy()) > PIN_DISTANCE) {
            return false;
        }
        int waited = pinned.merge(siteId, ticks, Integer::sum);
        return waited <= PIN_MAX;
    }

    /** True while the flanker is still on its way round (it does not strike yet). */
    boolean maneuvering(Plan plan, Villager g) {
        return plan != null && g == plan.flanker() && stageOf(g) < 2;
    }

    /** Moves the flanker one step along its route: round the side of the enemy, then to the point behind it. */
    void flank(ServerLevel level, Plan plan, SimConfig cfg) {
        Villager g = plan.flanker();
        Vec2 self = pos(g);
        Vec2 enemy = new Vec2(plan.enemy().getX(), plan.enemy().getZ());
        Vec2 a = pos(plan.fixerA());
        Vec2 b = pos(plan.fixerB());
        double d = cfg.get(SimConfig.Key.GUARD_FLANK_DISTANCE);
        int st = stageOf(g);
        if (st == 0) {
            Vec2 wp = Flank.waypoint(self, enemy, a, b, d);
            if (self.dist(wp) < ARRIVED) {
                st = 1;
                stage.put(g.getUUID(), 1);
            } else {
                go(level, g, wp);
                return;
            }
        }
        if (st == 1) {
            Vec2 pt = Flank.point(enemy, a, b, d);
            if (self.dist(pt) < ARRIVED) {
                stage.put(g.getUUID(), 2);
            } else {
                go(level, g, pt);
            }
        }
    }

    private static Vec2 pos(Villager v) {
        return new Vec2(v.getX(), v.getZ());
    }

    private static void go(ServerLevel level, Villager g, Vec2 p) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(p.x(), 0, p.z()));
        g.getNavigation().moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0.6);
    }
}
