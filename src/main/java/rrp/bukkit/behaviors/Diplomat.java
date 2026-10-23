package rrp.bukkit.behaviors;

import org.bukkit.Location;
import rrp.bukkit.VillageSite;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.VillagerState;

/**
 * Visite le village (ou campement) voisin le plus proche : les relations des deux côtés augmentent de
 * {@code relations_per_visit}. Sans voisin, il parle aux autres villageois et renforce leur loyauté.
 */
public final class Diplomat extends RoleBehavior {

    @Override
    public Role role() {
        return Role.DIPLOMAT;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        VillageSite neighbour = nearestNeighbour(ctx);
        if (neighbour != null) {
            double gain = ctx.config().get(SimConfig.Key.RELATIONS_PER_VISIT);
            if (ctx.villager().hasAI()) {
                ctx.villager().getPathfinder().moveTo(neighbour.center(ctx.world()));
            }
            ctx.site().state().setRelations(ctx.site().state().relations() + gain);
            neighbour.state().setRelations(neighbour.state().relations() + gain);
            ctx.registry().markDirty();
            return true;
        }
        double bonus = ctx.config().get(SimConfig.Key.TALK_LOYALTY_BONUS);
        boolean talked = false;
        for (VillagerState v : ctx.site().state().villagers()) {
            if (v != ctx.state()) {
                v.setLoyalty(v.loyalty() + bonus);
                talked = true;
            }
        }
        return talked;
    }

    private static VillageSite nearestNeighbour(BehaviorContext ctx) {
        Location at = ctx.site().center(ctx.world());
        VillageSite best = null;
        double bestDistance = Double.MAX_VALUE;
        for (VillageSite s : ctx.registry().all()) {
            if (s == ctx.site() || !s.worldName().equals(ctx.site().worldName())) {
                continue;
            }
            double d = s.distance(ctx.site().worldName(), at.getX(), at.getZ());
            if (d < bestDistance) {
                bestDistance = d;
                best = s;
            }
        }
        return best;
    }
}
