package rrp.neoforge;

import rrp.core.VillageState;
import rrp.neoforge.urban.BuildOrder;
import rrp.neoforge.urban.BuildType;
import rrp.neoforge.urban.ConstructionSite;
import rrp.neoforge.urban.RoadNetwork;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Un village (ou campement) placé dans le monde : état du cœur + dimension, centre, rayon et entrepôt. */
public final class VillageSite {

    private final VillageState state;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private int radius;
    /** Rayon d'appartenance élargi pendant une guerre civile (non persisté). */
    private int warReach;
    /** Coordonnées du tonneau-entrepôt, ou {@code null} tant qu'aucun n'est désigné. */
    private int[] warehouse;
    /** Coordonnées du bloc d'or de l'Hôtel de Ville, ou {@code null} tant qu'il n'existe pas. */
    private int[] hall;
    /** Ordres de construction en attente (émis par l'intendant), dans l'ordre. */
    private final List<BuildOrder> orders = new ArrayList<>();
    /** Chantier en cours (un seul à la fois), ou {@code null}. */
    private ConstructionSite construction;
    /** Routes du village (tracé, posées puis pavées), dans l'ordre de commande. */
    private final List<RoadNetwork.Road> roads = new ArrayList<>();
    /** Nombre de bâtiments construits par type (hors huttes historiques). */
    private final Map<BuildType, Integer> built = new EnumMap<>(BuildType.class);

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
    /** Identifiant de la dimension, par exemple {@code minecraft:overworld}. */
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

    public int[] hall() { return hall == null ? null : hall.clone(); }
    public boolean hasHall() { return hall != null; }

    public void setHall(int bx, int by, int bz) {
        this.hall = new int[] {bx, by, bz};
    }

    public List<BuildOrder> orders() { return orders; }
    public List<RoadNetwork.Road> roads() { return roads; }
    public Map<BuildType, Integer> built() { return built; }
    public ConstructionSite construction() { return construction; }
    public void setConstruction(ConstructionSite c) { this.construction = c; }

    public void clearHall() {
        this.hall = null;
    }

    /** Vrai si le point est dans le cœur (rayon {@code coreRadius} autour de l'Hôtel de Ville) ; faux sans hôtel de ville. */
    public boolean inCore(String world, double px, double pz, int coreRadius) {
        return hall != null && worldName.equals(world) && Math.hypot(px - (hall[0] + 0.5), pz - (hall[2] + 0.5)) <= coreRadius;
    }

    public void clearWarehouse() {
        this.warehouse = null;
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
