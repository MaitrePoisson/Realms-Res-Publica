package rrp.core.abstractsim;

import rrp.core.DemographyEngine;
import rrp.core.Gauges;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.SimConfig.Key;
import rrp.core.SimEvent;
import rrp.core.VillageState;
import rrp.core.VillagerState;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Economy of a village that is not loaded: no entity is ticked. Harvests are seeded binomial draws from the role
 * counts and the biome yield; eating and morale reuse {@link Gauges}; births reuse {@link DemographyEngine}. A long gap
 * is caught up in at most {@code abstract_max_chunks} chunks, so the cost does not depend on the elapsed time.
 */
public final class AbstractEconomy {

    /** What one call changed, so that the caller can reconcile the physical world later. */
    public static final class Report {
        public int births;
        public final Map<Resource, Integer> produced = new EnumMap<>(Resource.class);
        public int chunks;
    }

    private AbstractEconomy() {
    }

    /**
     * @param yield         biome multiplier per resource (missing = 1.0)
     * @param dt            elapsed seconds
     * @param pendingBirths births already made but not yet materialised (they occupy beds and population)
     */
    public static Report advance(VillageState v, SimConfig cfg, Map<Resource, Double> yield, double dt, long seed,
                                 int pendingBirths) {
        Report report = new Report();
        for (Resource r : Resource.values()) {
            report.produced.put(r, 0);
        }
        if (dt <= 0 || v.villagers().isEmpty()) {
            return report;
        }
        Random rng = new Random(seed);
        double stepS = cfg.get(Key.ABSTRACT_STEP_TICKS) / 20.0;
        int chunks = (int) Math.min(cfg.get(Key.ABSTRACT_MAX_CHUNKS), Math.max(1, Math.ceil(dt / stepS)));
        double part = dt / chunks;
        report.chunks = chunks;
        int pending = pendingBirths;
        for (int i = 0; i < chunks; i++) {
            harvest(v, cfg, yield, part, rng, report);
            Gauges.step(v, cfg, part);
            int beds = v.beds();
            // a pending baby already takes a bed: hide those beds from the engine for this call
            v.setBeds(beds - pending);
            boolean room = v.population() < (int) cfg.get(Key.MAX_POPULATION) - pending;
            if (room) {
                for (SimEvent e : DemographyEngine.step(v, cfg, part)) {
                    if (e instanceof SimEvent.Birth) {
                        pending++;
                        report.births++;
                    }
                }
            } else {
                v.setBirthTimer(Math.min(cfg.get(Key.BIRTH_INTERVAL_S), v.birthTimer() + part));
            }
            v.setBeds(beds);
        }
        return report;
    }

    private static void harvest(VillageState v, SimConfig cfg, Map<Resource, Double> yield, double dt, Random rng, Report report) {
        double intervals = dt / cfg.get(Key.BEHAVIOR_INTERVAL_S);
        double p = cfg.get(Key.ABSTRACT_WORK_SUCCESS);
        for (Role role : new Role[] {Role.FARMER, Role.FORESTER, Role.MINER}) {
            int workers = 0;
            for (VillagerState s : v.villagers()) {
                if (s.role() == role && Gauges.canWork(s, cfg)) {
                    workers++;
                }
            }
            if (workers == 0) {
                continue;
            }
            int trials = roundSeeded(workers * intervals, rng);
            int successes = binomial(trials, p, rng);
            switch (role) {
                case FARMER -> add(v, Resource.FOOD, successes, cfg.get(Key.ABSTRACT_YIELD_FOOD), yield, rng, report);
                case FORESTER -> add(v, Resource.WOOD, successes, cfg.get(Key.ABSTRACT_YIELD_WOOD), yield, rng, report);
                default -> {
                    add(v, Resource.STONE, successes, cfg.get(Key.ABSTRACT_YIELD_STONE), yield, rng, report);
                    add(v, Resource.ORE, successes, cfg.get(Key.ABSTRACT_YIELD_ORE), yield, rng, report);
                }
            }
        }
    }

    private static void add(VillageState v, Resource r, int successes, double perSuccess, Map<Resource, Double> yield,
                            Random rng, Report report) {
        double mult = yield == null ? 1.0 : yield.getOrDefault(r, 1.0);
        int n = roundSeeded(successes * perSuccess * mult, rng);
        if (n > 0) {
            v.addStock(r, n);
            report.produced.merge(r, n, Integer::sum);
        }
    }

    /** Rounds to an integer so that the expected value is kept (2.3 gives 2, or 3 with probability 0.3). */
    static int roundSeeded(double x, Random rng) {
        int base = (int) Math.floor(x);
        return base + (rng.nextDouble() < x - base ? 1 : 0);
    }

    /** Binomial draw: exact for small n, normal approximation above (cost does not grow with n). */
    static int binomial(int n, double p, Random rng) {
        if (n <= 0 || p <= 0) {
            return 0;
        }
        if (p >= 1) {
            return n;
        }
        if (n <= 40) {
            int k = 0;
            for (int i = 0; i < n; i++) {
                if (rng.nextDouble() < p) {
                    k++;
                }
            }
            return k;
        }
        double mean = n * p;
        double sd = Math.sqrt(n * p * (1 - p));
        return (int) Math.max(0, Math.min(n, Math.round(mean + sd * rng.nextGaussian())));
    }
}
