package rrp.core.tactics;

import java.util.ArrayList;
import java.util.List;

/**
 * Mobile patrols along the border of a village: the patrolling guards are grouped in pairs, each pair walks its own
 * stretch of the border ring and the two members keep a few blocks apart.
 */
public final class PatrolRoute {

    /** Number of waypoints on the border ring. */
    public static final int WAYPOINTS = 12;

    private PatrolRoute() {
    }

    /** Pair a patrolling guard belongs to: guards 0 and 1 form pair 0, guards 2 and 3 pair 1, and so on. */
    public static int pairOf(int rank) {
        return Math.max(0, rank) / 2;
    }

    /** Partner rank in the pair, or -1 when the guard walks alone (odd one out). */
    public static int partnerOf(int rank, int patrolCount) {
        int partner = (rank % 2 == 0) ? rank + 1 : rank - 1;
        return partner >= 0 && partner < patrolCount ? partner : -1;
    }

    /** The waypoints of the border ring of radius {@code radius} around (cx, cz), evenly spread. */
    public static List<Vec2> ring(double cx, double cz, double radius) {
        List<Vec2> out = new ArrayList<>(WAYPOINTS);
        for (int i = 0; i < WAYPOINTS; i++) {
            double a = 2 * Math.PI * i / WAYPOINTS;
            out.add(new Vec2(cx + radius * Math.cos(a), cz + radius * Math.sin(a)));
        }
        return out;
    }

    /**
     * Waypoint a guard heads for. Each pair starts on a different part of the ring ({@code pairs} pairs share it)
     * and moves on by one waypoint each time {@code step} grows; the second member of a pair follows one waypoint
     * behind so the two never stand on the same spot.
     */
    public static Vec2 waypoint(List<Vec2> ring, int rank, int pairs, int step) {
        int pair = pairOf(rank);
        int start = ring.size() * pair / Math.max(1, pairs);
        int behind = rank % 2;
        return ring.get(Math.floorMod(start + step - behind, ring.size()));
    }
}
