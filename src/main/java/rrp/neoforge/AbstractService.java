package rrp.neoforge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.abstractsim.AbstractEconomy;
import rrp.core.abstractsim.YieldProfile;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Economy of ABSTRACT villages: harvests, eating and births are computed mathematically ({@link AbstractEconomy}) on
 * the in-memory village state, with no entity or block touched. The stock at the moment the village fell asleep is
 * remembered; when it wakes up the difference is applied once to the real warehouse and the pending births are spawned,
 * so nothing is duplicated or lost.
 */
public final class AbstractService {

    private final Supplier<SimConfig> config;
    private final Supplier<MinecraftServer> server;
    private final VillageRegistry registry;
    private final WarehouseService warehouse;
    private long accumulated;

    public AbstractService(Supplier<SimConfig> config, Supplier<MinecraftServer> server, VillageRegistry registry,
                           WarehouseService warehouse) {
        this.config = config;
        this.server = server;
        this.registry = registry;
        this.warehouse = warehouse;
    }

    public void onTicks(long ticks) {
        accumulated += ticks;
        long step = (long) config.get().get(SimConfig.Key.ABSTRACT_STEP_TICKS);
        if (accumulated < step) {
            return;
        }
        long elapsed = accumulated;
        accumulated = 0;
        for (VillageSite site : new ArrayList<>(registry.all())) {
            if (site.isAbstract()) {
                advance(site, elapsed);
            }
        }
    }

    /** Advances one abstract village by {@code ticks}; returns the number of births made. */
    public int advance(VillageSite site, long ticks) {
        if (site.abstractBaseline() == null) {
            sleep(site);
        }
        site.setAbstractClock(site.abstractClock() + ticks);
        long seed = site.id().hashCode() * 31L + site.abstractClock();
        site.state().setFoodManaged(site.hasWarehouse());
        AbstractEconomy.Report r = AbstractEconomy.advance(site.state(), config.get(), YieldProfile.of(site.doctrine()),
                ticks / 20.0, seed, site.pendingBirths());
        site.setPendingBirths(site.pendingBirths() + r.births);
        return r.births;
    }

    /** Called when a village turns ABSTRACT: remembers the stock to reconcile later. */
    public void sleep(VillageSite site) {
        Map<Resource, Integer> base = new EnumMap<>(Resource.class);
        for (Resource r : Resource.values()) {
            base.put(r, site.state().stock(r));
        }
        site.setAbstractBaseline(base);
        site.setPendingBirths(0);
    }

    /** True when the warehouse of a sleeping village can be reconciled now (or there is nothing to reconcile). */
    public boolean canWake(VillageSite site, ServerLevel level) {
        return site.abstractBaseline() == null || !site.hasWarehouse() || WarehouseService.barrel(site, level) != null;
    }

    /**
     * Called when a village turns ACTIVE again: applies the stock difference to the real warehouse and spawns the
     * babies born meanwhile (bounded by the free beds).
     */
    public void wake(VillageSite site, ServerLevel level) {
        Map<Resource, Integer> base = site.abstractBaseline();
        site.setAbstractBaseline(null);
        int pending = site.pendingBirths();
        site.setPendingBirths(0);
        if (base == null || level == null) {
            return;
        }
        if (site.hasWarehouse() && WarehouseService.barrel(site, level) != null) {
            Map<Resource, Integer> deltas = new EnumMap<>(Resource.class);
            for (Resource r : Resource.values()) { // before any deposit: depositing refreshes the in-memory stock
                deltas.put(r, site.state().stock(r) - base.get(r));
            }
            for (Resource r : Resource.values()) {
                int delta = deltas.get(r);
                if (delta > 0) {
                    warehouse.deposit(site, level, CaravanService.stackOf(r, delta));
                } else if (delta < 0) {
                    warehouse.withdraw(site, level, r, -delta);
                }
            }
            warehouse.refresh(site, level);
        }
        int free = Math.max(0, site.state().beds() - site.state().population());
        for (int i = 0; i < Math.min(pending, free); i++) {
            RrpMod.births().spawnBaby(site, level);
        }
        registry.markDirty();
    }
}
