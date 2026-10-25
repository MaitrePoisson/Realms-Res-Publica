package rrp.core.logistics;

/**
 * State machine of one physical trip to a container: walk to the chest, rummage in it, carry the items back, work.
 * The trip is driven by observations only (distance to the chest, stock, position at the worksite), so that it can be
 * tested without Minecraft. Taking the items is allowed only on the tick the rummage ends ({@link #takeNow()}), and
 * only if the villager is within reach of the chest at that moment.
 */
public final class ChestTrip {

    /** Phase of the trip. */
    public enum Phase {
        GO_TO_CHEST,
        RUMMAGE,
        CARRY,
        WORK,
        /** The chest had nothing: the worker waits actively, then restarts the trip. */
        ACTIVE_WAIT
    }

    private final double reach;
    private final int rummageTicks;
    private Phase phase = Phase.GO_TO_CHEST;
    private int rummaged;
    private long waited;
    private boolean takeNow;

    public ChestTrip(double reach, int rummageTicks) {
        if (reach <= 0 || rummageTicks < 1) {
            throw new IllegalArgumentException("reach/rummageTicks must be positive");
        }
        this.reach = reach;
        this.rummageTicks = rummageTicks;
    }

    /** Rummage duration drawn between {@code min} and {@code max} ticks from a roll in [0, 1). */
    public static int rummageDuration(int min, int max, double roll) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        double r = Math.min(Math.max(roll, 0), 0.999999);
        return lo + (int) Math.floor(r * (hi - lo + 1));
    }

    /** The reach rule: the chest can be used only at {@code reach} blocks or less (limit included). */
    public static boolean inReach(double distance, double reach) {
        return distance <= reach;
    }

    public Phase phase() {
        return phase;
    }

    public int rummaged() {
        return rummaged;
    }

    public int rummageTicks() {
        return rummageTicks;
    }

    /** True for the single tick the rummage just ended with stock available: the items may be moved now. */
    public boolean takeNow() {
        return takeNow;
    }

    /** Ticks spent in the current active wait. */
    public long waited() {
        return waited;
    }

    /**
     * Advances the trip by one tick.
     *
     * @param distanceToChest distance from the villager to the container
     * @param chestHasStock   the container holds something this trip can take
     * @param atWorksite      the villager is back at the place it works
     */
    public Phase tick(double distanceToChest, boolean chestHasStock, boolean atWorksite) {
        takeNow = false;
        boolean near = inReach(distanceToChest, reach);
        switch (phase) {
            case GO_TO_CHEST -> {
                if (near) {
                    phase = Phase.RUMMAGE;
                }
            }
            case RUMMAGE -> {
                if (!near) {
                    // Interrupted (pushed away, path changed): walk back, the progress is kept.
                    phase = Phase.GO_TO_CHEST;
                } else if (++rummaged >= rummageTicks) {
                    if (chestHasStock) {
                        takeNow = true;
                        phase = Phase.CARRY;
                    } else {
                        waited = 0;
                        phase = Phase.ACTIVE_WAIT;
                    }
                }
            }
            case CARRY -> {
                if (atWorksite) {
                    phase = Phase.WORK;
                }
            }
            case WORK -> {
                // Stays here until the caller starts a new trip with restart().
            }
            case ACTIVE_WAIT -> waited++;
        }
        return phase;
    }

    /** Starts a new trip (after the work phase, or when an active wait ends). */
    public void restart() {
        phase = Phase.GO_TO_CHEST;
        rummaged = 0;
        waited = 0;
        takeNow = false;
    }
}
