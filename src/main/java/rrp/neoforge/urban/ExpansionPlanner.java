package rrp.neoforge.urban;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import rrp.core.player.ZoneType;
import rrp.core.player.Zones;
import rrp.core.territory.Doctrine;

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
        return plan(s, q, spareBeds, null);
    }

    /** Idem, avec les bâtiments et les défenses favorisés par la doctrine ({@code null} = neutre). */
    public static Optional<BuildOrder> plan(Situation s, Quotas q, int spareBeds, Doctrine doctrine) {
        return plan(s, q, spareBeds, doctrine, List.of());
    }

    /** Plot type a building must stand on once the ruler has drawn plots of it; null = no dedicated plot type. */
    static ZoneType zoneOf(BuildType t) {
        return switch (t) {
            case HOUSE -> ZoneType.RESIDENTIAL;
            case FARM -> ZoneType.FARM;
            case MARKET -> ZoneType.MARKET;
            case BARRACKS -> ZoneType.BARRACKS;
            default -> null;
        };
    }

    /**
     * Idem, honouring the plots drawn by the ruler: a building with a dedicated plot type goes into such a plot when
     * one exists (and is favoured), every other site avoids the plots, and nothing is ever placed in a forbidden one.
     */
    public static Optional<BuildOrder> plan(Situation s, Quotas q, int spareBeds, Doctrine doctrine, List<Zones.Zone> zones) {
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
        int bestScore = 0;
        for (BuildType t : BuildType.values()) {
            int need = target(t, s, spareBeds, doctrine) - have.getOrDefault(t, 0);
            int score = need > 0 && preferred(doctrine, t) ? need + 100 : need;
            if (need > 0 && zoneOf(t) != null && Zones.has(zones, zoneOf(t))) {
                score += 50;
            }
            if (score > bestScore && need > 0 && wood >= t.wood() && stone >= t.stone()) {
                best = t;
                bestScore = score;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        int index = s.queued().size() + s.built().values().stream().mapToInt(Integer::intValue).sum();
        int[] p = place(s.hall(), index, best, have.getOrDefault(best, 0), zones);
        return Optional.of(BuildOrder.of(best, p[0], p[1], p[2]));
    }

    /** Building spot: inside a plot of its own type if the ruler drew one, else on the ring outside every plot. */
    static int[] place(int[] hall, int index, BuildType t, int sameType, List<Zones.Zone> zones) {
        if (zones.isEmpty()) {
            return slot(hall, index);
        }
        ZoneType zt = zoneOf(t);
        if (zt != null) {
            int[] spot = Zones.spot(zones, zt, sameType, 8);
            if (spot != null) {
                return new int[] {spot[0], hall[1], spot[1]};
            }
        }
        for (int i = 0; i < 64; i++) {
            int[] p = slot(hall, index + i);
            if (Zones.typeAt(zones, p[0], p[2]) == null) {
                return p;
            }
        }
        return slot(hall, index);
    }

    /** Nombre de bâtiments souhaités de ce type (déjà construits ou commandés compris) pour cette population. */
    static int target(BuildType t, Situation s, int spareBeds) {
        return target(t, s, spareBeds, null);
    }

    /** Bâtiments que la doctrine passe avant tous les autres tant qu'il en manque. */
    static boolean preferred(Doctrine d, BuildType t) {
        if (d == null) {
            return false;
        }
        return switch (d) {
            case MERCHANT_CITY -> t == BuildType.MARKET;
            case WARLIKE_EMPIRE -> t == BuildType.BARRACKS;
            case ISOLATIONIST_THEOCRACY -> t == BuildType.MONUMENT || t == BuildType.BREWERY;
        };
    }

    static int target(BuildType t, Situation s, int spareBeds, Doctrine d) {
        int pop = s.population();
        double wallPriority = d == null ? 1.0 : d.wallPriority();
        return switch (t) {
            case HOUSE -> Math.max(0, pop + spareBeds - s.beds()) + built(s, BuildType.HOUSE);
            case FARM -> pop / 4;
            case WORKSHOP -> pop / 6;
            case TOWER -> (int) Math.round(pop / 8.0 * wallPriority);
            case WALL -> pop >= Math.round(12 / wallPriority) ? 4 : 0;
            case MARKET -> d == Doctrine.MERCHANT_CITY ? Math.max(1, pop / 5) : 0;
            case BARRACKS -> d == Doctrine.WARLIKE_EMPIRE ? Math.max(1, pop / 8) : 0;
            case MONUMENT -> d == Doctrine.ISOLATIONIST_THEOCRACY && pop >= 10 ? 1 : 0;
            case BREWERY -> d == Doctrine.ISOLATIONIST_THEOCRACY ? Math.max(1, pop / 10) : 0;
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
