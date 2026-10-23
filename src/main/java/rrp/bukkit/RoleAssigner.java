package rrp.bukkit;

import org.bukkit.entity.Villager;
import rrp.core.Role;
import rrp.core.RoleBalance;
import rrp.core.VillagerState;

import java.util.ArrayList;
import java.util.List;

/**
 * Attribue un rôle aux villageois nouvellement adoptés. La profession vanilla n'est qu'un indice
 * (le rôle vit dans le PDC) ; l'équilibrage est délégué au cœur ({@link RoleBalance}).
 */
public final class RoleAssigner {

    private RoleAssigner() { }

    /** Indice de rôle déduit de la profession, ou {@code null} sans profession utile. */
    public static Role hint(Villager.Profession p) {
        if (p == Villager.Profession.FARMER || p == Villager.Profession.SHEPHERD
                || p == Villager.Profession.FISHERMAN || p == Villager.Profession.BUTCHER) {
            return Role.FARMER;
        }
        if (p == Villager.Profession.FLETCHER || p == Villager.Profession.LEATHERWORKER) {
            return Role.FORESTER;
        }
        if (p == Villager.Profession.TOOLSMITH) {
            return Role.MINER;
        }
        if (p == Villager.Profession.WEAPONSMITH || p == Villager.Profession.ARMORER) {
            return Role.GUARD;
        }
        if (p == Villager.Profession.MASON) {
            return Role.BUILDER;
        }
        if (p == Villager.Profession.LIBRARIAN || p == Villager.Profession.CARTOGRAPHER) {
            return Role.DIPLOMAT;
        }
        if (p == Villager.Profession.CLERIC) {
            return Role.STEWARD;
        }
        return null;
    }

    /**
     * Donne un rôle à chaque nouvel arrivant de {@code fresh} (dans l'ordre), en tenant compte des
     * villageois déjà en place dans {@code existing}, puis comble les rôles essentiels manquants.
     *
     * @param hints indice de rôle de chaque nouvel arrivant, dans le même ordre que {@code fresh}
     */
    public static void assign(List<VillagerState> existing, List<VillagerState> fresh, List<Role> hints) {
        List<VillagerState> all = new ArrayList<>(existing);
        int population = existing.size() + fresh.size();
        for (int i = 0; i < fresh.size(); i++) {
            VillagerState v = fresh.get(i);
            v.setRole(RoleBalance.choose(RoleBalance.count(all), population, hints.get(i)));
            all.add(v);
        }
        RoleBalance.ensureEssentials(all, fresh);
    }
}
