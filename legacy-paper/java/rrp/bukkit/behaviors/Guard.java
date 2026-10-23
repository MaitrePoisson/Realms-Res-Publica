package rrp.bukkit.behaviors;

import org.bukkit.Location;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import rrp.core.Role;

import java.util.Comparator;
import java.util.Optional;

/** Repère le monstre hostile le plus proche dans son rayon de travail et le frappe. */
public final class Guard extends RoleBehavior {

    /** Dégâts d'une frappe de garde : de quoi abattre un monstre courant d'un coup. */
    static final double STRIKE_DAMAGE = 20;

    @Override
    public Role role() {
        return Role.GUARD;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        Location at = ctx.villager().getLocation();
        int r = ctx.workRadius();
        Optional<LivingEntity> target = ctx.world().getNearbyEntities(at, r, 16, r, e -> e instanceof Enemy && e.isValid()).stream()
                .filter(e -> e instanceof LivingEntity && ctx.site().contains(ctx.world().getName(), e.getLocation().getX(), e.getLocation().getZ()))
                .map(e -> (LivingEntity) e)
                .min(Comparator.comparingDouble((Entity e) -> e.getLocation().distanceSquared(at)));
        if (target.isEmpty()) {
            return false;
        }
        LivingEntity monster = target.get();
        if (ctx.villager().hasAI()) {
            ctx.villager().getPathfinder().moveTo(monster.getLocation());
        }
        monster.damage(STRIKE_DAMAGE, ctx.villager());
        if (monster.isDead() || !monster.isValid()) {
            ctx.site().state().setThreats(ctx.site().state().threats() - 1);
        }
        return true;
    }
}
