package rrp.neoforge.urban;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import rrp.neoforge.VillageSite;
import rrp.neoforge.Worlds;

import java.util.Arrays;
import java.util.Optional;

/**
 * Choix et nivellement du terrain d'un chantier. Seul le terrain naturel (terre, sable, pierre, neige...) est
 * coupé ou comblé ; la végétation et les arbres sont dégagés ; tout autre bloc (planches, coffre, champ, eau...)
 * est considéré comme un bâtiment ou un obstacle : l'emplacement est alors refusé.
 */
public final class TerrainLeveler {

    /** Écart de hauteur maximal toléré sous l'emprise d'un bâtiment. */
    public static final int MAX_SLOPE = 3;
    /** Marge nivelée autour de l'emprise. */
    public static final int MARGIN = 1;
    /** Profondeur maximale explorée pour retrouver le sol sous la végétation. */
    private static final int SCAN_DEPTH = 26;
    /** Hauteur au-dessus de la référence au-delà de laquelle on ne cherche pas le sol (falaise, carte de hauteur périmée). */
    private static final int REACH_ABOVE = 10;
    public static final int INVALID = Integer.MIN_VALUE;

    private TerrainLeveler() {
    }

    public static boolean isGround(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.GRAVEL) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
                || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CLAY);
    }

    public static boolean isRemovable(BlockState s) {
        return s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.REPLACEABLE)
                || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS);
    }

    /**
     * Hauteur du dernier bloc de sol naturel à la colonne, ou {@link #INVALID} (eau, bâtiment, chunk absent...).
     * La recherche part de la surface, plafonnée à {@code refY + 10} : au-delà, c'est une falaise ou un obstacle.
     */
    public static int groundY(ServerLevel level, int x, int z, int refY) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return INVALID;
        }
        int y = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, refY + REACH_ABOVE);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < SCAN_DEPTH; i++, y--) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            if (isGround(s)) {
                return y;
            }
            if (!isRemovable(s)) {
                return INVALID;
            }
        }
        return INVALID;
    }

    /**
     * Cherche, en spirale autour de {@code (cx, cz)}, une emprise {@code sx × sz} (hauteur {@code sy}) entièrement
     * dans les frontières du village, sur terrain naturel quasi plat, sans bâtiment ni être vivant. Renvoie l'origine
     * (angle ouest-nord ; {@code y} = bloc de sol que remplace l'étage 0 du plan) ou rien.
     */
    public static Optional<BlockPos> findSpot(ServerLevel level, VillageSite site, int cx, int cz, int sx, int sy, int sz,
                                              int maxRing, int refY, Entity ignore) {
        for (int d = 0; d <= maxRing; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d || ((dx + dz) & 1) != 0) {
                        continue;
                    }
                    int ox = cx + dx - sx / 2;
                    int oz = cz + dz - sz / 2;
                    int oy = groundLevel(level, site, ox, oz, sx, sy, sz, refY, ignore);
                    if (oy != INVALID) {
                        return Optional.of(new BlockPos(ox, oy, oz));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Hauteur retenue pour l'emprise (médiane des sols), ou {@link #INVALID} si l'emprise est refusée. */
    static int groundLevel(ServerLevel level, VillageSite site, int ox, int oz, int sx, int sy, int sz, int refY, Entity ignore) {
        String world = Worlds.name(level);
        int x0 = ox - MARGIN, z0 = oz - MARGIN, x1 = ox + sx - 1 + MARGIN, z1 = oz + sz - 1 + MARGIN;
        for (int[] c : new int[][] {{x0, z0}, {x0, z1}, {x1, z0}, {x1, z1}}) {
            if (!site.contains(world, c[0] + 0.5, c[1] + 0.5)) {
                return INVALID;
            }
        }
        int[] heights = new int[(x1 - x0 + 1) * (z1 - z0 + 1)];
        int n = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int g = groundY(level, x, z, refY);
                if (g == INVALID) {
                    return INVALID;
                }
                heights[n++] = g;
            }
        }
        int[] sorted = heights.clone();
        Arrays.sort(sorted);
        if (sorted[sorted.length - 1] - sorted[0] > MAX_SLOPE) {
            return INVALID;
        }
        int oy = sorted[sorted.length / 2];
        // au-dessus du sol : seulement du vide ou de la végétation (sinon un bâtiment est dans l'emprise)
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        n = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = heights[n] + 1; y <= oy + sy + 1; y++) {
                    if (!isRemovable(level.getBlockState(p.set(x, y, z)))) {
                        return INVALID;
                    }
                }
                n++;
            }
        }
        AABB box = new AABB(x0, oy - MAX_SLOPE, z0, x1 + 1, oy + sy + 2, z1 + 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e != ignore) {
                return INVALID;
            }
        }
        return oy;
    }

    /**
     * Nivelle une rangée (z) de l'emprise élargie : coupe le terrain et dégage la végétation au-dessus de {@code oy},
     * comble en terre jusqu'à {@code oy} (herbe en surface sur la marge). Renvoie faux sans rien poser si un être
     * vivant se trouverait emmuré par le comblement (la rangée est à refaire plus tard).
     */
    public static boolean levelRow(ServerLevel level, BlockPos origin, int sx, int sy, int sz, int row, Entity ignore) {
        int oy = origin.getY();
        int z = origin.getZ() - MARGIN + row;
        int x0 = origin.getX() - MARGIN;
        int x1 = origin.getX() + sx + MARGIN;
        int[] ground = new int[x1 - x0];
        for (int i = 0; i < ground.length; i++) {
            ground[i] = groundY(level, x0 + i, z, oy);
            if (ground[i] != INVALID && ground[i] < oy) {
                AABB fill = new AABB(x0 + i, ground[i] + 1, z, x0 + i + 1, oy + 2, z + 1);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, fill)) {
                    if (e != ignore) {
                        return false;
                    }
                }
            }
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < ground.length; i++) {
            int x = x0 + i;
            int g = ground[i];
            if (g == INVALID) {
                continue;
            }
            boolean margin = x < origin.getX() || x >= origin.getX() + sx || row == 0 || row == sz + 2 * MARGIN - 1;
            for (int y = oy + 1; y <= oy + sy + 1; y++) {
                p.set(x, y, z);
                BlockState s = level.getBlockState(p);
                if (!s.isAir() && (isGround(s) || isRemovable(s))) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            for (int y = g + 1; y <= oy; y++) {
                p.set(x, y, z);
                level.setBlock(p, (y == oy && margin ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return true;
    }
}
