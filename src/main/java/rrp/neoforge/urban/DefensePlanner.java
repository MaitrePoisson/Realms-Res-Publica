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
import rrp.neoforge.building.BuildJob;
import rrp.neoforge.building.UnitProgress;

import java.util.ArrayList;
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

    /**
     * Blocs à poser dans la colonne, de bas en haut : clôture ou mur de {@link DefensePlan#HEIGHT} blocs, ou porte à un
     * croisement de route (les blocs au-dessus de la porte sont dégagés). Ce qui est déjà en place (même bloc) est omis :
     * une colonne qui n'a besoin de rien donne une liste vide. La colonne n'est pas touchée.
     */
    public static List<BuildJob.Placement> columnBlocks(ServerLevel level, int x, int z, int refY, boolean stone, boolean gate,
                                                        Direction gateFacing) {
        List<BuildJob.Placement> out = new ArrayList<>();
        int g = groundY(level, x, z, refY);
        if (g == Integer.MIN_VALUE) {
            return out;
        }
        BlockState body = stone ? Blocks.STONE_BRICK_WALL.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState();
        BlockState gateState = Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, gateFacing);
        for (int h = 1; h <= DefensePlan.HEIGHT; h++) {
            BlockPos p = new BlockPos(x, g + h, z);
            BlockState cur = level.getBlockState(p);
            if (!cur.isAir() && !isFortification(cur.getBlock()) && !cur.canBeReplaced() && !cur.is(BlockTags.LEAVES)) {
                continue;
            }
            BlockState want = gate ? (h == 1 ? gateState : Blocks.AIR.defaultBlockState()) : body;
            if (cur.is(want.getBlock())) {
                continue;
            }
            out.add(new BuildJob.Placement(p, want));
        }
        return out;
    }

    /** Pose (ou remplace) la colonne en une fois (mode instantané) : voir {@link #columnBlocks}. */
    public static void placeColumn(ServerLevel level, int x, int z, int refY, boolean stone, boolean gate, Direction gateFacing) {
        for (BuildJob.Placement pl : columnBlocks(level, x, z, refY, stone, gate, gateFacing)) {
            level.setBlock(pl.pos(), pl.state(), Block.UPDATE_ALL);
        }
    }

    /** Orientation de la porte dans la colonne {@code c} de l'enceinte (elle s'ouvre à travers le côté). */
    public static Direction gateFacing(int cz, int[] c, int r) {
        return Math.abs(c[1] - cz) == r ? Direction.NORTH : Direction.EAST;
    }

    /** Pose ou améliore {@code count} colonnes à partir de {@code from} ; renvoie le nombre de colonnes traitées. */
    public static int work(ServerLevel level, Defense d, int cx, int cz, int refY, List<int[]> roadPaths, int from, int count, boolean stone) {
        int done = 0;
        int r = d.radius();
        while (done < count && from + done < d.length()) {
            int[] c = DefensePlan.ringAt(cx, cz, r, from + done);
            boolean gate = DefensePlan.isGate(roadPaths, cx, cz, r, c[0], c[1]);
            placeColumn(level, c[0], c[1], refY, stone, gate, gateFacing(cz, c, r));
            done++;
        }
        return done;
    }

    /**
     * Blocs de {@code count} colonnes de l'enceinte à partir de {@code from}, colonne après colonne (de bas en haut dans
     * chacune), les fins de colonne étant inscrites dans {@code units}. Clôture ou mur de pierre, porte à un croisement de route.
     */
    public static List<BuildJob.Placement> ringBlocks(ServerLevel level, Defense d, int cx, int cz, int refY, List<int[]> roadPaths,
                                                      int from, int count, boolean stone, UnitProgress units) {
        List<BuildJob.Placement> out = new ArrayList<>();
        int r = d.radius();
        for (int done = 0; done < count && from + done < d.length(); done++) {
            int[] c = DefensePlan.ringAt(cx, cz, r, from + done);
            boolean gate = DefensePlan.isGate(roadPaths, cx, cz, r, c[0], c[1]);
            out.addAll(columnBlocks(level, c[0], c[1], refY, stone, gate, gateFacing(cz, c, r)));
            units.unitEnds(out.size());
        }
        return out;
    }

    /** Vrai si la colonne porte une clôture, un mur ou une porte juste au-dessus du sol (test de fermeture). */
    public static boolean closed(ServerLevel level, int x, int z, int refY) {
        int g = groundY(level, x, z, refY);
        return g == Integer.MIN_VALUE || isFortification(level.getBlockState(new BlockPos(x, g + 1, z)).getBlock());
    }
}
