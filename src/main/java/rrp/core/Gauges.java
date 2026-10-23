package rrp.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import rrp.core.SimConfig.Key;

/**
 * Dynamique déterministe des trois jauges (satiété, moral, loyauté) d'un village. Aucune dépendance
 * à Bukkit ni au hasard : l'adaptateur remplit le {@link VillageState} avec les données réelles.
 */
public final class Gauges {
    /** Garde-fou : nombre maximal de repas d'un villageois dans un seul pas de simulation. */
    private static final int MAX_MEALS_PER_STEP = 100;

    private Gauges() { }

    /** Avance les jauges de tous les villageois de {@code dt} secondes de simulation. */
    public static void step(VillageState village, SimConfig cfg, double dt) {
        if (dt <= 0 || village.villagers().isEmpty()) {
            return;
        }
        decaySatiety(village, cfg, dt);
        feed(village, cfg);
        updateMoraleAndLoyalty(village, cfg, dt);
    }

    private static void decaySatiety(VillageState village, SimConfig cfg, double dt) {
        double loss = cfg.get(Key.SATIETY_DECAY_PER_MIN) / 60.0 * dt;
        for (VillagerState v : village.villagers()) {
            v.setSatiety(v.satiety() - loss);
        }
    }

    /**
     * Les villageois sous le seuil mangent dans l'entrepôt, les plus affamés d'abord (accès équitable)
     * ; sans stock rien ne remonte. Renvoie le nombre d'unités de nourriture consommées.
     */
    public static int feed(VillageState village, SimConfig cfg) {
        double threshold = cfg.get(Key.EAT_BELOW_SATIETY);
        double perFood = cfg.get(Key.SATIETY_PER_FOOD);
        List<VillagerState> hungry = new ArrayList<>();
        for (VillagerState v : village.villagers()) {
            if (v.satiety() < threshold) {
                hungry.add(v);
            }
        }
        hungry.sort(Comparator.comparingDouble(VillagerState::satiety));
        int eaten = 0;
        for (VillagerState v : hungry) {
            for (int i = 0; i < MAX_MEALS_PER_STEP && v.satiety() < threshold && village.stock(Resource.FOOD) > 0; i++) {
                village.addStock(Resource.FOOD, -1);
                v.setSatiety(v.satiety() + perFood);
                eaten++;
            }
        }
        return eaten;
    }

    /** Moral cible d'un villageois, selon sa satiété, son lit, la sécurité et la surpopulation. */
    public static double moraleTarget(VillageState village, VillagerState villager, int villagerIndex, SimConfig cfg) {
        double target = cfg.get(Key.MORALE_BASE) + cfg.get(Key.MORALE_W_SATIETY) * villager.satiety();
        if (villagerIndex < village.beds()) {
            target += cfg.get(Key.MORALE_BED_BONUS);
        }
        target += cfg.get(Key.MORALE_SECURITY_BONUS) * securityFactor(village, cfg);
        if (village.population() > village.beds() * cfg.get(Key.OVERPOPULATION_RATIO)) {
            target -= cfg.get(Key.MORALE_CROWDING_PENALTY);
        }
        return VillagerState.clamp(target);
    }

    /** 1 = aucune menace ; 0,5 = menaces couvertes par les gardes ; 0 = menaces non maîtrisées. */
    static double securityFactor(VillageState village, SimConfig cfg) {
        int threats = village.threats();
        if (threats == 0) {
            return 1.0;
        }
        double covered = village.count(Role.GUARD) * cfg.get(Key.INSECURITY_THREATS_PER_GUARD);
        return threats <= covered ? 0.5 : 0.0;
    }

    private static void updateMoraleAndLoyalty(VillageState village, SimConfig cfg, double dt) {
        boolean contested = village.level() != CrisisLevel.STABLE;
        double downFactor = contested ? cfg.get(Key.CONTEST_DECAY_PENALTY) : 1.0;
        double avgMorale = village.averageMorale();
        double loyaltyTarget = VillagerState.clamp(avgMorale
                + (village.count(Role.DIPLOMAT) > 0 ? cfg.get(Key.LOYALTY_DIPLOMAT_BONUS) : 0));
        double moraleSpeed = cfg.get(Key.MORALE_SPEED_PER_S) * dt;
        double loyaltySpeed = cfg.get(Key.LOYALTY_SPEED_PER_S) * dt;
        double lowMorale = cfg.get(Key.LOYALTY_LOW_MORALE);
        double lowDecay = cfg.get(Key.LOYALTY_LOW_MORALE_DECAY_PER_S) * dt;

        List<VillagerState> list = village.villagers();
        for (int i = 0; i < list.size(); i++) {
            VillagerState v = list.get(i);
            double lowMoraleNow = v.morale();
            v.setMorale(approach(v.morale(), moraleTarget(village, v, i, cfg), moraleSpeed, downFactor));
            double loyalty = approach(v.loyalty(), loyaltyTarget, loyaltySpeed, downFactor);
            if (lowMoraleNow < lowMorale) {
                loyalty -= lowDecay * downFactor;
            }
            v.setLoyalty(loyalty);
        }
    }

    /** Rapproche {@code value} de {@code target} d'au plus {@code speed} (multiplié si on descend). */
    private static double approach(double value, double target, double speed, double downFactor) {
        if (target >= value) {
            return Math.min(target, value + speed);
        }
        return Math.max(target, value - speed * downFactor);
    }

    /** Désobéissance déterministe : un villageois au moral trop bas ne travaille pas. */
    public static boolean canWork(VillagerState villager, SimConfig cfg) {
        return villager.morale() >= cfg.get(Key.DISOBEY_MORALE);
    }
}
