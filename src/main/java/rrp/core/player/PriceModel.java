package rrp.core.player;

import java.util.HashMap;
import java.util.Map;

/**
 * Local prices: a resource is cheap where the stock exceeds the village's need and dear where it falls short.
 * Every unit traded moves the stock, hence the next unit's price. Pure logic.
 */
public final class PriceModel {

    private final double base;
    private final double need;
    private final double minMult;
    private final double maxMult;
    private final double spread;

    public PriceModel(double base, double need, double minMult, double maxMult, double spread) {
        this.base = base;
        this.need = Math.max(1, need);
        this.minMult = minMult;
        this.maxMult = Math.max(minMult, maxMult);
        this.spread = spread;
    }

    /** Mid price of one unit when {@code stock} units are held: base at stock = need, rising to the cap as it empties. */
    public double unitPrice(double stock) {
        double ratio = stock <= 0 ? maxMult : need / stock;
        return base * Math.max(minMult, Math.min(maxMult, ratio));
    }

    /** Emeralds the merchant pays to buy {@code qty} units out of {@code stock} (dearer than the mid price). */
    public long buyCost(double stock, int qty) {
        double sum = 0;
        for (int i = 0; i < qty; i++) {
            sum += unitPrice(stock - i) * (1 + spread);
        }
        return (long) Math.ceil(sum - 1e-9);
    }

    /** Emeralds the merchant receives for selling {@code qty} units into {@code stock} (cheaper than the mid price). */
    public long sellRevenue(double stock, int qty) {
        double sum = 0;
        for (int i = 0; i < qty; i++) {
            sum += unitPrice(stock + i) * (1 - spread);
        }
        return (long) Math.floor(sum + 1e-9);
    }

    /** Largest quantity (at most {@code want}) the merchant can buy with {@code purse} emeralds. */
    public int affordable(double stock, int want, long purse) {
        int n = Math.max(0, want);
        while (n > 0 && buyCost(stock, n) > purse) {
            n--;
        }
        return n;
    }

    /** Units already traded per village and resource in the current window, to cap what a village will absorb. */
    public static final class Saturation {
        private final Map<String, Integer> used = new HashMap<>();

        private static String key(String village, String resource) {
            return village + "|" + resource;
        }

        public int remaining(String village, String resource, int limit) {
            return Math.max(0, limit - used.getOrDefault(key(village, resource), 0));
        }

        public void record(String village, String resource, int qty) {
            if (qty > 0) {
                used.merge(key(village, resource), qty, Integer::sum);
            }
        }

        public int used(String village, String resource) {
            return used.getOrDefault(key(village, resource), 0);
        }

        public void reset() {
            used.clear();
        }
    }
}
