package rrp.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pose un campement dissident sur un sol libre : un feu de camp au centre, un tonneau (le stock du camp)
 * et des tentes de laine (toit et côtés, extrémités ouvertes) abritant chacune un vrai lit. L'emplacement
 * est choisi parmi huit directions à bonne distance du village : le moins accidenté (ni eau, ni lave, ni
 * arbre, peu de pente) l'emporte. Les chunks concernés sont chargés (sur le fil principal) avant d'y toucher.
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
    private static final Material[] WOOLS = {Material.ORANGE_WOOL, Material.BROWN_WOOL, Material.GRAY_WOOL,
            Material.LIGHT_GRAY_WOOL, Material.RED_WOOL, Material.YELLOW_WOOL};

    /** Contrat d'un campement posé : où est le feu, le tonneau, et où se tenir devant chaque lit neuf. */
    public record Result(Location fire, Block barrel, List<Location> spots, int beds) { }

    private Consumer<Block> recorder;

    /** Appelé avec chaque bloc juste avant qu'il soit modifié (les tests s'en servent pour tout restaurer). */
    public void setRecorder(Consumer<Block> recorder) {
        this.recorder = recorder;
    }

    public static int maxTents() {
        return SLOTS.length;
    }

    /** Distance entre le centre du village et celui du campement. */
    public static int distance(VillageSite village, int configured) {
        return Math.max(configured, village.radius() + CAMP_RADIUS + GAP);
    }

    /** Meilleur emplacement pour le feu de camp d'un campement du village (toujours un résultat). */
    public Location findSite(World world, VillageSite village, int configuredDistance) {
        int dist = distance(village, configuredDistance);
        double base = Math.floorMod(village.id().hashCode(), 360);
        Location best = null;
        int bestPenalty = Integer.MAX_VALUE;
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(base + 45.0 * k);
            int cx = (int) Math.round(village.x() + dist * Math.cos(a));
            int cz = (int) Math.round(village.z() + dist * Math.sin(a));
            int penalty = penalty(world, cx, cz);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                best = new Location(world, cx + 0.5, surfaceY(world, cx, cz) + 1, cz + 0.5);
                if (penalty == 0) {
                    break;
                }
            }
        }
        return best;
    }

    /** Mesure de l'inadéquation du terrain autour de (cx, cz) : 0 = plat et libre. */
    private int penalty(World world, int cx, int cz) {
        int yc = surfaceY(world, cx, cz);
        int penalty = 0;
        for (int dx = -6; dx <= 6; dx += 2) {
            for (int dz = -13; dz <= 12; dz += 2) {
                int y = surfaceY(world, cx + dx, cz + dz);
                Material ground = world.getBlockAt(cx + dx, y, cz + dz).getType();
                if (ground == Material.WATER || ground == Material.LAVA || Tag.LEAVES.isTagged(ground)
                        || Tag.LOGS.isTagged(ground) || ground == Material.ICE || ground == Material.AIR) {
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
    private int surfaceY(World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.getChunkAt(x >> 4, z >> 4);
        }
        return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }

    /**
     * Pose jusqu'à {@code tents} tentes neuves dans les emplacements encore libres autour de {@code fire} ;
     * si {@code fresh}, pose d'abord le feu de camp et le tonneau (nouveau campement).
     */
    public Result build(World world, Location fire, int tents, boolean fresh) {
        int fx = fire.getBlockX();
        int fz = fire.getBlockZ();
        Block fireBlock = world.getBlockAt(fx, fire.getBlockY(), fz);
        Block barrel = null;
        if (fresh) {
            fireBlock = set(world, fx, surfaceY(world, fx, fz) + 1, fz, Material.CAMPFIRE);
            barrel = set(world, fx + 2, surfaceY(world, fx + 2, fz) + 1, fz, Material.BARREL);
        }
        List<Location> spots = new ArrayList<>();
        int built = 0;
        for (int i = 0; i < SLOTS.length && built < tents; i++) {
            int x = fx + SLOTS[i][0] + 1; // colonne du lit (milieu de la tente)
            int z = fz + SLOTS[i][1];
            if (tentAt(world, x, z)) {
                continue;
            }
            spots.add(tent(world, x, z, WOOLS[(i + built) % WOOLS.length]));
            built++;
        }
        return new Result(fireBlock.getLocation().add(0.5, 0, 0.5), barrel, spots, built);
    }

    /** Une tente occupe déjà cet emplacement si un lit y est posé. */
    private boolean tentAt(World world, int x, int z) {
        int top = surfaceY(world, x, z); // le toit d'une tente existante est le bloc le plus haut
        for (int dy = -3; dy <= 0; dy++) {
            if (Tag.BEDS.isTagged(world.getBlockAt(x, top + dy, z).getType())) {
                return true;
            }
        }
        return false;
    }

    /** Tente 3×2 : lit au milieu (pied en z, tête en z+1), côtés de 2 blocs, toit ; ouverte aux deux bouts. */
    private Location tent(World world, int x, int z, Material wool) {
        int y = surfaceY(world, x, z) + 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    Block b = world.getBlockAt(x + dx, y + dy, z + dz);
                    boolean wall = dx != 0 && dy < 2;
                    boolean roof = dy == 2;
                    if (wall || roof) {
                        set(b, wool);
                    } else if (!b.getType().isAir()) {
                        set(b, Material.AIR);
                    }
                }
            }
        }
        // Sol plein sous la tente (herbe ou terre) pour que le lit tienne même sur une légère dénivellation.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                Block ground = world.getBlockAt(x + dx, y - 1, z + dz);
                if (!ground.getType().isSolid()) {
                    set(ground, Material.DIRT);
                }
            }
        }
        Block foot = world.getBlockAt(x, y, z);
        Block head = foot.getRelative(BlockFace.SOUTH);
        record(foot);
        record(head);
        Bed data = (Bed) Bukkit.createBlockData(Material.RED_BED);
        data.setFacing(BlockFace.SOUTH);
        data.setPart(Bed.Part.FOOT);
        foot.setBlockData(data, false);
        Bed headData = (Bed) data.clone();
        headData.setPart(Bed.Part.HEAD);
        head.setBlockData(headData, false);
        return new Location(world, x + 0.5, y, z - 0.5);
    }

    private Block set(World world, int x, int y, int z, Material type) {
        return set(world.getBlockAt(x, y, z), type);
    }

    private Block set(Block b, Material type) {
        record(b);
        b.setType(type, false);
        return b;
    }

    private void record(Block b) {
        if (recorder != null) {
            recorder.accept(b);
        }
    }
}
