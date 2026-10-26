package rrp.neoforge.building;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;
import rrp.core.Resource;
import rrp.neoforge.VillageSite;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * A generic job for the {@link BuildDriver}: a builder laying an explicit list of (position, state) blocks in the given
 * order, one every {@code build_block_ticks} (the hut, the Town Hall with its barrels, a border post...). The wood of
 * the job is paid from the builder's carried load as the blocks go down, never at once. {@code onDone} runs when the
 * last block is laid (registering the beds, the hall, the warehouse...). Not persisted: after a restart what stands
 * stays, and the builder's next action starts again from the world.
 */
public final class BuildJob {

    /** One block to lay. */
    public record Placement(BlockPos pos, BlockState state) {
    }

    final Villager villager;
    final VillageSite site;
    final ServerLevel level;
    final List<Placement> blocks;
    final int woodCost;
    final Runnable onDone;
    /** What the job's cost is paid in (wood for a palisade or a hut, stone for walls and paving). */
    final Resource payWith;
    /** What the job is for ("defense", "roads"...): one job of a kind per village at a time, so counters never run ahead. */
    String kind;
    /** Called with the number of blocks laid after each block (persisting the counters of a palisade, a road...), or {@code null}. */
    final IntConsumer progress;
    int index;
    int paid;
    int since;
    int walking;
    int aside;

    public BuildJob(Villager villager, VillageSite site, ServerLevel level, List<Placement> blocks, int woodCost,
                    Runnable onDone) {
        this(villager, site, level, blocks, woodCost, Resource.WOOD, null, onDone);
    }

    public BuildJob(Villager villager, VillageSite site, ServerLevel level, List<Placement> blocks, int cost,
                    Resource payWith, IntConsumer progress, Runnable onDone) {
        this.villager = villager;
        this.site = site;
        this.level = level;
        this.blocks = List.copyOf(blocks);
        this.woodCost = Math.max(0, cost);
        this.payWith = payWith;
        this.progress = progress;
        this.onDone = onDone;
    }

    public List<Placement> blocks() { return blocks; }

    public BuildJob kind(String kind) {
        this.kind = kind;
        return this;
    }

    public String kind() { return kind; }

    public int laid() { return index; }

    public int total() { return blocks.size(); }

    public boolean complete() { return index >= blocks.size(); }

    /** Wood that should have been paid once {@code laid} blocks are down (the share grows with the blocks; the total is exact). */
    static int woodDue(int woodCost, int laid, int total) {
        return total <= 0 ? 0 : (int) ((long) woodCost * Math.min(laid, total) / total);
    }
}
