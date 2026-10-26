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
import rrp.core.Resource;
import rrp.core.logistics.Forge;
import rrp.neoforge.ExploitationWatcher;
import rrp.neoforge.logistics.Armoury;
import rrp.neoforge.logistics.CarriedItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Casse minerais (en priorité) et pierre naturelle proches de son poste, et porte le butin à l'entrepôt à pied. */
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

    /**
     * The miner is the village smith: while the guards lack kit pieces and the warehouse can pay for the next one, it
     * carries the ore (or wood, for a bow) from the warehouse to the armoury, forges there for {@code forge_ticks}
     * and stocks the piece. Returns true if it fetched material or started forging.
     */
    private static boolean smith(BehaviorContext ctx) {
        var site = ctx.site();
        var level = ctx.level();
        var v = ctx.villager();
        var next = Forge.next(Armoury.needs(site, level));
        if (next.isEmpty()) {
            return false;
        }
        var cost = Armoury.cost(next.get(), ctx.config());
        int ore = CarriedItems.count(v, Resource.ORE);
        int wood = CarriedItems.count(v, Resource.WOOD);
        if (Forge.affordable(cost, ore, wood)) {
            return ctx.logistics().requestForge(v, site, level) != null;
        }
        Resource lacking = ore < cost.ore() ? Resource.ORE : Resource.WOOD;
        int want = lacking == Resource.ORE ? cost.ore() - ore : cost.wood() - wood;
        if (ctx.warehouse().read(site, level).getOrDefault(lacking, 0) < want) {
            return false; // the warehouse cannot pay for it: back to the rock
        }
        var got = ctx.logistics().access().withdraw(v, site, level, lacking, want, true);
        return got.moved() > 0 || got.tripRequested();
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        if (!ctx.site().hasWarehouse()) {
            return false;
        }
        if (ctx.site().hasArmoury() && smith(ctx)) {
            return true;
        }
        if (!hasRoom(ctx)) {
            return unload(ctx); // full: walk to the warehouse and deposit
        }
        Level level = ctx.level();
        List<BlockPos> targets = new ArrayList<>(nearby(ctx, 6, 3, Miner::isOre).stream().filter(p -> isExposed(level, p)).toList());
        targets.addAll(nearby(ctx, 2, 3, s -> NATURAL_STONE.contains(s.getBlock())).stream().filter(p -> isExposed(level, p)).toList());
        targets = new ArrayList<>(ExploitationWatcher.filter(ctx, targets));
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        int done = 0;
        for (BlockPos pos : targets) {
            if (done >= MAX_PER_RUN || !hasRoom(ctx)) {
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
                carry(ctx, drop, pos);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            ExploitationWatcher.worked(ctx, pos);
            done++;
        }
        if (done == 0 || !hasRoom(ctx)) {
            unload(ctx); // no more rock in range, or the arms are full: take the load home
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
