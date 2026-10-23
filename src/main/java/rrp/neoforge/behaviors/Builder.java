package rrp.neoforge.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.neoforge.TownHall;
import rrp.neoforge.Worlds;

/**
 * Consomme du bois de l'entrepôt pour bâtir une petite hutte (4×3, murs de 2 blocs, toit, porte ouverte
 * à l'ouest) contenant un vrai lit. Il ne bâtit que s'il manque des lits au village.
 */
public final class Builder extends RoleBehavior {

    static final int WIDTH = 4;
    static final int DEPTH = 3;
    /** Hauteur libre exigée au-dessus du sol (murs de 2 + toit). */
    static final int HEIGHT = 3;

    @Override
    public Role role() {
        return Role.BUILDER;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        if (!ctx.site().hasHall()) {
            return buildTownHall(ctx);
        }
        if (!ctx.site().hasWarehouse() || ctx.site().state().beds()
                >= ctx.site().state().population() + (int) ctx.config().get(SimConfig.Key.BUILDER_SPARE_BEDS)) {
            return false;
        }
        int cost = (int) ctx.config().get(SimConfig.Key.HUT_WOOD);
        if (ctx.warehouse().read(ctx.site(), ctx.level()).getOrDefault(Resource.WOOD, 0) < cost) {
            return false;
        }
        BlockPos origin = findSpot(ctx);
        if (origin == null) {
            return false;
        }
        if (ctx.warehouse().withdraw(ctx.site(), ctx.level(), Resource.WOOD, cost) < cost) {
            return false;
        }
        walkToward(ctx, origin);
        build(ctx.level(), origin);
        ctx.site().state().setBeds(ctx.site().state().beds() + 1);
        ctx.registry().markDirty();
        return true;
    }

    /** Tout village a un Hôtel de Ville : à défaut, le bâtisseur en pose un près du centre. */
    private boolean buildTownHall(BehaviorContext ctx) {
        BlockPos base = TownHall.findSpot(ctx.level(), ctx.site());
        if (base == null) {
            return false;
        }
        walkToward(ctx, base);
        TownHall.build(ctx.level(), base);
        ctx.site().setHall(base.getX(), base.getY(), base.getZ());
        ctx.registry().markDirty();
        return true;
    }

    /** Pose la hutte dont l'angle ouest-nord est {@code origin} (premier bloc libre au-dessus du sol). */
    static void build(ServerLevel level, BlockPos origin) {
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                boolean perimeter = dx == 0 || dz == 0 || dx == WIDTH - 1 || dz == DEPTH - 1;
                boolean door = dx == 0 && dz == DEPTH / 2;
                if (perimeter && !door) {
                    level.setBlock(origin.offset(dx, 0, dz), planks, Block.UPDATE_CLIENTS);
                    level.setBlock(origin.offset(dx, 1, dz), planks, Block.UPDATE_CLIENTS);
                }
                level.setBlock(origin.offset(dx, 2, dz), planks, Block.UPDATE_CLIENTS);
            }
        }
        BlockPos foot = origin.offset(1, 0, DEPTH / 2);
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_CLIENTS);
        level.setBlock(foot.east(), bed.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS);
    }

    /** Emplacement plat et libre le plus proche du bâtisseur, dans son rayon de travail et dans le village. */
    private static BlockPos findSpot(BehaviorContext ctx) {
        BlockPos at = ctx.villager().blockPosition();
        int r = ctx.workRadius();
        for (int d = 3; d <= r; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) {
                        continue;
                    }
                    BlockPos o = at.offset(dx, 0, dz);
                    if (fits(ctx, o)) {
                        return o;
                    }
                }
            }
        }
        return null;
    }

    private static boolean fits(BehaviorContext ctx, BlockPos o) {
        ServerLevel w = ctx.level();
        String name = Worlds.name(w);
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                int x = o.getX() + dx;
                int z = o.getZ() + dz;
                if (!w.hasChunk(x >> 4, z >> 4) || !ctx.site().contains(name, x + 0.5, z + 0.5)) {
                    return false;
                }
                if (!w.getBlockState(new BlockPos(x, o.getY() - 1, z)).blocksMotion()) {
                    return false;
                }
                for (int dy = 0; dy < HEIGHT; dy++) {
                    if (!w.getBlockState(new BlockPos(x, o.getY() + dy, z)).isAir()) {
                        return false;
                    }
                }
            }
        }
        // jamais sur un être vivant : la hutte l'emmurerait (il mourrait étouffé)
        return w.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(o.getX(), o.getY(), o.getZ(), o.getX() + WIDTH, o.getY() + HEIGHT, o.getZ() + DEPTH)).isEmpty();
    }
}
