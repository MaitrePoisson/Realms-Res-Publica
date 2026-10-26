package rrp.neoforge.building;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.building.BuildPace;
import rrp.neoforge.RrpMod;
import rrp.neoforge.VillageSite;
import rrp.neoforge.logistics.CarriedItems;
import rrp.neoforge.tactics.CivilianMachine;
import rrp.neoforge.urban.ConstructionSite;
import rrp.neoforge.urban.TerrainLeveler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lays a site block by block, tick after tick: one step (a levelling row, then one block of the plan in
 * {@link BuildBlocks} order) every {@code build_block_ticks}, with the block item in the main hand, the arm swing, the
 * place sound and a few particles. The builder walks along to stay within {@code build_reach} of the block; an entity in
 * the way makes it wait and retry. A builder with a job is {@link #busy} and left alone by the behaviour scheduler.
 * Jobs are not persisted: the step index lives in the {@link ConstructionSite} (saved with the village), so after a
 * restart the builder's next action starts a new job at the same block.
 */
public final class BuildDriver {

    /** Ticks spent walking towards a block before giving up on the reach rule and laying it from where the builder stands. */
    static final int MAX_WALK_TICKS = 200;
    /** Ticks between two walk orders (the vanilla brain may overwrite them). */
    static final int REORDER_TICKS = 10;
    /** Ticks a builder standing in the block to lay gets to walk aside before being moved. */
    static final int ASIDE_TICKS = 60;
    static final float WALK_SPEED = 0.6f;

    /** One job: a builder laying the steps of a site. */
    public static final class Job {
        private final Villager villager;
        private final VillageSite site;
        private final ServerLevel level;
        private final ConstructionSite cs;
        private final StructureTemplate template;
        private final ConstructionSite.Supply supply;
        private int since;
        private int walking;
        private int aside;

        Job(Villager villager, VillageSite site, ServerLevel level, ConstructionSite cs, StructureTemplate template,
            ConstructionSite.Supply supply) {
            this.villager = villager;
            this.site = site;
            this.level = level;
            this.cs = cs;
            this.template = template;
            this.supply = supply;
        }

        public ConstructionSite site() { return cs; }
    }

    private final Supplier<SimConfig> config;
    private final Map<UUID, Job> jobs = new HashMap<>();
    private final Map<UUID, BuildJob> lists = new HashMap<>();
    private long placed;
    private long levelled;
    private long waits;

    public BuildDriver(Supplier<SimConfig> config) {
        this.config = config;
    }

    /** True while the villager has a job (the scheduler leaves it alone). */
    public boolean busy(Villager v) {
        return jobs.containsKey(v.getUUID()) || lists.containsKey(v.getUUID());
    }

    /** The generic block-list job of the builder (hut, Town Hall, border post...), or {@code null}. */
    public BuildJob listJob(Villager v) {
        return lists.get(v.getUUID());
    }

    public Job job(Villager v) {
        return jobs.get(v.getUUID());
    }

    public int activeJobs() {
        return jobs.size() + lists.size();
    }

    /** Blocks laid, levelling rows done and ticks lost to an entity in the way since the start (tests, diagnostic). */
    public long placed() { return placed; }
    public long levelled() { return levelled; }
    public long waits() { return waits; }

    /** True when the visible mode is on ({@code build_block_ticks} above 0). */
    public boolean visible() {
        return config.get().get(SimConfig.Key.BUILD_BLOCK_TICKS) > 0;
    }

    /** Starts (or keeps) a job for the builder on this site. */
    public Job start(Villager v, VillageSite site, ServerLevel level, ConstructionSite cs, StructureTemplate template,
                     ConstructionSite.Supply supply) {
        Job running = jobs.get(v.getUUID());
        if (running != null) {
            return running;
        }
        Job job = new Job(v, site, level, cs, template, supply);
        jobs.put(v.getUUID(), job);
        return job;
    }

    /** Starts (or keeps) a block-list job for the builder: the blocks go down in the given order, at the visible pace. */
    public BuildJob startList(Villager v, VillageSite site, ServerLevel level, java.util.List<BuildJob.Placement> blocks,
                              int woodCost, Runnable onDone) {
        BuildJob running = lists.get(v.getUUID());
        if (running != null) {
            return running;
        }
        BuildJob job = new BuildJob(v, site, level, blocks, woodCost, onDone);
        lists.put(v.getUUID(), job);
        return job;
    }

    /** Stops a builder's job (hand cleared, builder released). */
    public void cancel(Villager v) {
        Job j = jobs.remove(v.getUUID());
        if (j != null) {
            release(j);
        }
        BuildJob l = lists.remove(v.getUUID());
        if (l != null) {
            releaseHand(l.villager);
        }
    }

    /** Ends every job (server stop). */
    public void closeAll() {
        for (Job j : new ArrayList<>(jobs.values())) {
            jobs.remove(j.villager.getUUID());
            release(j);
        }
        for (BuildJob l : new ArrayList<>(lists.values())) {
            lists.remove(l.villager.getUUID());
            releaseHand(l.villager);
        }
    }

    /** Advances every job by one tick (called with the mod clock; one step whatever the acceleration). */
    public void onTicks(long ticks) {
        if ((jobs.isEmpty() && lists.isEmpty()) || ticks <= 0) {
            return;
        }
        for (Job j : new ArrayList<>(jobs.values())) {
            if (jobs.get(j.villager.getUUID()) == j) {
                tick(j);
            }
        }
        for (BuildJob l : new ArrayList<>(lists.values())) {
            if (lists.get(l.villager.getUUID()) == l) {
                tickList(l);
            }
        }
    }

    /** Ticks a builder may spend walking to a block-list job (a border post can be far); then it lays from where it stands. */
    static final int MAX_LIST_WALK_TICKS = 1200;

    private void tickList(BuildJob j) {
        Villager v = j.villager;
        if (!v.isAlive() || v.isRemoved() || v.level() != j.level || CivilianMachine.sheltered(v)
                || !RrpMod.registry().all().contains(j.site)) {
            endList(j, false);
            return;
        }
        if (j.complete()) {
            endList(j, true);
            return;
        }
        SimConfig c = config.get();
        BuildPace pace = new BuildPace(Math.max(1, (int) c.get(SimConfig.Key.BUILD_BLOCK_TICKS)));
        j.since++;
        BuildJob.Placement next = j.blocks.get(j.index);
        BlockPos target = next.pos();
        Gesture.hold(v, next.state());
        Gesture.look(v, target);
        double reach = c.get(SimConfig.Key.BUILD_REACH);
        if (v.isNoAi() || v.getEyePosition().distanceTo(Vec3.atCenterOf(target)) <= reach) {
            j.walking = 0;
        } else if (j.walking < MAX_LIST_WALK_TICKS) {
            if (j.walking++ % REORDER_TICKS == 0) {
                walkTo(v, target);
            }
            return;
        }
        if (!pace.ready(j.since)) {
            return;
        }
        for (LivingEntity l : j.level.getEntitiesOfClass(LivingEntity.class, new AABB(target))) {
            waits++;
            if (l != v) {
                j.since = 0; // someone stands in the way: look again after another pace
                return;
            }
            if (v.isNoAi() || ++j.aside > ASIDE_TICKS) {
                BlockPos away = awayFrom(v, target);
                v.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
                j.aside = 0;
            } else if (j.aside % REORDER_TICKS == 1) {
                stepAside(v, target);
            }
            return;
        }
        j.level.setBlock(target, next.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        j.index++;
        j.since = 0;
        j.aside = 0;
        placed++;
        int due = BuildJob.woodDue(j.woodCost, j.index, j.blocks.size());
        if (due > j.paid) {
            CarriedItems.take(v, Resource.WOOD, due - j.paid);
            j.paid = due;
        }
        RrpMod.registry().markDirty();
        Gesture.placed(j.level, v, target, next.state(), (int) c.get(SimConfig.Key.BUILD_PARTICLES));
        if (j.complete()) {
            endList(j, true);
        }
    }

    private void endList(BuildJob j, boolean done) {
        lists.remove(j.villager.getUUID());
        releaseHand(j.villager);
        if (done) {
            j.onDone.run();
            RrpMod.registry().markDirty();
        }
        if (done && j.villager.isAlive() && !j.villager.isRemoved() && RrpMod.behaviors() != null) {
            RrpMod.behaviors().runOnce(j.site, j.level, j.villager);
        }
    }

    private void tick(Job j) {
        Villager v = j.villager;
        if (!v.isAlive() || v.isRemoved() || v.level() != j.level || j.site.construction() != j.cs
                || CivilianMachine.sheltered(v)
                || !RrpMod.registry().all().contains(j.site)) {
            end(j, false);
            return;
        }
        ConstructionSite cs = j.cs;
        if (cs.done(j.template)) {
            end(j, true);
            return;
        }
        SimConfig c = config.get();
        BuildPace pace = new BuildPace(Math.max(1, (int) c.get(SimConfig.Key.BUILD_BLOCK_TICKS)));
        j.since++;
        var next = cs.nextBlock(j.template);
        BlockPos target = next != null ? next.at(cs.origin()) : rowCentre(j);
        if (next != null) {
            Gesture.hold(v, next.state());
        }
        Gesture.look(v, target);
        // stay within arm's reach of the work: walk along (the rule is dropped after MAX_WALK_TICKS without arriving)
        if (v.isNoAi() || inReach(v, target, next == null, j, c)) {
            j.walking = 0;
        } else if (j.walking < MAX_WALK_TICKS) {
            if (j.walking++ % REORDER_TICKS == 0) {
                walkTo(v, new BlockPos(target.getX(), Math.min(target.getY(), cs.origin().getY() + 1), target.getZ()));
            }
            return;
        } // else: no way to the block found in time, it is laid from where the builder stands
        if (!pace.ready(j.since)) {
            return;
        }
        var result = cs.advanceOne(j.level, j.template, v, j.supply, false);
        switch (result) {
            case DONE -> {
                j.since = 0;
                j.aside = 0;
                RrpMod.registry().markDirty();
                int particles = (int) c.get(SimConfig.Key.BUILD_PARTICLES);
                if (next != null) {
                    placed++;
                    Gesture.placed(j.level, v, target, next.state(), particles);
                } else {
                    levelled++;
                    v.swing(InteractionHand.MAIN_HAND);
                    var dirt = Blocks.DIRT.defaultBlockState();
                    j.level.playSound(null, target, dirt.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6f, 0.9f);
                    Gesture.dust(j.level, target, dirt, particles);
                }
            }
            case NO_SUPPLY -> end(j, true); // the builder goes to fetch more, then comes back
            case BLOCKED -> {
                waits++;
                j.since = 0; // someone stands in the way: look again after another pace
            }
            case BUILDER_IN_WAY -> {
                waits++;
                if (v.isNoAi() || ++j.aside > ASIDE_TICKS) {
                    cs.moveOut(j.level, v);
                    j.aside = 0;
                } else if (j.aside % REORDER_TICKS == 1) {
                    stepAside(v, target);
                }
            }
        }
    }

    /** Middle of the levelling row being cut (the builder works from anywhere along it). */
    private static BlockPos rowCentre(Job j) {
        var size = j.template.getSize();
        var o = j.site.construction().origin();
        return new BlockPos(o.getX() + size.getX() / 2, o.getY(),
                o.getZ() - TerrainLeveler.MARGIN + j.cs.step());
    }

    private boolean inReach(Villager v, BlockPos target, boolean row, Job j, SimConfig c) {
        double reach = c.get(SimConfig.Key.BUILD_REACH);
        Vec3 eye = v.getEyePosition();
        if (row) {
            // a levelling row is as long as the plan is wide: the builder may be anywhere along it
            double slack = j.template.getSize().getX() / 2.0 + TerrainLeveler.MARGIN;
            double dx = eye.x - (target.getX() + 0.5);
            double dz = eye.z - (target.getZ() + 0.5);
            return Math.sqrt(dx * dx + dz * dz) <= reach + slack && Math.abs(v.getY() - target.getY()) <= reach + 2;
        }
        return eye.distanceTo(Vec3.atCenterOf(target)) <= reach;
    }

    private static void walkTo(Villager v, BlockPos at) {
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(at, WALK_SPEED, 1));
        v.getNavigation().moveTo(v.getNavigation().createPath(at, 2), WALK_SPEED);
    }

    /** The builder stands in the block to lay: walks three blocks away from it. */
    private static void stepAside(Villager v, BlockPos block) {
        walkTo(v, awayFrom(v, block));
    }

    /** The ground three blocks from {@code block}, on the side the builder stands. */
    private static BlockPos awayFrom(Villager v, BlockPos block) {
        double dx = v.getX() - (block.getX() + 0.5);
        double dz = v.getZ() - (block.getZ() + 0.5);
        double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        if (len < 0.1) {
            dx = -1;
            dz = 0;
            len = 1;
        }
        return BlockPos.containing(block.getX() + 0.5 + 3 * dx / len, v.getY(), block.getZ() + 0.5 + 3 * dz / len);
    }

    private void end(Job j, boolean chain) {
        jobs.remove(j.villager.getUUID());
        release(j);
        if (chain && j.villager.isAlive() && !j.villager.isRemoved() && RrpMod.behaviors() != null) {
            // the plan is complete or the builder is out of materials: its role decides what comes next at once
            RrpMod.behaviors().runOnce(j.site, j.level, j.villager);
        }
    }

    private static void release(Job j) {
        releaseHand(j.villager);
    }

    private static void releaseHand(Villager v) {
        if (v.isAlive() && !v.isRemoved()) {
            v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            CarriedItems.showInHand(v);
            v.getNavigation().stop();
            v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
    }
}
