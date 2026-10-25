package rrp.neoforge.urban;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class ExpansionPlannerTest {

    private static final ExpansionPlanner.Quotas Q = new ExpansionPlanner.Quotas(60, 40, 6, 3);
    private static final int[] HALL = {100, 64, 100};

    private static ExpansionPlanner.Situation sit(int pop, int beds, int wood, int stone, Map<BuildType, Integer> built,
                                                  List<BuildOrder> queued) {
        return new ExpansionPlanner.Situation(pop, beds, wood, stone, HALL, built, queued);
    }

    @Test
    void nothingBelowQuota() {
        assertTrue(ExpansionPlanner.plan(sit(10, 10, 59, 100, Map.of(), List.of()), Q, 2).isEmpty(), "wood below quota");
        assertTrue(ExpansionPlanner.plan(sit(10, 10, 100, 39, Map.of(), List.of()), Q, 2).isEmpty(), "stone below quota");
        assertTrue(ExpansionPlanner.plan(sit(5, 5, 500, 500, Map.of(), List.of()), Q, 2).isEmpty(), "population below quota");
    }

    @Test
    void noHallNoOrder() {
        var s = new ExpansionPlanner.Situation(10, 0, 500, 500, null, Map.of(), List.of());
        assertTrue(ExpansionPlanner.plan(s, Q, 2).isEmpty());
    }

    @Test
    void bedShortageGivesAHouse() {
        Optional<BuildOrder> o = ExpansionPlanner.plan(sit(10, 4, 200, 100, Map.of(), List.of()), Q, 2);
        assertEquals(BuildType.HOUSE, o.orElseThrow().type());
        assertEquals(30, o.get().wood());
    }

    @Test
    void housedVillageGetsAFarmFirst() {
        Optional<BuildOrder> o = ExpansionPlanner.plan(sit(8, 20, 200, 100, Map.of(), List.of()), Q, 2);
        assertEquals(BuildType.FARM, o.orElseThrow().type());
    }

    @Test
    void queuedOrdersReserveStockAndAreCapped() {
        BuildOrder q = BuildOrder.of(BuildType.HOUSE, 1, 64, 1);
        // 100 wood - 30 reserved = 70 >= quota 60 -> still allowed
        assertTrue(ExpansionPlanner.plan(sit(10, 4, 100, 100, Map.of(), List.of(q)), Q, 2).isPresent());
        // 85 - 30 = 55 < 60 -> refused
        assertTrue(ExpansionPlanner.plan(sit(10, 4, 85, 100, Map.of(), List.of(q)), Q, 2).isEmpty());
        // queue full
        assertTrue(ExpansionPlanner.plan(sit(10, 4, 900, 900, Map.of(), List.of(q, q, q)), Q, 2).isEmpty());
    }

    @Test
    void siteIsAwayFromTheHallAndDistinct() {
        int[] a = ExpansionPlanner.slot(HALL, 0);
        int[] b = ExpansionPlanner.slot(HALL, 1);
        assertTrue(Math.hypot(a[0] - HALL[0], a[2] - HALL[2]) >= 13);
        assertTrue(a[0] != b[0] || a[2] != b[2]);
    }

    @Test
    void doctrinesPickDifferentFirstBuildings() {
        var s = sit(16, 16, 500, 500, Map.of(), List.of());
        var merchant = ExpansionPlanner.plan(s, Q, 2, rrp.core.territory.Doctrine.MERCHANT_CITY).orElseThrow().type();
        var empire = ExpansionPlanner.plan(s, Q, 2, rrp.core.territory.Doctrine.WARLIKE_EMPIRE).orElseThrow().type();
        var theocracy = ExpansionPlanner.plan(s, Q, 2, rrp.core.territory.Doctrine.ISOLATIONIST_THEOCRACY).orElseThrow().type();
        assertEquals(BuildType.MARKET, merchant);
        assertEquals(BuildType.BARRACKS, empire);
        assertEquals(BuildType.MONUMENT, theocracy);
    }

    @Test
    void neutralPlanIgnoresDoctrineBuildings() {
        var o = ExpansionPlanner.plan(sit(16, 16, 500, 500, Map.of(), List.of()), Q, 2).orElseThrow();
        assertTrue(o.type() != BuildType.MARKET && o.type() != BuildType.BARRACKS && o.type() != BuildType.MONUMENT);
    }

    @Test
    void zonesGuideTheSite() {
        var farmPlot = List.of(rrp.core.player.Zones.Zone.of(rrp.core.player.ZoneType.FARM, 200, 200, 230, 230));
        BuildOrder o = ExpansionPlanner.plan(sit(8, 20, 200, 100, Map.of(), List.of()), Q, 2, null, farmPlot).orElseThrow();
        assertEquals(BuildType.FARM, o.type());
        assertTrue(farmPlot.get(0).contains(o.x(), o.z()), "farm built inside the market plot");
        // a house with only a market plot drawn stays outside it
        BuildOrder h = ExpansionPlanner.plan(sit(10, 4, 200, 100, Map.of(), List.of()), Q, 2, null,
                List.of(rrp.core.player.Zones.Zone.of(rrp.core.player.ZoneType.MARKET, 110, 90, 130, 110))).orElseThrow();
        assertEquals(BuildType.HOUSE, h.type());
        assertTrue(!(h.x() >= 110 && h.x() <= 130 && h.z() >= 90 && h.z() <= 110), "house avoids the market plot");
    }

    @Test
    void forbiddenPlotsAreNeverBuiltOn() {
        var all = new java.util.ArrayList<rrp.core.player.Zones.Zone>();
        all.add(rrp.core.player.Zones.Zone.of(rrp.core.player.ZoneType.FORBIDDEN, 100, 90, 130, 110));
        for (int built = 0; built < 12; built++) {
            BuildOrder o = ExpansionPlanner.plan(sit(10, 4, 200, 100, Map.of(BuildType.HOUSE, built), List.of()), Q, 2, null, all).orElseThrow();
            assertTrue(rrp.core.player.Zones.typeAt(all, o.x(), o.z()) == null, "free of plots: " + o);
        }
    }
}
