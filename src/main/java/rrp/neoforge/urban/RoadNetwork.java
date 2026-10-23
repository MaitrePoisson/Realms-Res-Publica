package rrp.neoforge.urban;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Routes d'un village : recherche de chemin à la surface (A* sur une grille de hauteurs, sans eau ni bâtiment, pentes
 * d'un bloc au plus), puis pose tuile par tuile : sentier de terre d'abord, pavage (pierre taillée / pavé) ensuite.
 * Une route ({@link Road}) ne persiste que ses extrémités, son tracé et ses deux curseurs ; elle reprend après un redémarrage.
 */
public final class RoadNetwork {

    /** Hauteur d'une colonne infranchissable (eau, bâtiment, falaise, chunk absent). */
    public static final int BLOCKED = Integer.MIN_VALUE;
    /** Marge de la fenêtre de recherche autour du rectangle départ-arrivée. */
    public static final int MARGIN = 16;
    /** Nombre maximal de cases explorées par recherche. */
    public static final int MAX_NODES = 40000;
    /** Positions du tracé posées (ou pavées) à chaque pas du bâtisseur. */
    public static final int TILES_PER_STEP = 12;

    private RoadNetwork() {
    }

    /** Hauteur de la surface marchable d'une colonne, ou {@link #BLOCKED}. */
    @FunctionalInterface
    public interface Surface {
        int height(int x, int z);
    }

    /** Route entre deux points : tracé (couples x,z), positions de terre posées, positions pavées. */
    public static final class Road {
        private final int fromX;
        private final int fromZ;
        private final int toX;
        private final int toZ;
        private final boolean wide;
        private int[] path;
        private int laid;
        private int paved;

        public Road(int fromX, int fromZ, int toX, int toZ, boolean wide, int[] path, int laid, int paved) {
            this.fromX = fromX;
            this.fromZ = fromZ;
            this.toX = toX;
            this.toZ = toZ;
            this.wide = wide;
            this.path = path;
            this.laid = laid;
            this.paved = paved;
        }

        public int fromX() { return fromX; }
        public int fromZ() { return fromZ; }
        public int toX() { return toX; }
        public int toZ() { return toZ; }
        /** Vrai pour une route de 2 de large (vers l'Hôtel de Ville), faux pour une piste. */
        public boolean wide() { return wide; }
        /** Tracé en couples x,z, ou {@code null} tant qu'il n'est pas calculé. */
        public int[] path() { return path; }
        public void setPath(int[] path) { this.path = path; }
        public int length() { return path == null ? 0 : path.length / 2; }
        public int laid() { return laid; }
        public int paved() { return paved; }
        public boolean hasPath() { return path != null && path.length > 0; }
        public boolean laidDone() { return hasPath() && laid >= length(); }
        public boolean pavedDone() { return hasPath() && paved >= length(); }
        public boolean sameTarget(int x, int z) { return Math.abs(toX - x) <= 2 && Math.abs(toZ - z) <= 2; }
    }

    /** Chemin le plus court (4 voisins, dénivelé ≤ 1) de {@code (sx,sz)} à {@code (gx,gz)} ; {@code null} s'il n'y en a pas. */
    public static int[] findPath(Surface surface, int sx, int sz, int gx, int gz) {
        int x0 = Math.min(sx, gx) - MARGIN;
        int x1 = Math.max(sx, gx) + MARGIN;
        int z0 = Math.min(sz, gz) - MARGIN;
        int z1 = Math.max(sz, gz) + MARGIN;
        long start = key(sx, sz);
        long goal = key(gx, gz);
        Map<Long, Integer> height = new HashMap<>();
        Map<Long, Long> from = new HashMap<>();
        Map<Long, Double> cost = new HashMap<>();
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        cost.put(start, 0.0);
        height.put(start, surface.height(sx, sz));
        open.add(new double[] {heuristic(sx, sz, gx, gz), sx, sz});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < MAX_NODES) {
            double[] cur = open.poll();
            int cx = (int) cur[1];
            int cz = (int) cur[2];
            long ck = key(cx, cz);
            if (ck == goal) {
                return rebuild(from, ck, start);
            }
            double cc = cost.get(ck);
            if (cur[0] > cc + heuristic(cx, cz, gx, gz) + 1e-9) {
                continue;
            }
            int ch = height.get(ck);
            for (int[] d : dirs) {
                int nx = cx + d[0];
                int nz = cz + d[1];
                if (nx < x0 || nx > x1 || nz < z0 || nz > z1) {
                    continue;
                }
                long nk = key(nx, nz);
                Integer cached = height.get(nk);
                int nh = cached != null ? cached : surface.height(nx, nz);
                height.put(nk, nh);
                if (nh == BLOCKED || Math.abs(nh - ch) > 1) {
                    continue;
                }
                double nc = cc + 1 + (nh != ch ? 0.5 : 0);
                Double old = cost.get(nk);
                if (old == null || nc < old) {
                    cost.put(nk, nc);
                    from.put(nk, ck);
                    open.add(new double[] {nc + heuristic(nx, nz, gx, gz), nx, nz});
                }
            }
        }
        return null;
    }

    private static double heuristic(int x, int z, int gx, int gz) {
        return Math.abs(x - gx) + Math.abs(z - gz);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static int[] rebuild(Map<Long, Long> from, long end, long start) {
        List<Long> keys = new ArrayList<>();
        long k = end;
        keys.add(k);
        while (k != start) {
            k = from.get(k);
            keys.add(k);
        }
        Collections.reverse(keys);
        int[] out = new int[keys.size() * 2];
        for (int i = 0; i < keys.size(); i++) {
            out[2 * i] = (int) (keys.get(i) >> 32);
            out[2 * i + 1] = (int) (long) keys.get(i);
        }
        return out;
    }

    // ---------- monde ----------

    public static boolean isRoadBlock(BlockState s) {
        return s.is(Blocks.DIRT_PATH) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.STONE_BRICKS);
    }

    /** Surface d'une colonne du monde : le sol naturel, ou une tuile de route existante. */
    public static int surfaceY(ServerLevel level, int x, int z, int refY) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return BLOCKED;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (isRoadBlock(level.getBlockState(new BlockPos(x, top, z)))) {
            return top;
        }
        int g = TerrainLeveler.groundY(level, x, z, refY);
        return g == TerrainLeveler.INVALID ? BLOCKED : g;
    }

    /** Colonne marchable la plus proche de {@code (x,z)} (spirale), ou {@code null}. */
    public static int[] nearestWalkable(ServerLevel level, int x, int z, int refY, int maxRing) {
        for (int d = 0; d <= maxRing; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == d && surfaceY(level, x + dx, z + dz, refY) != BLOCKED) {
                        return new int[] {x + dx, z + dz};
                    }
                }
            }
        }
        return null;
    }

    /** Calcule le tracé d'une route dans le monde. Faux si les extrémités ou le chemin sont introuvables. */
    public static boolean plan(ServerLevel level, Road road, int refY) {
        int[] s = nearestWalkable(level, road.fromX(), road.fromZ(), refY, 6);
        int[] g = nearestWalkable(level, road.toX(), road.toZ(), refY, 6);
        if (s == null || g == null) {
            return false;
        }
        int[] path = findPath((x, z) -> surfaceY(level, x, z, refY), s[0], s[1], g[0], g[1]);
        if (path == null) {
            return false;
        }
        road.setPath(path);
        return true;
    }

    /** Tuiles d'une position du tracé : la tuile elle-même et, pour une route large, sa voisine latérale. */
    private static List<int[]> tiles(Road road, int i) {
        int[] p = road.path();
        int x = p[2 * i];
        int z = p[2 * i + 1];
        List<int[]> out = new ArrayList<>(2);
        out.add(new int[] {x, z});
        if (road.wide()) {
            int nx = i + 1 < road.length() ? p[2 * i + 2] : (i > 0 ? p[2 * i - 2] : x);
            out.add(nx != x ? new int[] {x, z + 1} : new int[] {x + 1, z});
        }
        return out;
    }

    /** Pose jusqu'à {@code count} positions de terre. Renvoie le nombre de positions traitées. */
    public static int lay(ServerLevel level, Road road, int count, int refY) {
        int done = 0;
        while (done < count && !road.laidDone()) {
            for (int[] t : tiles(road, road.laid)) {
                lay(level, t[0], t[1], refY);
            }
            road.laid++;
            done++;
        }
        return done;
    }

    /** Pave jusqu'à {@code count} positions déjà posées. Renvoie le nombre de positions pavées. */
    public static int pave(ServerLevel level, Road road, int count, int refY) {
        int done = 0;
        while (done < count && road.paved < road.laid) {
            for (int[] t : tiles(road, road.paved)) {
                pave(level, t[0], t[1], refY, (t[0] + t[1]) & 1);
            }
            road.paved++;
            done++;
        }
        return done;
    }

    private static void lay(ServerLevel level, int x, int z, int refY) {
        int y = surfaceY(level, x, z, refY);
        if (y == BLOCKED) {
            return;
        }
        BlockPos p = new BlockPos(x, y, z);
        BlockState s = level.getBlockState(p);
        if (isRoadBlock(s) || !(s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL))) {
            return;
        }
        clearAbove(level, p);
        level.setBlock(p, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void pave(ServerLevel level, int x, int z, int refY, int variant) {
        int y = surfaceY(level, x, z, refY);
        if (y == BLOCKED) {
            return;
        }
        BlockPos p = new BlockPos(x, y, z);
        if (!level.getBlockState(p).is(Blocks.DIRT_PATH)) {
            return;
        }
        level.setBlock(p, (variant == 0 ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS).defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Dégage la végétation au-dessus d'une tuile (un sentier a besoin d'air au-dessus). */
    private static void clearAbove(ServerLevel level, BlockPos ground) {
        for (int i = 1; i <= 2; i++) {
            BlockPos a = ground.above(i);
            BlockState s = level.getBlockState(a);
            if (!s.isAir() && TerrainLeveler.isRemovable(s)) {
                level.setBlock(a, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** Vrai si la tuile centrale de chaque position du tracé est en pierre (sauf colonnes non marchables). */
    public static boolean allPaved(ServerLevel level, Road road, int refY) {
        for (int i = 0; i < road.length(); i++) {
            int x = road.path()[2 * i];
            int z = road.path()[2 * i + 1];
            int y = surfaceY(level, x, z, refY);
            if (y == BLOCKED) {
                continue;
            }
            BlockState s = level.getBlockState(new BlockPos(x, y, z));
            if (!s.is(Blocks.COBBLESTONE) && !s.is(Blocks.STONE_BRICKS)) {
                return false;
            }
        }
        return true;
    }
}
