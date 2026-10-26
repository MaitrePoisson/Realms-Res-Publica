package rrp.core.building;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The order in which a builder lays the blocks of a plan: bottom-up (one layer after the other), and inside a layer a
 * snake walk (row after row, the direction flipping on each row) so that the builder hardly has to move between two
 * blocks. The sort is stable and deterministic: the same set of positions always gives the same list, so the index of
 * the next block is all that has to be saved to resume a build after a restart.
 */
public final class BlockOrder {

    private BlockOrder() {
    }

    /** The positions ({@code {x, y, z}}) sorted in building order; the input list is left untouched. */
    public static List<int[]> sort(List<int[]> positions) {
        List<int[]> sorted = new ArrayList<>(positions);
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (int[] p : sorted) {
            minX = Math.min(minX, p[0]);
            minZ = Math.min(minZ, p[2]);
        }
        // row parity counted from the first row of the plan, so the snake does not depend on world coordinates
        final int z0 = minZ;
        sorted.sort(Comparator.<int[]>comparingInt(p -> p[1])
                .thenComparingInt(p -> p[2])
                .thenComparingInt(p -> ((p[2] - z0) & 1) == 0 ? p[0] : -p[0]));
        return sorted;
    }

    /** Layers (distinct {@code y}) crossed by the first {@code count} blocks of a sorted list. */
    public static int layersTouched(List<int[]> sorted, int count) {
        int layers = 0;
        int last = Integer.MIN_VALUE;
        for (int i = 0; i < Math.min(count, sorted.size()); i++) {
            if (layers == 0 || sorted.get(i)[1] != last) {
                layers++;
                last = sorted.get(i)[1];
            }
        }
        return layers;
    }

    /** Total horizontal-plus-vertical Manhattan distance walked from block to block along a sorted list. */
    public static int pathLength(List<int[]> sorted) {
        int d = 0;
        for (int i = 1; i < sorted.size(); i++) {
            int[] a = sorted.get(i - 1);
            int[] b = sorted.get(i);
            d += Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
        }
        return d;
    }
}
