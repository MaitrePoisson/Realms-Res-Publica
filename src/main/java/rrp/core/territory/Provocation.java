package rrp.core.territory;

import java.util.HashMap;
import java.util.Map;

/**
 * Provocation : score par couple (propriétaire, intrus) qui monte quand un village exploite un chunk
 * revendiqué par un voisin et retombe avec le temps. Trois seuils : avertissement, expulsion, guerre
 * de frontière. Pur et déterministe.
 */
public final class Provocation {

    /** Palier atteint par un couple. */
    public enum Stage { NONE, WARN, EXPEL, WAR }

    private final double warn;
    private final double expel;
    private final double war;
    private final double decayPerS;
    private final Map<String, Double> scores = new HashMap<>();
    private final Map<String, Stage> stages = new HashMap<>();

    public Provocation(double warn, double expel, double war, double decayPerS) {
        this.warn = warn;
        this.expel = Math.max(warn, expel);
        this.war = Math.max(this.expel, war);
        this.decayPerS = decayPerS;
    }

    private static String key(String owner, String intruder) {
        return owner + ">" + intruder;
    }

    public double score(String owner, String intruder) {
        return scores.getOrDefault(key(owner, intruder), 0.0);
    }

    public Stage stage(String owner, String intruder) {
        return stages.getOrDefault(key(owner, intruder), Stage.NONE);
    }

    private Stage stageFor(double s) {
        return s >= war ? Stage.WAR : s >= expel ? Stage.EXPEL : s >= warn ? Stage.WARN : Stage.NONE;
    }

    /** Ajoute de la provocation ; renvoie le nouveau palier s'il vient d'être franchi, sinon {@code NONE}. */
    public Stage add(String owner, String intruder, double amount) {
        String k = key(owner, intruder);
        double s = scores.merge(k, Math.max(0, amount), Double::sum);
        Stage now = stageFor(s);
        Stage before = stages.getOrDefault(k, Stage.NONE);
        if (now.compareTo(before) > 0) {
            stages.put(k, now);
            return now;
        }
        return Stage.NONE;
    }

    /** Fait retomber tous les scores ; un palier atteint est oublié quand le score repasse sous son seuil. */
    public void decay(double dtSeconds) {
        double d = decayPerS * dtSeconds;
        scores.replaceAll((k, v) -> Math.max(0, v - d));
        scores.entrySet().removeIf(e -> e.getValue() <= 0);
        stages.replaceAll((k, st) -> st.compareTo(stageFor(scores.getOrDefault(k, 0.0))) > 0
                ? stageFor(scores.getOrDefault(k, 0.0)) : st);
        stages.values().removeIf(st -> st == Stage.NONE);
    }

    /** Efface un couple (fin de la guerre de frontière). */
    public void reset(String owner, String intruder) {
        scores.remove(key(owner, intruder));
        stages.remove(key(owner, intruder));
    }
}
