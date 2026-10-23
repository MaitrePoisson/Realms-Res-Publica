package rrp.neoforge.urban;

import java.util.List;

/**
 * Décisions et géométrie pures des défenses (sans Minecraft, donc testables) : seuils de déclenchement, emplacement des
 * tours, tracé carré de l'enceinte et portes aux croisements de route.
 */
public final class DefensePlan {

    /** Seuils de déclenchement (config.yml). */
    public record Rules(int population, int threats, int ringRadius, int stoneStock, int stonePopulation) {}

    /** Colonnes de l'enceinte posées (ou améliorées) à chaque pas du bâtisseur. */
    public static final int COLUMNS_PER_STEP = 8;
    /** Hauteur de la palissade et des murs. */
    public static final int HEIGHT = 3;
    /** Nombre de tours commandées avant et après {@link #BIG_VILLAGE} habitants. */
    public static final int TOWERS = 4;
    public static final int TOWERS_BIG = 8;
    public static final int BIG_VILLAGE = 24;
    /** Retrait des tours vers l'intérieur par rapport à l'enceinte. */
    public static final int TOWER_INSET = 5;

    private DefensePlan() {}

    // ---------- décision ----------

    /** Vrai quand la population ou l'insécurité justifie de commencer l'enceinte. */
    public static boolean shouldStart(int population, int threats, Rules r) {
        return population >= r.population() || threats >= r.threats();
    }

    /** Vrai quand la palissade est terminée et que le stock de pierre et la population permettent les murs. */
    public static boolean shouldUpgrade(Defense d, int stoneStock, int population, Rules r) {
        return d.stage() == Defense.PALISADE && d.palisadeDone() && stoneStock >= r.stoneStock() && population >= r.stonePopulation();
    }

    /** Nombre de tours souhaitées pour cette population. */
    public static int towerTarget(int population) {
        return population >= BIG_VILLAGE ? TOWERS_BIG : TOWERS;
    }

    /** Emplacement de la tour {@code index} : coins puis milieux des côtés, retirés vers l'intérieur. */
    public static int[] towerSite(int cx, int cz, int radius, int index) {
        int r = Math.max(1, radius - TOWER_INSET);
        int[][] dirs = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}, {0, 1}, {-1, 0}, {0, -1}, {1, 0}};
        int[] d = dirs[index % dirs.length];
        return new int[] {cx + d[0] * r, cz + d[1] * r};
    }

    // ---------- géométrie ----------

    /** Nombre de colonnes de l'enceinte carrée de rayon {@code r}. */
    public static int ringLength(int r) {
        return 8 * r;
    }

    /** Colonne {@code i} de l'enceinte (x,z) : on part du coin nord-ouest et on tourne dans le sens horaire. */
    public static int[] ringAt(int cx, int cz, int r, int i) {
        int side = 2 * r;
        int k = Math.floorMod(i, 4 * side);
        int s = k / side;
        int t = k % side;
        return switch (s) {
            case 0 -> new int[] {cx - r + t, cz - r};
            case 1 -> new int[] {cx + r, cz - r + t};
            case 2 -> new int[] {cx + r - t, cz + r};
            default -> new int[] {cx - r, cz + r - t};
        };
    }

    /** Vrai si {@code (x,z)} est sur le tracé d'une route ou juste à côté : la porte s'ouvre là. Jamais sur un coin. */
    public static boolean isGate(List<int[]> roadPaths, int cx, int cz, int r, int x, int z) {
        if (Math.abs(x - cx) == r && Math.abs(z - cz) == r) {
            return false;
        }
        for (int[] path : roadPaths) {
            for (int i = 0; i + 1 < path.length; i += 2) {
                if (Math.abs(path[i] - x) + Math.abs(path[i + 1] - z) <= 1) {
                    return true;
                }
            }
        }
        return false;
    }
}
