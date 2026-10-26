package rrp.core.logistics;

/** How the total cost of a job is spread over its steps, so that the steps pay exactly the total and nothing more. */
public final class CostShare {

    private CostShare() {
    }

    /** Part of {@code cost} due once the first {@code done} of {@code steps} steps are done (the last step completes the total). */
    public static int due(int cost, int steps, int done) {
        if (steps <= 0) {
            return cost;
        }
        return (int) ((long) cost * Math.max(0, Math.min(done, steps)) / steps);
    }

    /** Part of {@code cost} that the step number {@code step} (from 0) consumes. */
    public static int ofStep(int cost, int steps, int step) {
        return due(cost, steps, step + 1) - due(cost, steps, step);
    }
}
