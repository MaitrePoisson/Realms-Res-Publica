package rrp.neoforge.behaviors;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import rrp.core.SimConfig;
import rrp.core.VillagerState;
import rrp.neoforge.VillageRegistry;
import rrp.neoforge.VillageSite;
import rrp.neoforge.WarehouseService;

/** Tout ce dont une action de rôle a besoin : le villageois, son état, son village, l'entrepôt et le registre. */
public record BehaviorContext(ServerLevel level, Villager villager, VillagerState state, VillageSite site,
                              WarehouseService warehouse, SimConfig config, VillageRegistry registry) {

    /** The physical chest layer: carried loads, reach gate, walking trips. */
    public rrp.neoforge.logistics.LogisticsDriver logistics() {
        return rrp.neoforge.RrpMod.logistics();
    }

    /** Rayon de travail (blocs) autour du villageois. */
    public int workRadius() {
        return (int) config.get(SimConfig.Key.WORK_RADIUS);
    }
}
