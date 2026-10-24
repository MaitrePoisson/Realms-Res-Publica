package rrp.neoforge.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import rrp.core.Role;
import rrp.neoforge.ExploitationWatcher;

import java.util.List;

/** Récolte les cultures mûres proches, les replante (âge 0) et dépose la récolte à l'entrepôt. */
public final class Farmer extends RoleBehavior {

    /** Cultures récoltées au plus à chaque action. */
    static final int MAX_PER_RUN = 4;

    @Override
    public Role role() {
        return Role.FARMER;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        if (!ctx.site().hasWarehouse()) {
            return false;
        }
        List<BlockPos> ripe = ExploitationWatcher.filter(ctx, nearby(ctx, 3, 3, Farmer::isRipe));
        int done = 0;
        for (BlockPos pos : ripe) {
            if (done >= MAX_PER_RUN) {
                break;
            }
            if (done == 0) {
                walkToward(ctx, pos);
            }
            BlockState state = ctx.level().getBlockState(pos);
            for (ItemStack drop : Block.getDrops(state, ctx.level(), pos, null)) {
                store(ctx, drop, pos);
            }
            ctx.level().setBlock(pos, ((CropBlock) state.getBlock()).getStateForAge(0), Block.UPDATE_CLIENTS);
            ExploitationWatcher.worked(ctx, pos);
            done++;
        }
        return done > 0;
    }

    static boolean isRipe(BlockState s) {
        return s.getBlock() instanceof CropBlock c && c.isMaxAge(s);
    }
}
