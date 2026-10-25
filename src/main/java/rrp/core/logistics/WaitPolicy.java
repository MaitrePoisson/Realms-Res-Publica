package rrp.core.logistics;

/**
 * What a worker does while it waits for a chest to be refilled (active wait): it complains to the steward once,
 * helps clearing the land when there is something to clear, otherwise sits. Pure decision, no side effect.
 */
public final class WaitPolicy {

    /** One way of waiting. */
    public enum Choice {
        /** Tell the steward that the chest is empty (raises the shortage flag). */
        COMPLAIN,
        /** Break grass and bushes nearby. */
        CLEAR,
        /** Sit down and rest. */
        SIT
    }

    private WaitPolicy() {
    }

    /**
     * @param alreadyComplained the steward has already been told during this wait
     * @param stewardKnown      the village has a steward to complain to
     * @param canClear          there is vegetation to clear within reach
     */
    public static Choice choose(boolean alreadyComplained, boolean stewardKnown, boolean canClear) {
        if (!alreadyComplained && stewardKnown) {
            return Choice.COMPLAIN;
        }
        return canClear ? Choice.CLEAR : Choice.SIT;
    }

    /** True when it is time to look in the chest again. */
    public static boolean shouldRetry(long waitedTicks, long retryTicks) {
        return waitedTicks >= retryTicks;
    }
}
