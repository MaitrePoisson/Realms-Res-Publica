package rrp.core.tactics;

/**
 * State machine of a civilian facing danger: ALERT (the danger is noticed, the bell rings) -> SHELTER (walks to the
 * house or the hall) -> WAIT_SECURED (stays inside) -> back to work. The way back is closed until the danger has been
 * gone, without interruption, for {@code releaseTicks}: the guards must have cleared it. Driven by observations only,
 * so that it can be tested without Minecraft.
 */
public final class CivilianCycle {

    /** Phase of the cycle. */
    public enum Phase {
        /** Nothing to fear: the civilian works. */
        CALM,
        /** The danger was just noticed (one tick: the village is warned). */
        ALERT,
        /** Walking to the refuge. */
        SHELTER,
        /** At the refuge, waiting for the guards to secure the village. */
        WAIT_SECURED
    }

    private final int releaseTicks;
    private final int shelterTimeout;
    private Phase phase = Phase.CALM;
    private int sheltering;
    private int clearFor;
    private boolean released;

    /**
     * @param releaseTicks   consecutive ticks without danger after which a sheltered civilian goes back to work
     * @param shelterTimeout ticks after which a civilian that cannot reach its refuge hides where it stands
     */
    public CivilianCycle(int releaseTicks, int shelterTimeout) {
        if (releaseTicks < 1 || shelterTimeout < 1) {
            throw new IllegalArgumentException("releaseTicks/shelterTimeout must be positive");
        }
        this.releaseTicks = releaseTicks;
        this.shelterTimeout = shelterTimeout;
    }

    public Phase phase() {
        return phase;
    }

    /** True when the civilian must not work (any phase but CALM). */
    public boolean sheltered() {
        return phase != Phase.CALM;
    }

    /** True for the single tick the civilian was released back to work. */
    public boolean released() {
        return released;
    }

    /** Ticks without danger counted so far in WAIT_SECURED. */
    public int clearFor() {
        return clearFor;
    }

    /**
     * Advances the cycle by one tick.
     *
     * @param danger    a hostile is perceived near the civilian right now
     * @param atRefuge  the civilian stands at its refuge
     */
    public Phase tick(boolean danger, boolean atRefuge) {
        released = false;
        switch (phase) {
            case CALM -> {
                if (danger) {
                    phase = Phase.ALERT;
                    sheltering = 0;
                    clearFor = 0;
                }
            }
            case ALERT -> phase = atRefuge ? Phase.WAIT_SECURED : Phase.SHELTER;
            case SHELTER -> {
                clearFor = danger ? 0 : clearFor + 1;
                if (atRefuge || ++sheltering >= shelterTimeout) {
                    phase = Phase.WAIT_SECURED;
                } else if (clearFor >= releaseTicks) {
                    release(); // the guards cleared the threat while it was still on its way
                }
            }
            case WAIT_SECURED -> {
                clearFor = danger ? 0 : clearFor + 1;
                if (clearFor >= releaseTicks) {
                    release();
                }
            }
        }
        return phase;
    }

    private void release() {
        phase = Phase.CALM;
        released = true;
    }

    /** Back to CALM at once (the villager changed role, died, was moved away). */
    public void reset() {
        phase = Phase.CALM;
        sheltering = 0;
        clearFor = 0;
        released = false;
    }
}
