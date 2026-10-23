package rrp.neoforge.behaviors;

import net.minecraft.core.BlockPos;
import rrp.core.Faction;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.VillageState;
import rrp.core.VillagerState;
import rrp.neoforge.VillageSite;

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
            walkToward(ctx, BlockPos.containing(neighbour.x(), neighbour.y(), neighbour.z()));
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

    /** En pleine rupture, le diplomate négocie avec l'autre camp (campement du village, ou village du campement). */
    private static VillageSite counterpart(BehaviorContext ctx) {
        VillageState mine = ctx.site().state();
        for (VillageSite s : ctx.registry().all()) {
            VillageState o = s.state();
            if (s != ctx.site() && (o.faction() == Faction.DISSIDENT && mine.id().equals(o.parentId())
                    || mine.faction() == Faction.DISSIDENT && o.id().equals(mine.parentId()))) {
                return s;
            }
        }
        return null;
    }

    private static VillageSite nearestNeighbour(BehaviorContext ctx) {
        VillageSite other = counterpart(ctx);
        if (other != null) {
            return other;
        }
        VillageSite me = ctx.site();
        VillageSite best = null;
        double bestDistance = Double.MAX_VALUE;
        for (VillageSite s : ctx.registry().all()) {
            if (s == me || !s.worldName().equals(me.worldName())) {
                continue;
            }
            double d = s.distance(me.worldName(), me.x(), me.z());
            if (d < bestDistance) {
                bestDistance = d;
                best = s;
            }
        }
        return best;
    }
}
