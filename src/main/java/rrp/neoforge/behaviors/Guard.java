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

    /** Portée d'une frappe supplémentaire, en blocs, d'un garde posté sur une tour. */
    static final int TOWER_BONUS = 8;
    /** Distance (blocs) à la tour en deçà de laquelle le garde est considéré comme posté. */
    static final int TOWER_REACH = 6;

    /** Tour de guet de ce garde (répartition stable par identifiant), ou {@code null} si le village n'en a pas. */
    static int[] tower(BehaviorContext ctx) {
        var towers = ctx.site().defense().towers();
        return towers.isEmpty() ? null : towers.get(Math.floorMod(ctx.villager().getUUID().hashCode(), towers.size()));
    }

    @Override
    public Role role() {
        return Role.GUARD;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        if (rrp.neoforge.ExploitationWatcher.expelStrangers(ctx.level(), ctx.site(), ctx.config(), ctx.registry()) > 0) {
            return true;
        }
        int r = ctx.workRadius();
        int[] post = tower(ctx);
        if (post != null && ctx.villager().distanceToSqr(post[0] + 0.5, post[1], post[2] + 0.5) <= TOWER_REACH * TOWER_REACH) {
            r += TOWER_BONUS;
        }
        AABB box = ctx.villager().getBoundingBox().inflate(r, 16, r);
        String world = Worlds.name(ctx.level());
        Optional<Mob> target = ctx.level().getEntitiesOfClass(Mob.class, box, e -> e instanceof Enemy && e.isAlive())
                .stream()
                .filter(e -> ctx.site().contains(world, e.getX(), e.getZ()))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(ctx.villager())));
        if (target.isEmpty()) {
            if (post != null && !ctx.villager().isNoAi()
                    && ctx.villager().distanceToSqr(post[0] + 0.5, post[1], post[2] + 0.5) > 4) {
                ctx.villager().getNavigation().moveTo(post[0] + 0.5, post[1], post[2] + 0.5, 0.5);
                return true;
            }
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
