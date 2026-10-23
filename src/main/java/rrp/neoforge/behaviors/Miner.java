package rrp.neoforge.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import rrp.core.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Casse minerais (en priorité) et pierre naturelle proches de son poste, et dépose le butin. */
public final class Miner extends RoleBehavior {

    private static final Set<Block> NATURAL_STONE = Set.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.ANDESITE,
            Blocks.DIORITE, Blocks.GRANITE);
    private static final Direction[] FACES = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    /** Blocs cassés au plus à chaque action. */
    static final int MAX_PER_RUN = 3;

    @Override
    public Role role() {
        return Role.MINER;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        if (!ctx.site().hasWarehouse()) {
            return false;
        }
        Level level = ctx.level();
        List<BlockPos> targets = new ArrayList<>(nearby(ctx, 6, 3, Miner::isOre).stream().filter(p -> isExposed(level, p)).toList());
        targets.addAll(nearby(ctx, 2, 3, s -> NATURAL_STONE.contains(s.getBlock())).stream().filter(p -> isExposed(level, p)).toList());
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        int done = 0;
        for (BlockPos pos : targets) {
            if (done >= MAX_PER_RUN) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (done == 0) {
                walkToward(ctx, pos);
            }
            for (ItemStack drop : Block.getDrops(state, ctx.level(), pos, null, ctx.villager(), pickaxe)) {
                store(ctx, drop, pos);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            done++;
        }
        return done > 0;
    }

    /** Le mineur ne creuse pas de galeries sous le village : il ne prend que ce qui affleure (une face à l'air libre). */
    static boolean isExposed(Level level, BlockPos b) {
        for (Direction f : FACES) {
            if (level.getBlockState(b.relative(f)).isAir()) {
                return true;
            }
        }
        return false;
    }

    static boolean isOre(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().endsWith("_ore");
    }
}
