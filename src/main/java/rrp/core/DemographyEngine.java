package rrp.core;

import java.util.ArrayList;
import java.util.List;
import rrp.core.SimConfig.Key;

/**
 * Démographie déterministe d'un village : une naissance n'a lieu que si la nourriture stockée atteint
 * le seuil, qu'un lit est libre et que la population est sous le maximum. Le moteur consomme la
 * nourriture et émet {@link SimEvent.Birth} ; c'est l'appelant (simulateur ou adaptateur Bukkit) qui
 * ajoute réellement le nouveau villageois.
 */
public final class DemographyEngine {
    private DemographyEngine() { }

    /** Avance le minuteur de naissance de {@code dt} secondes ; renvoie 0 ou 1 événement de naissance. */
    public static List<SimEvent> step(VillageState village, SimConfig cfg, double dt) {
        List<SimEvent> events = new ArrayList<>();
        if (dt <= 0) {
            return events;
        }
        double interval = cfg.get(Key.BIRTH_INTERVAL_S);
        // Le minuteur reste plafonné à l'intervalle : une naissance bloquée part dès que les conditions sont réunies.
        village.setBirthTimer(Math.min(interval, village.birthTimer() + dt));
        if (village.birthTimer() < interval || !canBirth(village, cfg)) {
            return events;
        }
        int cost = (int) cfg.get(Key.BIRTH_FOOD_COST);
        village.addStock(Resource.FOOD, -cost);
        village.setBirthTimer(0);
        events.add(new SimEvent.Birth(village.id(), cost));
        return events;
    }

    /** Conditions de naissance, hors minuteur : nourriture, lit libre, place sous le maximum. */
    public static boolean canBirth(VillageState village, SimConfig cfg) {
        int cost = (int) cfg.get(Key.BIRTH_FOOD_COST);
        int needed = Math.max((int) cfg.get(Key.BIRTH_FOOD_THRESHOLD), cost);
        return village.stock(Resource.FOOD) >= needed
                && village.level() == CrisisLevel.STABLE
                && !CrisisEngine.indicators(village, cfg).famine()
                && freeBeds(village) > 0
                && village.population() < (int) cfg.get(Key.MAX_POPULATION);
    }

    public static int freeBeds(VillageState village) {
        return village.beds() - village.population();
    }
}
