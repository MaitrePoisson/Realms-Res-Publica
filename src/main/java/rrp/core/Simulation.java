package rrp.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestre un pas de simulation du cœur pour un ensemble de villages (villages et campements) :
 * jauges, démographie puis crises. Les événements renvoyés sont appliqués par l'appelant (adaptateur
 * Bukkit ou simulateur CLI), qui ajoute aussi les villageois nés ({@link SimEvent.Birth}).
 */
public final class Simulation {
    private final SimConfig cfg;
    private final List<VillageState> world = new ArrayList<>();

    public Simulation(SimConfig cfg) {
        this.cfg = cfg;
    }

    public SimConfig config() { return cfg; }

    /** Villages et campements, modifiable : un exil y ajoute le campement. */
    public List<VillageState> villages() { return world; }

    public VillageState village(String id) {
        for (VillageState v : world) {
            if (v.id().equals(id)) {
                return v;
            }
        }
        return null;
    }

    public VillageState add(VillageState village) {
        world.add(village);
        return village;
    }

    /** Avance tout de {@code dt} secondes de simulation et renvoie les événements, dans l'ordre. */
    public List<SimEvent> step(double dt) {
        List<SimEvent> events = new ArrayList<>();
        if (dt <= 0) {
            return events;
        }
        for (VillageState v : List.copyOf(world)) {
            Gauges.step(v, cfg, dt);
            events.addAll(DemographyEngine.step(v, cfg, dt));
        }
        events.addAll(CrisisEngine.step(world, cfg, dt));
        return events;
    }
}
