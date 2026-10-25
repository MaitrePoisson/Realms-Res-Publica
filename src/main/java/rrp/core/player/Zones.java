package rrp.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Urban-planning plots drawn by the rulers, by village id. Pure data, persisted by the mod. */
public final class Zones {

    /** A rectangle of the XZ plane (bounds inclusive, normalised). */
    public record Zone(ZoneType type, int minX, int minZ, int maxX, int maxZ) {

        public static Zone of(ZoneType type, int x1, int z1, int x2, int z2) {
            return new Zone(type, Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2));
        }

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public int width() { return maxX - minX + 1; }
        public int depth() { return maxZ - minZ + 1; }
    }

    private final Map<String, List<Zone>> byVillage = new TreeMap<>();

    public List<Zone> of(String village) {
        return byVillage.computeIfAbsent(village, k -> new ArrayList<>());
    }

    public List<Zone> peek(String village) {
        return byVillage.getOrDefault(village, List.of());
    }

    public void add(String village, Zone z) {
        of(village).add(z);
    }

    public void clear(String village) {
        byVillage.remove(village);
    }

    public void clear() {
        byVillage.clear();
    }

    public Map<String, List<Zone>> all() {
        return byVillage;
    }

    /** Zone type at a point: FORBIDDEN beats everything, otherwise the most recently drawn plot; null = unzoned. */
    public static ZoneType typeAt(List<Zone> zones, int x, int z) {
        ZoneType found = null;
        for (Zone zone : zones) {
            if (zone.contains(x, z)) {
                if (zone.type() == ZoneType.FORBIDDEN) {
                    return ZoneType.FORBIDDEN;
                }
                found = zone.type();
            }
        }
        return found;
    }

    public ZoneType typeAt(String village, int x, int z) {
        return typeAt(peek(village), x, z);
    }

    /**
     * The n-th building spot of the plots of {@code type} (grid of {@code spacing} blocks, plots in drawing order),
     * or null when no plot of that type exists. Spots wrap around when the plots are full.
     */
    public static int[] spot(List<Zone> zones, ZoneType type, int n, int spacing) {
        List<int[]> spots = new ArrayList<>();
        for (Zone z : zones) {
            if (z.type() != type) {
                continue;
            }
            int cols = Math.max(1, z.width() / spacing);
            int rows = Math.max(1, z.depth() / spacing);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    spots.add(new int[] {Math.min(z.maxX(), z.minX() + spacing / 2 + c * spacing),
                            Math.min(z.maxZ(), z.minZ() + spacing / 2 + r * spacing)});
                }
            }
        }
        return spots.isEmpty() ? null : spots.get(n % spots.size());
    }

    public static boolean has(List<Zone> zones, ZoneType type) {
        return zones.stream().anyMatch(z -> z.type() == type);
    }
}
