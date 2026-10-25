package rrp.core.abstractsim;

import org.junit.jupiter.api.Test;
import rrp.core.Faction;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.SimConfig.Key;
import rrp.core.SimEvent;
import rrp.core.Simulation;
import rrp.core.VillageState;
import rrp.core.VillagerState;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractEconomyTest {
    private final SimConfig cfg = SimConfig.defaults();

    private static VillageState village(int farmers, int foresters, int idle, int beds, int food) {
        VillageState s = new VillageState("v1", Faction.LOYALIST);
        s.setBeds(beds);
        s.setStock(Resource.FOOD, food);
        for (int i = 0; i < farmers; i++) {
            s.villagers().add(new VillagerState(UUID.randomUUID(), Role.FARMER, "v1"));
        }
        for (int i = 0; i < foresters; i++) {
            s.villagers().add(new VillagerState(UUID.randomUUID(), Role.FORESTER, "v1"));
        }
        for (int i = 0; i < idle; i++) {
            s.villagers().add(new VillagerState(UUID.randomUUID(), Role.GUARD, "v1"));
        }
        return s;
    }

    @Test
    void harvestMatchesExpectedValueOverManySeeds() {
        double span = 3600;
        double expected = 5 * span / cfg.get(Key.BEHAVIOR_INTERVAL_S) * cfg.get(Key.ABSTRACT_WORK_SUCCESS) * cfg.get(Key.ABSTRACT_YIELD_FOOD);
        double sum = 0;
        int runs = 200;
        for (int seed = 0; seed < runs; seed++) {
            VillageState v = village(5, 0, 0, 5, 100000);
            sum += AbstractEconomy.advance(v, cfg, Map.of(), span, seed, 0).produced.get(Resource.FOOD);
        }
        assertEquals(expected, sum / runs, expected * 0.03);
    }

    @Test
    void biomeYieldScalesTheHarvest() {
        VillageState a = village(0, 4, 0, 4, 100);
        VillageState b = village(0, 4, 0, 4, 100);
        int plain = AbstractEconomy.advance(a, cfg, Map.of(), 3600, 7, 0).produced.get(Resource.WOOD);
        int rich = AbstractEconomy.advance(b, cfg, Map.of(Resource.WOOD, 2.0), 3600, 7, 0).produced.get(Resource.WOOD);
        assertTrue(rich > plain * 1.7 && rich < plain * 2.3, plain + " vs " + rich);
    }

    @Test
    void sameSeedSameResultAndLargeGapIsBoundedInChunks() {
        VillageState a = village(3, 3, 2, 12, 50);
        VillageState b = village(3, 3, 2, 12, 50);
        var ra = AbstractEconomy.advance(a, cfg, Map.of(), 86400 * 30, 42, 0);
        var rb = AbstractEconomy.advance(b, cfg, Map.of(), 86400 * 30, 42, 0);
        assertEquals(ra.produced, rb.produced);
        assertEquals(a.stock(Resource.FOOD), b.stock(Resource.FOOD));
        assertEquals((int) cfg.get(Key.ABSTRACT_MAX_CHUNKS), ra.chunks);
    }

    @Test
    void withoutWorkersMatchesTheFullSimulation() {
        VillageState full = village(0, 0, 6, 10, 400);
        VillageState abs = village(0, 0, 6, 10, 400);
        Simulation sim = new Simulation(cfg);
        sim.add(full);
        int fullBirths = 0;
        int seconds = 3600;
        for (int i = 0; i < seconds; i++) {
            for (SimEvent e : sim.step(1.0)) {
                if (e instanceof SimEvent.Birth) {
                    fullBirths++;
                    full.villagers().add(new VillagerState(UUID.randomUUID(), Role.GUARD, "v1"));
                }
            }
        }
        var report = AbstractEconomy.advance(abs, cfg, Map.of(), seconds, 1, 0);
        assertEquals(fullBirths, report.births, 1);
        assertEquals(full.stock(Resource.FOOD), abs.stock(Resource.FOOD), 400 * 0.1);
    }

    @Test
    void pendingBirthsOccupyBedsAndBedsAreRestored() {
        VillageState v = village(0, 0, 4, 5, 1000);
        assertEquals(1, AbstractEconomy.advance(v, cfg, Map.of(), 86400, 3, 0).births); // one free bed
        assertEquals(5, v.beds());
        VillageState w = village(0, 0, 4, 5, 1000);
        assertEquals(0, AbstractEconomy.advance(w, cfg, Map.of(), 86400, 3, 1).births);
        assertEquals(5, w.beds());
    }
}
