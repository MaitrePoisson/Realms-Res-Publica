package rrp.core.territory;

/**
 * Relations entre deux factions : un point de départ selon le couple de doctrines, dégradé par les incidents de
 * frontière. Pur et déterministe ; le diplomate s'en sert pour moduler ses visites.
 */
public final class FactionRelations {

    /** Attitude entre deux factions. */
    public enum Stance {
        TRADE(1.5), NEUTRAL(1.0), HOSTILE(0.25);

        private final double visitMultiplier;

        Stance(double visitMultiplier) { this.visitMultiplier = visitMultiplier; }

        /** Multiplicateur du gain de relations d'une visite diplomatique. */
        public double visitMultiplier() { return visitMultiplier; }
    }

    private FactionRelations() { }

    /** Attitude de départ du couple de doctrines ({@code null} = inconnue, donc neutre). */
    public static Stance base(Doctrine a, Doctrine b) {
        if (a == null || b == null) {
            return Stance.NEUTRAL;
        }
        if (a == Doctrine.WARLIKE_EMPIRE && b != Doctrine.MERCHANT_CITY
                || b == Doctrine.WARLIKE_EMPIRE && a != Doctrine.MERCHANT_CITY) {
            return Stance.HOSTILE;
        }
        return a == Doctrine.MERCHANT_CITY && b == Doctrine.MERCHANT_CITY ? Stance.TRADE : Stance.NEUTRAL;
    }

    /** Attitude réelle : un incident la dégrade d'un cran, trois la rendent hostile. */
    public static Stance stance(Doctrine a, Doctrine b, int incidents) {
        Stance s = base(a, b);
        if (incidents >= 3) {
            return Stance.HOSTILE;
        }
        if (incidents >= 1 && s != Stance.HOSTILE) {
            return Stance.values()[s.ordinal() + 1];
        }
        return s;
    }
}
