package rrp.neoforge.urban;

/** Types de bâtiments que l'intendant peut commander, avec leur coût en bois et en pierre. */
public enum BuildType {
    HOUSE(30, 0),
    FARM(20, 0),
    WORKSHOP(40, 20),
    TOWER(20, 40),
    WALL(10, 30);

    private final int wood;
    private final int stone;

    BuildType(int wood, int stone) {
        this.wood = wood;
        this.stone = stone;
    }

    public int wood() { return wood; }
    public int stone() { return stone; }
}
