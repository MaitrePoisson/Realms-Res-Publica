package rrp.core.diplomacy;

import java.util.Map;
import java.util.TreeMap;
import rrp.core.territory.Doctrine;
import rrp.core.territory.FactionRelations;
import rrp.core.territory.FactionRelations.Stance;

/**
 * Indice relationnel (-100 à +100) entre chaque couple de villages connus. Chaque couple a un indice courant et une
 * base (issue du couple de doctrines) vers laquelle l'indice revient lentement. Pur et déterministe.
 */
public final class RelationMatrix {

    public static final double MIN = -100;
    public static final double MAX = 100;

    /** Seuils de lecture de l'indice. */
    public record Thresholds(double tradeAt, double hostileAt) { }

    private static final class Entry {
        double value;
        double baseline;

        Entry(double value, double baseline) {
            this.value = value;
            this.baseline = baseline;
        }
    }

    private final Map<String, Entry> entries = new TreeMap<>();

    /** Clé non orientée d'un couple. */
    public static String key(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    /** Base d'un couple de doctrines : alliés naturels, neutres ou ennemis naturels. */
    public static double baseline(Doctrine a, Doctrine b, double tradeBase, double hostileBase) {
        return switch (FactionRelations.base(a, b)) {
            case TRADE -> tradeBase;
            case HOSTILE -> hostileBase;
            case NEUTRAL -> 0;
        };
    }

    private static double clamp(double v) {
        return Math.max(MIN, Math.min(MAX, v));
    }

    /** Déclare le couple (sans effet s'il est déjà connu) : l'indice démarre à sa base. */
    public void ensure(String a, String b, double baseline) {
        double base = clamp(baseline);
        entries.putIfAbsent(key(a, b), new Entry(base, base));
    }

    public boolean known(String a, String b) {
        return entries.containsKey(key(a, b));
    }

    /** Indice courant ; 0 pour un couple inconnu. */
    public double get(String a, String b) {
        Entry e = entries.get(key(a, b));
        return e == null ? 0 : e.value;
    }

    /** Variation de l'indice (borné) ; renvoie la nouvelle valeur. Un couple inconnu part de 0. */
    public double add(String a, String b, double delta) {
        Entry e = entries.computeIfAbsent(key(a, b), k -> new Entry(0, 0));
        e.value = clamp(e.value + delta);
        return e.value;
    }

    /** Fait revenir chaque indice vers sa base de {@code rate * seconds} au plus. */
    public void tick(double seconds, double rate) {
        double step = Math.max(0, seconds * rate);
        if (step == 0) {
            return;
        }
        for (Entry e : entries.values()) {
            double d = e.baseline - e.value;
            e.value = Math.abs(d) <= step ? e.baseline : e.value + Math.signum(d) * step;
        }
    }

    /** Lecture à trois niveaux de l'indice. */
    public static Stance stance(double index, Thresholds t) {
        if (index <= t.hostileAt()) {
            return Stance.HOSTILE;
        }
        return index >= t.tradeAt() ? Stance.TRADE : Stance.NEUTRAL;
    }

    public Stance stance(String a, String b, Thresholds t) {
        return stance(get(a, b), t);
    }

    /** Copie en lecture seule : clé -> {indice, base}. */
    public Map<String, double[]> snapshot() {
        Map<String, double[]> out = new TreeMap<>();
        entries.forEach((k, e) -> out.put(k, new double[] {e.value, e.baseline}));
        return out;
    }

    public void restore(String key, double value, double baseline) {
        entries.put(key, new Entry(clamp(value), clamp(baseline)));
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }
}
