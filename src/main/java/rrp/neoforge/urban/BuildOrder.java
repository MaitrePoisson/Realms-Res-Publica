package rrp.neoforge.urban;

/** Ordre de construction émis par l'intendant : quoi, où (cible approximative, affinée par le bâtisseur) et à quel prix. */
public record BuildOrder(BuildType type, int x, int y, int z, int wood, int stone) {

    public static BuildOrder of(BuildType type, int x, int y, int z) {
        return new BuildOrder(type, x, y, z, type.wood(), type.stone());
    }
}
