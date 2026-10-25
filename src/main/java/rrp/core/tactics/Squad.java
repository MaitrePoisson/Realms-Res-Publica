package rrp.core.tactics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Roles of a guard squad facing a group: two guards fix the nearest enemy, a third goes around it. */
public final class Squad {

    /** Role of a guard in the encirclement. */
    public enum Role {
        FIXER,
        FLANKER
    }

    /** A guard of the squad and where it stands. */
    public record Member(String id, Vec2 pos) {
    }

    private Squad() {
    }

    /**
     * Assigns the roles. The encirclement needs at least {@code minHostiles} hostiles and three guards: the two guards
     * nearest to the enemy fix it, the next one is the flanker, any further guard fixes too. Otherwise everybody
     * fixes (a plain line). Ties are broken by id so the result is deterministic.
     *
     * @return role by member id, nearest to the enemy first
     */
    public static Map<String, Role> assign(List<Member> members, Vec2 enemy, int hostileCount, int minHostiles) {
        List<Member> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparingDouble((Member m) -> m.pos().dist(enemy)).thenComparing(Member::id));
        boolean encircle = hostileCount >= minHostiles && sorted.size() >= 3;
        Map<String, Role> out = new LinkedHashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            out.put(sorted.get(i).id(), encircle && i == 2 ? Role.FLANKER : Role.FIXER);
        }
        return out;
    }
}
