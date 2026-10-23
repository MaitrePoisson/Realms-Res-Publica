package rrp.neoforge.urban;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Décide si le village s'agrandit : quand les quotas de stock (bois, pierre) ET de population sont atteints,
 * choisit le bâtiment le plus utile et en fixe une place sur un anneau autour de l'Hôtel de Ville.
 * Logique pure (sans Minecraft) pour être testable.
 */
public final class ExpansionPlanner {

    /** Quotas et plafonds (config.yml). */
    public record Quotas(int wood, int stone, int population, int maxOrders) {}

    /** Photo du village au moment de la décision. */
    public record Situation(int population, int beds, int woodStock, int stoneStock, int[] hall,
                            Map<BuildType, Integer> built, List<BuildOrder> queued) {}

    private ExpansionPlanner() {}

    /** Un ordre à émettre, ou rien si un quota n'est pas atteint, si la file est pleine ou s'il n'y a pas de besoin. */
    public static Optional<BuildOrder> plan(Situation s, Quotas q, int spareBeds) {
        if (s.hall() == null || s.queued().size() >= q.maxOrders() || s.population() < q.population()) {
            return Optional.empty();
        }
        int reservedWood = 0;
        int reservedStone = 0;
        Map<BuildType, Integer> have = new EnumMap<>(BuildType.class);
        have.putAll(s.built());
        for (BuildOrder o : s.queued()) {
            reservedWood += o.wood();
            reservedStone += o.stone();
            have.merge(o.type(), 1, Integer::sum);
        }
        int wood = s.woodStock() - reservedWood;
        int stone = s.stoneStock() - reservedStone;
        if (wood < q.wood() || stone < q.stone()) {
            return Optional.empty();
        }
        BuildType best = null;
        int bestNeed = 0;
        for (BuildType t : BuildType.values()) {
            int need = target(t, s, spareBeds) - have.getOrDefault(t, 0);
            if (need > bestNeed && wood >= t.wood() && stone >= t.stone()) {
                best = t;
                bestNeed = need;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        int index = s.queued().size() + s.built().values().stream().mapToInt(Integer::intValue).sum();
        int[] p = slot(s.hall(), index);
        return Optional.of(BuildOrder.of(best, p[0], p[1], p[2]));
    }

    /** Nombre de bâtiments souhaités de ce type (déjà construits ou commandés compris) pour cette population. */
    static int target(BuildType t, Situation s, int spareBeds) {
        int pop = s.population();
        return switch (t) {
            case HOUSE -> Math.max(0, pop + spareBeds - s.beds()) + built(s, BuildType.HOUSE);
            case FARM -> pop / 4;
            case WORKSHOP -> pop / 6;
            case TOWER -> pop / 8;
            case WALL -> pop >= 12 ? 4 : 0;
        };
    }

    private static int built(Situation s, BuildType t) {
        return s.built().getOrDefault(t, 0);
    }

    /** Emplacement n sur une spirale d'anneaux de 16 places autour de l'Hôtel de Ville (rayon 14, +8 par anneau). */
    static int[] slot(int[] hall, int index) {
        int ring = index / 16;
        double radius = 14 + 8 * ring;
        double angle = 2 * Math.PI * (index % 16) / 16.0;
        return new int[] {hall[0] + (int) Math.round(radius * Math.cos(angle)), hall[1],
                hall[2] + (int) Math.round(radius * Math.sin(angle))};
    }
}
