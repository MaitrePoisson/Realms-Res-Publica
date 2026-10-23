package rrp.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Pose un campement dissident sur un sol libre : un feu de camp au centre, un tonneau (le stock du camp)
 * et des tentes de laine (toit et côtés, extrémités ouvertes) abritant chacune un vrai lit. L'emplacement
 * est choisi parmi huit directions à bonne distance du village : le moins accidenté (ni eau, ni lave, ni
 * arbre, peu de pente) l'emporte. Les chunks concernés sont chargés avant d'y toucher.
 */
public final class CampBuilder {

    /** Rayon du campement (blocs) : assez large pour toutes les tentes, assez petit pour rester loin du village. */
    public static final int CAMP_RADIUS = 20;
    /** Marge entre le bord du village et celui du campement. */
    private static final int GAP = 4;

    /** Décalages (x, z) du coin ouest-nord de chaque tente par rapport au feu de camp ; ordre de remplissage. */
    private static final int[][] SLOTS = {
            {-5, -7}, {-1, -7}, {3, -7}, {-5, 5}, {-1, 5}, {3, 5},
            {-5, -12}, {-1, -12}, {3, -12}, {-5, 10}, {-1, 10}, {3, 10}};
    private static final Block[] WOOLS = {Blocks.ORANGE_WOOL, Blocks.BROWN_WOOL, Blocks.GRAY_WOOL,
            Blocks.LIGHT_GRAY_WOOL, Blocks.RED_WOOL, Blocks.YELLOW_WOOL};

    /** Contrat d'un campement posé : où est le feu, le tonneau, et où se tenir devant chaque lit neuf. */
    public record Result(BlockPos fire, BlockPos barrel, List<Vec3> spots, int beds) { }

    public static int maxTents() {
        return SLOTS.length;
    }

    /** Distance entre le centre du village et celui du campement. */
    public static int distance(VillageSite village, int configured) {
        return Math.max(configured, village.radius() + CAMP_RADIUS + GAP);
    }

    /** Meilleur emplacement pour le feu de camp d'un campement du village (toujours un résultat). */
    public BlockPos findSite(ServerLevel level, VillageSite village, int configuredDistance) {
        int dist = distance(village, configuredDistance);
        double base = Math.floorMod(village.id().hashCode(), 360);
        BlockPos best = null;
        int bestPenalty = Integer.MAX_VALUE;
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(base + 45.0 * k);
            int cx = (int) Math.round(village.x() + dist * Math.cos(a));
            int cz = (int) Math.round(village.z() + dist * Math.sin(a));
            int penalty = penalty(level, cx, cz);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                best = new BlockPos(cx, surfaceY(level, cx, cz) + 1, cz);
                if (penalty == 0) {
                    break;
                }
            }
        }
        return best;
    }

    /** Mesure de l'inadéquation du terrain autour de (cx, cz) : 0 = plat et libre. */
    private int penalty(ServerLevel level, int cx, int cz) {
        int yc = surfaceY(level, cx, cz);
        int penalty = 0;
        for (int dx = -6; dx <= 6; dx += 2) {
            for (int dz = -13; dz <= 12; dz += 2) {
                int y = surfaceY(level, cx + dx, cz + dz);
                BlockState ground = level.getBlockState(new BlockPos(cx + dx, y, cz + dz));
                if (ground.is(Blocks.WATER) || ground.is(Blocks.LAVA) || ground.is(BlockTags.LEAVES)
                        || ground.is(BlockTags.LOGS) || ground.is(Blocks.ICE) || ground.isAir()) {
                    penalty += 5;
                }
                if (Math.abs(y - yc) > 2) {
                    penalty += 2;
                }
            }
        }
        return penalty;
    }

    /** Altitude du dernier bloc au sol (feuillages ignorés) ; charge le chunk au besoin. */
    private int surfaceY(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4); // charge le chunk s'il ne l'est pas
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /**
     * Pose jusqu'à {@code tents} tentes neuves dans les emplacements encore libres autour de {@code fire} ;
     * si {@code fresh}, pose d'abord le feu de camp et le tonneau (nouveau campement).
     */
    public Result build(ServerLevel level, BlockPos fire, int tents, boolean fresh) {
        int fx = fire.getX();
        int fz = fire.getZ();
        BlockPos fireBlock = fire;
        BlockPos barrel = null;
        if (fresh) {
            fireBlock = set(level, fx, surfaceY(level, fx, fz) + 1, fz, Blocks.CAMPFIRE);
            barrel = set(level, fx + 2, surfaceY(level, fx + 2, fz) + 1, fz, Blocks.BARREL);
        }
        List<Vec3> spots = new ArrayList<>();
        int built = 0;
        for (int i = 0; i < SLOTS.length && built < tents; i++) {
            int x = fx + SLOTS[i][0] + 1; // colonne du lit (milieu de la tente)
            int z = fz + SLOTS[i][1];
            if (tentAt(level, x, z)) {
                continue;
            }
            spots.add(tent(level, x, z, WOOLS[(i + built) % WOOLS.length]));
            built++;
        }
        return new Result(fireBlock, barrel, spots, built);
    }

    /** Une tente occupe déjà cet emplacement si un lit y est posé. */
    private boolean tentAt(ServerLevel level, int x, int z) {
        int top = surfaceY(level, x, z); // le toit d'une tente existante est le bloc le plus haut
        for (int dy = -3; dy <= 0; dy++) {
            if (level.getBlockState(new BlockPos(x, top + dy, z)).is(BlockTags.BEDS)) {
                return true;
            }
        }
        return false;
    }

    /** Tente 3×2 : lit au milieu (pied en z, tête en z+1), côtés de 2 blocs, toit ; ouverte aux deux bouts. */
    private Vec3 tent(ServerLevel level, int x, int z, Block wool) {
        int y = surfaceY(level, x, z) + 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos p = new BlockPos(x + dx, y + dy, z + dz);
                    boolean wall = dx != 0 && dy < 2;
                    boolean roof = dy == 2;
                    if (wall || roof) {
                        set(level, p, wool);
                    } else if (!level.getBlockState(p).isAir()) {
                        set(level, p, Blocks.AIR);
                    }
                }
            }
        }
        // Sol plein sous la tente pour que le lit tienne même sur une légère dénivellation.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                BlockPos ground = new BlockPos(x + dx, y - 1, z + dz);
                if (!level.getBlockState(ground).blocksMotion()) {
                    set(level, ground, Blocks.DIRT);
                }
            }
        }
        BlockPos foot = new BlockPos(x, y, z);
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_CLIENTS);
        level.setBlock(foot.south(), bed.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS);
        return new Vec3(x + 0.5, y, z - 0.5);
    }

    private BlockPos set(ServerLevel level, int x, int y, int z, Block type) {
        return set(level, new BlockPos(x, y, z), type);
    }

    private BlockPos set(ServerLevel level, BlockPos p, Block type) {
        level.setBlock(p, type.defaultBlockState(), Block.UPDATE_CLIENTS);
        return p;
    }
}
