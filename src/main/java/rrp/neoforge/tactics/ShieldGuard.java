package rrp.neoforge.tactics;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import rrp.core.SimConfig;
import rrp.core.tactics.ShieldArc;
import rrp.neoforge.RrpMod;

/** A guard with its shield up takes no damage from projectiles coming from inside the front arc. */
@EventBusSubscriber(modid = RrpMod.MOD_ID)
public final class ShieldGuard {

    private ShieldGuard() {
    }

    /** True when this hit is stopped by the guard's raised shield. */
    public static boolean blocks(Villager guard, net.minecraft.world.damagesource.DamageSource source) {
        if (!GuardAi.shielded(guard) || !source.is(DamageTypeTags.IS_PROJECTILE)) {
            return false;
        }
        var from = source.getSourcePosition();
        if (from == null) {
            return false;
        }
        double arc = RrpMod.simConfig().get(SimConfig.Key.GUARD_SHIELD_ARC);
        return ShieldArc.blocks(guard.getYRot(), from.x - guard.getX(), from.z - guard.getZ(), arc);
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Villager v && blocks(v, event.getSource())) {
            event.setCanceled(true);
            v.level().playSound(null, v.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
    }
}
