package rrp.neoforge.tactics;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import rrp.core.SimConfig;
import rrp.neoforge.RrpMod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Anti-creeper stance (M74). Beyond {@code guard_creeper_close} blocks archers shoot the creeper before it ignites;
 * closer, a guard holds its ground behind the raised shield: it never steps back, the explosion damage is cut by
 * {@code guard_creeper_reduction} percent and its knockback is cancelled.
 */
@EventBusSubscriber(modid = RrpMod.MOD_ID)
public final class CreeperGuard {

    private static final Map<UUID, Long> HOLD_UNTIL = new HashMap<>();
    private static long absorbed;

    private CreeperGuard() {
    }

    /** The guard holds its ground against a creeper for the next few steps. */
    public static void hold(Villager v) {
        HOLD_UNTIL.put(v.getUUID(), v.level().getGameTime() + GuardAi.STEP * 3L);
    }

    public static boolean holding(Villager v) {
        Long until = HOLD_UNTIL.get(v.getUUID());
        return until != null && until >= v.level().getGameTime();
    }

    /** Explosions whose damage a holding guard has absorbed since the start (tests, diagnostic). */
    public static long absorbed() {
        return absorbed;
    }

    public static void reset() {
        HOLD_UNTIL.clear();
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Villager v && holding(v) && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            double cut = RrpMod.simConfig().get(SimConfig.Key.GUARD_CREEPER_REDUCTION) / 100.0;
            event.setAmount((float) (event.getAmount() * (1.0 - cut)));
            absorbed++;
        }
    }

    @SubscribeEvent
    public static void onKnockback(ExplosionKnockbackEvent event) {
        if (event.getAffectedEntity() instanceof Villager v && holding(v)) {
            event.setKnockbackVelocity(Vec3.ZERO);
        }
    }
}
