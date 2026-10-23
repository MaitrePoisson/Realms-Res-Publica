package rrp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import rrp.core.SimConfig.Key;

class DemographyTest {
    private final SimConfig cfg = SimConfig.defaults();

    private static VillageState village(int pop, int beds, int food) {
        VillageState s = new VillageState("v1", Faction.LOYALIST);
        s.setBeds(beds);
        s.setStock(Resource.FOOD, food);
        for (int i = 0; i < pop; i++) {
            s.villagers().add(new VillagerState(UUID.randomUUID(), Role.FARMER, "v1"));
        }
        return s;
    }

    /** Fait tourner le moteur et ajoute les nouveau-nés (comme le fera le simulateur). Renvoie les naissances. */
    private int run(VillageState s, SimConfig c, int seconds) {
        int births = 0;
        for (int i = 0; i < seconds; i++) {
            for (SimEvent e : DemographyEngine.step(s, c, 1.0)) {
                assertTrue(e instanceof SimEvent.Birth);
                s.villagers().add(new VillagerState(UUID.randomUUID(), Role.FARMER, s.id()));
                births++;
            }
        }
        return births;
    }

    @Test
    void birthNeedsInterval() {
        VillageState s = village(2, 5, 100);
        int interval = (int) cfg.get(Key.BIRTH_INTERVAL_S);
        assertEquals(0, run(s, cfg, interval - 1));
        assertEquals(1, run(s, cfg, 1));
    }

    @Test
    void birthConsumesFood() {
        VillageState s = village(2, 5, 100);
        run(s, cfg, (int) cfg.get(Key.BIRTH_INTERVAL_S));
        assertEquals(100 - (int) cfg.get(Key.BIRTH_FOOD_COST), s.stock(Resource.FOOD));
        assertEquals(3, s.population());
    }

    @Test
    void waitsFullIntervalBetweenBirths() {
        VillageState s = village(2, 20, 1000);
        int interval = (int) cfg.get(Key.BIRTH_INTERVAL_S);
        assertEquals(1, run(s, cfg, interval));
        assertEquals(0, run(s, cfg, interval - 1));
        assertEquals(1, run(s, cfg, 1));
    }

    @Test
    void noFoodMeansNoBirthEvenWithBeds() {
        VillageState s = village(2, 50, 0);
        assertEquals(0, run(s, cfg, 3600));
        assertEquals(2, s.population());
    }

    @Test
    void foodBelowThresholdMeansNoBirth() {
        VillageState s = village(2, 50, (int) cfg.get(Key.BIRTH_FOOD_THRESHOLD) - 1);
        assertEquals(0, run(s, cfg, 3600));
    }

    @Test
    void noFreeBedMeansNoBirthEvenWithFood() {
        VillageState s = village(4, 4, 1000);
        assertEquals(0, run(s, cfg, 3600));
        assertEquals(4, s.population());
        assertEquals(1000, s.stock(Resource.FOOD));
    }

    @Test
    void populationGrowsWithFoodAndBeds() {
        VillageState s = village(2, 10, 1000);
        int births = run(s, cfg, 3600);
        assertTrue(births >= 2, "naissances sur 1 h : " + births);
        assertEquals(2 + births, s.population());
        assertTrue(s.population() <= 10);
    }

    @Test
    void growthStopsWhenBedsAreFull() {
        VillageState s = village(2, 4, 10000);
        run(s, cfg, 36000);
        assertEquals(4, s.population());
    }

    @Test
    void maxPopulationIsRespected() {
        SimConfig c = SimConfig.fromMap(java.util.Map.of("max_population", 5));
        VillageState s = village(2, 100, 10000);
        run(s, c, 36000);
        assertEquals(5, s.population());
    }

    @Test
    void blockedBirthFiresAsSoonAsConditionsAreMet() {
        VillageState s = village(2, 2, 100);
        run(s, cfg, 3600);
        assertEquals(2, s.population());
        s.setBeds(3);
        assertEquals(1, run(s, cfg, 1));
    }

    @Test
    void isDeterministicAndIgnoresNonPositiveStep() {
        VillageState s = village(2, 5, 100);
        assertTrue(DemographyEngine.step(s, cfg, 0).isEmpty());
        assertTrue(DemographyEngine.step(s, cfg, -5).isEmpty());
        assertEquals(0, s.birthTimer());
        assertFalse(DemographyEngine.canBirth(village(2, 2, 100), cfg));
        List<SimEvent> ev = DemographyEngine.step(s, cfg, 1e6);
        assertEquals(1, ev.size());
    }
}
