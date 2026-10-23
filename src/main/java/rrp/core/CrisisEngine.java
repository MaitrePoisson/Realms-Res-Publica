package rrp.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import rrp.core.SimConfig.Key;

/**
 * Escalade déterministe des crises : STABLE → CONTESTATION → EXIL → GUERRE_CIVILE, calculée depuis
 * l'état seul (aucun hasard, aucun accès au monde). Chaque niveau au-dessus de STABLE possède une
 * « jauge » (minuteur plein à l'entrée) qui se vide lentement, au rythme {@code crisis_recovery_factor},
 * quand la cause du niveau a disparu ; vide, le village redescend d'un niveau.
 *
 * <ul>
 *   <li>STABLE → CONTESTATION : un indicateur (famine, surpopulation, insécurité) actif {@code contest_after} s.</li>
 *   <li>CONTESTATION → EXIL : loyauté moyenne sous {@code exile_loyalty} pendant {@code exile_after} s ;
 *       les mécontents (loyauté sous le seuil) fondent un campement dissident.</li>
 *   <li>EXIL → GUERRE_CIVILE : tension ≥ {@code war_tension} pendant {@code war_after} s (voir {@link #tension}).</li>
 *   <li>Fin de guerre : trêve (diplomate vivant et relations ≥ {@code truce_relations}) ou élimination
 *       des combattants (gardes) d'un camp.</li>
 * </ul>
 */
public final class CrisisEngine {
    private CrisisEngine() { }

    /** Indicateurs réels de crise d'un village. */
    public record Indicators(boolean famine, boolean overpopulation, boolean insecurity) {
        public boolean any() {
            return famine || overpopulation || insecurity;
        }
    }

    /** Jours de nourriture restants au rythme de consommation configuré (infini si personne à nourrir). */
    public static double foodDays(VillageState v, SimConfig cfg) {
        if (v.population() == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return v.stock(Resource.FOOD) / (v.population() * cfg.get(Key.FOOD_PER_VILLAGER_PER_DAY));
    }

    public static Indicators indicators(VillageState v, SimConfig cfg) {
        boolean famine = v.foodManaged() && v.population() > 0 && foodDays(v, cfg) < cfg.get(Key.FAMINE_DAYS_THRESHOLD);
        boolean overpopulation = v.population() > v.beds() * cfg.get(Key.OVERPOPULATION_RATIO);
        double covered = v.count(Role.GUARD) * cfg.get(Key.INSECURITY_THREATS_PER_GUARD);
        boolean insecurity = v.threats() > 0 && v.threats() > covered;
        return new Indicators(famine, overpopulation, insecurity);
    }

    /**
     * Tension entre un village et son campement : moyenne de l'insatisfaction (100 − loyauté moyenne) des
     * deux camps, plus {@code war_no_diplomat_penalty} si aucun diplomate vivant ne les sépare.
     */
    public static double tension(VillageState village, VillageState camp, SimConfig cfg) {
        double t = ((100 - camp.averageLoyalty()) + (100 - village.averageLoyalty())) / 2.0;
        if (!hasDiplomat(village, camp)) {
            t += cfg.get(Key.WAR_NO_DIPLOMAT_PENALTY);
        }
        return t;
    }

    /** Trêve : un diplomate vivant a amené les relations au seuil. */
    public static boolean truceReached(VillageState village, VillageState camp, SimConfig cfg) {
        return hasDiplomat(village, camp) && village.relations() >= cfg.get(Key.TRUCE_RELATIONS);
    }

    private static boolean hasDiplomat(VillageState a, VillageState b) {
        return a.count(Role.DIPLOMAT) > 0 || b.count(Role.DIPLOMAT) > 0;
    }

    /** Campement dissident d'un village, ou {@code null}. */
    public static VillageState campOf(List<VillageState> world, VillageState village) {
        for (VillageState v : world) {
            if (v.faction() == Faction.DISSIDENT && village.id().equals(v.parentId())) {
                return v;
            }
        }
        return null;
    }

    /**
     * Avance les crises de tous les villages de {@code world} de {@code dt} secondes. Un exil ajoute le
     * campement à {@code world} (liste modifiable) ; la fin d'une guerre par élimination retire un
     * campement vide. Renvoie les événements émis, dans l'ordre.
     */
    public static List<SimEvent> step(List<VillageState> world, SimConfig cfg, double dt) {
        List<SimEvent> events = new ArrayList<>();
        if (dt <= 0) {
            return events;
        }
        // Un village qui change de niveau pendant ce pas ne franchit pas une seconde marche au même pas.
        List<VillageState> fresh = new ArrayList<>();
        for (VillageState v : List.copyOf(world)) {
            if (!v.active()) {
                continue;
            }
            CrisisLevel before = v.level();
            contestation(v, cfg, dt, events);
            if (v.level() != before) {
                fresh.add(v);
            } else if (v.faction() == Faction.LOYALIST) {
                exile(world, v, cfg, dt, events);
                if (v.level() != before) {
                    fresh.add(v);
                }
            }
        }
        for (VillageState v : List.copyOf(world)) {
            if (v.faction() == Faction.LOYALIST && !fresh.contains(v)) {
                VillageState camp = campOf(world, v);
                if (camp != null && v.active() && camp.active()) {
                    war(world, v, camp, cfg, dt, events);
                }
            }
        }
        return events;
    }

    private static void change(VillageState v, CrisisLevel to, List<SimEvent> events) {
        CrisisLevel from = v.level();
        if (from != to) {
            v.setLevel(to);
            events.add(new SimEvent.CrisisChanged(v.id(), from, to));
        }
    }

    /** STABLE ↔ CONTESTATION, d'après les indicateurs réels. */
    private static void contestation(VillageState v, SimConfig cfg, double dt, List<SimEvent> events) {
        double after = cfg.get(Key.CONTEST_AFTER_S);
        double drain = dt * cfg.get(Key.CRISIS_RECOVERY_FACTOR);
        boolean active = indicators(v, cfg).any();
        switch (v.level()) {
            case STABLE -> {
                if (!active) {
                    v.setContestTimer(v.contestTimer() - drain);
                } else if (v.contestTimer() + dt >= after) {
                    v.setContestTimer(after);
                    change(v, CrisisLevel.CONTESTATION, events);
                } else {
                    v.setContestTimer(v.contestTimer() + dt);
                }
            }
            case CONTESTATION -> {
                if (active) {
                    v.setContestTimer(after);
                } else if (v.contestTimer() - drain <= 0) {
                    v.setContestTimer(0);
                    change(v, CrisisLevel.STABLE, events);
                } else {
                    v.setContestTimer(v.contestTimer() - drain);
                }
            }
            default -> v.setContestTimer(after);
        }
    }

    /** CONTESTATION → EXIL (départ des mécontents) et retour EXIL → CONTESTATION quand la cause a disparu. */
    private static void exile(List<VillageState> world, VillageState v, SimConfig cfg, double dt, List<SimEvent> events) {
        double after = cfg.get(Key.EXILE_AFTER_S);
        double drain = dt * cfg.get(Key.CRISIS_RECOVERY_FACTOR);
        boolean discontent = v.population() > 0 && v.averageLoyalty() < cfg.get(Key.EXILE_LOYALTY);
        if (v.level() == CrisisLevel.CONTESTATION) {
            if (discontent && v.population() >= 2) {
                if (v.exileTimer() + dt >= after) {
                    doExile(world, v, cfg, events);
                } else {
                    v.setExileTimer(v.exileTimer() + dt);
                }
            } else {
                v.setExileTimer(v.exileTimer() - drain);
            }
        } else if (v.level() == CrisisLevel.EXIL) {
            boolean cause = discontent || indicators(v, cfg).any();
            if (cause) {
                v.setExileTimer(after);
            } else if (v.exileTimer() - drain <= 0) {
                v.setExileTimer(0);
                v.setWarTimer(0);
                v.setContestTimer(cfg.get(Key.CONTEST_AFTER_S));
                change(v, CrisisLevel.CONTESTATION, events);
            } else {
                v.setExileTimer(v.exileTimer() - drain);
            }
        }
    }

    /**
     * Les mécontents (loyauté sous le seuil) quittent le village, au plus {@code population − 1} : ils
     * rejoignent le campement du village (créé au besoin) avec une part de la nourriture ; une part
     * {@code camp_guard_share} d'entre eux devient garde ; les tentes offrent un lit par exilé.
     */
    private static void doExile(List<VillageState> world, VillageState v, SimConfig cfg, List<SimEvent> events) {
        double threshold = cfg.get(Key.EXILE_LOYALTY);
        List<VillagerState> leaving = new ArrayList<>();
        for (VillagerState s : v.villagers()) {
            if (s.loyalty() < threshold) {
                leaving.add(s);
            }
        }
        leaving.sort(Comparator.comparingDouble(VillagerState::loyalty));
        while (leaving.size() > v.population() - 1) {
            leaving.remove(leaving.size() - 1);
        }
        VillageState camp = campOf(world, v);
        if (camp == null) {
            camp = new VillageState(freeCampId(world, v.id()), Faction.DISSIDENT);
            camp.setParentId(v.id());
            world.add(camp);
        }
        int food = (int) Math.floor(v.stock(Resource.FOOD) * cfg.get(Key.EXILE_FOOD_SHARE));
        v.addStock(Resource.FOOD, -food);
        camp.addStock(Resource.FOOD, food);
        List<UUID> ids = new ArrayList<>();
        int guards = (int) Math.ceil(leaving.size() * cfg.get(Key.CAMP_GUARD_SHARE));
        for (VillagerState s : leaving) {
            if (s.role() == Role.GUARD) {
                guards--;
            }
        }
        for (VillagerState s : leaving) {
            v.villagers().remove(s);
            s.setFactionId(camp.id());
            if (guards > 0 && s.role() != Role.GUARD) {
                s.setRole(Role.GUARD);
                guards--;
            }
            camp.villagers().add(s);
            ids.add(s.id());
        }
        camp.setBeds(camp.beds() + leaving.size());
        v.setExileTimer(cfg.get(Key.EXILE_AFTER_S));
        change(v, CrisisLevel.EXIL, events);
        events.add(new SimEvent.Exile(v.id(), List.copyOf(ids), camp.id(), food));
    }

    /** Identifiant libre pour un nouveau campement (un camp devenu autonome garde le sien). */
    private static String freeCampId(List<VillageState> world, String villageId) {
        String id = villageId + "-camp";
        for (int n = 2; isTaken(world, id); n++) {
            id = villageId + "-camp" + n;
        }
        return id;
    }

    private static boolean isTaken(List<VillageState> world, String id) {
        for (VillageState s : world) {
            if (s.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static int combatants(VillageState v) {
        return v.count(Role.GUARD);
    }

    /** EXIL → GUERRE_CIVILE et fin de guerre (trêve ou élimination). */
    private static void war(List<VillageState> world, VillageState v, VillageState camp, SimConfig cfg, double dt,
                            List<SimEvent> events) {
        double after = cfg.get(Key.WAR_AFTER_S);
        boolean truce = truceReached(v, camp, cfg);
        if (v.level() == CrisisLevel.GUERRE_CIVILE) {
            if (truce) {
                endWar(world, v, camp, cfg, true, events);
            } else if (combatants(v) == 0 || combatants(camp) == 0) {
                endWar(world, v, camp, cfg, false, events);
            }
            return;
        }
        if (v.level() != CrisisLevel.EXIL) {
            v.setWarTimer(0);
            return;
        }
        boolean ready = !truce && tension(v, camp, cfg) >= cfg.get(Key.WAR_TENSION)
                && combatants(v) > 0 && combatants(camp) > 0;
        if (!ready) {
            v.setWarTimer(v.warTimer() - dt * cfg.get(Key.CRISIS_RECOVERY_FACTOR));
        } else if (v.warTimer() + dt >= after) {
            v.setWarTimer(after);
            camp.setWarTimer(after);
            change(v, CrisisLevel.GUERRE_CIVILE, events);
            change(camp, CrisisLevel.GUERRE_CIVILE, events);
            events.add(new SimEvent.WarStarted(v.id(), camp.id()));
        } else {
            v.setWarTimer(v.warTimer() + dt);
        }
    }

    private static void endWar(List<VillageState> world, VillageState v, VillageState camp, SimConfig cfg,
                               boolean truce, List<SimEvent> events) {
        v.setWarTimer(0);
        camp.setWarTimer(0);
        change(v, CrisisLevel.EXIL, events);
        v.setExileTimer(cfg.get(Key.EXILE_AFTER_S));
        if (camp.population() == 0) {
            world.remove(camp);
        } else {
            camp.setContestTimer(cfg.get(Key.CONTEST_AFTER_S));
            change(camp, CrisisLevel.CONTESTATION, events);
        }
        events.add(new SimEvent.WarEnded(v.id(), camp.id(), truce));
    }
}
