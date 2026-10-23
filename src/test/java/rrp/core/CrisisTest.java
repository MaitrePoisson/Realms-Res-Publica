package rrp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import rrp.core.SimConfig.Key;

class CrisisTest {
    private final SimConfig cfg = SimConfig.defaults();

    private static VillagerState villager(Role role, double loyalty) {
        return new VillagerState(UUID.randomUUID(), role, "v1", 70, 80, loyalty);
    }

    /** Village équilibré : lits pour tous, nourriture abondante, aucune menace. */
    private static VillageState healthy(int pop) {
        VillageState s = new VillageState("v1", Faction.LOYALIST);
        s.setBeds(pop + 2);
        s.setStock(Resource.FOOD, 1000);
        for (int i = 0; i < pop; i++) {
            s.villagers().add(villager(Role.FARMER, 80));
        }
        return s;
    }

    private static List<VillageState> world(VillageState... vs) {
        return new ArrayList<>(List.of(vs));
    }

    /** Avance de {@code seconds} pas d'une seconde et cumule les événements. */
    private List<SimEvent> run(List<VillageState> w, int seconds) {
        List<SimEvent> all = new ArrayList<>();
        for (int i = 0; i < seconds; i++) {
            all.addAll(CrisisEngine.step(w, cfg, 1.0));
        }
        return all;
    }

    private static <T extends SimEvent> List<T> only(List<SimEvent> events, Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    // --- Indicateurs ---

    @Test
    void healthyVillageHasNoIndicator() {
        assertFalse(CrisisEngine.indicators(healthy(5), cfg).any());
    }

    @Test
    void famineWhenFoodDaysBelowThreshold() {
        VillageState s = healthy(5);
        double perDay = 5 * cfg.get(Key.FOOD_PER_VILLAGER_PER_DAY);
        s.setStock(Resource.FOOD, (int) (perDay * cfg.get(Key.FAMINE_DAYS_THRESHOLD)) + 1);
        assertFalse(CrisisEngine.indicators(s, cfg).famine());
        s.setStock(Resource.FOOD, (int) (perDay * cfg.get(Key.FAMINE_DAYS_THRESHOLD)) - 1);
        assertTrue(CrisisEngine.indicators(s, cfg).famine());
        s.setStock(Resource.FOOD, 0);
        assertTrue(CrisisEngine.indicators(s, cfg).famine());
    }

    @Test
    void emptyVillageIsNotInFamine() {
        VillageState s = new VillageState("v1", Faction.LOYALIST);
        assertFalse(CrisisEngine.indicators(s, cfg).any());
    }

    @Test
    void overpopulationWhenMoreVillagersThanBedsTimesRatio() {
        VillageState s = healthy(5);
        s.setBeds(5);
        assertFalse(CrisisEngine.indicators(s, cfg).overpopulation());
        s.setBeds(4);
        assertTrue(CrisisEngine.indicators(s, cfg).overpopulation());
    }

    @Test
    void insecurityWhenThreatsExceedGuardCoverage() {
        VillageState s = healthy(5);
        s.setThreats(3);
        assertTrue(CrisisEngine.indicators(s, cfg).insecurity());
        s.villagers().get(0).setRole(Role.GUARD); // couvre 2 menaces
        assertTrue(CrisisEngine.indicators(s, cfg).insecurity());
        s.villagers().get(1).setRole(Role.GUARD); // couvre 4 menaces
        assertFalse(CrisisEngine.indicators(s, cfg).insecurity());
        s.setThreats(0);
        s.villagers().get(0).setRole(Role.FARMER);
        s.villagers().get(1).setRole(Role.FARMER);
        assertFalse(CrisisEngine.indicators(s, cfg).insecurity());
    }

    // --- Contestation ---

    @Test
    void calmVillageStaysStableForever() {
        VillageState s = healthy(6);
        List<SimEvent> events = run(world(s), 5000);
        assertTrue(events.isEmpty());
        assertEquals(CrisisLevel.STABLE, s.level());
    }

    @Test
    void sustainedFamineGivesContestationAtConfiguredDuration() {
        VillageState s = healthy(6);
        s.setStock(Resource.FOOD, 0);
        int after = cfg.getInt(Key.CONTEST_AFTER_S);
        List<VillageState> w = world(s);
        List<SimEvent> early = run(w, after - 1);
        assertTrue(early.isEmpty());
        assertEquals(CrisisLevel.STABLE, s.level());
        List<SimEvent> events = run(w, 1);
        assertEquals(CrisisLevel.CONTESTATION, s.level());
        assertEquals(List.of(new SimEvent.CrisisChanged("v1", CrisisLevel.STABLE, CrisisLevel.CONTESTATION)), events);
    }

    @Test
    void overpopulationAndInsecurityAlsoGiveContestation() {
        VillageState crowded = healthy(6);
        crowded.setBeds(2);
        run(world(crowded), cfg.getInt(Key.CONTEST_AFTER_S));
        assertEquals(CrisisLevel.CONTESTATION, crowded.level());

        VillageState unsafe = healthy(6);
        unsafe.setThreats(5);
        run(world(unsafe), cfg.getInt(Key.CONTEST_AFTER_S));
        assertEquals(CrisisLevel.CONTESTATION, unsafe.level());
    }

    @Test
    void shortProblemDoesNotEscalateAndBarDrains() {
        VillageState s = healthy(6);
        List<VillageState> w = world(s);
        s.setStock(Resource.FOOD, 0);
        run(w, 60);
        s.setStock(Resource.FOOD, 1000);
        run(w, 1000);
        assertEquals(CrisisLevel.STABLE, s.level());
        assertEquals(0, s.contestTimer(), 1e-9);
    }

    @Test
    void contestationRecedesSlowlyWhenProblemDisappears() {
        VillageState s = healthy(6);
        List<VillageState> w = world(s);
        s.setStock(Resource.FOOD, 0);
        run(w, cfg.getInt(Key.CONTEST_AFTER_S));
        assertEquals(CrisisLevel.CONTESTATION, s.level());
        s.setStock(Resource.FOOD, 1000);
        double slow = cfg.get(Key.CONTEST_AFTER_S) / cfg.get(Key.CRISIS_RECOVERY_FACTOR);
        run(w, (int) slow - 5);
        assertEquals(CrisisLevel.CONTESTATION, s.level());
        List<SimEvent> events = run(w, 10);
        assertEquals(CrisisLevel.STABLE, s.level());
        assertEquals(1, only(events, SimEvent.CrisisChanged.class).size());
    }

    // --- Exil ---

    /** Village de 7 : 5 mécontents (loyauté 5) et 2 loyaux, sans nourriture. */
    private VillageState discontentVillage(Role firstLoyal, Role secondLoyal) {
        VillageState s = new VillageState("v1", Faction.LOYALIST);
        s.setBeds(9);
        for (int i = 0; i < 5; i++) {
            s.villagers().add(villager(Role.FARMER, 5));
        }
        s.villagers().add(villager(firstLoyal, 80));
        s.villagers().add(villager(secondLoyal, 80));
        s.setStock(Resource.FOOD, 0);
        return s;
    }

    @Test
    void secondExileNeverReusesTheIdOfAnAutonomousCamp() {
        VillageState s = discontentVillage(Role.GUARD, Role.FARMER);
        List<VillageState> w = world(s);
        run(w, cfg.getInt(Key.CONTEST_AFTER_S) + cfg.getInt(Key.EXILE_AFTER_S));
        VillageState first = CrisisEngine.campOf(w, s);
        assertNotNull(first);
        first.setParentId(null); // camp devenu autonome après une trêve
        s.setLevel(CrisisLevel.CONTESTATION);
        s.setExileTimer(cfg.get(Key.EXILE_AFTER_S));
        for (int i = 0; i < 6; i++) {
            s.villagers().add(villager(Role.FARMER, 5));
        }
        s.setBeds(10);
        run(w, 1);
        VillageState second = CrisisEngine.campOf(w, s);
        assertNotNull(second);
        assertTrue(w.contains(first) && !first.id().equals(second.id()), first.id() + " / " + second.id());
        assertEquals(3, w.size());
    }

    @Test
    void villageWithoutWarehouseHasNoFamineAndKeepsItsSatiety() {
        VillageState s = healthy(5);
        s.setStock(Resource.FOOD, 0);
        s.setFoodManaged(false);
        assertFalse(CrisisEngine.indicators(s, cfg).famine());
        double before = s.villagers().get(0).satiety();
        Gauges.step(s, cfg, 600);
        assertEquals(before, s.villagers().get(0).satiety(), 1e-9);
        s.setFoodManaged(true);
        assertTrue(CrisisEngine.indicators(s, cfg).famine());
    }

    @Test
    void inactiveVillageIsFrozen() {
        VillageState s = healthy(5);
        s.setStock(Resource.FOOD, 0);
        s.setActive(false);
        List<SimEvent> events = run(world(s), cfg.getInt(Key.CONTEST_AFTER_S) * 3);
        assertTrue(events.isEmpty());
        assertEquals(CrisisLevel.STABLE, s.level());
        s.setActive(true);
        run(world(s), cfg.getInt(Key.CONTEST_AFTER_S));
        assertEquals(CrisisLevel.CONTESTATION, s.level());
    }

    @Test
    void exileHappensAfterConfiguredDurationAndMovesDiscontents() {
        VillageState s = discontentVillage(Role.GUARD, Role.FARMER);
        List<VillageState> w = world(s);
        int total = cfg.getInt(Key.CONTEST_AFTER_S) + cfg.getInt(Key.EXILE_AFTER_S);
        List<SimEvent> events = run(w, total - 1);
        assertTrue(only(events, SimEvent.Exile.class).isEmpty());
        assertEquals(CrisisLevel.CONTESTATION, s.level());
        events = run(w, 1);
        List<SimEvent.Exile> exiles = only(events, SimEvent.Exile.class);
        assertEquals(1, exiles.size());
        assertEquals(5, exiles.get(0).villagerIds().size());
        assertEquals(CrisisLevel.EXIL, s.level());
        assertEquals(2, s.population());
        assertEquals(2, w.size());
        VillageState camp = CrisisEngine.campOf(w, s);
        assertNotNull(camp);
        assertEquals(Faction.DISSIDENT, camp.faction());
        assertEquals("v1", camp.parentId());
        assertEquals(exiles.get(0).campId(), camp.id());
        assertEquals(5, camp.population());
        assertEquals(5, camp.beds());
        // Une part des exilés devient garde.
        assertEquals((int) Math.ceil(5 * cfg.get(Key.CAMP_GUARD_SHARE)), camp.count(Role.GUARD));
        for (VillagerState e : camp.villagers()) {
            assertEquals(camp.id(), e.factionId());
        }
    }

    @Test
    void exilesTakeTheirShareOfFood() {
        VillageState s = discontentVillage(Role.GUARD, Role.FARMER);
        s.setStock(Resource.FOOD, 10); // famine : 10 < 7 × 4 × 2 jours
        List<VillageState> w = world(s);
        List<SimEvent> events = run(w, cfg.getInt(Key.CONTEST_AFTER_S) + cfg.getInt(Key.EXILE_AFTER_S));
        int share = (int) Math.floor(10 * cfg.get(Key.EXILE_FOOD_SHARE));
        assertTrue(share > 0);
        assertEquals(share, only(events, SimEvent.Exile.class).get(0).foodMoved());
        assertEquals(share, CrisisEngine.campOf(w, s).stock(Resource.FOOD));
        assertEquals(10 - share, s.stock(Resource.FOOD));
    }

    @Test
    void loyalVillageNeverExilesEvenInFamine() {
        VillageState s = healthy(6);
        s.setStock(Resource.FOOD, 0);
        List<VillageState> w = world(s);
        List<SimEvent> events = run(w, 3000);
        assertEquals(CrisisLevel.CONTESTATION, s.level());
        assertTrue(only(events, SimEvent.Exile.class).isEmpty());
        assertEquals(1, w.size());
    }

    @Test
    void atLeastOneVillagerAlwaysStays() {
        VillageState s = new VillageState("v1", Faction.LOYALIST);
        s.setBeds(4);
        s.villagers().add(villager(Role.FARMER, 5));
        s.villagers().add(villager(Role.FARMER, 10));
        List<VillageState> w = world(s);
        run(w, cfg.getInt(Key.CONTEST_AFTER_S) + cfg.getInt(Key.EXILE_AFTER_S));
        assertEquals(1, s.population());
        assertEquals(1, CrisisEngine.campOf(w, s).population());
    }

    @Test
    void singleVillagerNeverExiles() {
        VillageState s = new VillageState("v1", Faction.LOYALIST);
        s.setBeds(0);
        s.villagers().add(villager(Role.FARMER, 5));
        List<VillageState> w = world(s);
        run(w, 3000);
        assertEquals(1, w.size());
        assertEquals(CrisisLevel.CONTESTATION, s.level());
    }

    @Test
    void exileLevelRecedesWhenCauseDisappears() {
        VillageState s = discontentVillage(Role.FARMER, Role.FARMER);
        List<VillageState> w = world(s);
        run(w, cfg.getInt(Key.CONTEST_AFTER_S) + cfg.getInt(Key.EXILE_AFTER_S));
        assertEquals(CrisisLevel.EXIL, s.level());
        s.setStock(Resource.FOOD, 1000);
        s.villagers().forEach(v -> v.setLoyalty(80));
        int slow = (int) (cfg.get(Key.EXILE_AFTER_S) / cfg.get(Key.CRISIS_RECOVERY_FACTOR));
        run(w, slow - 5);
        assertEquals(CrisisLevel.EXIL, s.level());
        run(w, 10);
        assertEquals(CrisisLevel.CONTESTATION, s.level());
    }

    // --- Guerre civile ---

    /** Amène un village jusqu'à l'exil et renvoie le monde (village EXIL + campement). */
    private List<VillageState> afterExile(VillageState s) {
        List<VillageState> w = world(s);
        run(w, cfg.getInt(Key.CONTEST_AFTER_S) + cfg.getInt(Key.EXILE_AFTER_S));
        assertEquals(CrisisLevel.EXIL, s.level());
        return w;
    }

    @Test
    void tensionFormulaFollowsDesign() {
        VillageState village = healthy(3);
        VillageState camp = new VillageState("c", Faction.DISSIDENT);
        camp.villagers().add(villager(Role.GUARD, 10));
        // loyalistes 80, dissidents 10 : (90 + 20) / 2 = 55 ; + pénalité sans diplomate
        assertEquals(55 + cfg.get(Key.WAR_NO_DIPLOMAT_PENALTY), CrisisEngine.tension(village, camp, cfg), 1e-9);
        village.villagers().get(0).setRole(Role.DIPLOMAT);
        assertEquals(55, CrisisEngine.tension(village, camp, cfg), 1e-9);
    }

    @Test
    void warStartsWhenTensionIsHighAndSustained() {
        VillageState s = discontentVillage(Role.GUARD, Role.FARMER);
        List<VillageState> w = afterExile(s);
        VillageState camp = CrisisEngine.campOf(w, s);
        assertTrue(CrisisEngine.tension(s, camp, cfg) >= cfg.get(Key.WAR_TENSION));
        int after = cfg.getInt(Key.WAR_AFTER_S);
        assertTrue(only(run(w, after - 1), SimEvent.WarStarted.class).isEmpty());
        assertEquals(CrisisLevel.EXIL, s.level());
        List<SimEvent> events = run(w, 1);
        assertEquals(List.of(new SimEvent.WarStarted("v1", camp.id())), only(events, SimEvent.WarStarted.class));
        assertEquals(CrisisLevel.GUERRE_CIVILE, s.level());
        assertEquals(CrisisLevel.GUERRE_CIVILE, camp.level());
    }

    @Test
    void diplomatLowersTensionBelowWarThreshold() {
        VillageState s = discontentVillage(Role.GUARD, Role.DIPLOMAT);
        List<VillageState> w = afterExile(s);
        VillageState camp = CrisisEngine.campOf(w, s);
        assertTrue(CrisisEngine.tension(s, camp, cfg) < cfg.get(Key.WAR_TENSION));
        assertTrue(only(run(w, 2000), SimEvent.WarStarted.class).isEmpty());
        assertEquals(CrisisLevel.EXIL, s.level());
    }

    @Test
    void noWarWithoutCombatantsOnBothSides() {
        VillageState s = discontentVillage(Role.FARMER, Role.FARMER); // aucun garde loyaliste
        List<VillageState> w = afterExile(s);
        assertTrue(only(run(w, 2000), SimEvent.WarStarted.class).isEmpty());
        assertEquals(CrisisLevel.EXIL, s.level());
    }

    @Test
    void truceEndsTheWar() {
        VillageState s = discontentVillage(Role.GUARD, Role.FARMER);
        List<VillageState> w = afterExile(s);
        VillageState camp = CrisisEngine.campOf(w, s);
        run(w, cfg.getInt(Key.WAR_AFTER_S));
        assertEquals(CrisisLevel.GUERRE_CIVILE, s.level());
        // Un diplomate vivant amène les relations au seuil de trêve.
        s.villagers().get(s.population() - 1).setRole(Role.DIPLOMAT);
        s.setRelations(cfg.get(Key.TRUCE_RELATIONS));
        List<SimEvent> events = run(w, 1);
        assertEquals(List.of(new SimEvent.WarEnded("v1", camp.id(), true)), only(events, SimEvent.WarEnded.class));
        assertEquals(CrisisLevel.EXIL, s.level());
        assertEquals(CrisisLevel.CONTESTATION, camp.level());
        assertEquals(2, w.size()); // le camp devient autonome
        // La trêve tient : pas de nouvelle guerre tant que les relations restent hautes.
        assertTrue(only(run(w, 2000), SimEvent.WarStarted.class).isEmpty());
    }

    @Test
    void relationsWithoutLivingDiplomatDoNotMakeTruce() {
        VillageState s = discontentVillage(Role.GUARD, Role.FARMER);
        List<VillageState> w = afterExile(s);
        s.setRelations(100);
        run(w, cfg.getInt(Key.WAR_AFTER_S));
        assertEquals(CrisisLevel.GUERRE_CIVILE, s.level());
    }

    @Test
    void warEndsWhenACampLosesAllItsFighters() {
        VillageState s = discontentVillage(Role.GUARD, Role.FARMER);
        List<VillageState> w = afterExile(s);
        VillageState camp = CrisisEngine.campOf(w, s);
        run(w, cfg.getInt(Key.WAR_AFTER_S));
        assertEquals(CrisisLevel.GUERRE_CIVILE, s.level());
        camp.villagers().removeIf(v -> v.role() == Role.GUARD);
        List<SimEvent> events = run(w, 1);
        assertEquals(List.of(new SimEvent.WarEnded("v1", camp.id(), false)), only(events, SimEvent.WarEnded.class));
        assertEquals(CrisisLevel.EXIL, s.level());
        assertEquals(CrisisLevel.CONTESTATION, camp.level());
    }

    @Test
    void emptyCampIsRemovedWhenWarEndsByElimination() {
        VillageState s = discontentVillage(Role.GUARD, Role.FARMER);
        List<VillageState> w = afterExile(s);
        VillageState camp = CrisisEngine.campOf(w, s);
        run(w, cfg.getInt(Key.WAR_AFTER_S));
        camp.villagers().clear();
        run(w, 1);
        assertNull(CrisisEngine.campOf(w, s));
        assertEquals(1, w.size());
    }

    @Test
    void secondExileJoinsTheExistingCamp() {
        VillageState s = discontentVillage(Role.FARMER, Role.FARMER);
        List<VillageState> w = afterExile(s);
        VillageState camp = CrisisEngine.campOf(w, s);
        // La cause disparaît, le village redescend, puis une nouvelle crise éclate.
        s.setStock(Resource.FOOD, 1000);
        s.villagers().forEach(v -> v.setLoyalty(80));
        run(w, 4000);
        assertEquals(CrisisLevel.STABLE, s.level());
        for (int i = 0; i < 4; i++) {
            s.villagers().add(villager(Role.FARMER, 3));
        }
        s.setStock(Resource.FOOD, 0);
        run(w, 1000);
        assertEquals(2, w.size());
        assertTrue(camp.population() > 5);
    }

    // --- Déterminisme et orchestration ---

    @Test
    void identicalRunsGiveIdenticalEvents() {
        List<SimEvent> a = scenarioEvents();
        List<SimEvent> b = scenarioEvents();
        assertFalse(a.isEmpty());
        assertEquals(a.toString(), b.toString());
    }

    private List<SimEvent> scenarioEvents() {
        VillageState s = discontentVillage(Role.GUARD, Role.FARMER);
        List<VillagerState> fixed = new ArrayList<>();
        long n = 0;
        for (VillagerState v : s.villagers()) {
            fixed.add(new VillagerState(new UUID(0, n++), v.role(), "v1", v.morale(), v.satiety(), v.loyalty()));
        }
        s.villagers().clear();
        s.villagers().addAll(fixed);
        return run(world(s), 1000);
    }

    @Test
    void simulationRunsGaugesThenCrisesAndStarvationLeadsToContestation() {
        Simulation sim = new Simulation(cfg);
        VillageState s = sim.add(healthy(6));
        s.setStock(Resource.FOOD, 0);
        List<SimEvent> events = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            events.addAll(sim.step(1.0));
        }
        assertEquals(CrisisLevel.CONTESTATION, s.level());
        assertTrue(s.villagers().get(0).satiety() < 80, "la satiété a baissé sous la famine");
        assertEquals(1, only(events, SimEvent.CrisisChanged.class).size());
        assertTrue(sim.step(0).isEmpty());
    }
}
