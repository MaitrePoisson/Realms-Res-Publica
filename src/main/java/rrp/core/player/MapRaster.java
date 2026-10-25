package rrp.core.player;

import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;

/**
 * Rasterises the political map: one cell per pixel of a square map, each cell either empty, a faction slot
 * (owner id {@code >= 1}) or one of the overlay codes below. Pure and deterministic; the NeoForge layer maps the
 * codes to vanilla map colours.
 */
public final class MapRaster {

    public static final int EMPTY = 0;
    public static final int BORDER = -1;
    public static final int WAR = -2;
    public static final int ROUTE = -3;
    public static final int MARKER = -4;

    private MapRaster() {
    }

    /**
     * @param ownerOfChunk owner id (>= 1) of a chunk (chunkX, chunkZ), 0 when free or contested
     * @param warOwners    owner ids currently at war (hatched)
     * @param routes       caravan routes as {x1, z1, x2, z2} in blocks
     * @param markers      village markers as {x, z} in blocks
     */
    public static int[] render(int size, int blocksPerPixel, int centerX, int centerZ, IntBinaryOperator ownerOfChunk,
                               Set<Integer> warOwners, List<int[]> routes, List<int[]> markers) {
        int[] owner = new int[size * size];
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int bx = centerX + (px - size / 2) * blocksPerPixel;
                int bz = centerZ + (py - size / 2) * blocksPerPixel;
                owner[py * size + px] = ownerOfChunk.applyAsInt(Math.floorDiv(bx, 16), Math.floorDiv(bz, 16));
            }
        }
        int[] out = new int[size * size];
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int o = owner[py * size + px];
                int cell = o > 0 ? o : EMPTY;
                if (o > 0 && warOwners.contains(o) && (px + py) % 4 == 0) {
                    cell = WAR;
                }
                boolean edge = px + 1 < size && owner[py * size + px + 1] != o || py + 1 < size && owner[(py + 1) * size + px] != o;
                if (o > 0 && edge) {
                    cell = BORDER;
                }
                out[py * size + px] = cell;
            }
        }
        for (int[] r : routes) {
            line(out, size, pixel(r[0], centerX, blocksPerPixel, size), pixel(r[1], centerZ, blocksPerPixel, size),
                    pixel(r[2], centerX, blocksPerPixel, size), pixel(r[3], centerZ, blocksPerPixel, size));
        }
        for (int[] m : markers) {
            int cx = pixel(m[0], centerX, blocksPerPixel, size);
            int cy = pixel(m[1], centerZ, blocksPerPixel, size);
            for (int d = -2; d <= 2; d++) {
                set(out, size, cx + d, cy, MARKER);
                set(out, size, cx, cy + d, MARKER);
            }
        }
        return out;
    }

    private static int pixel(int block, int center, int bpp, int size) {
        return Math.floorDiv(block - center, bpp) + size / 2;
    }

    private static void set(int[] out, int size, int x, int y, int code) {
        if (x >= 0 && y >= 0 && x < size && y < size) {
            out[y * size + x] = code;
        }
    }

    private static void line(int[] out, int size, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int step = 0;
        while (step++ < 4 * size) {
            if (step % 3 != 0) {
                set(out, size, x0, y0, ROUTE);
            }
            if (x0 == x1 && y0 == y1) {
                return;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }
}
