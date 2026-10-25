package rrp.core.player;

import java.util.Map;
import java.util.TreeMap;

/** The laws of every player-ruled village, by village id. Villages without a decree follow the defaults. */
public final class Laws {

    private final Map<String, VillageLaws> byVillage = new TreeMap<>();

    /** Laws of a village, created on first use. */
    public VillageLaws of(String village) {
        return byVillage.computeIfAbsent(village, k -> new VillageLaws());
    }

    /** Laws of a village if any decree exists, else null (never creates one). */
    public VillageLaws peek(String village) {
        return byVillage.get(village);
    }

    public boolean embargo(String a, String b) {
        VillageLaws la = byVillage.get(a);
        VillageLaws lb = byVillage.get(b);
        return la != null && la.embargoes(b) || lb != null && lb.embargoes(a);
    }

    public Map<String, VillageLaws> all() {
        return byVillage;
    }

    public void remove(String village) {
        byVillage.remove(village);
    }

    public void clear() {
        byVillage.clear();
    }
}
