package rrp.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
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
import java.util.logging.Logger;

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
    private final VillageRegistry registry;
    private final VillagerAdapter adapter;
    private final CrisisService crises;
    private final Lang lang;
    private final Logger log;
    private long accumulated;
    private long started;
    private long ended;
    private long hits;
    private long deaths;
    private String lastEnd = "";

    public WarService(Supplier<SimConfig> config, VillageRegistry registry, VillagerAdapter adapter,
                      CrisisService crises, Lang lang, Logger log) {
        this.config = config;
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
            World world = Bukkit.getWorld(village.worldName());
            if (camp == null || world == null) {
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

    private static List<Villager> guards(VillageSite site) {
        List<Villager> out = new ArrayList<>();
        for (VillagerState s : site.state().villagers()) {
            if (s.role() == Role.GUARD && Bukkit.getEntity(s.id()) instanceof Villager v && v.isValid() && !v.isDead()) {
                out.add(v);
            }
        }
        return out;
    }

    private void fight(World world, VillageSite village, VillageSite camp) {
        double damage = config.get().get(SimConfig.Key.WAR_DAMAGE);
        boolean death = false;
        for (boolean villageSide : new boolean[] {true, false}) {
            VillageSite mine = villageSide ? village : camp;
            VillageSite theirs = villageSide ? camp : village;
            List<Villager> enemies = guards(theirs);
            for (Villager g : guards(mine)) {
                Villager target = nearest(g, enemies);
                if (target == null) {
                    break;
                }
                if (g.getLocation().distance(target.getLocation()) > REACH) {
                    approach(world, g, target, camp, villageSide);
                }
                if (g.getLocation().distance(target.getLocation()) <= REACH) {
                    double before = target.getHealth();
                    target.setNoDamageTicks(0); // une frappe par seconde de jeu, même quand le temps est accéléré
                    target.damage(damage, g);
                    if (target.isDead() || target.getHealth() < before) {
                        hits++;
                    }
                    if (target.isDead() || !target.isValid()) {
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
            if (c.isValid() && !c.isDead()) {
                double d = from.getLocation().distanceSquared(c.getLocation());
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
    private void approach(World world, Villager g, Villager target, VillageSite camp, boolean villageSide) {
        Location from = g.getLocation();
        Location to = target.getLocation();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist < 1e-6) {
            return;
        }
        double step = Math.min(STEP, Math.max(0, dist - 1.0));
        double nx = from.getX() + dx / dist * step;
        double nz = from.getZ() + dz / dist * step;
        double cx = nx - camp.x();
        double cz = nz - camp.z();
        double fromCamp = Math.hypot(cx, cz);
        double limit = villageSide ? camp.radius() + 0.7 : camp.radius() - 0.7;
        if (fromCamp > 1e-6 && (villageSide ? fromCamp < limit : fromCamp > limit)) {
            nx = camp.x() + cx / fromCamp * limit;
            nz = camp.z() + cz / fromCamp * limit;
        }
        int y = world.getHighestBlockYAt((int) Math.floor(nx), (int) Math.floor(nz)) + 1;
        g.teleport(new Location(world, nx, y, nz, from.getYaw(), from.getPitch()));
    }

    private void onEvent(SimEvent e) {
        if (e instanceof SimEvent.WarStarted w) {
            started++;
            VillageSite v = registry.get(w.villageId());
            VillageSite c = registry.get(w.otherVillageId());
            if (v != null && c != null) {
                crises.tell(v, lang.msg("crisis.war_started", v.id(), c.id()));
                crises.tell(c, lang.msg("crisis.war_started", v.id(), c.id()));
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
            crises.tell(village, lang.msg("crisis.war_won", village.id(), camp == null ? 0 : camp.state().population(),
                    village.state().count(Role.GUARD)));
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
        crises.tell(village, lang.msg("crisis.truce_autonomy", village.id(), camp.id()));
    }

    /** La cause a disparu : les dissidents rentrent au village, avec une loyauté qui évite un nouvel exil. */
    private void reconcile(VillageSite village, VillageSite camp) {
        lastEnd = "reconciliation";
        World world = Bukkit.getWorld(village.worldName());
        double floor = config.get().get(SimConfig.Key.EXILE_LOYALTY)
                + config.get().get(SimConfig.Key.RECONCILE_LOYALTY_BONUS);
        VillageState vs = village.state();
        for (VillagerState s : new ArrayList<>(camp.state().villagers())) {
            s.setFactionId(village.id());
            s.setLoyalty(Math.max(s.loyalty(), floor));
            vs.villagers().add(s);
            Entity e = Bukkit.getEntity(s.id());
            if (e instanceof Villager v && world != null) {
                v.teleport(village.center(world).add((vs.villagers().size() % 5) - 2, 0, 3));
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
        crises.tell(village, lang.msg("crisis.truce_reconciled", village.id()));
        log.info("Réconciliation : " + camp.id() + " rentre dans " + village.id());
    }
}
