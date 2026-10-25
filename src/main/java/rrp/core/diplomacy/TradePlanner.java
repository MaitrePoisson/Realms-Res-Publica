package rrp.core.diplomacy;

import java.util.Map;
import java.util.Optional;
import rrp.core.Resource;

/**
 * Troc entre deux villages : chacun cède une ressource dont il a le surplus contre une ressource en manque chez lui
 * et en surplus chez l'autre, selon la valeur relative des biens (le minerai vaut plus que le blé). Pur.
 */
public final class TradePlanner {

    /** {@code give} part de A vers B ; {@code get} revient de B vers A. */
    public record Offer(Resource give, int giveAmount, Resource get, int getAmount) { }

    private TradePlanner() { }

    /** Valeur d'une unité (nourriture = 1). */
    public static int value(Resource r) {
        return switch (r) {
            case ORE -> 3;
            case FOOD, WOOD, STONE -> 1;
        };
    }

    /**
     * Cherche un troc équitable. Un village cède ce qui dépasse {@code surplus} (en gardant au moins {@code deficit})
     * et reçoit ce qui lui manque (stock sous {@code deficit}). {@code maxLoad} borne chaque chargement.
     */
    public static Optional<Offer> plan(Map<Resource, Integer> a, Map<Resource, Integer> b, int surplus, int deficit,
                                       int maxLoad) {
        Offer best = null;
        for (Resource give : Resource.values()) {
            for (Resource get : Resource.values()) {
                if (give == get) {
                    continue;
                }
                int sa = a.getOrDefault(give, 0);
                int sb = b.getOrDefault(get, 0);
                boolean aSurplus = sa >= surplus && b.getOrDefault(give, 0) < deficit;
                boolean bSurplus = sb >= surplus && a.getOrDefault(get, 0) < deficit;
                if (!aSurplus || !bSurplus) {
                    continue;
                }
                int availableGive = Math.min(maxLoad, sa - deficit);
                int availableGet = Math.min(maxLoad, sb - deficit);
                // quantité reçue limitée par l'offre de B ; quantité cédée proportionnelle à la valeur
                int getAmount = Math.min(availableGet, availableGive * value(give) / value(get));
                int giveAmount = (int) Math.ceil((double) getAmount * value(get) / value(give));
                if (getAmount <= 0 || giveAmount <= 0 || giveAmount > availableGive) {
                    continue;
                }
                if (best == null || getAmount * value(get) > best.getAmount() * value(best.get())) {
                    best = new Offer(give, giveAmount, get, getAmount);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Chance d'embuscade : chaque garde d'escorte la divise par deux. */
    public static double ambushChance(double base, int escorts) {
        return Math.max(0, Math.min(1, base)) * Math.pow(0.5, Math.max(0, escorts));
    }
}
