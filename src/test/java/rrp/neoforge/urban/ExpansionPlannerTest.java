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
}
