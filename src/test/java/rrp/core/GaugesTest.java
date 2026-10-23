package rrp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import rrp.core.SimConfig.Key;

class GaugesTest {
    private final SimConfig cfg = SimConfig.defaults();

    private static VillagerState villager(Role role, double morale, double satiety, double loyalty) {
        return new VillagerState(UUID.randomUUID(), role, "f1", morale, satiety, loyalty);
    }

    private static VillageState village(int beds, int food, VillagerState... vs) {
        VillageState s = new VillageState("v1", Faction.LOYALIST);
        s.setBeds(beds);
        s.setStock(Resource.FOOD, food);
        for (VillagerState v : vs) {
            s.villagers().add(v);
        }
        return s;
    }

    private void run(VillageState s, SimConfig c, int seconds) {
        for (int i = 0; i < seconds; i++) {
            Gauges.step(s, c, 1.0);
        }
    }

    @Test
    void satietyDecaysAtConfiguredRate() {
        VillagerState v = villager(Role.FARMER, 70, 80, 70);
        VillageState s = village(1, 0, v);
        run(s, cfg, 60);
        assertEquals(80 - cfg.get(Key.SATIETY_DECAY_PER_MIN), v.satiety(), 1e-6);
    }

    @Test
    void hungryVillagerEatsFromStockAndSatietyRises() {
        VillagerState v = villager(Role.FARMER, 70, 40, 70);
        VillageState s = village(1, 10, v);
        Gauges.step(s, cfg, 1.0);
        assertTrue(v.satiety() > 40 + cfg.get(Key.SATIETY_PER_FOOD) - 1, "a mangé : " + v.satiety());
        assertTrue(s.stock(Resource.FOOD) < 10);
    }

    @Test
    void withoutStockSatietyFallsToZeroAndStays() {
        VillagerState v = villager(Role.FARMER, 70, 20, 70);
        VillageState s = village(1, 0, v);
        run(s, cfg, 1200);
        assertEquals(0, v.satiety());
        run(s, cfg, 100);
        assertEquals(0, v.satiety());
        assertEquals(0, s.stock(Resource.FOOD));
    }

    @Test
    void mostStarvingVillagerIsFedFirst() {
        VillagerState starving = villager(Role.FARMER, 70, 5, 70);
        VillagerState peckish = villager(Role.MINER, 70, 45, 70);
        VillageState s = village(2, 1, starving, peckish);
        int eaten = Gauges.feed(s, cfg);
        assertEquals(1, eaten);
        assertEquals(5 + cfg.get(Key.SATIETY_PER_FOOD), starving.satiety(), 1e-9);
        assertEquals(45, peckish.satiety(), 1e-9);
    }

    @Test
    void moraleConvergesUpWhenFedHousedAndSafe() {
        VillagerState v = villager(Role.FARMER, 20, 100, 70);
        VillageState s = village(1, 100000, v);
        run(s, cfg, 600);
        assertTrue(v.morale() > 75, "moral = " + v.morale());
    }

    @Test
    void moraleConvergesDownWhenStarvedHomelessAndThreatened() {
        VillagerState v = villager(Role.FARMER, 90, 0, 70);
        VillageState s = village(0, 0, v);
        s.setThreats(5);
        run(s, cfg, 600);
        assertTrue(v.morale() < 5, "moral = " + v.morale());
    }

    @Test
    void bedAssignedRaisesMoraleTarget() {
        VillagerState housed = villager(Role.FARMER, 50, 80, 70);
        VillagerState homeless = villager(Role.MINER, 50, 80, 70);
        VillageState s = village(1, 0, housed, homeless);
        double with = Gauges.moraleTarget(s, housed, 0, cfg);
        double without = Gauges.moraleTarget(s, homeless, 1, cfg);
        assertEquals(cfg.get(Key.MORALE_BED_BONUS), with - without, 1e-9);
    }

    @Test
    void guardsCoverThreatsPartially() {
        VillagerState g = villager(Role.GUARD, 50, 80, 70);
        VillageState s = village(1, 0, g);
        double safe = Gauges.moraleTarget(s, g, 0, cfg);
        s.setThreats(1);
        double covered = Gauges.moraleTarget(s, g, 0, cfg);
        s.setThreats(50);
        double overrun = Gauges.moraleTarget(s, g, 0, cfg);
        assertTrue(safe > covered && covered > overrun);
    }

    @Test
    void overpopulationLowersMorale() {
        VillagerState a = villager(Role.FARMER, 50, 80, 70);
        VillagerState b = villager(Role.MINER, 50, 80, 70);
        VillageState roomy = village(2, 0, a, b);
        VillageState crowded = village(1, 0, a, b);
        assertEquals(cfg.get(Key.MORALE_CROWDING_PENALTY),
                Gauges.moraleTarget(roomy, a, 0, cfg) - Gauges.moraleTarget(crowded, a, 0, cfg), 1e-9);
    }

    @Test
    void loyaltyFallsWhenMoraleIsLow() {
        VillagerState v = villager(Role.FARMER, 70, 0, 70);
        VillageState s = village(0, 0, v);
        run(s, cfg, 2000);
        assertTrue(v.loyalty() < cfg.get(Key.EXILE_LOYALTY), "loyauté = " + v.loyalty());
    }

    @Test
    void diplomatRaisesLoyaltyTarget() {
        SimConfig slow = cfg.with(Key.LOYALTY_SPEED_PER_S, 0.05);
        VillagerState a = villager(Role.FARMER, 70, 100, 90);
        VillageState without = village(5, 100000, a);
        VillagerState b = villager(Role.FARMER, 70, 100, 90);
        VillagerState d = villager(Role.DIPLOMAT, 70, 100, 90);
        VillageState with = village(5, 100000, b, d);
        run(without, slow, 600);
        run(with, slow, 600);
        assertTrue(b.loyalty() > a.loyalty(), b.loyalty() + " vs " + a.loyalty());
    }

    @Test
    void contestationAcceleratesLoyaltyLoss() {
        VillagerState calm = villager(Role.FARMER, 20, 0, 80);
        VillageState stable = village(0, 0, calm);
        VillagerState angry = villager(Role.FARMER, 20, 0, 80);
        VillageState contested = village(0, 0, angry);
        contested.setLevel(CrisisLevel.CONTESTATION);
        run(stable, cfg, 100);
        run(contested, cfg, 100);
        assertTrue(angry.loyalty() < calm.loyalty());
    }

    @Test
    void lowMoraleMeansDisobedience() {
        assertFalse(Gauges.canWork(villager(Role.MINER, cfg.get(Key.DISOBEY_MORALE) - 1, 50, 50), cfg));
        assertTrue(Gauges.canWork(villager(Role.MINER, cfg.get(Key.DISOBEY_MORALE), 50, 50), cfg));
    }

    @Test
    void everythingStaysBetweenZeroAndHundred() {
        VillagerState hi = villager(Role.GUARD, 100, 100, 100);
        VillagerState lo = villager(Role.MINER, 0, 0, 0);
        VillageState s = village(1, 5, hi, lo);
        s.setThreats(3);
        for (int i = 0; i < 5000; i++) {
            Gauges.step(s, cfg, 7.0);
            if (i % 500 == 0) {
                s.addStock(Resource.FOOD, 50);
            }
            for (VillagerState v : s.villagers()) {
                assertTrue(v.morale() >= 0 && v.morale() <= 100);
                assertTrue(v.satiety() >= 0 && v.satiety() <= 100);
                assertTrue(v.loyalty() >= 0 && v.loyalty() <= 100);
            }
        }
    }

    @Test
    void nonPositiveStepAndEmptyVillageChangeNothing() {
        VillagerState v = villager(Role.FARMER, 60, 60, 60);
        VillageState s = village(1, 5, v);
        Gauges.step(s, cfg, 0);
        Gauges.step(s, cfg, -3);
        assertEquals(60, v.satiety());
        Gauges.step(new VillageState("vide", Faction.LOYALIST), cfg, 10);
    }
}
