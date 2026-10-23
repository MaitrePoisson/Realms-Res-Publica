package rrp.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rrp.core.CrisisEngine;
import rrp.core.CrisisLevel;
import rrp.core.SimConfig;
import rrp.core.SimEvent;
import rrp.core.VillageState;
import rrp.sim.ScenarioRunner.Result;
import rrp.sim.ScenarioRunner.Sample;

class ScenarioTest {
    private static final SimConfig CFG = SimConfig.defaults();

    private static Result run(Scenario sc) {
        return ScenarioRunner.run(sc, CFG);
    }

    @Test
    void prosperousVillageGrowsAndStaysStable() {
        Result r = run(Scenario.prosperous());
        Sample first = r.of(Scenario.VILLAGE_ID).get(0);
        Sample last = r.last(Scenario.VILLAGE_ID);
        assertTrue(last.population() > first.population() * 2, "la population croît : " + last.population());
        assertTrue(r.events(SimEvent.CrisisChanged.class).isEmpty(), "aucune crise");
        assertTrue(last.morale() > 70, "moral élevé : " + last.morale());
        assertEquals(CrisisLevel.STABLE, last.level());
        assertEquals(1, r.villageIds().size(), "aucun campement");
    }

    @Test
    void famineGivesContestationThenExile() {
        VillageState start = Scenario.famine().newVillage();
        assertTrue(CrisisEngine.indicators(start, CFG).famine(), "famine dès le départ");
        Result r = run(Scenario.famine());
        double contest = r.firstTimeAt(Scenario.VILLAGE_ID, CrisisLevel.CONTESTATION);
        double exile = r.firstTimeAt(Scenario.VILLAGE_ID, CrisisLevel.EXIL);
        assertTrue(contest > 0, "contestation atteinte");
        assertTrue(exile > contest, "exil après la contestation : " + contest + " puis " + exile);
        assertEquals(1, r.events(SimEvent.Exile.class).size());
        assertEquals(2, r.villageIds().size(), "un campement dissident apparaît");
        assertTrue(r.last(Scenario.VILLAGE_ID).satiety() < 5, "les villageois ont faim");
    }

    @Test
    void overpopulationGivesContestation() {
        VillageState start = Scenario.overpopulation().newVillage();
        assertTrue(CrisisEngine.indicators(start, CFG).overpopulation());
        assertFalse(CrisisEngine.indicators(start, CFG).famine());
        Result r = run(Scenario.overpopulation());
        assertTrue(r.firstTimeAt(Scenario.VILLAGE_ID, CrisisLevel.CONTESTATION) > 0, "contestation atteinte");
    }

    @Test
    void insecurityGivesContestationAsLongAsNoGuardAppears() {
        VillageState start = Scenario.insecurity().newVillage();
        assertEquals(0, start.count(rrp.core.Role.GUARD));
        Result r = run(Scenario.insecurity());
        double contest = r.firstTimeAt(Scenario.VILLAGE_ID, CrisisLevel.CONTESTATION);
        assertTrue(contest > 0 && contest < 600, "contestation précoce : " + contest);
        assertTrue(r.samples().stream().anyMatch(s -> s.threats() > 0), "des menaces apparaissent");
    }

    @Test
    void insecurityAloneEscalatesToExile() {
        Result r = run(Scenario.insecurity());
        double contest = r.firstTimeAt(Scenario.VILLAGE_ID, CrisisLevel.CONTESTATION);
        double exile = r.firstTimeAt(Scenario.VILLAGE_ID, CrisisLevel.EXIL);
        assertTrue(exile > contest, "l'insécurité durable mène à l'exil : " + contest + " puis " + exile);
    }

    @Test
    void runsAreDeterministic() {
        for (Scenario sc : Scenario.all()) {
            assertEquals(Report.csv(run(sc)), Report.csv(run(sc)), sc.name());
        }
    }

    @Test
    void reportsAreWellFormed() {
        Result r = run(Scenario.famine());
        String csv = Report.csv(r);
        assertTrue(csv.startsWith("t_s,village,population,food"));
        assertEquals(1 + r.samples().size(), csv.lines().count());
        String svg = Report.svg(r);
        assertTrue(svg.startsWith("<svg") && svg.trim().endsWith("</svg>"));
        assertTrue(svg.contains("<polyline"));
        for (String t : new String[] {"Population", "Nourriture", "Moral", "Niveau de crise", "exil"}) {
            assertTrue(svg.contains(t), t);
        }
        assertNotNull(Report.summary(r));
    }

    @Test
    void cliWritesCsvAndSvgForEachScenario(@TempDir Path dir) throws IOException {
        SimCli.main(new String[] {dir.toString()});
        for (Scenario sc : Scenario.all()) {
            assertTrue(Files.size(dir.resolve("sim-" + sc.name() + ".svg")) > 1000, sc.name());
            assertTrue(Files.size(dir.resolve("sim-" + sc.name() + ".csv")) > 100, sc.name());
        }
    }
}
