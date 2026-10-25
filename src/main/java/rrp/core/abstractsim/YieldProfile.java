package rrp.core.abstractsim;

import rrp.core.Resource;
import rrp.core.territory.Doctrine;

import java.util.EnumMap;
import java.util.Map;

/**
 * Harvest multipliers of an abstract village. A village's doctrine is fixed from the dominant biome around its hall,
 * so it stands for the local biome yield (plains and coasts farm, mountains mine, forests cut wood).
 */
public final class YieldProfile {

    private YieldProfile() {
    }

    public static Map<Resource, Double> of(Doctrine doctrine) {
        Map<Resource, Double> m = new EnumMap<>(Resource.class);
        if (doctrine == null) {
            return m;
        }
        switch (doctrine) {
            case MERCHANT_CITY -> m.put(Resource.FOOD, 1.2);
            case WARLIKE_EMPIRE -> {
                m.put(Resource.ORE, 1.5);
                m.put(Resource.STONE, 1.3);
                m.put(Resource.FOOD, 0.8);
            }
            case ISOLATIONIST_THEOCRACY -> m.put(Resource.WOOD, 1.4);
        }
        return m;
    }
}
