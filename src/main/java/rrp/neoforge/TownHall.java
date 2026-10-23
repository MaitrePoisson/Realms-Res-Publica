package rrp.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Hôtel de Ville : un lutrin posé sur un bloc d'or. Marqueur vanilla (aucun bloc enregistré, donc aucun asset
 * client) qui délimite le cœur du village ; sa position est celle du bloc d'or.
 */
public final class TownHall {

    private TownHall() {
    }

    /** Vrai si {@code base} est un bloc d'or surmonté d'un lutrin. */
    public static boolean isHall(ServerLevel level, BlockPos base) {
        return level.getBlockState(base).is(Blocks.GOLD_BLOCK) && level.getBlockState(base.above()).is(Blocks.LECTERN);
    }

    /** Vrai si {@code p} est le lutrin d'un Hôtel de Ville. */
    public static boolean isLectern(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).is(Blocks.LECTERN) && level.getBlockState(p.below()).is(Blocks.GOLD_BLOCK);
    }

    /** Pose l'Hôtel de Ville (socle d'or + lutrin) avec le socle en {@code base}. */
    public static void build(ServerLevel level, BlockPos base) {
        level.setBlock(base, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(base.above(), Blocks.LECTERN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LecternBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
    }

    /** Premier emplacement libre (sol plein, deux blocs d'air) autour du centre du village, ou {@code null}. */
    public static BlockPos findSpot(ServerLevel level, VillageSite site) {
        int cx = (int) Math.floor(site.x());
        int cz = (int) Math.floor(site.z());
        for (int d = 0; d <= Math.min(site.radius(), 12); d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d || !level.hasChunk((cx + dx) >> 4, (cz + dz) >> 4)) {
                        continue;
                    }
                    int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            cx + dx, cz + dz);
                    BlockPos base = new BlockPos(cx + dx, y, cz + dz);
                    if (level.getBlockState(base.below()).blocksMotion() && level.getBlockState(base).isAir()
                            && level.getBlockState(base.above()).isAir()
                            && level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                            new net.minecraft.world.phys.AABB(base).inflate(0.5, 1, 0.5)).isEmpty()) {
                        return base;
                    }
                }
            }
        }
        return null;
    }
}
