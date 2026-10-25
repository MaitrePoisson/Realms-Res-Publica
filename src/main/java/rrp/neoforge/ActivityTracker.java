package rrp.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import rrp.core.SimConfig;
import rrp.core.abstractsim.Lod;
import rrp.core.abstractsim.LodPolicy;

import java.util.function.Supplier;

/**
 * Level-of-detail switch: every {@code lod_check_ticks} each village is tested for "is its hall in an entity-ticking
 * chunk" (a player is near, or the chunk is force-loaded). Only ACTIVE villages get physical behaviour (see
 * {@link WarehouseService#loaded}); the others are left to the abstract simulation.
 */
public final class ActivityTracker {

    private final Supplier<SimConfig> config;
    private final Supplier<MinecraftServer> server;
    private final VillageRegistry registry;
    private long accumulated;

    public ActivityTracker(Supplier<SimConfig> config, Supplier<MinecraftServer> server, VillageRegistry registry) {
        this.config = config;
        this.server = server;
        this.registry = registry;
    }

    public void onTicks(long ticks) {
        accumulated += ticks;
        long interval = Math.max(1, (long) config.get().get(SimConfig.Key.LOD_CHECK_TICKS));
        if (accumulated < interval) {
            return;
        }
        long elapsed = accumulated;
        accumulated = 0;
        for (VillageSite site : registry.all()) {
            evaluate(site, elapsed);
        }
    }

    /** Re-evaluates one village after {@code elapsed} ticks and returns its new level of detail. */
    public Lod evaluate(VillageSite site, long elapsed) {
        SimConfig cfg = config.get();
        if (site.lodForced() != null) {
            return apply(site, site.lodForced(), true);
        }
        if (cfg.get(SimConfig.Key.LOD_ENABLED) < 1) {
            return apply(site, Lod.ACTIVE, true);
        }
        boolean live = live(site);
        site.setLodInactiveTicks(live ? 0 : site.lodInactiveTicks() + elapsed);
        long grace = (long) (cfg.get(SimConfig.Key.LOD_GRACE_S) * 20);
        return apply(site, LodPolicy.next(site.lod(), live, site.lodInactiveTicks(), grace), false);
    }

    /**
     * Switches the village to {@code target}, running the abstract-economy hooks: remember the stock when it falls
     * asleep, reconcile the warehouse when it wakes up. An automatic wake-up waits until the warehouse is readable.
     */
    private Lod apply(VillageSite site, Lod target, boolean forced) {
        if (target == site.lod()) {
            return target;
        }
        AbstractService abstractEconomy = RrpMod.abstractEconomy();
        if (target == Lod.ABSTRACT) {
            abstractEconomy.sleep(site);
        } else {
            ServerLevel w = Worlds.level(server.get(), site.worldName());
            if (!forced && !abstractEconomy.canWake(site, w)) {
                return site.lod();
            }
            abstractEconomy.wake(site, w);
        }
        site.setLod(target);
        return target;
    }

    private boolean live(VillageSite site) {
        ServerLevel w = Worlds.level(server.get(), site.worldName());
        if (w == null) {
            return false;
        }
        BlockPos pos = BlockPos.containing(site.x(), site.y(), site.z());
        return w.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && w.isPositionEntityTicking(pos);
    }
}
