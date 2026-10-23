package rrp.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import rrp.core.CrisisLevel;
import rrp.core.Gauges;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.SimEvent;
import rrp.core.Simulation;
import rrp.core.VillageState;
import rrp.core.VillagerState;

/** Exécute un scénario pas à pas avec l'économie virtuelle et relève des échantillons. */
public final class ScenarioRunner {
    /** Pas de simulation (s) et période d'échantillonnage (s). */
    public static final double STEP_S = 5;
    public static final double SAMPLE_S = 30;
    private static final int MAX_THREATS = 12;

    /** Part visée de chaque rôle dans un village qui grandit (les naissances comblent le plus grand déficit). */
    private static final Map<Role, Double> TARGET = Map.of(
            Role.FARMER, 0.35, Role.FORESTER, 0.15, Role.BUILDER, 0.10, Role.GUARD, 0.15,
            Role.STEWARD, 0.10, Role.MINER, 0.10, Role.DIPLOMAT, 0.05);

    public record Sample(double t, String villageId, int population, int food, int wood, int beds, int threats,
                         double morale, double satiety, double loyalty, CrisisLevel level) { }

    public record TimedEvent(double t, SimEvent event) { }

    public record Result(Scenario scenario, List<Sample> samples, List<TimedEvent> events) {
        public List<Sample> of(String villageId) {
            return samples.stream().filter(s -> s.villageId().equals(villageId)).toList();
        }

        public Sample last(String villageId) {
            List<Sample> l = of(villageId);
            return l.isEmpty() ? null : l.get(l.size() - 1);
        }

        /** Ids des villages vus dans les échantillons, dans l'ordre d'apparition. */
        public List<String> villageIds() {
            return samples.stream().map(Sample::villageId).distinct().toList();
        }

        public <T extends SimEvent> List<TimedEvent> events(Class<T> type) {
            return events.stream().filter(e -> type.isInstance(e.event())).toList();
        }

        /** Instant du premier passage du village au niveau donné, ou -1. */
        public double firstTimeAt(String villageId, CrisisLevel level) {
            for (TimedEvent e : events) {
                if (e.event() instanceof SimEvent.CrisisChanged c && c.villageId().equals(villageId) && c.to() == level) {
                    return e.t();
                }
            }
            return -1;
        }
    }

    /** Cumuls fractionnaires de l'économie d'un village. */
    private static final class Acc {
        double food;
        double wood;
        double bedTimer;
        double spawn;
        double kills;
    }

    private ScenarioRunner() { }

    public static Result run(Scenario sc, SimConfig cfg) {
        Simulation sim = new Simulation(cfg);
        sim.add(sc.newVillage());
        Map<String, Acc> accs = new HashMap<>();
        List<Sample> samples = new ArrayList<>();
        List<TimedEvent> events = new ArrayList<>();
        sample(sim, 0, samples);
        double nextSample = SAMPLE_S;
        for (double t = STEP_S; t <= sc.durationS() + 1e-9; t += STEP_S) {
            for (VillageState v : List.copyOf(sim.villages())) {
                diplomacy(sim.villages(), v, cfg, STEP_S);
            }
            for (VillageState v : sim.villages()) {
                economy(v, sc.economy(), cfg, accs.computeIfAbsent(v.id(), k -> new Acc()), STEP_S);
            }
            for (SimEvent e : sim.step(STEP_S)) {
                events.add(new TimedEvent(t, e));
                if (e instanceof SimEvent.Birth b) {
                    VillageState v = sim.village(b.villageId());
                    v.villagers().add(newborn(v));
                }
            }
            if (t + 1e-9 >= nextSample) {
                sample(sim, t, samples);
                nextSample += SAMPLE_S;
            }
        }
        return new Result(sc, samples, events);
    }

    /** Un diplomate qui obéit négocie avec l'autre camp d'une rupture : +relations_per_visit toutes les behavior_interval_s. */
    private static void diplomacy(List<VillageState> world, VillageState v, SimConfig cfg, double dt) {
        if (v.faction() != rrp.core.Faction.LOYALIST) {
            return;
        }
        VillageState camp = rrp.core.CrisisEngine.campOf(world, v);
        if (camp == null) {
            return;
        }
        long diplomats = v.villagers().stream().filter(s -> s.role() == Role.DIPLOMAT && Gauges.canWork(s, cfg)).count()
                + camp.villagers().stream().filter(s -> s.role() == Role.DIPLOMAT && Gauges.canWork(s, cfg)).count();
        double gain = diplomats * cfg.get(SimConfig.Key.RELATIONS_PER_VISIT) * dt / cfg.get(SimConfig.Key.BEHAVIOR_INTERVAL_S);
        v.setRelations(v.relations() + gain);
        camp.setRelations(camp.relations() + gain);
    }

    private static VillagerState newborn(VillageState v) {
        Role best = Role.FARMER;
        double bestDeficit = Double.NEGATIVE_INFINITY;
        for (Role r : Role.values()) {
            double deficit = TARGET.get(r) * (v.population() + 1) - v.count(r);
            if (deficit > bestDeficit) {
                bestDeficit = deficit;
                best = r;
            }
        }
        return new VillagerState(UUID.randomUUID(), best, v.id(), 70, 80, 70);
    }

    /** Production et sécurité du village pendant {@code dt} secondes ; seuls les villageois qui obéissent travaillent. */
    private static void economy(VillageState v, Scenario.Economy eco, SimConfig cfg, Acc a, double dt) {
        double minutes = dt / 60.0;
        int farmers = 0;
        int foresters = 0;
        int builders = 0;
        int guards = 0;
        for (VillagerState vs : v.villagers()) {
            if (!Gauges.canWork(vs, cfg)) {
                continue;
            }
            switch (vs.role()) {
                case FARMER -> farmers++;
                case FORESTER -> foresters++;
                case BUILDER -> builders++;
                case GUARD -> guards++;
                default -> { }
            }
        }
        a.food += farmers * eco.foodPerFarmerPerMin() * minutes;
        a.wood += foresters * eco.woodPerForesterPerMin() * minutes;
        int food = (int) a.food;
        int wood = (int) a.wood;
        a.food -= food;
        a.wood -= wood;
        v.addStock(Resource.FOOD, food);
        v.addStock(Resource.WOOD, wood);

        // Les bâtisseurs ne construisent que si le village manque de lits (marge de 2).
        if (builders > 0 && v.beds() < v.population() + (int) cfg.get(SimConfig.Key.BUILDER_SPARE_BEDS)) {
            a.bedTimer += dt * builders;
            while (a.bedTimer >= eco.bedBuildSeconds() && v.stock(Resource.WOOD) >= eco.woodPerBed()) {
                a.bedTimer -= eco.bedBuildSeconds();
                v.addStock(Resource.WOOD, -eco.woodPerBed());
                v.setBeds(v.beds() + 1);
            }
            a.bedTimer = Math.min(a.bedTimer, eco.bedBuildSeconds());
        }

        a.spawn += eco.threatsPerMin() * minutes;
        int spawned = (int) a.spawn;
        a.spawn -= spawned;
        a.kills += guards * eco.killsPerGuardPerMin() * minutes;
        int killed = (int) a.kills;
        a.kills -= killed;
        v.setThreats(Math.min(MAX_THREATS, Math.max(0, v.threats() + spawned - killed)));
    }

    private static void sample(Simulation sim, double t, List<Sample> out) {
        for (VillageState v : sim.villages()) {
            double satiety = v.villagers().stream().mapToDouble(VillagerState::satiety).average().orElse(0);
            out.add(new Sample(t, v.id(), v.population(), v.stock(Resource.FOOD), v.stock(Resource.WOOD), v.beds(),
                    v.threats(), v.averageMorale(), satiety, v.averageLoyalty(), v.level()));
        }
    }
}
