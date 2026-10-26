package rrp.neoforge.tactics;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import rrp.core.Role;
import rrp.neoforge.RrpMod;
import rrp.neoforge.Worlds;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Military against civilian reaction to a threat. Guards never panic: their hostile and hurt sensors are taken off
 * their brain (and any panic memory or activity is wiped as a safety net), so they stand their ground. Civilians keep
 * the vanilla flight, also from the monsters vanilla villagers ignore (skeletons, spiders, creepers), ring the village
 * bell and head for their bed when the hostile is not right on them.
 */
@EventBusSubscriber(modid = RrpMod.MOD_ID)
public final class CivilianReaction {

    /** Ticks between two bell rings of the same village. */
    static final int BELL_COOLDOWN = 100;
    /** Distance (blocks) from which a civilian notices a monster vanilla villagers do not fear. */
    static final int NOTICE_RANGE = 12;
    /** Below this distance (blocks) to the hostile a civilian just runs; farther, it heads for its bed. */
    static final double RUN_RANGE = 6;

    private static final Map<Villager, Map<SensorType<?>, Sensor<?>>> STRIPPED = new WeakHashMap<>();
    private static final Map<String, Long> LAST_RING = new HashMap<>();
    private static long rings;

    private CivilianReaction() {
    }

    /** Number of bell rings since the start (tests, diagnostic). */
    public static long rings() {
        return rings;
    }

    public static void reset() {
        LAST_RING.clear();
        rings = 0;
    }

    @SubscribeEvent
    public static void onVillagerTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager v) || !(v.level() instanceof ServerLevel level) || v.isNoAi()
                || RrpMod.adapter() == null) {
            return;
        }
        var state = RrpMod.adapter().stateOf(v);
        boolean child = v.isBaby();
        if (state == null && !child) {
            return;
        }
        if (state != null && state.role() == Role.GUARD && !child) {
            CivilianMachine.forget(v);
            soldier(v);
        } else {
            restore(v);
            civilian(v, level);
        }
    }

    // ---------- guards ----------

    /** True when this guard's panic sensors are off (diagnostic). */
    public static boolean stripped(Villager v) {
        return STRIPPED.containsKey(v);
    }

    private static void soldier(Villager v) {
        strip(v);
        Brain<Villager> brain = v.getBrain();
        boolean panic = brain.isActive(Activity.PANIC);
        brain.eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
        brain.eraseMemory(MemoryModuleType.HURT_BY);
        brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        if (panic) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.PATH);
            v.getNavigation().stop();
            brain.setActiveActivityIfPossible(Activity.IDLE);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<SensorType<?>, Sensor<?>> sensors(Brain<?> brain) throws ReflectiveOperationException {
        Field f = Brain.class.getDeclaredField("sensors");
        f.setAccessible(true);
        return (Map<SensorType<?>, Sensor<?>>) f.get(brain);
    }

    private static void strip(Villager v) {
        if (STRIPPED.containsKey(v)) {
            return;
        }
        try {
            Map<SensorType<?>, Sensor<?>> live = sensors(v.getBrain());
            Map<SensorType<?>, Sensor<?>> taken = new HashMap<>();
            for (SensorType<?> t : new SensorType<?>[] {SensorType.VILLAGER_HOSTILES, SensorType.HURT_BY}) {
                Sensor<?> s = live.remove(t);
                if (s != null) {
                    taken.put(t, s);
                }
            }
            STRIPPED.put(v, taken);
        } catch (ReflectiveOperationException | RuntimeException e) {
            STRIPPED.put(v, Map.of()); // no reflection access: the per-tick wipe is the only guard against panic
        }
    }

    /** A villager that stopped being a guard gets its fear back. */
    private static void restore(Villager v) {
        Map<SensorType<?>, Sensor<?>> taken = STRIPPED.remove(v);
        if (taken == null || taken.isEmpty()) {
            return;
        }
        try {
            sensors(v.getBrain()).putAll(taken);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // nothing more can be done
        }
    }

    // ---------- civilians ----------

    private static void civilian(Villager v, ServerLevel level) {
        Brain<Villager> brain = v.getBrain();
        if (v.tickCount % 10 == 0 && !brain.hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)) {
            Monster feared = level.getEntitiesOfClass(Monster.class, v.getBoundingBox().inflate(NOTICE_RANGE),
                    m -> m.isAlive() && (m instanceof AbstractSkeleton || m instanceof Spider || m instanceof Creeper))
                    .stream().findFirst().orElse(null);
            if (feared != null) {
                brain.setMemory(MemoryModuleType.NEAREST_HOSTILE, feared); // the vanilla panic takes it from here
            }
        }
        boolean alarmed = brain.hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE) || brain.isActive(Activity.PANIC);
        CivilianMachine.tick(v, level, alarmed); // alert, bell, shelter, wait for the guards, release
    }

    static void ringBell(Villager v, ServerLevel level) {
        var site = RrpMod.registry().find(Worlds.name(level), v.getX(), v.getZ());
        String key = site == null ? Worlds.name(level) : site.id();
        long now = level.getGameTime();
        Long last = LAST_RING.get(key);
        if (last != null && now - last < BELL_COOLDOWN) {
            return;
        }
        LAST_RING.put(key, now);
        rings++;
        level.playSound(null, v.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 4.0F, 1.0F);
    }
}
