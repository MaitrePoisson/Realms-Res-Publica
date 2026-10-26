package rrp.neoforge.tactics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.tactics.ArcherCycle;
import rrp.core.tactics.GuardPosture;
import rrp.core.tactics.GuardPosture.Posture;
import rrp.core.tactics.PatrolRoute;
import rrp.core.tactics.ThreatRanking;
import rrp.core.tactics.ThreatRanking.Kind;
import rrp.core.tactics.ThreatRanking.Threat;
import rrp.core.tactics.Vec2;
import rrp.neoforge.VillageRegistry;
import rrp.neoforge.VillageSite;
import rrp.neoforge.VillagerAdapter;
import rrp.neoforge.WarehouseService;
import rrp.neoforge.Worlds;
import rrp.neoforge.behaviors.Guard;
import rrp.neoforge.logistics.Armoury;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Real-time combat posture of the village guards (M73). Every few ticks each armed guard looks for hostiles: archers
 * shoot real arrows from up to {@code guard_ranged_range} blocks then reload ducked behind the battlements; melee
 * guards raise their shield and advance together, striking when they reach the enemy. With nothing to fight a guard
 * stands on its tower post (sentinel) or walks the border in pairs (patrol). Guards with no AI (tests, frozen
 * villages) are left to the one-shot strike of the Guard behaviour.
 */
public final class GuardAi {

    /** Ticks between two decisions. */
    static final int STEP = 5;
    /** A step never covers more than this many ticks (accelerated time does not fast-forward a fight). */
    static final int MAX_TICKS = 20;
    /** Melee reach (blocks) of a guard's blow. */
    static final double REACH = 2.6;
    /** Ticks between two blows. */
    static final int STRIKE_COOLDOWN = 20;
    /** A melee guard waits when it is this many blocks nearer to the enemy than its comrades are. */
    static final double HOLD_MARGIN = 3;
    /** Melee detection radius floor (blocks). */
    static final int MELEE_DETECT = 16;

    private static final Map<UUID, Long> SHIELD_UNTIL = new HashMap<>();

    private final Supplier<SimConfig> config;
    private final Supplier<MinecraftServer> server;
    private final VillageRegistry registry;
    private final VillagerAdapter adapter;
    private final Map<UUID, ArcherCycle> cycles = new HashMap<>();
    private final Map<UUID, Long> nextStrike = new HashMap<>();
    private final Map<UUID, Integer> patrolStep = new HashMap<>();
    private final SquadTactics squad = new SquadTactics();
    private final Retreat retreat = new Retreat();
    private long accumulated;
    private long arrows;

    public GuardAi(Supplier<SimConfig> config, Supplier<MinecraftServer> server, VillageRegistry registry,
                   VillagerAdapter adapter) {
        this.config = config;
        this.server = server;
        this.registry = registry;
        this.adapter = adapter;
    }

    /** Arrows loosed by guards since the start (tests, diagnostic). */
    public long arrows() {
        return arrows;
    }

    /** Tactical retreat of the wounded guards (tests, diagnostic). */
    public Retreat retreat() {
        return retreat;
    }

    /** Encirclements started since the start (tests, diagnostic). */
    public long flankManeuvers() {
        return squad.maneuvers();
    }

    /** Flank stage of a guard: 0 going round, 1 to the point behind the enemy, 2 attacking from behind. */
    public int flankStage(Villager v) {
        return squad.stageOf(v);
    }

    /** True while this guard holds its shield up (its front arc blocks projectiles). */
    public static boolean shielded(Villager v) {
        Long until = SHIELD_UNTIL.get(v.getUUID());
        return until != null && until >= v.level().getGameTime();
    }

    /** Raises the shield of a guard for a few steps (the AI renews it while the guard advances). */
    public static void raiseShield(Villager v) {
        SHIELD_UNTIL.put(v.getUUID(), v.level().getGameTime() + STEP * 3L);
        if (!v.getOffhandItem().is(Items.SHIELD)) {
            v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            v.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
        }
    }

    public static void lowerShield(Villager v) {
        SHIELD_UNTIL.remove(v.getUUID());
        if (v.getOffhandItem().is(Items.SHIELD)) {
            v.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    /** Forgets every per-guard state (server stop, tests). */
    public void reset() {
        SHIELD_UNTIL.clear();
        cycles.clear();
        nextStrike.clear();
        patrolStep.clear();
        squad.reset();
        retreat.reset();
        CreeperGuard.reset();
        accumulated = 0;
    }

    /** Appelé à chaque avance du temps. */
    public void onTicks(long ticks) {
        accumulated += ticks;
        if (accumulated < STEP) {
            return;
        }
        int step = (int) Math.min(MAX_TICKS, accumulated);
        accumulated = 0;
        for (VillageSite site : new ArrayList<>(registry.all())) {
            ServerLevel level = Worlds.level(server.get(), site.worldName());
            if (level == null || !WarehouseService.loaded(site, level)) {
                continue;
            }
            runSite(site, level, step);
        }
    }

    /** One decision of a guard, computed before anybody moves so comrades can be compared. */
    record Engagement(Villager guard, Mob target, double dist, Posture posture, boolean archer, int hostiles) { }

    private void runSite(VillageSite site, ServerLevel level, int ticks) {
        SimConfig cfg = config.get();
        List<Villager> guards = new ArrayList<>();
        for (Villager v : adapter.present(site, level)) {
            var st = adapter.stateOf(v);
            if (st != null && st.role() == Role.GUARD && v.isAlive() && !v.isNoAi() && !busy(v) && armed(site, v)) {
                guards.add(v);
            }
        }
        if (guards.isEmpty()) {
            return;
        }
        guards.sort(Comparator.comparing(Villager::getUUID));
        List<Villager> standing = new ArrayList<>();
        List<Villager> withdrawn = new ArrayList<>();
        for (Villager v : guards) {
            (retreat.update(v, cfg) ? withdrawn : standing).add(v);
        }
        for (Villager w : withdrawn) {
            retreat.step(site, level, w, standing, ticks, cfg);
        }
        List<Engagement> engaged = new ArrayList<>();
        List<Villager> free = new ArrayList<>();
        for (Villager v : standing) {
            Engagement e = engagement(site, level, v, cfg);
            if (e == null) {
                free.add(v);
            } else {
                engaged.add(e);
            }
        }
        SquadTactics.Plan plan = squad.plan(site.id(), engaged, cfg);
        for (Engagement e : engaged) {
            act(site, level, e, engaged, plan, ticks, cfg);
        }
        List<Villager> patrol = new ArrayList<>();
        for (Villager v : free) {
            lowerAll(v);
            if (Guard.tower(site, v) == null) {
                patrol.add(v);
            }
        }
        for (int rank = 0; rank < patrol.size(); rank++) {
            walkPatrol(site, level, patrol.get(rank), rank, patrol.size());
        }
    }

    private static boolean busy(Villager v) {
        var logistics = rrp.neoforge.RrpMod.logistics();
        return logistics != null && logistics.busy(v);
    }

    /** Armed (or living in a village with no armoury, which keeps the old unarmed behaviour), or defending itself. */
    private static boolean armed(VillageSite site, Villager v) {
        return Armoury.hasWeapon(v) || !site.hasArmoury() || threatNear(v);
    }

    /** Distance (blocks) within which a hostile makes an unarmed guard defend itself instead of waiting for a weapon. */
    private static final double SELF_DEFENCE = 8;

    /** True when a hostile monster is within {@link #SELF_DEFENCE} blocks of the villager. */
    public static boolean threatNear(Villager v) {
        return !v.level().getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(SELF_DEFENCE),
                e -> e instanceof Enemy && e.isAlive()).isEmpty();
    }

    private static int arrowsOf(Villager v) {
        int n = 0;
        var inv = v.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.ARROW)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void takeArrow(Villager v) {
        var inv = v.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.ARROW)) {
                s.shrink(1);
                if (s.isEmpty()) {
                    inv.setItem(i, ItemStack.EMPTY);
                }
                return;
            }
        }
    }

    private static Kind kindOf(Mob m) {
        if (m instanceof Creeper) {
            return Kind.CREEPER;
        }
        return m instanceof AbstractSkeleton ? Kind.RANGED : Kind.MELEE;
    }

    private Engagement engagement(VillageSite site, ServerLevel level, Villager v, SimConfig cfg) {
        double range = cfg.get(SimConfig.Key.GUARD_RANGED_RANGE);
        boolean archer = v.getMainHandItem().is(Items.BOW) && arrowsOf(v) > 0;
        double detect = archer ? range : Math.max(MELEE_DETECT, cfg.get(SimConfig.Key.WORK_RADIUS));
        String world = Worlds.name(level);
        Map<String, Mob> byId = new HashMap<>();
        List<Threat> threats = new ArrayList<>();
        for (Mob m : level.getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(detect, 16, detect),
                e -> e instanceof Enemy && e.isAlive())) {
            if (site.contains(world, m.getX(), m.getZ()) && v.distanceTo(m) <= detect) {
                byId.put(m.getUUID().toString(), m);
                threats.add(new Threat(m.getUUID().toString(), kindOf(m), v.distanceTo(m)));
            }
        }
        Threat top = ThreatRanking.top(threats, cfg.get(SimConfig.Key.GUARD_CREEPER_ALERT), range);
        if (top == null) {
            return null;
        }
        Mob target = byId.get(top.id());
        Posture p = GuardPosture.choose(archer, Guard.tower(site, v) != null, top.distance(), range);
        return new Engagement(v, target, top.distance(), p, archer, threats.size());
    }

    private void act(VillageSite site, ServerLevel level, Engagement e, List<Engagement> all, SquadTactics.Plan plan,
                     int ticks, SimConfig cfg) {
        Villager g = e.guard();
        Mob target = e.target();
        g.getLookControl().setLookAt(target, 30, 30);
        ArcherCycle cycle = cycles.computeIfAbsent(g.getUUID(),
                k -> new ArcherCycle(cfg.getInt(SimConfig.Key.GUARD_RELOAD_TICKS)));
        if (target instanceof Creeper && e.dist() <= cfg.get(SimConfig.Key.GUARD_CREEPER_CLOSE)) {
            // too close to shoot safely: hold the ground behind the shield (no step back, blast cut, no knockback)
            cycle.advance(ticks, false);
            g.setShiftKeyDown(false);
            raiseShield(g);
            CreeperGuard.hold(g);
            g.getNavigation().stop();
            if (e.dist() <= REACH) {
                strike(site, level, g, target);
            }
            return;
        }
        if (e.posture() == Posture.SHOOT && g.hasLineOfSight(target)) {
            lowerShield(g);
            g.getNavigation().stop();
            int shots = cycle.advance(ticks, true);
            g.setShiftKeyDown(cycle.ducking());
            for (int i = 0; i < shots && arrowsOf(g) > 0; i++) {
                fire(level, g, target);
            }
            return;
        }
        cycle.advance(ticks, false);
        g.setShiftKeyDown(false);
        raiseShield(g);
        if (squad.maneuvering(plan, g)) {
            squad.flank(level, plan, cfg); // round the side, then behind the enemy
            return;
        }
        double dist = e.dist();
        if (plan != null && g == plan.flanker()) {
            target = plan.enemy(); // in position: hit it from behind
            dist = g.distanceTo(target);
        }
        if (!e.archer() && squad.pinning(site.id(), plan, e, ticks)) {
            g.getNavigation().stop(); // fixing the enemy behind the shield while the flanker comes round
            return;
        }
        if (dist <= REACH) {
            g.getNavigation().stop();
            strike(site, level, g, target);
            return;
        }
        if (holdsBack(e, all)) {
            g.getNavigation().stop();
            return;
        }
        g.getNavigation().moveTo(target, 0.55);
    }

    /** The guard that is well ahead of its comrades waits for them so the line advances together. */
    private static boolean holdsBack(Engagement e, List<Engagement> all) {
        double sum = 0;
        int n = 0;
        for (Engagement o : all) {
            if (o != e && o.posture() == Posture.SHIELD_ADVANCE && o.guard().distanceTo(e.guard()) < 16) {
                sum += o.dist();
                n++;
            }
        }
        return n > 0 && e.dist() + HOLD_MARGIN < sum / n;
    }

    private void strike(VillageSite site, ServerLevel level, Villager g, Mob target) {
        long now = level.getGameTime();
        if (nextStrike.getOrDefault(g.getUUID(), 0L) > now) {
            return;
        }
        nextStrike.put(g.getUUID(), now + STRIKE_COOLDOWN);
        g.swing(InteractionHand.MAIN_HAND);
        target.hurt(level.damageSources().mobAttack(g), Guard.STRIKE_DAMAGE);
        if (!target.isAlive()) {
            site.state().setThreats(site.state().threats() - 1);
        }
    }

    private void fire(ServerLevel level, Villager g, Mob target) {
        ItemStack bow = g.getMainHandItem();
        Arrow arrow = new Arrow(level, g, new ItemStack(Items.ARROW), bow);
        double dx = target.getX() - g.getX();
        double dy = target.getY(0.33) - arrow.getY();
        double dz = target.getZ() - g.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.2, dz, 1.6F, 2.0F);
        arrow.setBaseDamage(3);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.addFreshEntity(arrow);
        level.playSound(null, g.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        takeArrow(g);
        arrows++;
    }

    private static void lowerAll(Villager g) {
        lowerShield(g);
        g.setShiftKeyDown(false);
    }

    /** Pair patrol along the border: the guard walks to its waypoint and moves on to the next one when it gets there. */
    private void walkPatrol(VillageSite site, ServerLevel level, Villager g, int rank, int count) {
        List<Vec2> ring = PatrolRoute.ring(site.x(), site.z(), Math.max(8, site.radius() * 0.8));
        int pairs = (count + 1) / 2;
        int step = patrolStep.getOrDefault(g.getUUID(), 0);
        Vec2 wp = PatrolRoute.waypoint(ring, rank, pairs, step);
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(wp.x(), 0, wp.z()));
        double d2 = (g.getX() - wp.x()) * (g.getX() - wp.x()) + (g.getZ() - wp.z()) * (g.getZ() - wp.z());
        if (d2 < 9) {
            patrolStep.put(g.getUUID(), step + 1);
        } else if (g.getNavigation().isDone()) {
            g.getNavigation().moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0.5);
        }
    }
}
