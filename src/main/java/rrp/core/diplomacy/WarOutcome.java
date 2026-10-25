package rrp.core.diplomacy;

/**
 * Issue d'une guerre : comparaison de la puissance des deux camps (force du régiment, attrition, brèches ouvertes)
 * et choix entre repli, pillage, annexion avec tribut ou destruction de l'Hôtel de Ville. Logique pure.
 */
public final class WarOutcome {

    /** Résultat de la guerre pour le vainqueur. */
    public enum Kind { REPELLED, PILLAGE, ANNEXATION, DESTRUCTION }

    /** Un camp : force à la levée, part des soldats encore vivants, brèches ouvertes chez l'ennemi et plafond. */
    public record Side(double strength, double aliveShare, int breaches, int breachCap) { }

    /** Verdict : vainqueur (0 = premier camp, 1 = second), genre d'issue et rapport de puissance. */
    public record Verdict(int winner, Kind kind, double ratio) { }

    /** Seuils de rapport de puissance : pillage dès {@code pillageAt}, annexion dès {@code annexAt}, destruction dès {@code destroyAt}. */
    public record Rules(double pillageAt, double annexAt, double destroyAt) { }

    private WarOutcome() { }

    /** Puissance d'un camp : force x survivants x (1 + part du plafond de brèches atteinte). */
    public static double power(Side s) {
        double alive = Math.max(0, Math.min(1, s.aliveShare()));
        double breach = s.breachCap() <= 0 ? 0 : Math.min(1.0, (double) s.breaches() / s.breachCap());
        return Math.max(0, s.strength()) * alive * (1 + breach);
    }

    /** Le plus puissant gagne ; à égalité, le premier camp (déterministe). Un repli sous {@code pillageAt}. */
    public static Verdict decide(Side first, Side second, Rules rules) {
        double a = power(first);
        double b = power(second);
        int winner = a >= b ? 0 : 1;
        double win = winner == 0 ? a : b;
        double lose = winner == 0 ? b : a;
        double ratio = lose <= 0 ? (win <= 0 ? 1 : Double.POSITIVE_INFINITY) : win / lose;
        Side w = winner == 0 ? first : second;
        Kind kind;
        if (ratio < rules.pillageAt()) {
            kind = Kind.REPELLED;
        } else if (ratio < rules.annexAt()) {
            kind = Kind.PILLAGE;
        } else if (ratio < rules.destroyAt() || w.breachCap() <= 0 || w.breaches() < w.breachCap()) {
            kind = Kind.ANNEXATION;
        } else {
            kind = Kind.DESTRUCTION;
        }
        return new Verdict(winner, kind, ratio);
    }

    /** Butin d'un pillage : {@code share} du stock du vaincu, dans la limite de {@code cap}. */
    public static int loot(int stock, double share, int cap) {
        return Math.max(0, Math.min(cap, (int) Math.floor(Math.max(0, stock) * Math.max(0, Math.min(1, share)))));
    }

    /** Tribut quotidien : {@code share} du stock produit du vaincu, dans la limite de {@code cap}. */
    public static int tribute(int stock, double share, int cap) {
        return loot(stock, share, cap);
    }
}
