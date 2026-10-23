package rrp.bukkit.behaviors;

import org.bukkit.World;
import org.bukkit.entity.Villager;
import rrp.bukkit.VillageRegistry;
import rrp.bukkit.VillageSite;
import rrp.bukkit.WarehouseService;
import rrp.core.SimConfig;
import rrp.core.VillagerState;

/** Tout ce dont une action de rôle a besoin : le villageois, son état, son village, l'entrepôt et le registre. */
public record BehaviorContext(World world, Villager villager, VillagerState state, VillageSite site,
                              WarehouseService warehouse, SimConfig config, VillageRegistry registry) {

    /** Rayon de travail (blocs) autour du villageois. */
    public int workRadius() {
        return (int) config.get(SimConfig.Key.WORK_RADIUS);
    }
}
