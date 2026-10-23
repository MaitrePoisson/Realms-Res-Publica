package rrp.bukkit;

import org.bukkit.Location;
import org.bukkit.entity.Villager;
import rrp.core.SimConfig;

import java.util.ArrayList;
import java.util.List;

import static rrp.bukkit.TestWorld.check;
import static rrp.bukkit.VillageCases.of;

/** Cas de selftest de M18 : coût du plugin avec plusieurs villages peuplés, budget de scan, écritures PDC groupées. */
final class PerfCases {

    private static final int VILLAGES = 5;
    private static final int VILLAGERS = 40;
    private static final int RUN_TICKS = 1200;
    private static final double MAX_MS_PER_TICK = 5.0;

    private PerfCases() {
    }

    static List<SelftestCase> all() {
        return List.of(fiveVillagesCost(), pdcWritesAreGrouped());
    }

    private static List<VillageSite> populate(TestWorld w, RrpPlugin p, int baseX) {
        List<VillageSite> sites = new ArrayList<>();
        for (int i = 0; i < VILLAGES; i++) {
            List<Villager> vs = new ArrayList<>();
            sites.add(RoleCases.villageOf(w, p, baseX + i * 200, 6000, VILLAGERS, vs));
        }
        return sites;
    }

    private static SelftestCase fiveVillagesCost() {
        return of("perf : 5 villages × 40 villageois, coût moyen du plugin < 5 ms/tick, scan borné", (w, p) -> {
            List<VillageSite> sites = populate(w, p, 6000);
            for (VillageSite s : sites) {
                check(s.state().population() == VILLAGERS, "population " + s.state().population() + " au lieu de " + VILLAGERS);
            }
            int budget = p.scanner().budgetPerTick();
            p.scanner().resetStats();
            p.behaviors().resetClock();
            p.ticks().resetCost();
            long actions = p.behaviors().actions();
            for (int t = 0; t < RUN_TICKS; t++) {
                if (t % 200 == 0) {
                    p.scanner().enqueueAll();
                }
                p.ticks().advance(1);
            }
            double ms = p.ticks().averageMsPerTick();
            p.getLogger().info(String.format("perf : %d villages x %d villageois, %d ticks : %.3f ms/tick en moyenne, "
                    + "%d actions de rôle, scan max %d blocs/tick (budget %d)", VILLAGES, VILLAGERS, RUN_TICKS, ms,
                    p.behaviors().actions() - actions, p.scanner().maxScannedPerTick(), budget));
            check(ms < MAX_MS_PER_TICK, String.format("coût moyen %.3f ms/tick, plafond %.1f", ms, MAX_MS_PER_TICK));
            check(p.scanner().maxScannedPerTick() <= budget,
                    "budget de scan dépassé : " + p.scanner().maxScannedPerTick() + " > " + budget);
        });
    }

    private static SelftestCase pdcWritesAreGrouped() {
        return of("perf : écritures PDC groupées (flush périodique, rien d'écrit si rien n'a changé)", (w, p) -> {
            List<VillageSite> sites = populate(w, p, 7500);
            p.gauges().saveAll(); // état de départ stocké
            long base = p.adapter().pdcWrites();
            p.gauges().saveAll();
            check(p.adapter().pdcWrites() == base, "écriture inutile alors que rien n'a changé : "
                    + (p.adapter().pdcWrites() - base));
            double flushS = p.simConfig().get(SimConfig.Key.PERSISTENCE_FLUSH_S);
            long gaugeSteps = p.gauges().steps();
            for (int t = 0; t < RUN_TICKS; t++) {
                p.ticks().advance(1);
            }
            long steps = p.gauges().steps() - gaugeSteps;
            long writes = p.adapter().pdcWrites() - base;
            long total = (long) VILLAGES * VILLAGERS;
            long flushes = (long) Math.ceil(RUN_TICKS / 20.0 / flushS) + 1;
            p.getLogger().info("perf : " + writes + " écritures PDC en " + RUN_TICKS + " ticks (" + steps
                    + " pas de jauges, " + total + " villageois, flush toutes les " + flushS + " s)");
            check(steps > 0, "aucun pas de jauges exécuté");
            check(writes <= total * flushes, "écritures non groupées : " + writes + " > " + total * flushes);
            check(writes < steps * VILLAGERS * 2L / 3, "autant d'écritures que de pas : " + writes);
        });
    }
}
