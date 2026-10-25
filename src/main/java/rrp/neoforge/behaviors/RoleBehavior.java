package rrp.neoforge.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import rrp.core.Gauges;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.VillagerState;
import rrp.neoforge.WarehouseService;
import rrp.neoforge.logistics.CarriedItems;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Action concrète d'un rôle dans le monde. Une instance sert tous les villageois de ce rôle. */
public abstract class RoleBehavior {

    /** Rôle servi par ce comportement. */
    public abstract Role role();

    /** Exécute une fois l'action, sans tenir compte de la cadence ni du moral. Renvoie vrai si quelque chose a été fait. */
    public abstract boolean runOnce(BehaviorContext ctx);

    /** Désobéissance déterministe : un moral sous {@code disobey_morale} empêche de travailler. */
    public boolean canWork(VillagerState state, SimConfig config) {
        return Gauges.canWork(state, config);
    }

    /**
     * Positions dont le bloc satisfait {@code wanted} dans le rayon de travail (carré horizontal autour du
     * villageois, de {@code below} blocs en dessous à {@code above} au-dessus), du plus proche au plus lointain.
     */
    protected static List<BlockPos> nearby(BehaviorContext ctx, int below, int above, Predicate<BlockState> wanted) {
        BlockPos at = ctx.villager().blockPosition();
        int r = ctx.workRadius();
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = at.getX() - r; x <= at.getX() + r; x++) {
            for (int z = at.getZ() - r; z <= at.getZ() + r; z++) {
                if (!ctx.level().hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = at.getY() - below; y <= at.getY() + above; y++) {
                    p.set(x, y, z);
                    if (wanted.test(ctx.level().getBlockState(p))) {
                        found.add(p.immutable());
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble((BlockPos b) -> b.distSqr(at)).thenComparingInt(BlockPos::getY));
        return found;
    }

    /** Dépose un objet compté dans l'entrepôt ; le reste (tonneau plein) tombe au sol à {@code spill}. */
    protected static void store(BehaviorContext ctx, ItemStack stack, BlockPos spill) {
        if (stack == null || stack.isEmpty() || WarehouseService.classify(stack) == null) {
            return;
        }
        ItemStack left = ctx.warehouse().deposit(ctx.site(), ctx.level(), stack);
        if (left != null && !left.isEmpty()) {
            Block.popResource(ctx.level(), spill, left);
        }
    }

    /** True while the villager can still carry something (its load is below {@code carry_capacity}). */
    protected static boolean hasRoom(BehaviorContext ctx) {
        return CarriedItems.free(ctx.villager(), ctx.logistics().access().capacity()) > 0;
    }

    /**
     * Puts a drop in the villager's load (never straight into the warehouse). Items that are not counted resources
     * are dropped; the surplus over {@code carry_capacity} falls at {@code spill}.
     */
    protected static void carry(BehaviorContext ctx, ItemStack stack, BlockPos spill) {
        if (stack == null || stack.isEmpty() || WarehouseService.classify(stack) == null) {
            return;
        }
        int took = CarriedItems.add(ctx.villager(), stack, ctx.logistics().access().capacity());
        if (took < stack.getCount()) {
            Block.popResource(ctx.level(), spill, stack.copyWithCount(stack.getCount() - took));
        }
        CarriedItems.showInHand(ctx.villager());
    }

    /**
     * Brings the load to the warehouse: deposits at once when the villager stands within reach of it, otherwise
     * starts the walk to it (the deposit happens when it arrives). Returns true if something was moved or a trip started.
     */
    protected static boolean unload(BehaviorContext ctx) {
        var result = ctx.logistics().access().deposit(ctx.villager(), ctx.site(), ctx.level(), null, true);
        return result.moved() > 0 || result.tripRequested();
    }

    /** Si le villageois a de l'IA, il se dirige vers la position (l'action reste immédiate). */
    protected static void walkToward(BehaviorContext ctx, BlockPos target) {
        if (!ctx.villager().isNoAi()) {
            ctx.villager().getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.5);
        }
    }
}
