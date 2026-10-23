package rrp.core;

/** Niveaux d'escalade d'un village, du calme à la guerre civile. */
public enum CrisisLevel {
    STABLE("Stable"),
    CONTESTATION("Contestation"),
    EXIL("Exil"),
    GUERRE_CIVILE("Guerre civile");

    private final String label;

    CrisisLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Niveau immédiatement plus grave (le dernier reste inchangé). */
    public CrisisLevel up() {
        return this == GUERRE_CIVILE ? this : values()[ordinal() + 1];
    }

    /** Niveau immédiatement moins grave (le premier reste inchangé). */
    public CrisisLevel down() {
        return this == STABLE ? this : values()[ordinal() - 1];
    }
}
