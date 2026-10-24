package rrp.core.territory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/** Où poser les bornes : au milieu de chaque côté de chunk revendiqué qui touche un chunk non possédé. */
public final class BorderPlan {

    private BorderPlan() {
    }

    /** Bornes (x, z en blocs) d'une faction, triées de façon déterministe. */
    public static List<int[]> posts(InfluenceMap map, String faction) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (var m : map.markers()) {
            if (m.faction().equals(faction)) {
                minX = Math.min(minX, m.pos().x() - m.radius());
                maxX = Math.max(maxX, m.pos().x() + m.radius());
                minZ = Math.min(minZ, m.pos().z() - m.radius());
                maxZ = Math.max(maxZ, m.pos().z() + m.radius());
            }
        }
        if (minX == Integer.MAX_VALUE) {
            return List.of();
        }
        TreeSet<long[]> set = new TreeSet<>(Comparator.<long[]>comparingLong(a -> a[0]).thenComparingLong(a -> a[1]));
        for (ChunkPos c : map.claimedBy(faction, minX - 1, minZ - 1, maxX + 1, maxZ + 1)) {
            int bx = c.x() * 16;
            int bz = c.z() * 16;
            if (!faction.equals(map.owner(new ChunkPos(c.x() - 1, c.z())))) {
                set.add(new long[] {bx, bz + 8});
            }
            if (!faction.equals(map.owner(new ChunkPos(c.x() + 1, c.z())))) {
                set.add(new long[] {bx + 15, bz + 8});
            }
            if (!faction.equals(map.owner(new ChunkPos(c.x(), c.z() - 1)))) {
                set.add(new long[] {bx + 8, bz});
            }
            if (!faction.equals(map.owner(new ChunkPos(c.x(), c.z() + 1)))) {
                set.add(new long[] {bx + 8, bz + 15});
            }
        }
        List<int[]> out = new ArrayList<>();
        for (long[] a : set) {
            out.add(new int[] {(int) a[0], (int) a[1]});
        }
        return out;
    }
}
