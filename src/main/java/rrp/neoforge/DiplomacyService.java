package rrp.neoforge;

import java.util.ArrayList;
import java.util.List;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.diplomacy.DiplomacyDecider;
import rrp.core.diplomacy.DiplomacyDecider.Action;
import rrp.core.diplomacy.Treaty;
import rrp.core.diplomacy.Treaty.State;

/**
 * Diplomatie vivante : évalue régulièrement chaque couple de villages (traité commercial, alliance défensive,
 * rupture, déclaration de guerre) et annonce les décisions. Une guerre est d'abord annoncée par un ultimatum que
 * le Diplomat peut désamorcer avant son expiration ; les alliés défensifs du défenseur la rejoignent.
 */
public final class DiplomacyService {

    /** Guerre annoncée, pas encore ouverte. */
    public static final class Pending {
        private final String aggressor;
        private final String defender;
        private double remaining;

        Pending(String aggressor, String defender, double remaining) {
            this.aggressor = aggressor;
            this.defender = defender;
            this.remaining = remaining;
        }

        public String aggressor() { return aggressor; }
        public String defender() { return defender; }
        public double remaining() { return remaining; }
    }

    private static final Treaty TREATIES = new Treaty();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static double sinceEvaluation;

    private DiplomacyService() { }

    public static synchronized Treaty treaties() { return TREATIES; }

    public static synchronized List<Pending> pending() { return new ArrayList<>(PENDING); }

    public static synchronized void reset() {
        TREATIES.clear();
        PENDING.clear();
        sinceEvaluation = 0;
    }

    public static synchronized State state(String a, String b) { return TREATIES.get(a, b); }

    public static synchronized boolean atWar(String a, String b) { return TREATIES.atWar(a, b); }

    public static DiplomacyDecider.Rules rules(SimConfig cfg) {
        return new DiplomacyDecider.Rules(cfg.get(SimConfig.Key.RELATION_TRADE_AT), cfg.get(SimConfig.Key.DIPLOMACY_ALLIANCE_AT),
                cfg.get(SimConfig.Key.RELATION_HOSTILE_AT), (int) cfg.get(SimConfig.Key.DIPLOMACY_WAR_INCIDENTS));
    }

    public static synchronized void onTicks(long ticks, SimConfig cfg, VillageRegistry registry) {
        double seconds = ticks / 20.0;
        for (Pending p : new ArrayList<>(PENDING)) {
            p.remaining -= seconds;
            if (p.remaining <= 0) {
                PENDING.remove(p);
                open(p.aggressor, p.defender, registry);
            }
        }
        sinceEvaluation += seconds;
        double interval = cfg.get(SimConfig.Key.DIPLOMACY_INTERVAL_S);
        if (interval > 0 && sinceEvaluation >= interval) {
            sinceEvaluation = 0;
            evaluate(cfg, registry);
        }
    }

    /** Évalue tous les couples d'un même monde et applique les démarches décidées. */
    public static synchronized void evaluate(SimConfig cfg, VillageRegistry registry) {
        var all = new ArrayList<>(registry.all());
        var rules = rules(cfg);
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                VillageSite a = all.get(i);
                VillageSite b = all.get(j);
                if (a.worldName().equals(b.worldName()) && !pendingFor(a.id(), b.id())) {
                    apply(a, b, decide(a, b, all, cfg, rules), cfg);
                }
            }
        }
    }

    private static Action decide(VillageSite a, VillageSite b, List<VillageSite> all, SimConfig cfg,
                                 DiplomacyDecider.Rules rules) {
        double hostile = rules.hostileAt();
        boolean common = false;
        for (VillageSite c : all) {
            if (c != a && c != b && c.worldName().equals(a.worldName())
                    && RelationService.index(a, c, cfg) <= hostile && RelationService.index(b, c, cfg) <= hostile) {
                common = true;
                break;
            }
        }
        double food = cfg.get(SimConfig.Key.DIPLOMACY_CRITICAL_FOOD);
        boolean shortage = a.state().stock(Resource.FOOD) < food || b.state().stock(Resource.FOOD) < food;
        return DiplomacyDecider.decide(new DiplomacyDecider.Situation(TREATIES.get(a.id(), b.id()),
                RelationService.index(a, b, cfg), ExploitationWatcher.incidents(a.id(), b.id()), common, shortage), rules);
    }

    private static void apply(VillageSite a, VillageSite b, Action action, SimConfig cfg) {
        switch (action) {
            case PROPOSE_TRADE -> {
                TREATIES.set(a.id(), b.id(), State.TRADE_TREATY);
                announce(a, b, "diplomacy.trade", a.id(), b.id());
            }
            case PROPOSE_ALLIANCE -> {
                TREATIES.set(a.id(), b.id(), State.DEFENSIVE_ALLIANCE);
                announce(a, b, "diplomacy.alliance", a.id(), b.id());
            }
            case BREAK_TREATY -> {
                TREATIES.set(a.id(), b.id(), State.PEACE);
                announce(a, b, "diplomacy.break", a.id(), b.id());
            }
            case DECLARE_WAR -> {
                boolean border = ExploitationWatcher.incidents(a.id(), b.id()) >= (int) cfg.get(SimConfig.Key.DIPLOMACY_WAR_INCIDENTS);
                // l'agresseur est le moins bien nourri
                VillageSite agg = a.state().stock(Resource.FOOD) <= b.state().stock(Resource.FOOD) ? a : b;
                VillageSite def = agg == a ? b : a;
                double delay = cfg.get(SimConfig.Key.DIPLOMACY_WAR_DELAY_S);
                String reason = RrpMod.lang().get(border ? "diplomacy.reason.border" : "diplomacy.reason.shortage");
                PENDING.add(new Pending(agg.id(), def.id(), delay));
                announce(a, b, "diplomacy.ultimatum", agg.id(), def.id(), Math.round(delay), reason);
            }
            default -> { }
        }
    }

    /** Ouvre la guerre : rompt le traité du couple et entraîne les alliés défensifs du défenseur. */
    public static synchronized void open(String aggressor, String defender, VillageRegistry registry) {
        VillageSite agg = registry.get(aggressor);
        VillageSite def = registry.get(defender);
        if (agg == null || def == null || TREATIES.atWar(aggressor, defender)) {
            return;
        }
        List<String> joined = TREATIES.declareWar(aggressor, defender);
        announce(agg, def, "diplomacy.war", agg.id(), def.id());
        for (String id : joined) {
            VillageSite ally = registry.get(id);
            if (ally != null) {
                announce(ally, agg, "diplomacy.join", ally.id(), agg.id());
            }
        }
        registry.markDirty();
    }

    /** Announces a war from {@code agg} to {@code def}; false when one is already pending or under way. */
    public static synchronized boolean ultimatum(VillageSite agg, VillageSite def, SimConfig cfg) {
        if (TREATIES.atWar(agg.id(), def.id()) || pendingFor(agg.id(), def.id())) {
            return false;
        }
        double delay = cfg.get(SimConfig.Key.DIPLOMACY_WAR_DELAY_S);
        PENDING.add(new Pending(agg.id(), def.id(), delay));
        announce(agg, def, "diplomacy.ultimatum", agg.id(), def.id(), Math.round(delay), RrpMod.lang().get("diplomacy.reason.emissary"));
        return true;
    }

    /** Sets a treaty between two villages and announces it. */
    public static synchronized void conclude(VillageSite a, VillageSite b, State s) {
        TREATIES.set(a.id(), b.id(), s);
        String key = switch (s) {
            case TRADE_TREATY -> "diplomacy.trade";
            case DEFENSIVE_ALLIANCE -> "diplomacy.alliance";
            default -> "diplomacy.ceasefire";
        };
        announce(a, b, key, a.id(), b.id());
    }

    private static boolean pendingFor(String a, String b) {
        for (Pending p : PENDING) {
            if (p.aggressor.equals(a) && p.defender.equals(b) || p.aggressor.equals(b) && p.defender.equals(a)) {
                return true;
            }
        }
        return false;
    }

    /** Un Diplomat apaise le couple : l'indice monte et toute déclaration en attente est annulée. */
    public static synchronized boolean calm(VillageSite a, VillageSite b, SimConfig cfg) {
        boolean cancelled = PENDING.removeIf(p -> p.aggressor.equals(a.id()) && p.defender.equals(b.id())
                || p.aggressor.equals(b.id()) && p.defender.equals(a.id()));
        if (cancelled) {
            RelationService.change(a, b, cfg.get(SimConfig.Key.DIPLOMACY_CALM_GAIN), cfg);
            announce(a, b, "diplomacy.calmed", a.id(), b.id());
        }
        return cancelled;
    }

    private static void announce(VillageSite a, VillageSite b, String key, Object... args) {
        String msg = RrpMod.lang().msg(key, args);
        RrpMod.crises().tell(a, msg);
        if (b != null) {
            RrpMod.crises().tell(b, msg);
        }
    }
}
