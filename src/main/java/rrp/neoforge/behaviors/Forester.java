package rrp.neoforge.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import rrp.core.Role;
import rrp.neoforge.ExploitationWatcher;

import java.util.List;

/** Abat des troncs proches (par le haut du tronc), replante une pousse au pied et porte le bois à l'entrepôt à pied. */
public final class Forester extends RoleBehavior {

    /** Troncs coupés au plus à chaque action. */
    static final int MAX_PER_RUN = 4;

    @Override
    public Role role() {
        return Role.FORESTER;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        if (!ctx.site().hasWarehouse()) {
            return false;
        }
        if (!hasRoom(ctx)) {
            return unload(ctx); // full: walk to the warehouse and deposit
        }
        Level level = ctx.level();
        int done = 0;
        for (int i = 0; i < MAX_PER_RUN && hasRoom(ctx); i++) {
            List<BlockPos> logs = nearby(ctx, 2, 12, Forester::isLog).stream()
                    .filter(p -> isTreeTrunk(level, p)).toList();
            logs = ExploitationWatcher.filter(ctx, logs);
            if (logs.isEmpty()) {
                break;
            }
            BlockPos log = logs.get(0);
            while (isLog(level.getBlockState(log.above()))) {
                log = log.above();
            }
            if (done == 0) {
                walkToward(ctx, log);
            }
            BlockState kind = level.getBlockState(log);
            for (ItemStack drop : Block.getDrops(kind, ctx.level(), log, null)) {
                carry(ctx, drop, log);
            }
            level.setBlock(log, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (level.getBlockState(log.below()).is(BlockTags.DIRT)) {
                level.setBlock(log, sapling(kind), Block.UPDATE_CLIENTS);
            }
            ExploitationWatcher.worked(ctx, log);
            done++;
        }
        if (done == 0 || !hasRoom(ctx)) {
            unload(ctx); // no more tree in range, or the arms are full: take the load home
        }
        return done > 0;
    }

    /**
     * Vrai si le tronc appartient à un arbre : sa colonne porte du feuillage à son sommet. Les piliers
     * des huttes et les cabanes (bois sans feuilles) ne sont jamais abattus.
     */
    static boolean isTreeTrunk(Level level, BlockPos b) {
        BlockPos top = b;
        while (isLog(level.getBlockState(top.above()))) {
            top = top.above();
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (level.getBlockState(top.offset(dx, dy, dz)).is(BlockTags.LEAVES)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean isLog(BlockState s) {
        return s.is(BlockTags.LOGS) && !BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().startsWith("stripped_");
    }

    /** Pousse correspondant à l'essence du tronc (chêne par défaut). */
    static BlockState sapling(BlockState log) {
        String path = BuiltInRegistries.BLOCK.getKey(log.getBlock()).getPath().replace("_log", "_sapling");
        Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(path));
        return (b.defaultBlockState().is(BlockTags.SAPLINGS) ? b : Blocks.OAK_SAPLING).defaultBlockState();
    }
}
