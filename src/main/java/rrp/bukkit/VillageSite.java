package rrp.bukkit;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import rrp.core.VillageState;

/** Un village (ou campement) placé dans le monde : état du cœur + centre, rayon et entrepôt. */
public final class VillageSite {

    private final VillageState state;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private int radius;
    /** Rayon d'appartenance élargi pendant une guerre civile (non persisté) : les combattants peuvent atteindre le camp adverse. */
    private int warReach;
    /** Coordonnées du tonneau-entrepôt, ou {@code null} tant qu'aucun n'est désigné. */
    private int[] warehouse;

    public VillageSite(VillageState state, String worldName, double x, double y, double z, int radius) {
        this.state = state;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
    }

    public VillageState state() { return state; }
    public String id() { return state.id(); }
    public String worldName() { return worldName; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public int radius() { return radius; }
    public void setRadius(int radius) { this.radius = radius; }
    /** Rayon d'appartenance : le rayon du village, élargi tant qu'une guerre civile l'exige. */
    public int reach() { return Math.max(radius, warReach); }
    public void setWarReach(int warReach) { this.warReach = Math.max(0, warReach); }
    public int[] warehouse() { return warehouse == null ? null : warehouse.clone(); }
    public boolean hasWarehouse() { return warehouse != null; }

    public void setWarehouse(int bx, int by, int bz) {
        this.warehouse = new int[] {bx, by, bz};
    }

    public void clearWarehouse() {
        this.warehouse = null;
    }

    public Location center(World world) {
        return new Location(world, x, y, z);
    }

    /** Le tonneau de l'entrepôt si son chunk est chargé et si le bloc est bien un tonneau ; sinon {@code null}. */
    public Block warehouseBlock(World world) {
        if (warehouse == null || world == null || !world.isChunkLoaded(warehouse[0] >> 4, warehouse[2] >> 4)) {
            return null;
        }
        Block b = world.getBlockAt(warehouse[0], warehouse[1], warehouse[2]);
        return b.getType() == org.bukkit.Material.BARREL ? b : null;
    }

    /** Vrai si le chunk du centre (et celui de l'entrepôt) est chargé : sinon rien n'y est observable. */
    public boolean loaded(World world) {
        if (world == null || !world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
            return false;
        }
        return warehouse == null || world.isChunkLoaded(warehouse[0] >> 4, warehouse[2] >> 4);
    }

    /** Distance horizontale au centre. */
    public double distance(String world, double px, double pz) {
        if (!worldName.equals(world)) {
            return Double.MAX_VALUE;
        }
        return Math.hypot(px - x, pz - z);
    }

    public boolean contains(String world, double px, double pz) {
        return distance(world, px, pz) <= radius;
    }
}
