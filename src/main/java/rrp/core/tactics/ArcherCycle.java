package rrp.core.tactics;

/**
 * Firing rhythm of a guard archer: draw the bow, loose the arrow, then reload behind cover for a while before
 * drawing again. One instance per archer; time is fed in ticks.
 */
public final class ArcherCycle {

    /** Phase of the cycle. */
    public enum Phase {
        /** No target: bow lowered. */
        IDLE,
        /** The bow is being drawn. */
        DRAW,
        /** Reloading: the archer ducks behind the battlements and cannot shoot. */
        RELOAD
    }

    /** Ticks to draw the bow before the arrow leaves. */
    public static final int DRAW_TICKS = 20;

    private final int reloadTicks;
    private Phase phase = Phase.IDLE;
    private int left;

    public ArcherCycle(int reloadTicks) {
        if (reloadTicks < 1) {
            throw new IllegalArgumentException("reloadTicks >= 1");
        }
        this.reloadTicks = reloadTicks;
    }

    public Phase phase() {
        return phase;
    }

    /** True while the archer must stay behind cover. */
    public boolean ducking() {
        return phase == Phase.RELOAD;
    }

    /**
     * Advances by {@code ticks}. Returns the number of arrows loosed during them (0 or more: a long step can cover
     * several cycles). Without a target the draw is abandoned; a reload in progress still runs to its end.
     */
    public int advance(int ticks, boolean hasTarget) {
        int shots = 0;
        int rest = Math.max(0, ticks);
        while (rest > 0) {
            switch (phase) {
                case IDLE -> {
                    if (!hasTarget) {
                        return shots;
                    }
                    phase = Phase.DRAW;
                    left = DRAW_TICKS;
                }
                case DRAW -> {
                    if (!hasTarget) {
                        phase = Phase.IDLE;
                        return shots;
                    }
                    int used = Math.min(rest, left);
                    left -= used;
                    rest -= used;
                    if (left == 0) {
                        shots++;
                        phase = Phase.RELOAD;
                        left = reloadTicks;
                    }
                }
                case RELOAD -> {
                    int used = Math.min(rest, left);
                    left -= used;
                    rest -= used;
                    if (left == 0) {
                        phase = Phase.IDLE;
                    }
                }
                default -> throw new IllegalStateException();
            }
        }
        return shots;
    }
}
