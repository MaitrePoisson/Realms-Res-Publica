package rrp.core;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Répartition déterministe des rôles dans un village : un indice (la profession vanilla) est suivi
 * tant qu'un rôle ne devient pas majoritaire, et les rôles essentiels (garde, intendant, diplomate)
 * existent dès que la population le permet. Aucune dépendance à Bukkit ni au hasard.
 */
public final class RoleBalance {
    /** Ordre de préférence quand il faut combler un rôle (ex æquo). */
    private static final List<Role> PRIORITY = List.of(Role.FARMER, Role.GUARD, Role.STEWARD, Role.DIPLOMAT,
            Role.FORESTER, Role.MINER, Role.BUILDER);
    private static final List<Role> ESSENTIAL = List.of(Role.GUARD, Role.STEWARD, Role.DIPLOMAT);

    private RoleBalance() { }

    /** Population minimale pour exiger au moins un villageois dans ce rôle (0 = jamais exigé). */
    public static int minPopulation(Role role) {
        return switch (role) {
            case GUARD -> 3;
            case STEWARD -> 4;
            case DIPLOMAT -> 5;
            default -> 0;
        };
    }

    public static Map<Role, Integer> count(Collection<VillagerState> villagers) {
        Map<Role, Integer> counts = new EnumMap<>(Role.class);
        for (Role r : Role.values()) {
            counts.put(r, 0);
        }
        for (VillagerState v : villagers) {
            counts.merge(v.role(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Rôle d'un nouvel arrivant : l'indice s'il n'est pas déjà surreprésenté (plus d'un tiers de la
     * population, au moins un villageois), sinon le rôle le moins représenté.
     */
    public static Role choose(Map<Role, Integer> counts, int population, Role hint) {
        int cap = Math.max(1, (int) Math.ceil(population / 3.0));
        if (hint != null && counts.getOrDefault(hint, 0) < cap) {
            return hint;
        }
        Role best = PRIORITY.get(0);
        for (Role r : PRIORITY) {
            if (counts.getOrDefault(r, 0) < counts.getOrDefault(best, 0)) {
                best = r;
            }
        }
        return best;
    }

    /**
     * Garantit au moins un garde, un intendant et un diplomate quand la population est suffisante,
     * en reconvertissant des villageois de {@code adjustable} (jamais les autres) pris dans le rôle
     * le plus nombreux.
     */
    public static void ensureEssentials(List<VillagerState> all, Collection<VillagerState> adjustable) {
        for (Role needed : ESSENTIAL) {
            if (all.size() < minPopulation(needed)) {
                continue;
            }
            Map<Role, Integer> counts = count(all);
            if (counts.get(needed) > 0) {
                continue;
            }
            VillagerState pick = null;
            for (VillagerState v : adjustable) {
                if (counts.get(v.role()) > 1 && (pick == null || counts.get(v.role()) >= counts.get(pick.role()))) {
                    pick = v;
                }
            }
            if (pick != null) {
                pick.setRole(needed);
            }
        }
    }
}
