package rrp.core.building;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns what a build needs (planks, sticks, fences, a crafting table...) into the ordered crafting steps that make it
 * from what the builder carries, starting from logs. One {@link Step} is one craft the builder performs in plain view
 * (item in hand, arm swing, {@code craft_step_ticks} later the result). Pure logic: the world side does the gestures.
 */
public final class CraftPlan {

    /** The items a builder crafts. */
    public enum Item {
        LOG, PLANKS, STICK, FENCE, FENCE_GATE, CRAFTING_TABLE, DOOR, SLAB, TRAPDOOR
    }

    /** One recipe: {@code outCount} of {@code output} from {@code inputs}; {@code table} if it needs a crafting table (3x3). */
    public record Recipe(Item output, int outCount, Map<Item, Integer> inputs, boolean table) { }

    /** One craft in the plan: apply {@code recipe} once, taking {@code ticks} ticks. */
    public record Step(Recipe recipe, int ticks) { }

    /** The result: the steps in order, whether the needs can be met, and whether a crafting table is needed. */
    public record Plan(List<Step> steps, boolean feasible, boolean needsTable, Map<Item, Integer> missing) {

        public long totalTicks() {
            long t = 0;
            for (Step s : steps) {
                t += s.ticks();
            }
            return t;
        }
    }

    private static final Map<Item, Recipe> RECIPES = new EnumMap<>(Item.class);

    static {
        add(Item.PLANKS, 4, false, Item.LOG, 1);
        add(Item.STICK, 4, false, Item.PLANKS, 2);
        add(Item.CRAFTING_TABLE, 1, false, Item.PLANKS, 4);
        add(Item.FENCE, 3, true, Item.PLANKS, 4, Item.STICK, 2);
        add(Item.FENCE_GATE, 1, true, Item.PLANKS, 2, Item.STICK, 4);
        add(Item.DOOR, 3, true, Item.PLANKS, 6);
        add(Item.SLAB, 6, true, Item.PLANKS, 3);
        add(Item.TRAPDOOR, 2, true, Item.PLANKS, 6);
    }

    private CraftPlan() {
    }

    private static void add(Item out, int count, boolean table, Object... pairs) {
        Map<Item, Integer> in = new EnumMap<>(Item.class);
        for (int i = 0; i < pairs.length; i += 2) {
            in.put((Item) pairs[i], (Integer) pairs[i + 1]);
        }
        RECIPES.put(out, new Recipe(out, count, Map.copyOf(in), table));
    }

    /** The recipe making {@code item}, or {@code null} for a raw item (logs). */
    public static Recipe recipe(Item item) {
        return RECIPES.get(item);
    }

    /**
     * The steps that bring the carried items {@code have} up to {@code need}. Items already carried are used first,
     * only the missing ones are crafted, from the cheapest chain (planks from logs, sticks from planks...). If the logs
     * run out the plan is not {@link Plan#feasible} and {@link Plan#missing} says how many raw items are short.
     */
    public static Plan plan(Map<Item, Integer> need, Map<Item, Integer> have, int stepTicks) {
        Map<Item, Integer> stock = new EnumMap<>(Item.class);
        stock.putAll(have);
        List<Step> steps = new ArrayList<>();
        Map<Item, Integer> missing = new EnumMap<>(Item.class);
        for (Item item : Item.values()) {
            int n = need.getOrDefault(item, 0);
            if (n > 0) {
                ensure(item, n, stock, steps, missing, Math.max(0, stepTicks));
                stock.merge(item, -n, Integer::sum); // reserved for the build: later needs must not count it again
            }
        }
        boolean table = steps.stream().anyMatch(s -> s.recipe().table());
        return new Plan(List.copyOf(steps), missing.isEmpty(), table, Map.copyOf(missing));
    }

    /** Makes {@code count} of {@code item} available in {@code stock} (crafting what is missing), then reserves them. */
    private static void ensure(Item item, int count, Map<Item, Integer> stock, List<Step> steps,
                               Map<Item, Integer> missing, int stepTicks) {
        int have = stock.getOrDefault(item, 0);
        if (have < count) {
            Recipe r = RECIPES.get(item);
            int shortBy = count - have;
            if (r == null) {
                missing.merge(item, shortBy, Integer::sum);
                stock.put(item, count); // pretend, so that the rest of the plan can still be laid out
            } else {
                int crafts = (shortBy + r.outCount() - 1) / r.outCount();
                for (int c = 0; c < crafts; c++) {
                    for (Map.Entry<Item, Integer> in : r.inputs().entrySet()) {
                        ensure(in.getKey(), in.getValue(), stock, steps, missing, stepTicks);
                        stock.merge(in.getKey(), -in.getValue(), Integer::sum); // reserved for this craft
                    }
                    stock.merge(item, r.outCount(), Integer::sum);
                    steps.add(new Step(r, stepTicks));
                }
            }
        }
    }
}
