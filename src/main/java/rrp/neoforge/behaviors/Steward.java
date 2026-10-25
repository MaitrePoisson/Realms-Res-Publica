package rrp.neoforge.behaviors;

import net.minecraft.core.BlockPos;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.VillagerState;
import rrp.neoforge.WarehouseService;
import rrp.neoforge.urban.ExpansionPlanner;

import java.util.Comparator;
import java.util.List;

/**
 * Gère l'entrepôt : retire de la vraie nourriture du tonneau et la distribue aux villageois les plus
 * affamés d'abord (accès équitable). Il sert aussi ceux qui sont un peu au-dessus du seuil de repas.
 */
public final class Steward extends RoleBehavior {

    /** Villageois servis au plus à chaque action. */
    static final int MAX_SERVED = 3;
    /** Repas distribués au plus à chacun. */
    static final int MAX_MEALS = 2;
    /** Marge au-dessus du seuil de repas à partir de laquelle l'intendant sert quelqu'un. */
    static final double SERVE_MARGIN = 20;

    @Override
    public Role role() {
        return Role.STEWARD;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        boolean planned = planExpansion(ctx);
        return feed(ctx) | planned;
    }

    /** Émet un ordre de construction si les quotas de stock et de population le permettent. */
    private static boolean planExpansion(BehaviorContext ctx) {
        var site = ctx.site();
        if (!site.hasHall() || !site.hasWarehouse()) {
            return false;
        }
        var cfg = ctx.config();
        var quotas = new ExpansionPlanner.Quotas((int) cfg.get(SimConfig.Key.EXPANSION_WOOD_QUOTA),
                (int) cfg.get(SimConfig.Key.EXPANSION_STONE_QUOTA), (int) cfg.get(SimConfig.Key.EXPANSION_POPULATION_QUOTA),
                (int) cfg.get(SimConfig.Key.MAX_BUILD_ORDERS));
        var situation = new ExpansionPlanner.Situation(site.state().population(), site.state().beds(),
                site.state().stock(Resource.WOOD), site.state().stock(Resource.STONE), site.hall(), site.built(),
                java.util.List.copyOf(site.orders()));
        var order = ExpansionPlanner.plan(situation, quotas, (int) cfg.get(SimConfig.Key.BUILDER_SPARE_BEDS),
                site.doctrine(), rrp.neoforge.PlayerService.zones().peek(site.id()));
        order.ifPresent(o -> {
            site.orders().add(o);
            ctx.registry().markDirty();
        });
        return order.isPresent();
    }

    private static boolean feed(BehaviorContext ctx) {
        if (!ctx.site().hasWarehouse()) {
            return false;
        }
        double limit = ctx.config().get(SimConfig.Key.EAT_BELOW_SATIETY) + SERVE_MARGIN;
        double perFood = ctx.config().get(SimConfig.Key.SATIETY_PER_FOOD);
        List<VillagerState> hungry = ctx.site().state().villagers().stream()
                .filter(v -> v.satiety() < limit)
                .sorted(Comparator.comparingDouble(VillagerState::satiety))
                .limit(MAX_SERVED)
                .toList();
        if (WarehouseService.barrel(ctx.site(), ctx.level()) == null || hungry.isEmpty()) {
            return false;
        }
        int[] w = ctx.site().warehouse();
        walkToward(ctx, new BlockPos(w[0], w[1], w[2]));
        boolean served = false;
        for (VillagerState v : hungry) {
            for (int i = 0; i < MAX_MEALS && v.satiety() < limit; i++) {
                if (ctx.warehouse().withdraw(ctx.site(), ctx.level(), Resource.FOOD, 1) < 1) {
                    return served;
                }
                v.setSatiety(v.satiety() + perFood);
                served = true;
            }
        }
        return served;
    }
}
