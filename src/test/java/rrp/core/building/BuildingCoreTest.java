package rrp.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import rrp.core.building.CraftPlan.Item;

class BuildingCoreTest {

    private static List<int[]> box(int w, int h, int d) {
        List<int[]> l = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    l.add(new int[] {x + 100, y + 64, z - 30});
                }
            }
        }
        return l;
    }

    @Test
    void blocksGoBottomUpLayerByLayer() {
        List<int[]> sorted = BlockOrder.sort(box(4, 3, 3));
        int y = Integer.MIN_VALUE;
        for (int[] p : sorted) {
            assertTrue(p[1] >= y, "a lower block comes after a higher one");
            y = p[1];
        }
        assertEquals(1, BlockOrder.layersTouched(sorted, 12));
        assertEquals(2, BlockOrder.layersTouched(sorted, 13));
        assertEquals(3, BlockOrder.layersTouched(sorted, 36));
    }

    @Test
    void sortIsStableWhateverTheInputOrder() {
        List<int[]> a = box(5, 2, 4);
        List<int[]> b = new ArrayList<>(a);
        Collections.shuffle(b, new Random(7));
        List<int[]> sa = BlockOrder.sort(a);
        List<int[]> sb = BlockOrder.sort(b);
        for (int i = 0; i < sa.size(); i++) {
            assertEquals(List.of(sa.get(i)[0], sa.get(i)[1], sa.get(i)[2]), List.of(sb.get(i)[0], sb.get(i)[1], sb.get(i)[2]));
        }
        assertEquals(40, sa.size());
    }

    @Test
    void snakeWalkKeepsStepsShort() {
        List<int[]> sorted = BlockOrder.sort(box(6, 1, 6));
        // a plain row-by-row scan would jump back 5 blocks on each new row: the snake never walks more than 2 between blocks
        for (int i = 1; i < sorted.size(); i++) {
            int d = Math.abs(sorted.get(i)[0] - sorted.get(i - 1)[0]) + Math.abs(sorted.get(i)[2] - sorted.get(i - 1)[2]);
            assertTrue(d <= 1, "jump of " + d + " at " + i);
        }
        assertEquals(35, BlockOrder.pathLength(sorted));
    }

    @Test
    void paceGivesOneBlockPerDelay() {
        BuildPace p = new BuildPace(8);
        assertFalse(p.instant());
        assertEquals(0, p.due(7));
        assertEquals(1, p.due(8));
        assertEquals(5, p.due(47));
        assertEquals(3, p.ticksLeft(5));
        assertFalse(p.ready(7));
        assertTrue(p.ready(8));
        assertEquals(80, p.ticksFor(10));
    }

    @Test
    void paceZeroIsTheLegacyInstantMode() {
        BuildPace p = new BuildPace(0);
        assertTrue(p.instant());
        assertEquals(Long.MAX_VALUE, p.due(0));
        assertTrue(p.ready(0));
        assertEquals(0, p.ticksFor(1000));
        assertTrue(new BuildPace(-5).instant());
    }

    private static Map<Item, Integer> of(Object... pairs) {
        Map<Item, Integer> m = new EnumMap<>(Item.class);
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((Item) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }

    @Test
    void planksComeFromLogsWithoutTable() {
        var plan = CraftPlan.plan(of(Item.PLANKS, 8), of(Item.LOG, 2), 20);
        assertTrue(plan.feasible());
        assertFalse(plan.needsTable());
        assertEquals(2, plan.steps().size());
        assertEquals(Item.PLANKS, plan.steps().get(0).recipe().output());
        assertEquals(40, plan.totalTicks());
    }

    @Test
    void carriedItemsAreUsedBeforeCrafting() {
        var plan = CraftPlan.plan(of(Item.PLANKS, 4), of(Item.PLANKS, 4, Item.LOG, 5), 20);
        assertTrue(plan.feasible());
        assertTrue(plan.steps().isEmpty());
    }

    @Test
    void fencesNeedPlanksSticksAndATable() {
        // 6 fences = 2 crafts of 3 = 8 planks + 4 sticks (= 2 crafts of sticks = 4 planks) = 12 planks = 3 logs
        var plan = CraftPlan.plan(of(Item.FENCE, 6), of(Item.LOG, 3), 10);
        assertTrue(plan.feasible(), "missing " + plan.missing());
        assertTrue(plan.needsTable());
        long fences = plan.steps().stream().filter(s -> s.recipe().output() == Item.FENCE).count();
        assertEquals(2, fences);
        // every input exists when its craft runs: replay the plan on the carried items
        Map<Item, Integer> stock = of(Item.LOG, 3);
        for (var s : plan.steps()) {
            s.recipe().inputs().forEach((i, n) -> {
                stock.merge(i, -n, Integer::sum);
                assertTrue(stock.get(i) >= 0, "craft of " + s.recipe().output() + " lacks " + i);
            });
            stock.merge(s.recipe().output(), s.recipe().outCount(), Integer::sum);
        }
        assertEquals(6, stock.get(Item.FENCE));
        assertEquals(0, stock.getOrDefault(Item.LOG, 0));
    }

    @Test
    void severalNeedsDoNotCountTheSamePlanksTwice() {
        // 8 planks for the walls + a table (4 planks) from 3 logs = 12 planks exactly
        var plan = CraftPlan.plan(of(Item.PLANKS, 8, Item.CRAFTING_TABLE, 1), of(Item.LOG, 3), 20);
        assertTrue(plan.feasible(), "missing " + plan.missing());
        var tooFew = CraftPlan.plan(of(Item.PLANKS, 8, Item.CRAFTING_TABLE, 1), of(Item.LOG, 2), 20);
        assertFalse(tooFew.feasible());
        assertEquals(1, tooFew.missing().get(Item.LOG));
    }

    @Test
    void noLogsMeansAnInfeasiblePlan() {
        var plan = CraftPlan.plan(of(Item.DOOR, 3), of(), 20);
        assertFalse(plan.feasible());
        assertEquals(2, plan.missing().get(Item.LOG) == null ? -1 : plan.missing().get(Item.LOG)); // 6 planks = 2 logs
    }

    @Test
    void surplusIsWhatTheJobDoesNotNeedAnymore() {
        assertEquals(12, Surplus.leftover(20, 8, 0));
        assertEquals(10, Surplus.leftover(20, 8, 2));
        assertEquals(0, Surplus.leftover(5, 8, 0));
        var out = Surplus.toDeposit(Map.of("wood", 20, "stone", 3), Map.of("wood", 8, "stone", 3), 0);
        assertEquals(Map.of("wood", 12), out);
    }

    @Test
    void buildingKeysHaveTheirDefaults() {
        rrp.core.SimConfig c = rrp.core.SimConfig.defaults();
        assertEquals(8, c.get(rrp.core.SimConfig.Key.BUILD_BLOCK_TICKS), 1e-9);
        assertEquals(4.5, c.get(rrp.core.SimConfig.Key.BUILD_REACH), 1e-9);
        assertEquals(20, c.get(rrp.core.SimConfig.Key.CRAFT_STEP_TICKS), 1e-9);
        assertEquals(0, c.get(rrp.core.SimConfig.Key.SURPLUS_KEEP), 1e-9);
        assertEquals(1, c.get(rrp.core.SimConfig.Key.BUILD_PARTICLES), 1e-9);
        assertTrue(new BuildPace((int) c.get(rrp.core.SimConfig.Key.BUILD_BLOCK_TICKS)).ready(8));
    }
}
