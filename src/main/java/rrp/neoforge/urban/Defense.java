package rrp.neoforge.urban;

import java.util.ArrayList;
import java.util.List;

/**
 * Défenses d'un village : enceinte carrée autour de l'Hôtel de Ville (palissade de bois, puis murs de pierre) et
 * tours de guet. Ne conserve que des nombres : stade, rayon, curseurs de pose et d'amélioration, tours commandées
 * et sommets des tours construites. Persisté dans villages.json ; la pose reprend après un redémarrage.
 */
public final class Defense {

    public static final int NONE = 0;
    public static final int PALISADE = 1;
    public static final int STONE = 2;

    private int stage;
    private int radius;
    private int laid;
    private int upgraded;
    private int towersIssued;
    private final List<int[]> towers = new ArrayList<>();

    public void restore(int stage, int radius, int laid, int upgraded, int towersIssued) {
        this.stage = stage;
        this.radius = radius;
        this.laid = laid;
        this.upgraded = upgraded;
        this.towersIssued = towersIssued;
    }

    public int stage() { return stage; }
    public int radius() { return radius; }
    public int laid() { return laid; }
    public int upgraded() { return upgraded; }
    public int towersIssued() { return towersIssued; }
    /** Sommets (x,y,z) des tours de guet construites, où les gardes prennent leur poste. */
    public List<int[]> towers() { return towers; }

    public boolean started() { return stage >= PALISADE; }
    public int length() { return radius <= 0 ? 0 : DefensePlan.ringLength(radius); }
    public boolean palisadeDone() { return started() && laid >= length(); }
    public boolean stoneDone() { return stage >= STONE && upgraded >= length(); }

    public void start(int radius) {
        this.stage = PALISADE;
        this.radius = radius;
        this.laid = 0;
        this.upgraded = 0;
    }

    public void startUpgrade() {
        this.stage = STONE;
        this.upgraded = 0;
    }

    public void advanceLaid(int n) { laid = Math.min(length(), laid + n); }
    public void advanceUpgraded(int n) { upgraded = Math.min(length(), upgraded + n); }
    public void towerIssued() { towersIssued++; }
}
