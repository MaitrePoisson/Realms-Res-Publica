package rrp.neoforge.behaviors;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import rrp.core.Role;
import rrp.neoforge.Worlds;

import java.util.Comparator;
import java.util.Optional;

/** Repère le monstre hostile le plus proche dans son rayon de travail et le frappe. */
public final class Guard extends RoleBehavior {

    /** Dégâts d'une frappe de garde : de quoi abattre un monstre courant d'un coup. */
    static final float STRIKE_DAMAGE = 20;

    @Override
    public Role role() {
        return Role.GUARD;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        int r = ctx.workRadius();
        AABB box = ctx.villager().getBoundingBox().inflate(r, 16, r);
        String world = Worlds.name(ctx.level());
        Optional<Mob> target = ctx.level().getEntitiesOfClass(Mob.class, box, e -> e instanceof Enemy && e.isAlive())
                .stream()
                .filter(e -> ctx.site().contains(world, e.getX(), e.getZ()))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(ctx.villager())));
        if (target.isEmpty()) {
            return false;
        }
        Mob monster = target.get();
        if (!ctx.villager().isNoAi()) {
            ctx.villager().getNavigation().moveTo(monster, 0.5);
        }
        monster.hurt(ctx.level().damageSources().mobAttack(ctx.villager()), STRIKE_DAMAGE);
        if (!monster.isAlive()) {
            ctx.site().state().setThreats(ctx.site().state().threats() - 1);
        }
        return true;
    }
}
