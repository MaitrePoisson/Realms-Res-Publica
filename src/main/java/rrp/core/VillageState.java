package rrp.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * État d'un village (ou d'un campement dissident) vu par le cœur. Rempli par l'adaptateur Bukkit
 * (stock réel, lits réels, menaces réelles) ou par l'économie virtuelle du simulateur.
 */
public final class VillageState {
    private final String id;
    private final Faction faction;
    private int beds;
    private int threats;
    private double relations;
    private CrisisLevel level = CrisisLevel.STABLE;
    private final Map<Resource, Integer> stock = new EnumMap<>(Resource.class);
    private final List<VillagerState> villagers = new ArrayList<>();

    // Minuteurs de simulation (secondes), persistés avec le village.
    private double contestTimer;
    private double exileTimer;
    private double warTimer;
    private double birthTimer;

    public VillageState(String id, Faction faction) {
        this.id = id;
        this.faction = faction;
        for (Resource r : Resource.values()) {
            stock.put(r, 0);
        }
    }

    public String id() { return id; }
    public Faction faction() { return faction; }
    public int beds() { return beds; }
    public void setBeds(int beds) { this.beds = Math.max(0, beds); }
    public int threats() { return threats; }
    public void setThreats(int threats) { this.threats = Math.max(0, threats); }
    public double relations() { return relations; }
    public void setRelations(double v) { this.relations = VillagerState.clamp(v); }
    public CrisisLevel level() { return level; }
    public void setLevel(CrisisLevel level) { this.level = level; }
    public List<VillagerState> villagers() { return villagers; }
    public int population() { return villagers.size(); }

    public int stock(Resource r) { return stock.get(r); }
    public void setStock(Resource r, int amount) { stock.put(r, Math.max(0, amount)); }

    /** Ajoute (ou retire si négatif) une quantité ; le stock ne descend jamais sous 0. Renvoie le stock final. */
    public int addStock(Resource r, int delta) {
        int v = Math.max(0, stock.get(r) + delta);
        stock.put(r, v);
        return v;
    }

    public int count(Role role) {
        int n = 0;
        for (VillagerState v : villagers) {
            if (v.role() == role) {
                n++;
            }
        }
        return n;
    }

    public double averageLoyalty() {
        return average(false);
    }

    public double averageMorale() {
        return average(true);
    }

    private double average(boolean morale) {
        if (villagers.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (VillagerState v : villagers) {
            sum += morale ? v.morale() : v.loyalty();
        }
        return sum / villagers.size();
    }

    public double contestTimer() { return contestTimer; }
    public void setContestTimer(double v) { this.contestTimer = Math.max(0, v); }
    public double exileTimer() { return exileTimer; }
    public void setExileTimer(double v) { this.exileTimer = Math.max(0, v); }
    public double warTimer() { return warTimer; }
    public void setWarTimer(double v) { this.warTimer = Math.max(0, v); }
    public double birthTimer() { return birthTimer; }
    public void setBirthTimer(double v) { this.birthTimer = Math.max(0, v); }
}
