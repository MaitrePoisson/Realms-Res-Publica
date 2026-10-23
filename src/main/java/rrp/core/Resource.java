package rrp.core;

/** Ressources comptées dans l'entrepôt communal. */
public enum Resource {
    FOOD("Nourriture"),
    WOOD("Bois"),
    ORE("Minerai"),
    STONE("Pierre");

    private final String label;

    Resource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
