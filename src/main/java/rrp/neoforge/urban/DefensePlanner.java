package rrp.neoforge.urban;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Décide quand et où le village se fortifie, et pose l'enceinte : palissade de bois d'abord, remplacée par des murs de
 * pierre quand le stock et la population le permettent, avec une porte à chaque croisement de route. La logique de
 * décision et la géométrie sont pures (testables sans Minecraft) ; la pose agit sur le monde colonne par colonne.
 */
public final class DefensePlanner {

    // ---------- monde ----------

    private static boolean isFortification(Block b) {
        return b instanceof FenceBlock || b instanceof WallBlock || b instanceof FenceGateBlock;
    }

    /** Bloc de sol de la colonne : le plus haut bloc plein qui n'est ni clôture, ni mur, ni feuillage, ni tronc. */
    public static int groundY(ServerLevel level, int x, int z, int refY) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return Integer.MIN_VALUE;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = refY + 10; y >= refY - 14; y--) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            if (s.blocksMotion() && !isFortification(s.getBlock()) && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Pose (ou remplace) la colonne : clôture/mur de {@link DefensePlan#HEIGHT} blocs, ou porte s'il s'agit d'un croisement de route. */
    public static void placeColumn(ServerLevel level, int x, int z, int refY, boolean stone, boolean gate, Direction gateFacing) {
        int g = groundY(level, x, z, refY);
        if (g == Integer.MIN_VALUE) {
            return;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState body = stone ? Blocks.STONE_BRICK_WALL.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState();
        for (int h = 1; h <= DefensePlan.HEIGHT; h++) {
            p.set(x, g + h, z);
            BlockState cur = level.getBlockState(p);
            if (!cur.isAir() && !isFortification(cur.getBlock()) && !cur.canBeReplaced() && !cur.is(BlockTags.LEAVES)) {
                continue;
            }
            if (gate) {
                BlockState gateState = Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, gateFacing);
                level.setBlock(p, h == 1 ? gateState : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else {
                level.setBlock(p, body, Block.UPDATE_ALL);
            }
        }
    }

    /** Pose ou améliore {@code count} colonnes à partir de {@code from} ; renvoie le nombre de colonnes traitées. */
    public static int work(ServerLevel level, Defense d, int cx, int cz, int refY, List<int[]> roadPaths, int from, int count, boolean stone) {
        int done = 0;
        int r = d.radius();
        while (done < count && from + done < d.length()) {
            int[] c = DefensePlan.ringAt(cx, cz, r, from + done);
            boolean gate = DefensePlan.isGate(roadPaths, cx, cz, r, c[0], c[1]);
            Direction facing = Math.abs(c[1] - cz) == r ? Direction.NORTH : Direction.EAST;
            placeColumn(level, c[0], c[1], refY, stone, gate, facing);
            done++;
        }
        return done;
    }

    /** Vrai si la colonne porte une clôture, un mur ou une porte juste au-dessus du sol (test de fermeture). */
    public static boolean closed(ServerLevel level, int x, int z, int refY) {
        int g = groundY(level, x, z, refY);
        return g == Integer.MIN_VALUE || isFortification(level.getBlockState(new BlockPos(x, g + 1, z)).getBlock());
    }
}
