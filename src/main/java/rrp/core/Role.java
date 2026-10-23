package rrp.core;

/** Les sept rôles sociaux d'un villageois. */
public enum Role {
    BUILDER("Bâtisseur"),
    FORESTER("Forestier"),
    MINER("Mineur"),
    GUARD("Garde"),
    FARMER("Fermier"),
    STEWARD("Intendant"),
    DIPLOMAT("Diplomate");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
