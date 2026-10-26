package rrp.neoforge.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.logistics.WaitPolicy;
import rrp.core.logistics.WaitPolicy.Choice;
import rrp.neoforge.RrpMod;
import rrp.neoforge.logistics.ShortageBoard;

import java.util.Set;
import java.util.WeakHashMap;
import java.util.Map;

/**
 * What a worker does at an empty chest instead of standing idle: it complains to the steward once (a chat line and a
 * shortage flag the steward reads), then helps clearing the land (breaks grass and bushes nearby) or sits down. The
 * wait ends when the worker gets what it needs again ({@link #end}).
 */
public final class ActiveWait {

    /** Vegetation a waiting worker clears, by registry path (stable across game versions). */
    static final Set<String> CLEARABLE = Set.of("grass", "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "bush");
    /** Distance (blocks) within which a waiting worker walks to clear vegetation. */
    static final int CLEAR_RADIUS = 6;
    /** Distance (blocks) at which a worker breaks the plant it walked to. */
    static final double CLEAR_REACH = 2.5;

    private static final class Wait {
        boolean complained;
        boolean sitting;
        long lastScan = Long.MIN_VALUE;
    }

    private static final Map<Villager, Wait> WAITS = new WeakHashMap<>();
    private static long complaints;
    private static long cleared;
    private static long sits;

    private ActiveWait() {
    }

    /** Complaints made, plants cleared and times a worker sat down since the start (tests, diagnostic). */
    public static long complaints() { return complaints; }
    public static long cleared() { return cleared; }
    public static long sits() { return sits; }

    public static boolean waiting(Villager v) {
        return WAITS.containsKey(v);
    }

    public static boolean sitting(Villager v) {
        Wait w = WAITS.get(v);
        return w != null && w.sitting;
    }

    public static void reset() {
        WAITS.clear();
        complaints = 0;
        cleared = 0;
        sits = 0;
    }

    /**
     * The chest holds no {@code r}: the worker waits actively. Returns the way it waits this time.
     */
    public static Choice hold(BehaviorContext ctx, Resource r) {
        Villager v = ctx.villager();
        Wait w = WAITS.computeIfAbsent(v, k -> new Wait());
        long now = ctx.level().getGameTime();
        long retryTicks = (long) (ctx.config().get(SimConfig.Key.WAIT_RETRY_S) * 20);
        if (w.sitting && !WaitPolicy.shouldRetry(now - w.lastScan, retryTicks)) {
            return Choice.SIT; // seated: look around again (and re-read the chest) only every wait_retry_s
        }
        w.lastScan = now;
        boolean steward = ctx.site().state().villagers().stream().anyMatch(s -> s.role() == Role.STEWARD);
        BlockPos plant = nearestPlant(ctx);
        Choice choice = WaitPolicy.choose(w.complained, steward, plant != null);
        switch (choice) {
            case COMPLAIN -> {
                w.complained = true;
                complaints++;
                ShortageBoard.flag(ctx.site().id(), r);
                RrpMod.crises().tell(ctx.site(), RrpMod.lang().msg("logistics.complain", ctx.state().role().label()));
            }
            case CLEAR -> clear(ctx, plant);
            case SIT -> {
                if (!w.sitting) {
                    sits++;
                }
                w.sitting = true;
                v.getNavigation().stop();
                v.setShiftKeyDown(true);
            }
        }
        if (choice != Choice.SIT) {
            stand(v, w);
        }
        return choice;
    }

    /** The worker has what it needs again: the wait is over. */
    public static void end(Villager v) {
        Wait w = WAITS.remove(v);
        if (w != null) {
            stand(v, w);
        }
    }

    private static void stand(Villager v, Wait w) {
        if (w.sitting) {
            w.sitting = false;
            v.setShiftKeyDown(false);
        }
    }

    private static BlockPos nearestPlant(BehaviorContext ctx) {
        BlockPos at = ctx.villager().blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -CLEAR_RADIUS; x <= CLEAR_RADIUS; x++) {
            for (int z = -CLEAR_RADIUS; z <= CLEAR_RADIUS; z++) {
                for (int y = -2; y <= 2; y++) {
                    p.set(at.getX() + x, at.getY() + y, at.getZ() + z);
                    if (!ctx.level().hasChunkAt(p) || !clearable(ctx.level().getBlockState(p))) {
                        continue;
                    }
                    double d = p.distSqr(at);
                    if (d < bestD) {
                        bestD = d;
                        best = p.immutable();
                    }
                }
            }
        }
        return best;
    }

    static boolean clearable(BlockState s) {
        return CLEARABLE.contains(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath());
    }

    private static void clear(BehaviorContext ctx, BlockPos plant) {
        Villager v = ctx.villager();
        if (!v.isNoAi() && v.distanceToSqr(plant.getX() + 0.5, plant.getY(), plant.getZ() + 0.5) > CLEAR_REACH * CLEAR_REACH) {
            v.getNavigation().moveTo(plant.getX() + 0.5, plant.getY(), plant.getZ() + 0.5, 0.5);
            return;
        }
        ctx.level().destroyBlock(plant, false, v);
        cleared++;
    }
}
