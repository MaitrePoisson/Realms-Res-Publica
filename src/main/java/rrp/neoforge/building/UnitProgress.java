package rrp.neoforge.building;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Turns the number of blocks laid by a {@link BuildJob} into the number of whole units finished (a palisade column, a road
 * tile position), so the persisted counters of a palisade or a road advance as the work goes, never ahead of it. A unit
 * with no block to lay (an already standing column) is counted as soon as the units before it are.
 */
public final class UnitProgress implements IntConsumer {

    private final List<Integer> ends = new ArrayList<>();
    private final IntConsumer unitDone;
    private int counted;

    /** @param unitDone called with the number of units newly finished */
    public UnitProgress(IntConsumer unitDone) {
        this.unitDone = unitDone;
    }

    /** Closes the unit that holds the blocks added so far ({@code blocksSoFar} = total blocks of the units up to this one). */
    public void unitEnds(int blocksSoFar) {
        ends.add(blocksSoFar);
    }

    public int units() {
        return ends.size();
    }

    /** Blocks laid so far: counts the units whose last block is down. */
    @Override
    public void accept(int laid) {
        int fresh = 0;
        while (counted < ends.size() && ends.get(counted) <= laid) {
            counted++;
            fresh++;
        }
        if (fresh > 0) {
            unitDone.accept(fresh);
        }
    }

    /** Counts what is left (the job is over). */
    public void finish() {
        accept(Integer.MAX_VALUE);
    }
}
