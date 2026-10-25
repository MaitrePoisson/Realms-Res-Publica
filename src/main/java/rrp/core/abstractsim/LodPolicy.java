package rrp.core.abstractsim;

/**
 * Decides when a village switches between {@link Lod#ACTIVE} and {@link Lod#ABSTRACT}. Becoming active is immediate
 * (a player arrives, the world must be real at once); becoming abstract needs the village to stay out of any active
 * chunk for a grace period, so that walking along a chunk-loading border does not make it flap.
 */
public final class LodPolicy {

    private LodPolicy() {
    }

    /**
     * @param current       the current level of detail
     * @param live          true if the village hall is in a chunk that is entity-ticking right now
     * @param inactiveTicks consecutive ticks the village has been seen not live (0 when live)
     * @param graceTicks    ticks of continuous inactivity required to turn abstract
     */
    public static Lod next(Lod current, boolean live, long inactiveTicks, long graceTicks) {
        if (live) {
            return Lod.ACTIVE;
        }
        if (current == Lod.ABSTRACT) {
            return Lod.ABSTRACT;
        }
        return inactiveTicks >= graceTicks ? Lod.ABSTRACT : Lod.ACTIVE;
    }
}
