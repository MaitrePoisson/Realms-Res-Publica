package rrp.core.player;

/** Pure tax rules: yield of a levy and the unrest a heavy burden causes. */
public final class Taxation {

    private Taxation() {
    }

    /** Rate asked by the ruler, brought back into [0 ; cap]. */
    public static double clamp(double rate, double cap) {
        return Math.max(0, Math.min(rate, cap));
    }

    /** Items levied in one period: population x rate x output per villager (rounded down). */
    public static int yield(int population, double rate, double perVillager) {
        if (population <= 0 || rate <= 0 || perVillager <= 0) {
            return 0;
        }
        return (int) Math.floor(population * rate * perVillager);
    }

    /** Loyalty lost by each villager in one period: nothing up to {@code free}, then {@code perPoint} per excess point. */
    public static double unrest(double totalRate, double free, double perPoint) {
        return Math.max(0, totalRate - free) * perPoint;
    }
}
