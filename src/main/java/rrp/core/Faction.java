package rrp.core;

/** Camp d'un village : village d'origine ou campement dissident. */
public enum Faction {
    LOYALIST("Loyaliste"),
    DISSIDENT("Dissident");

    private final String label;

    Faction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
