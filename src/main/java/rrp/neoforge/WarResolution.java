package rrp.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.diplomacy.RelationMatrix;
import rrp.core.diplomacy.Treaty.State;
import rrp.core.diplomacy.WarOutcome;

/**
 * Résolution des guerres : une guerre dure au moins {@code war_resolve_min_s}, puis se conclut quand un camp n'a plus
 * de soldats, qu'un plafond de brèches est atteint ou que la durée maximale est écoulée. Le plus puissant gagne :
 * repli, pillage (part plafonnée de l'entrepôt), annexion (revendications transférées, tribut régulier, persisté)
 * ou destruction de l'Hôtel de Ville (le village tombe, sa population fuit). Le cessez-le-feu rétablit en partie la relation.
 */
public final class WarResolution {

    /** Issue enregistrée d'une guerre. */
    public record Result(String winner, String loser, WarOutcome.Kind kind, int loot) { }

    private static final Map<String, Double> AGES = new HashMap<>();
    /** Annexions : vaincu -> vainqueur (persisté). */
    private static final Map<String, String> TRIBUTARIES = new TreeMap<>();
    private static final List<Result> HISTORY = new ArrayList<>();
    private static double since;
    private static double sinceTribute;

    private WarResolution() { }

    public static synchronized void reset() {
        AGES.clear();
        TRIBUTARIES.clear();
        HISTORY.clear();
        since = 0;
        sinceTribute = 0;
    }

    public static synchronized Map<String, String> tributaries() { return new TreeMap<>(TRIBUTARIES); }

    public static synchronized void restore(String loser, String winner) { TRIBUTARIES.put(loser, winner); }

    public static synchronized void clearTributaries() { TRIBUTARIES.clear(); }

    public static synchronized List<Result> history() { return new ArrayList<>(HISTORY); }

    /** Appends a verdict to the history (used by tests that need a known outcome). */
    public static synchronized void record(Result r) { HISTORY.add(r); }

    /** Âge des guerres en cours, pour les commandes. */
    public static synchronized Map<String, Double> ages() { return new TreeMap<>(AGES); }

    /** Propriétaire effectif des revendications d'un village : son suzerain s'il est annexé, sinon lui-même. */
    public static synchronized String ownerOf(String village) {
        String owner = village;
        for (int i = 0; i < 5 && TRIBUTARIES.containsKey(owner); i++) {
            owner = TRIBUTARIES.get(owner);
        }
        return owner;
    }

    public static WarOutcome.Rules rules(SimConfig cfg) {
        return new WarOutcome.Rules(cfg.get(SimConfig.Key.WAR_PILLAGE_AT), cfg.get(SimConfig.Key.WAR_ANNEX_AT),
                cfg.get(SimConfig.Key.WAR_DESTROY_AT));
    }

    public static synchronized void onTicks(long ticks, SimConfig cfg, VillageRegistry registry) {
        double seconds = ticks / 20.0;
        since += seconds;
        double interval = cfg.get(SimConfig.Key.WAR_RESOLVE_INTERVAL_S);
        if (interval > 0 && since >= interval) {
            update(since, cfg, registry);
            since = 0;
        }
        sinceTribute += seconds;
        double period = cfg.get(SimConfig.Key.WAR_TRIBUTE_PERIOD_S);
        if (period > 0 && sinceTribute >= period) {
            sinceTribute = 0;
            payTribute(cfg, registry);
        }
    }

    /** Fait vieillir chaque guerre de {@code seconds} et conclut celles qui sont mûres. */
    public static synchronized void update(double seconds, SimConfig cfg, VillageRegistry registry) {
        if (RrpMod.server() == null) {
            return;
        }
        Map<String, VillageSite[]> wars = new TreeMap<>();
        for (VillageSite a : registry.all()) {
            for (String id : DiplomacyService.treaties().partners(a.id(), State.WAR)) {
                VillageSite b = registry.get(id);
                if (b != null && a.id().compareTo(b.id()) < 0) {
                    wars.put(RelationMatrix.key(a.id(), b.id()), new VillageSite[] {a, b});
                }
            }
        }
        AGES.keySet().retainAll(wars.keySet());
        for (var e : wars.entrySet()) {
            double age = AGES.merge(e.getKey(), seconds, Double::sum);
            VillageSite a = e.getValue()[0];
            VillageSite b = e.getValue()[1];
            if (age >= cfg.get(SimConfig.Key.WAR_RESOLVE_MIN_S) && ripe(a, b, age, cfg)) {
                resolve(a, b, cfg, registry);
                AGES.remove(e.getKey());
            }
        }
    }

    private static boolean ripe(VillageSite a, VillageSite b, double age, SimConfig cfg) {
        if (age >= cfg.get(SimConfig.Key.WAR_RESOLVE_MAX_S)) {
            return true;
        }
        if (!marchArrived(a) || !marchArrived(b)) { // an abstract army still on the road cannot have won yet
            return false;
        }
        var wa = side(a, cfg);
        var wb = side(b, cfg);
        boolean cap = wa.breachCap() > 0 && (wa.breaches() >= wa.breachCap() || wb.breaches() >= wb.breachCap());
        return cap || wa.strength() <= 0 || wb.strength() <= 0 || wa.aliveShare() <= 0 || wb.aliveShare() <= 0;
    }

    private static boolean marchArrived(VillageSite v) {
        var f = MobilizationService.force(v.id());
        return f == null || f.arrived();
    }

    /** Camp de {@code me} : sa force levée, ses survivants et les brèches qu'il a ouvertes chez son ennemi. */
    static WarOutcome.Side side(VillageSite me, SimConfig cfg) {
        var f = MobilizationService.force(me.id());
        double strength = f == null ? 0 : f.strength();
        double alive = 1;
        ServerLevel level = Worlds.level(RrpMod.server(), me.worldName());
        boolean loaded = level != null && me.hasHall()
                && level.hasChunkAt(new net.minecraft.core.BlockPos(me.hall()[0], me.hall()[1], me.hall()[2]));
        if (f != null && !f.isAbstract() && loaded && f.size() > 0) {
            alive = (double) MobilizationService.alive(f, level) / f.size();
        }
        var siege = SiegeService.siege(me.id());
        int breaches = siege == null ? 0 : siege.budget().used();
        int cap = siege == null ? (int) cfg.get(SimConfig.Key.SIEGE_BLOCK_CAP) : siege.budget().cap();
        return new WarOutcome.Side(strength, alive, breaches, cap);
    }

    /** Conclut la guerre du couple : verdict, conséquences, cessez-le-feu. */
    public static synchronized Result resolve(VillageSite a, VillageSite b, SimConfig cfg, VillageRegistry registry) {
        var verdict = WarOutcome.decide(side(a, cfg), side(b, cfg), rules(cfg));
        VillageSite winner = verdict.winner() == 0 ? a : b;
        VillageSite loser = winner == a ? b : a;
        int loot = 0;
        switch (verdict.kind()) {
            case PILLAGE -> loot = pillage(winner, loser, cfg);
            case ANNEXATION -> {
                TRIBUTARIES.put(loser.id(), winner.id());
                tellBoth(winner, loser, "warresolution.annexation", winner.id(), loser.id());
            }
            case DESTRUCTION -> destroy(winner, loser, registry);
            default -> tellBoth(winner, loser, "warresolution.repelled", winner.id(), loser.id());
        }
        DiplomacyService.treaties().set(a.id(), b.id(), State.PEACE);
        RelationService.change(a, b, cfg.get(SimConfig.Key.WAR_PEACE_GAIN), cfg);
        tellBoth(winner, loser, "warresolution.peace", a.id(), b.id());
        Result r = new Result(winner.id(), loser.id(), verdict.kind(), loot);
        HISTORY.add(r);
        registry.markDirty();
        return r;
    }

    private static void tellBoth(VillageSite a, VillageSite b, String key, Object... args) {
        String msg = RrpMod.lang().msg(key, args);
        RrpMod.crises().tell(a, msg);
        RrpMod.crises().tell(b, msg);
    }

    public static synchronized int pillage(VillageSite winner, VillageSite loser, SimConfig cfg) {
        ServerLevel lw = Worlds.level(RrpMod.server(), winner.worldName());
        ServerLevel ll = Worlds.level(RrpMod.server(), loser.worldName());
        if (lw == null || ll == null || !lw.hasChunkAt(chunkPos(winner)) || !ll.hasChunkAt(chunkPos(loser))) {
            return 0;
        }
        var stock = RrpMod.warehouse().read(loser, ll);
        int total = 0;
        for (Resource res : Resource.values()) {
            int n = WarOutcome.loot(stock.getOrDefault(res, 0), cfg.get(SimConfig.Key.WAR_LOOT_SHARE),
                    (int) cfg.get(SimConfig.Key.WAR_LOOT_CAP));
            int taken = n > 0 ? RrpMod.warehouse().withdraw(loser, ll, res, n) : 0;
            if (taken > 0) {
                var rest = RrpMod.warehouse().deposit(winner, lw, CaravanService.stackOf(res, taken));
                if (!rest.isEmpty()) {
                    RrpMod.warehouse().deposit(loser, ll, rest);
                    taken -= rest.getCount();
                }
                total += taken;
            }
        }
        tellBoth(winner, loser, "warresolution.pillage", winner.id(), loser.id(), total);
        return total;
    }

    /** Le lutrin et le socle d'or tombent ; la population gagne le village voisin le plus proche, ou se disperse. */
    private static void destroy(VillageSite winner, VillageSite loser, VillageRegistry registry) {
        ServerLevel level = Worlds.level(RrpMod.server(), loser.worldName());
        int[] hall = loser.hall();
        if (level != null && hall != null) {
            BlockPos base = new BlockPos(hall[0], hall[1], hall[2]);
            level.setBlock(base.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(base, Blocks.COBBLESTONE.defaultBlockState(), 3);
        }
        loser.clearHall();
        VillageSite refuge = null;
        for (VillageSite s : registry.all()) {
            if (s != loser && s != winner && s.worldName().equals(loser.worldName())
                    && (refuge == null || s.distance(loser.worldName(), loser.x(), loser.z())
                    < refuge.distance(loser.worldName(), loser.x(), loser.z()))) {
                refuge = s;
            }
        }
        if (refuge != null && level != null) {
            for (Villager v : RrpMod.adapter().present(loser, level)) {
                v.getNavigation().moveTo(refuge.x(), refuge.y(), refuge.z(), 0.6);
            }
            registry.merge(refuge, loser);
        }
        TRIBUTARIES.remove(loser.id());
        tellBoth(winner, loser, "warresolution.destruction", winner.id(), loser.id());
    }

    /** Chaque vaincu annexé verse à son suzerain une part plafonnée de ses stocks. */
    public static synchronized int payTribute(SimConfig cfg, VillageRegistry registry) {
        if (RrpMod.server() == null) {
            return 0;
        }
        int total = 0;
        for (var e : new ArrayList<>(TRIBUTARIES.entrySet())) {
            VillageSite loser = registry.get(e.getKey());
            VillageSite winner = registry.get(e.getValue());
            if (loser == null || winner == null) {
                TRIBUTARIES.remove(e.getKey());
                continue;
            }
            ServerLevel ll = Worlds.level(RrpMod.server(), loser.worldName());
            ServerLevel lw = Worlds.level(RrpMod.server(), winner.worldName());
            if (ll == null || lw == null || !lw.hasChunkAt(chunkPos(winner)) || !ll.hasChunkAt(chunkPos(loser))) {
                continue;
            }
            var stock = RrpMod.warehouse().read(loser, ll);
            int paid = 0;
            for (Resource res : Resource.values()) {
                int n = WarOutcome.tribute(stock.getOrDefault(res, 0), cfg.get(SimConfig.Key.WAR_TRIBUTE_SHARE),
                        (int) cfg.get(SimConfig.Key.WAR_TRIBUTE_CAP));
                int taken = n > 0 ? RrpMod.warehouse().withdraw(loser, ll, res, n) : 0;
                if (taken > 0) {
                    var rest = RrpMod.warehouse().deposit(winner, lw, CaravanService.stackOf(res, taken));
                    if (!rest.isEmpty()) {
                        RrpMod.warehouse().deposit(loser, ll, rest);
                        taken -= rest.getCount();
                    }
                    paid += taken;
                }
            }
            if (paid > 0) {
                total += paid;
                tellBoth(winner, loser, "warresolution.tribute", loser.id(), winner.id(), paid);
            }
        }
        return total;
    }

    private static net.minecraft.core.BlockPos chunkPos(VillageSite v) {
        return net.minecraft.core.BlockPos.containing(v.x(), v.y(), v.z());
    }
}
