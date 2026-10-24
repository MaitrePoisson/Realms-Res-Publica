package rrp.neoforge;

import rrp.core.Role;
import rrp.core.RoleBalance;
import rrp.core.VillagerState;

import java.util.ArrayList;
import java.util.List;

/**
 * Attribue un rôle aux villageois nouvellement adoptés. La profession vanilla n'est qu'un indice
 * (le rôle vit dans les données attachées) ; l'équilibrage est délégué au cœur ({@link RoleBalance}).
 */
public final class RoleAssigner {

    private RoleAssigner() { }

    /** Indice de rôle déduit du nom de la profession vanilla (ex. {@code farmer}), ou {@code null} sans profession utile. */
    public static Role hint(String profession) {
        return switch (profession) {
            case "farmer", "shepherd", "fisherman", "butcher" -> Role.FARMER;
            case "fletcher", "leatherworker" -> Role.FORESTER;
            case "toolsmith" -> Role.MINER;
            case "weaponsmith", "armorer" -> Role.GUARD;
            case "mason" -> Role.BUILDER;
            case "librarian", "cartographer" -> Role.DIPLOMAT;
            case "cleric" -> Role.STEWARD;
            default -> null;
        };
    }

    /**
     * Donne un rôle à chaque nouvel arrivant de {@code fresh} (dans l'ordre), en tenant compte des
     * villageois déjà en place dans {@code existing}, puis comble les rôles essentiels manquants.
     *
     * @param hints indice de rôle de chaque nouvel arrivant, dans le même ordre que {@code fresh}
     */
    public static void assign(List<VillagerState> existing, List<VillagerState> fresh, List<Role> hints) {
        assign(existing, fresh, hints, null);
    }

    /** Idem, avec les poids de rôle de la doctrine du village ({@code null} = neutre). */
    public static void assign(List<VillagerState> existing, List<VillagerState> fresh, List<Role> hints,
                              rrp.core.territory.Doctrine doctrine) {
        List<VillagerState> all = new ArrayList<>(existing);
        int population = existing.size() + fresh.size();
        for (int i = 0; i < fresh.size(); i++) {
            VillagerState v = fresh.get(i);
            v.setRole(RoleBalance.choose(RoleBalance.count(all), population, hints.get(i), doctrine));
            all.add(v);
        }
        RoleBalance.ensureEssentials(all, fresh);
    }
}
