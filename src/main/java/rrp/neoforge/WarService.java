package rrp.neoforge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import rrp.core.CrisisEngine;
import rrp.core.CrisisLevel;
import rrp.core.Faction;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.SimEvent;
import rrp.core.VillageState;
import rrp.core.VillagerState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Guerre civile dans le monde : le cœur ({@link CrisisEngine}) décide quand elle commence et finit ; ce service
 * fait se battre les gardes des deux camps. Chaque seconde de jeu, un garde se rapproche de l'ennemi le plus
 * proche (déplacement déterministe, borné pour rester dans son propre camp) puis le frappe de {@code war_damage}
 * PV s'il est à portée. Les morts sont retirés de l'état aussitôt ; quand un camp n'a plus de garde, ou qu'une
 * trêve est signée, le cœur émet WarEnded : réconciliation (le camp rejoint le village, cause disparue) ou
 * camp autonome (cause toujours là).
 */
public final class WarService {

    /** Un tour de combat par seconde de jeu. */
    static final long WAR_TICKS = 20;
    private static final int MAX_ROUNDS = 20;
    /** Portée de frappe au corps à corps, en blocs. */
    private static final double REACH = 2.5;
    /** Distance parcourue par un garde en un tour. */
    private static final double STEP = 8.0;

    private final Supplier<SimConfig> config;
    private final Supplier<MinecraftServer> server;
    private final VillageRegistry registry;
    private final VillagerAdapter adapter;
    private final CrisisService crises;
    private final Supplier<Lang> lang;
    private final Logger log;
    private long accumulated;
    private long started;
    private long ended;
    private long hits;
    private long deaths;
    private String lastEnd = "";

    public WarService(Supplier<SimConfig> config, Supplier<MinecraftServer> server, VillageRegistry registry,
                      VillagerAdapter adapter, CrisisService crises, Supplier<Lang> lang, Logger log) {
        this.config = config;
        this.server = server;
        this.registry = registry;
        this.adapter = adapter;
        this.crises = crises;
        this.lang = lang;
        this.log = log;
        crises.addListener(this::onEvent);
    }

    public long started() { return started; }
    public long ended() { return ended; }
    public long hits() { return hits; }
    public long deaths() { return deaths; }
    /** Issue de la dernière guerre : {@code reconciliation}, {@code autonomie} ou {@code elimination}. */
    public String lastEnd() { return lastEnd; }

    public void onTicks(long ticks) {
        accumulated += ticks;
        if (accumulated < WAR_TICKS) {
            return;
        }
        int rounds = (int) Math.min(MAX_ROUNDS, accumulated / WAR_TICKS);
        accumulated = 0;
        for (int i = 0; i < rounds; i++) {
            round();
        }
    }

    /** Un tour : élargit l'appartenance des camps en guerre (et la rétablit ailleurs), puis fait combattre. */
    void round() {
        for (VillageSite site : new ArrayList<>(registry.all())) {
            if (site.state().level() != CrisisLevel.GUERRE_CIVILE) {
                site.setWarReach(0);
            }
        }
        for (VillageSite village : new ArrayList<>(registry.all())) {
            if (village.state().faction() != Faction.LOYALIST || village.state().level() != CrisisLevel.GUERRE_CIVILE) {
                continue;
            }
            VillageSite camp = campOf(village);
            ServerLevel world = Worlds.level(server.get(), village.worldName());
            // Un camp ou un village non chargé est gelé, comme partout ailleurs : la guerre reprend au retour d'un joueur.
            if (camp == null || world == null || !WarehouseService.loaded(village, world)
                    || !WarehouseService.loaded(camp, world)) {
                continue;
            }
            double gap = camp.distance(village.worldName(), village.x(), village.z());
            village.setWarReach((int) Math.ceil(gap) + 1);
            fight(world, village, camp);
        }
    }

    private VillageSite campOf(VillageSite village) {
        for (VillageSite s : registry.all()) {
            if (s.state().faction() == Faction.DISSIDENT && village.id().equals(s.state().parentId())
                    && s.state().level() == CrisisLevel.GUERRE_CIVILE) {
                return s;
            }
        }
        return null;
    }

    private static List<Villager> guards(ServerLevel world, VillageSite site) {
        List<Villager> out = new ArrayList<>();
        for (VillagerState s : site.state().villagers()) {
            if (s.role() == Role.GUARD && world.getEntity(s.id()) instanceof Villager v && v.isAlive()) {
                out.add(v);
            }
        }
        return out;
    }

    private void fight(ServerLevel world, VillageSite village, VillageSite camp) {
        float damage = (float) config.get().get(SimConfig.Key.WAR_DAMAGE);
        boolean death = false;
        for (boolean villageSide : new boolean[] {true, false}) {
            VillageSite mine = villageSide ? village : camp;
            VillageSite theirs = villageSide ? camp : village;
            List<Villager> enemies = guards(world, theirs);
            for (Villager g : guards(world, mine)) {
                Villager target = nearest(g, enemies);
                if (target == null) {
                    break;
                }
                if (g.distanceTo(target) > REACH) {
                    approach(world, g, target, camp, villageSide);
                }
                if (g.distanceTo(target) <= REACH) {
                    float before = target.getHealth();
                    target.invulnerableTime = 0; // une frappe par seconde de jeu, même quand le temps est accéléré
                    target.hurt(world.damageSources().mobAttack(g), damage);
                    if (!target.isAlive() || target.getHealth() < before) {
                        hits++;
                    }
                    if (!target.isAlive()) {
                        deaths++;
                        death = true;
                        enemies.remove(target);
                    }
                }
            }
        }
        if (death) { // l'état doit refléter les morts avant que le cœur ne compte les combattants
            adapter.sync(village, world);
            adapter.sync(camp, world);
        }
    }

    private static Villager nearest(Villager from, List<Villager> candidates) {
        Villager best = null;
        double bestD = Double.MAX_VALUE;
        for (Villager c : candidates) {
            if (c.isAlive()) {
                double d = from.distanceToSqr(c);
                if (d < bestD) {
                    best = c;
                    bestD = d;
                }
            }
        }
        return best;
    }

    /**
     * Avance vers la cible de {@code STEP} blocs au plus, sans quitter son camp : un garde du camp reste dans
     * le rayon du campement, un garde du village reste hors de celui-ci (sinon l'état le réattribuerait).
     */
    private void approach(ServerLevel world, Villager g, Villager target, VillageSite camp, boolean villageSide) {
        double dx = target.getX() - g.getX();
        double dz = target.getZ() - g.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist < 1e-6) {
            return;
        }
        double step = Math.min(STEP, Math.max(0, dist - 1.0));
        double nx = g.getX() + dx / dist * step;
        double nz = g.getZ() + dz / dist * step;
        double cx = nx - camp.x();
        double cz = nz - camp.z();
        double fromCamp = Math.hypot(cx, cz);
        double limit = villageSide ? camp.radius() + 0.7 : camp.radius() - 0.7;
        if (fromCamp > 1e-6 && (villageSide ? fromCamp < limit : fromCamp > limit)) {
            nx = camp.x() + cx / fromCamp * limit;
            nz = camp.z() + cz / fromCamp * limit;
        }
        int bx = (int) Math.floor(nx);
        int bz = (int) Math.floor(nz);
        world.getChunk(bx >> 4, bz >> 4);
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        g.teleportTo(nx, y, nz);
    }

    private void onEvent(SimEvent e) {
        if (e instanceof SimEvent.WarStarted w) {
            started++;
            VillageSite v = registry.get(w.villageId());
            VillageSite c = registry.get(w.otherVillageId());
            if (v != null && c != null) {
                crises.tell(v, lang.get().msg("crisis.war_started", v.id(), c.id()));
                crises.tell(c, lang.get().msg("crisis.war_started", v.id(), c.id()));
            }
        } else if (e instanceof SimEvent.WarEnded w) {
            ended++;
            onWarEnded(w);
        }
    }

    private void onWarEnded(SimEvent.WarEnded w) {
        VillageSite village = registry.get(w.villageId());
        VillageSite camp = registry.get(w.otherVillageId());
        if (village != null) {
            village.setWarReach(0);
        }
        if (camp != null) {
            camp.setWarReach(0);
        }
        if (village == null) {
            return;
        }
        if (!w.truce()) {
            lastEnd = "elimination";
            crises.tell(village, lang.get().msg("crisis.war_won", village.id(),
                    camp == null ? 0 : camp.state().population(), village.state().count(Role.GUARD)));
            return;
        }
        if (camp == null) {
            lastEnd = "reconciliation";
            return;
        }
        if (CrisisEngine.indicators(village.state(), config.get()).any()) {
            makeAutonomous(village, camp);
        } else {
            reconcile(village, camp);
        }
    }

    /** La cause de la crise persiste : le camp reste, indépendant, et cesse d'être « enfant » du village. */
    private void makeAutonomous(VillageSite village, VillageSite camp) {
        lastEnd = "autonomie";
        camp.state().setParentId(null);
        camp.state().setLevel(CrisisLevel.STABLE);
        camp.state().setContestTimer(0);
        camp.state().setWarTimer(0);
        registry.markDirty();
        crises.tell(village, lang.get().msg("crisis.truce_autonomy", village.id(), camp.id()));
    }

    /** La cause a disparu : les dissidents rentrent au village, avec une loyauté qui évite un nouvel exil. */
    private void reconcile(VillageSite village, VillageSite camp) {
        lastEnd = "reconciliation";
        ServerLevel world = Worlds.level(server.get(), village.worldName());
        double floor = config.get().get(SimConfig.Key.EXILE_LOYALTY)
                + config.get().get(SimConfig.Key.RECONCILE_LOYALTY_BONUS);
        VillageState vs = village.state();
        for (VillagerState s : new ArrayList<>(camp.state().villagers())) {
            s.setFactionId(village.id());
            s.setLoyalty(Math.max(s.loyalty(), floor));
            vs.villagers().add(s);
            if (world != null && world.getEntity(s.id()) instanceof Villager v) {
                v.teleportTo(village.x() + (vs.villagers().size() % 5) - 2, village.y(), village.z() + 3);
                adapter.write(v, s);
            }
        }
        camp.state().villagers().clear();
        registry.remove(camp.id());
        vs.setLevel(CrisisLevel.CONTESTATION);
        vs.setExileTimer(0);
        vs.setWarTimer(0);
        vs.setContestTimer(config.get().get(SimConfig.Key.CONTEST_AFTER_S));
        registry.markDirty();
        crises.tell(village, lang.get().msg("crisis.truce_reconciled", village.id()));
        log.info("Réconciliation : {} rentre dans {}", camp.id(), village.id());
    }
}
