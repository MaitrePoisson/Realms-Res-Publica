package rrp.neoforge.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.neoforge.VillageRegistry;
import rrp.neoforge.VillageSite;
import rrp.neoforge.WarehouseService;

import java.util.List;
import java.util.function.Supplier;

/**
 * The physical gate between a villager and the village stock: a villager can take from or put in a container only
 * when it stands within {@code chest_reach} of it. Out of reach nothing moves ({@code moved == 0}) and, on request, a
 * trip to the container is started by the {@link LogisticsDriver}.
 */
public final class ChestAccess {

    /** Outcome of an access attempt: the units that really moved and whether a trip was requested instead. */
    public record Result(int moved, boolean tripRequested) {
    }

    private final Supplier<SimConfig> config;
    private final WarehouseService warehouse;
    private final VillageRegistry registry;
    private LogisticsDriver driver;

    public ChestAccess(Supplier<SimConfig> config, WarehouseService warehouse, VillageRegistry registry) {
        this.config = config;
        this.warehouse = warehouse;
        this.registry = registry;
    }

    void bind(LogisticsDriver driver) {
        this.driver = driver;
    }

    public double reach() {
        return config.get().get(SimConfig.Key.CHEST_REACH);
    }

    public int capacity() {
        return (int) config.get().get(SimConfig.Key.CARRY_CAPACITY);
    }

    /** Distance from the villager to the centre of a container block. */
    public static double distance(Villager v, BlockPos chest) {
        return v.position().distanceTo(Vec3.atCenterOf(chest));
    }

    /**
     * Takes up to {@code amount} units of {@code r} from the containers within reach into the villager's load
     * (bounded by its free capacity). Out of reach nothing is taken; with {@code requestTrip} a trip is started.
     */
    public Result withdraw(Villager v, VillageSite site, ServerLevel level, Resource r, int amount, boolean requestTrip) {
        int want = Math.min(amount, CarriedItems.free(v, capacity()));
        if (want <= 0 || warehouse.barrel(site, level) == null) {
            return new Result(0, false);
        }
        int moved = 0;
        for (Container c : warehouse.containersInReach(site, level, v.position(), reach())) {
            moved += takeFrom(c, v, r, want - moved);
            if (moved >= want) {
                break;
            }
        }
        if (moved > 0) {
            commit(v, site, level);
            return new Result(moved, false);
        }
        return new Result(0, requestTrip && driver != null
                && driver.requestWithdraw(v, site, level, r, amount) != null);
    }

    /**
     * Puts the carried units of {@code r} ({@code null} = everything counted) into the containers within reach.
     * Out of reach nothing is put; with {@code requestTrip} a trip is started.
     */
    public Result deposit(Villager v, VillageSite site, ServerLevel level, Resource r, boolean requestTrip) {
        int carried = r == null ? CarriedItems.total(v) : CarriedItems.count(v, r);
        if (carried <= 0 || warehouse.barrel(site, level) == null) {
            return new Result(0, false);
        }
        int moved = 0;
        for (Container c : warehouse.containersInReach(site, level, v.position(), reach())) {
            moved += CarriedItems.depositInto(v, r, c);
            c.setChanged();
            if (moved >= carried) {
                break;
            }
        }
        if (moved > 0) {
            commit(v, site, level);
            return new Result(moved, false);
        }
        return new Result(0, requestTrip && driver != null && driver.requestDeposit(v, site, level, r) != null);
    }

    /** The nearest container of the stock that holds {@code r}, or {@code null}. */
    public BlockPos containerWith(VillageSite site, ServerLevel level, Vec3 from, Resource r) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (Container c : warehouse.containers(site, level)) {
            if (c instanceof net.minecraft.world.level.block.entity.BlockEntity be && holds(c, r)) {
                double d = from.distanceToSqr(Vec3.atCenterOf(be.getBlockPos()));
                if (d < bestD) {
                    bestD = d;
                    best = be.getBlockPos();
                }
            }
        }
        return best;
    }

    /** The container a deposit goes to: the designated warehouse barrel, else the first stock container. */
    public BlockPos depositTarget(VillageSite site, ServerLevel level) {
        var primary = warehouse.barrel(site, level);
        if (primary != null) {
            return primary.getBlockPos();
        }
        List<Container> all = warehouse.containers(site, level);
        return !all.isEmpty() && all.get(0) instanceof net.minecraft.world.level.block.entity.BlockEntity be ? be.getBlockPos() : null;
    }

    static boolean holds(Container c, Resource r) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (WarehouseService.classify(c.getItem(i)) == r) {
                return true;
            }
        }
        return false;
    }

    private int takeFrom(Container c, Villager v, Resource r, int n) {
        int taken = 0;
        for (int i = 0; i < c.getContainerSize() && taken < n; i++) {
            ItemStack it = c.getItem(i);
            if (WarehouseService.classify(it) != r) {
                continue;
            }
            int got = CarriedItems.add(v, it.copyWithCount(Math.min(n - taken, it.getCount())), capacity());
            if (got <= 0) {
                break;
            }
            taken += got;
            it.shrink(got);
            if (it.isEmpty()) {
                c.setItem(i, ItemStack.EMPTY);
            }
        }
        c.setChanged();
        return taken;
    }

    private void commit(Villager v, VillageSite site, ServerLevel level) {
        warehouse.refresh(site, level);
        registry.markDirty();
        CarriedItems.showInHand(v);
    }
}
