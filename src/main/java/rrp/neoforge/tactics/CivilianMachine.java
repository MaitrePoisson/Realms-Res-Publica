package rrp.neoforge.tactics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import rrp.core.SimConfig;
import rrp.core.tactics.CivilianCycle;
import rrp.core.tactics.CivilianCycle.Phase;
import rrp.neoforge.RrpMod;
import rrp.neoforge.VillageSite;
import rrp.neoforge.Worlds;
import rrp.neoforge.behaviors.ActiveWait;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The civilian side of a danger, as a state machine per villager (builder, farmer, miner, forester, diplomat,
 * children): ALERT (the bell rings, the village is told, the work and any chest trip stop) -> SHELTER (walks to its
 * bed, else to the Town Hall) -> WAIT_SECURED (stays inside) -> back to work, only once no hostile has been near for
 * {@code civilian_release_ticks} in a row, that is after the guards cleared the threat. Guards never go through it.
 */
public final class CivilianMachine {

    /** Distance (blocks) within which a hostile keeps a civilian sheltered. */
    static final double DANGER_RANGE = 16;
    /** Ticks between two scans for hostiles. */
    static final int SCAN_EVERY = 10;
    /** Distance (blocks) to the refuge at which the civilian counts as inside. */
    static final double ARRIVE = 3.5;
    /** Ticks after which a civilian that cannot reach its refuge hides where it stands. */
    static final int SHELTER_TIMEOUT = 600;
    /** Ticks between two "danger" / "all clear" chat lines of one village. */
    static final int TELL_EVERY = 600;
    /** Ticks between two walk orders (the vanilla brain may overwrite them). */
    static final int REORDER = 10;
    static final float SPEED = 0.8F;

    private static final class Entry {
        CivilianCycle cycle;
        int releaseTicks;
        boolean danger;
        long scannedAt = -1_000_000L;
        BlockPos refuge;
    }

    private static final Map<Villager, Entry> ENTRIES = new WeakHashMap<>();
    private static final Map<String, Long> TOLD_ALARM = new HashMap<>();
    private static final Map<String, Long> TOLD_CLEAR = new HashMap<>();
    private static long alerts;
    private static long releases;

    private CivilianMachine() {
    }

    /** Phase of this civilian ({@link Phase#CALM} for a villager the machine never saw). */
    public static Phase phase(Villager v) {
        Entry e = ENTRIES.get(v);
        return e == null ? Phase.CALM : e.cycle.phase();
    }

    /** True while the civilian must not work: it is alerted, on its way to shelter or waiting inside. */
    public static boolean sheltered(Villager v) {
        Entry e = ENTRIES.get(v);
        return e != null && e.cycle.sheltered();
    }

    /** Alerts raised and releases back to work since the start (tests, diagnostic). */
    public static long alerts() { return alerts; }
    public static long releases() { return releases; }

    public static void reset() {
        ENTRIES.clear();
        TOLD_ALARM.clear();
        TOLD_CLEAR.clear();
        alerts = 0;
        releases = 0;
    }

    /** A villager that is not a civilian (any more) leaves the machine. */
    public static void forget(Villager v) {
        ENTRIES.remove(v);
    }

    /**
     * Advances the machine of one civilian by a tick.
     *
     * @param alarmed the vanilla brain already sees a hostile (or panics)
     */
    public static void tick(Villager v, ServerLevel level, boolean alarmed) {
        int release = (int) RrpMod.simConfig().get(SimConfig.Key.CIVILIAN_RELEASE_TICKS);
        Entry e = ENTRIES.get(v);
        if (e == null || e.releaseTicks != release) {
            Phase keep = e == null ? Phase.CALM : e.cycle.phase();
            e = new Entry();
            e.releaseTicks = release;
            e.cycle = new CivilianCycle(release, SHELTER_TIMEOUT);
            ENTRIES.put(v, e);
            if (keep != Phase.CALM) {
                e.cycle.tick(true, false); // a reloaded configuration keeps a sheltered civilian sheltered
            }
        }
        if (e.cycle.phase() == Phase.CALM && !alarmed && !scan(e, v, level)) {
            return; // nothing to fear, nothing to do
        }
        e.refuge = refuge(v, level);
        boolean danger = alarmed || scan(e, v, level);
        boolean inside = e.refuge != null && v.distanceToSqr(e.refuge.getX() + 0.5, e.refuge.getY(), e.refuge.getZ() + 0.5)
                <= ARRIVE * ARRIVE;
        Phase before = e.cycle.phase();
        Phase now = e.cycle.tick(danger, inside);
        if (now == Phase.ALERT && before == Phase.CALM) {
            alert(v, level);
        }
        switch (now) {
            case SHELTER -> head(v, e);
            case WAIT_SECURED -> {
                if (before != Phase.WAIT_SECURED) {
                    v.getBrain().eraseMemory(MemoryModuleType.PATH);
                }
                hold(v, e, inside);
            }
            default -> { }
        }
        if (e.cycle.released()) {
            releases++;
            tellClear(v, level);
        }
    }

    /** True when a hostile monster is within {@link #DANGER_RANGE} (scanned every {@link #SCAN_EVERY} ticks). */
    private static boolean scan(Entry e, Villager v, ServerLevel level) {
        long now = level.getGameTime();
        if (now - e.scannedAt >= SCAN_EVERY || now < e.scannedAt) {
            e.scannedAt = now;
            e.danger = !level.getEntitiesOfClass(Monster.class, v.getBoundingBox().inflate(DANGER_RANGE),
                    m -> m.isAlive() && m instanceof Enemy).isEmpty();
        }
        return e.danger;
    }

    /** The bed of the villager, else the Town Hall of its village, else nothing. */
    private static BlockPos refuge(Villager v, ServerLevel level) {
        GlobalPos home = v.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        if (home != null && home.dimension().equals(level.dimension())) {
            return home.pos();
        }
        VillageSite site = RrpMod.registry().find(Worlds.name(level), v.getX(), v.getZ());
        int[] hall = site == null ? null : site.hall();
        return hall == null ? null : new BlockPos(hall[0], hall[1], hall[2]);
    }

    private static void alert(Villager v, ServerLevel level) {
        alerts++;
        if (RrpMod.logistics() != null) {
            RrpMod.logistics().cancel(v); // the lid closes, the trip is over
        }
        ActiveWait.end(v);
        CivilianReaction.ringBell(v, level);
        VillageSite site = RrpMod.registry().find(Worlds.name(level), v.getX(), v.getZ());
        if (site != null && due(TOLD_ALARM, site.id(), level.getGameTime())) {
            RrpMod.crises().tell(site, RrpMod.lang().msg("tactics.alarm", site.id()));
        }
    }

    private static void tellClear(Villager v, ServerLevel level) {
        VillageSite site = RrpMod.registry().find(Worlds.name(level), v.getX(), v.getZ());
        if (site != null && due(TOLD_CLEAR, site.id(), level.getGameTime())) {
            RrpMod.crises().tell(site, RrpMod.lang().msg("tactics.all_clear", site.id()));
        }
    }

    private static boolean due(Map<String, Long> told, String key, long now) {
        Long last = told.get(key);
        if (last != null && now >= last && now - last < TELL_EVERY) {
            return false;
        }
        told.put(key, now);
        return true;
    }

    /** Walks to the refuge, unless a hostile is right on the villager: then the vanilla flight is left alone. */
    private static void head(Villager v, Entry e) {
        if (e.refuge == null || v.isNoAi() || v.tickCount % REORDER != 0) {
            return;
        }
        var hostile = v.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).orElse(null);
        if (hostile != null && hostile.distanceToSqr(v) < CivilianReaction.RUN_RANGE * CivilianReaction.RUN_RANGE) {
            return;
        }
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(e.refuge, SPEED, 1));
    }

    /** Stays at the refuge: back to it if pushed away, otherwise standing still. */
    private static void hold(Villager v, Entry e, boolean inside) {
        if (v.isNoAi() || v.tickCount % REORDER != 0) {
            return;
        }
        Brain<Villager> brain = v.getBrain();
        if (e.refuge != null && !inside) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(e.refuge, SPEED, 1));
        } else {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            v.getNavigation().stop();
        }
    }
}
