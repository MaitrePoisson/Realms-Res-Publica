package rrp.core.tactics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Priority of the threats a guard faces: a creeper that gets close first (it must be shot before it ignites), then
 * ranged attackers (skeletons) within shooting range, then melee monsters. Ties go to the nearest one.
 */
public final class ThreatRanking {

    /** Kind of hostile. */
    public enum Kind {
        CREEPER,
        RANGED,
        MELEE
    }

    /** A hostile seen by a guard. */
    public record Threat(String id, Kind kind, double distance) {
    }

    private ThreatRanking() {
    }

    /** 0 is the most urgent. */
    public static int tier(Threat t, double creeperAlert, double rangedRange) {
        return switch (t.kind()) {
            case CREEPER -> t.distance() <= creeperAlert ? 0 : 2;
            case RANGED -> t.distance() <= rangedRange ? 1 : 2;
            case MELEE -> 2;
        };
    }

    /** Threats sorted from the most to the least urgent (equal tier and distance: by id, so it is deterministic). */
    public static List<Threat> rank(List<Threat> threats, double creeperAlert, double rangedRange) {
        List<Threat> out = new ArrayList<>(threats);
        out.sort(Comparator
                .comparingInt((Threat t) -> tier(t, creeperAlert, rangedRange))
                .thenComparingDouble(Threat::distance)
                .thenComparing(Threat::id));
        return out;
    }

    /** The threat to deal with first, or null when there is none. */
    public static Threat top(List<Threat> threats, double creeperAlert, double rangedRange) {
        List<Threat> r = rank(threats, creeperAlert, rangedRange);
        return r.isEmpty() ? null : r.get(0);
    }
}
