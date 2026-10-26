package rrp.neoforge.behaviors;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import rrp.core.Role;
import rrp.neoforge.Worlds;
import rrp.neoforge.logistics.Armoury;
import rrp.neoforge.logistics.CarriedItems;
import rrp.core.Resource;
import rrp.core.SimConfig;

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

    /**
     * Kit rules of a village that has an armoury: an unequipped guard walks to it and takes what it lacks, never
     * patrols without a weapon (it waits at the armoury, and the village is told when the armoury is empty), and
     * takes its rations from the warehouse once equipped. Returns the outcome of the action, or {@code null} when
     * the guard is armed and free to patrol.
     */
    private static Boolean kit(BehaviorContext ctx) {
        var site = ctx.site();
        var level = ctx.level();
        var v = ctx.villager();
        var armoury = Armoury.barrel(site, level);
        if (armoury == null) {
            return null; // no armoury (yet): the guard keeps the old behaviour
        }
        var logistics = ctx.logistics();
        if (!Armoury.complete(v) && logistics.requestEquip(v, site, level) != null) {
            return true; // walking to the armoury
        }
        if (!Armoury.hasWeapon(v)) {
            Armoury.reportEmpty(site, level);
            if (!v.isNoAi() && v.distanceToSqr(armoury.getBlockPos().getCenter()) > 9) {
                v.getNavigation().moveTo(armoury.getBlockPos().getX() + 0.5, armoury.getBlockPos().getY(),
                        armoury.getBlockPos().getZ() + 0.5, 0.5);
            }
            return false;
        }
        int rations = (int) ctx.config().get(SimConfig.Key.GUARD_RATIONS);
        int missing = rations - CarriedItems.count(v, Resource.FOOD);
        // never empty a hungry village's larder: rations only when the stock covers them twice over
        if (missing > 0 && ctx.warehouse().read(site, level).getOrDefault(Resource.FOOD, 0) >= 2 * rations) {
            var got = logistics.access().withdraw(v, site, level, Resource.FOOD, missing, true);
            if (got.moved() > 0 || got.tripRequested()) {
                return true;
            }
        }
        return null;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        Boolean kitted = kit(ctx);
        if (kitted != null) {
            return kitted;
        }
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
