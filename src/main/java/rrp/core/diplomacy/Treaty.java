package rrp.core.diplomacy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Traités entre villages : un état par couple non orienté (paix = absence d'entrée). Pur et déterministe. */
public final class Treaty {

    /** État diplomatique d'un couple. */
    public enum State { PEACE, TRADE_TREATY, DEFENSIVE_ALLIANCE, WAR }

    private final Map<String, State> states = new TreeMap<>();

    public State get(String a, String b) {
        return states.getOrDefault(RelationMatrix.key(a, b), State.PEACE);
    }

    public void set(String a, String b, State s) {
        if (s == State.PEACE) {
            states.remove(RelationMatrix.key(a, b));
        } else {
            states.put(RelationMatrix.key(a, b), s);
        }
    }

    public boolean atWar(String a, String b) {
        return get(a, b) == State.WAR;
    }

    /** Villages liés à {@code village} par un état donné. */
    public List<String> partners(String village, State s) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, State> e : states.entrySet()) {
            if (e.getValue() != s) {
                continue;
            }
            String[] p = e.getKey().split("[|]", 2);
            if (p[0].equals(village)) {
                out.add(p[1]);
            } else if (p[1].equals(village)) {
                out.add(p[0]);
            }
        }
        return out;
    }

    /**
     * Déclare la guerre de {@code aggressor} à {@code defender} : le traité du couple est rompu, et les alliés
     * défensifs du défenseur (pas déjà alliés de l'agresseur) entrent en guerre contre l'agresseur.
     * Renvoie les alliés qui rejoignent la guerre.
     */
    public List<String> declareWar(String aggressor, String defender) {
        List<String> joined = new ArrayList<>();
        for (String ally : partners(defender, State.DEFENSIVE_ALLIANCE)) {
            if (!ally.equals(aggressor) && get(ally, aggressor) != State.DEFENSIVE_ALLIANCE) {
                joined.add(ally);
            }
        }
        set(aggressor, defender, State.WAR);
        for (String ally : joined) {
            set(ally, aggressor, State.WAR);
        }
        return joined;
    }

    public Map<String, State> snapshot() {
        return new TreeMap<>(states);
    }

    public void restore(String key, State s) {
        if (s != State.PEACE) {
            states.put(key, s);
        }
    }

    public void clear() {
        states.clear();
    }
}
