package rrp.core.building;

/**
 * How fast a builder lays blocks: one block every {@code ticksPerBlock} ticks. A pace of 0 is the legacy instant mode
 * (the whole job at once), kept for tests and for villages simulated in the abstract.
 */
public record BuildPace(int ticksPerBlock) {

    public BuildPace {
        ticksPerBlock = Math.max(0, ticksPerBlock);
    }

    /** True for the legacy mode: no delay, everything due at once. */
    public boolean instant() {
        return ticksPerBlock == 0;
    }

    /** Blocks due after {@code elapsedTicks} ticks of work (the first one is due after one full delay). */
    public long due(long elapsedTicks) {
        if (instant()) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, elapsedTicks) / ticksPerBlock;
    }

    /** Ticks left before the next block may be laid, {@code sinceLast} ticks after the previous one. */
    public int ticksLeft(long sinceLast) {
        if (instant()) {
            return 0;
        }
        return (int) Math.max(0, ticksPerBlock - Math.max(0, sinceLast));
    }

    /** True when the next block may be laid, {@code sinceLast} ticks after the previous one. */
    public boolean ready(long sinceLast) {
        return ticksLeft(sinceLast) == 0;
    }

    /** Ticks needed to lay {@code blocks} blocks. */
    public long ticksFor(long blocks) {
        return instant() ? 0 : Math.max(0, blocks) * ticksPerBlock;
    }
}
