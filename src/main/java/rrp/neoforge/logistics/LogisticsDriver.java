package rrp.neoforge.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.logistics.ChestTrip;
import rrp.neoforge.VillageSite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs the physical trips of villagers to the village containers: walk to the chest, open the lid (vanilla sound),
 * rummage 20-40 ticks looking at it and sneaking, move the items through {@link ChestAccess}, close the lid. A
 * villager on a trip is skipped by the behaviour scheduler ({@link #busy}). Trips are not persisted: after a restart
 * only the carried items (villager inventory) remain, which the roles deposit again.
 */
public final class LogisticsDriver {

    /** Direction of a trip. */
    public enum Kind {
        WITHDRAW,
        DEPOSIT,
        /** A guard equips itself from the armoury. */
        EQUIP,
        /** The smith forges one piece of the kit and stocks it in the armoury. */
        FORGE
    }

    /** Ticks after which a villager that still has not reached the chest gives up. */
    static final int MAX_TRAVEL_TICKS = 1200;
    /** Ticks between two walk orders (the vanilla brain may overwrite them). */
    static final int REORDER_TICKS = 10;
    static final double WALK_SPEED = 0.6;

    /** One trip to a container. */
    public static final class Trip {
        private final Kind kind;
        private final Villager villager;
        private final VillageSite site;
        private final ServerLevel level;
        private final Resource resource;
        private final int amount;
        private final BlockPos chest;
        private final ChestTrip core;
        private int travel;
        private boolean opened;
        private boolean done;
        private boolean failed;
        private String reason = "";
        private int moved;

        Trip(Kind kind, Villager villager, VillageSite site, ServerLevel level, Resource resource, int amount,
             BlockPos chest, ChestTrip core) {
            this.kind = kind;
            this.villager = villager;
            this.site = site;
            this.level = level;
            this.resource = resource;
            this.amount = amount;
            this.chest = chest;
            this.core = core;
        }

        public Kind kind() { return kind; }
        public BlockPos chest() { return chest; }
        public Resource resource() { return resource; }
        public ChestTrip.Phase phase() { return core.phase(); }
        /** The trip is over (success, empty chest or failure). */
        public boolean done() { return done; }
        /** The trip ended because the villager could not reach the chest or died. */
        public boolean failed() { return failed; }
        /** Why the trip failed (empty when it did not). */
        public String reason() { return reason; }
        /** Units moved by the trip. */
        public int moved() { return moved; }
        /** The trip ended with nothing moved although it reached the chest. */
        public boolean emptyHanded() { return done && !failed && moved == 0; }
    }

    private final Supplier<SimConfig> config;
    private final ChestAccess access;
    private final Map<UUID, Trip> trips = new HashMap<>();
    private long opens;
    private long closes;
    private long sounds;

    public LogisticsDriver(Supplier<SimConfig> config, ChestAccess access) {
        this.config = config;
        this.access = access;
        access.bind(this);
    }

    public ChestAccess access() {
        return access;
    }

    /** True while the villager is on a trip (the scheduler leaves it alone). */
    public boolean busy(Villager v) {
        return trips.containsKey(v.getUUID());
    }

    /** The trip in progress for this villager, or {@code null}. */
    public Trip trip(Villager v) {
        return trips.get(v.getUUID());
    }

    public int activeTrips() {
        return trips.size();
    }

    /** Lid openings, lid closings and lid sounds since the start (tests, diagnostic). */
    public long opens() { return opens; }
    public long closes() { return closes; }
    public long sounds() { return sounds; }

    /** Starts a trip to take {@code amount} units of {@code r}; {@code null} if no container holds it. Joins a running trip. */
    public Trip requestWithdraw(Villager v, VillageSite site, ServerLevel level, Resource r, int amount) {
        Trip running = trips.get(v.getUUID());
        if (running != null) {
            return running;
        }
        BlockPos chest = access.containerWith(site, level, v.position(), r);
        return chest == null ? null : start(new Trip(Kind.WITHDRAW, v, site, level, r, amount, chest, newCore(level)));
    }

    /** Starts a trip to put the carried {@code r} ({@code null} = everything) into the stock; {@code null} if nothing to put. */
    public Trip requestDeposit(Villager v, VillageSite site, ServerLevel level, Resource r) {
        Trip running = trips.get(v.getUUID());
        if (running != null) {
            return running;
        }
        BlockPos chest = access.depositTarget(site, level);
        boolean carries = (r == null ? CarriedItems.total(v) : CarriedItems.count(v, r)) > 0;
        return chest == null || !carries ? null
                : start(new Trip(Kind.DEPOSIT, v, site, level, r, 0, chest, newCore(level)));
    }

    /** Starts a trip to the armoury to equip a guard; {@code null} if there is no armoury or nothing it can take. */
    public Trip requestEquip(Villager v, VillageSite site, ServerLevel level) {
        Trip running = trips.get(v.getUUID());
        if (running != null) {
            return running;
        }
        var armoury = Armoury.barrel(site, level);
        return armoury == null || !Armoury.canTake(v, armoury) ? null
                : start(new Trip(Kind.EQUIP, v, site, level, null, 0, armoury.getBlockPos(), newCore(level)));
    }

    /** Starts a trip to the armoury where the smith forges for {@code forge_ticks}; {@code null} without armoury. */
    public Trip requestForge(Villager v, VillageSite site, ServerLevel level) {
        Trip running = trips.get(v.getUUID());
        if (running != null) {
            return running;
        }
        var armoury = Armoury.barrel(site, level);
        int ticks = (int) config.get().get(SimConfig.Key.FORGE_TICKS);
        return armoury == null ? null
                : start(new Trip(Kind.FORGE, v, site, level, null, 0, armoury.getBlockPos(), new ChestTrip(access.reach(), ticks)));
    }

    /** Stops a villager's trip (lid closed, villager released). */
    public void cancel(Villager v) {
        Trip t = trips.remove(v.getUUID());
        if (t != null) {
            release(t);
        }
    }

    /** Ends every trip (server stop): no lid stays open. */
    public void closeAll() {
        for (Trip t : new ArrayList<>(trips.values())) {
            finish(t, true);
        }
    }

    /** Advances every trip by one tick (called with the mod clock; one step whatever the acceleration). */
    public void onTicks(long ticks) {
        if (trips.isEmpty() || ticks <= 0) {
            return;
        }
        for (Trip t : new ArrayList<>(trips.values())) {
            tick(t);
        }
    }

    private ChestTrip newCore(ServerLevel level) {
        SimConfig c = config.get();
        int rummage = ChestTrip.rummageDuration((int) c.get(SimConfig.Key.RUMMAGE_MIN_TICKS),
                (int) c.get(SimConfig.Key.RUMMAGE_MAX_TICKS), level.random.nextDouble());
        return new ChestTrip(access.reach(), rummage);
    }

    private Trip start(Trip t) {
        trips.put(t.villager.getUUID(), t);
        return t;
    }

    private void tick(Trip t) {
        Villager v = t.villager;
        if (!v.isAlive() || v.isRemoved() || v.level() != t.level) {
            t.reason = "villager gone";
            finish(t, true);
            return;
        }
        if (!t.level.hasChunk(t.chest.getX() >> 4, t.chest.getZ() >> 4)
                || !(t.level.getBlockEntity(t.chest) instanceof Container target)) {
            t.reason = "container gone";
            finish(t, true);
            return;
        }
        boolean stock = switch (t.kind) {
            case DEPOSIT, FORGE -> true;
            case EQUIP -> Armoury.canTake(v, target);
            case WITHDRAW -> ChestAccess.holds(target, t.resource);
        };
        ChestTrip.Phase before = t.core.phase();
        ChestTrip.Phase now = t.core.tick(ChestAccess.distance(v, t.chest), stock, true);
        switch (now) {
            case GO_TO_CHEST -> {
                if (++t.travel > MAX_TRAVEL_TICKS) {
                    t.reason = "no way to the container";
                    finish(t, true);
                } else if (t.travel % REORDER_TICKS == 1) {
                    walkTo(v, t.chest);
                }
            }
            case RUMMAGE -> {
                if (before != ChestTrip.Phase.RUMMAGE) {
                    v.getNavigation().stop();
                    v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    v.setShiftKeyDown(true);
                    v.setPose(Pose.CROUCHING);
                    if (!t.opened) {
                        t.opened = true;
                        setOpen(t.level, t.chest, true);
                    }
                }
                v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(Vec3.atCenterOf(t.chest)));
                v.getLookControl().setLookAt(Vec3.atCenterOf(t.chest));
            }
            case CARRY, WORK -> {
                if (t.core.takeNow()) {
                    t.moved = switch (t.kind) {
                        case WITHDRAW -> access.withdraw(v, t.site, t.level, t.resource, t.amount, false).moved();
                        case DEPOSIT -> access.deposit(v, t.site, t.level, t.resource, false).moved();
                        case EQUIP -> Armoury.equip(v, target);
                        case FORGE -> Armoury.forge(v, t.site, t.level, config.get());
                    };
                }
                finish(t, false);
            }
            case ACTIVE_WAIT -> finish(t, false); // the chest had nothing: the role decides how to wait
        }
    }

    private static void walkTo(Villager v, BlockPos chest) {
        if (v.isNoAi()) {
            return;
        }
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(chest, (float) WALK_SPEED, 1));
        v.getNavigation().moveTo(v.getNavigation().createPath(chest, 1), WALK_SPEED);
    }

    private void finish(Trip t, boolean failed) {
        trips.remove(t.villager.getUUID());
        t.done = true;
        t.failed = failed;
        release(t);
    }

    private void release(Trip t) {
        if (t.opened) {
            t.opened = false;
            if (t.level.hasChunk(t.chest.getX() >> 4, t.chest.getZ() >> 4)) {
                setOpen(t.level, t.chest, false);
            }
        }
        Villager v = t.villager;
        if (v.isAlive() && !v.isRemoved()) {
            v.setShiftKeyDown(false);
            v.setPose(Pose.STANDING);
            v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            CarriedItems.showInHand(v);
        }
    }

    /** Opens or closes the lid of a chest (block event) or a barrel (OPEN state), with the vanilla sound. */
    private void setOpen(ServerLevel level, BlockPos pos, boolean open) {
        BlockState st = level.getBlockState(pos);
        SoundEvent sound;
        if (st.is(Blocks.BARREL)) {
            level.setBlock(pos, st.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
            sound = open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE;
        } else if (st.getBlock() instanceof ChestBlock) {
            level.blockEvent(pos, st.getBlock(), 1, open ? 1 : 0);
            if (st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos other = pos.relative(ChestBlock.getConnectedDirection(st));
                level.blockEvent(other, level.getBlockState(other).getBlock(), 1, open ? 1 : 0);
            }
            sound = open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE;
        } else {
            return;
        }
        if (open) {
            opens++;
        } else {
            closes++;
        }
        sounds++;
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, 0.5f,
                level.random.nextFloat() * 0.1f + 0.9f);
    }
}
