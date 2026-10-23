package rrp.sim;

import java.util.UUID;
import java.util.function.Consumer;
import rrp.core.Faction;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.VillageState;
import rrp.core.VillagerState;

/**
 * Scénario d'équilibrage : un village de départ et une économie virtuelle (production par rôle, apparition
 * de menaces). L'économie virtuelle sert à régler les paramètres ; elle ne prouve rien sur le serveur.
 */
public record Scenario(String name, String title, double durationS, Setup setup, Economy economy) {
    public static final String VILLAGE_ID = "village";

    /** Paramètres de l'économie virtuelle (par minute de simulation). */
    public record Economy(double foodPerFarmerPerMin, double woodPerForesterPerMin, int woodPerBed,
                          double bedBuildSeconds, double threatsPerMin, double killsPerGuardPerMin) { }

    /** Remplit l'état de départ. */
    @FunctionalInterface
    public interface Setup extends Consumer<VillageState> { }

    private static VillagerState villager(Role role) {
        return new VillagerState(UUID.randomUUID(), role, VILLAGE_ID);
    }

    private static void add(VillageState v, Role role, int n) {
        for (int i = 0; i < n; i++) {
            v.villagers().add(villager(role));
        }
    }

    public VillageState newVillage() {
        VillageState v = new VillageState(VILLAGE_ID, Faction.LOYALIST);
        setup.accept(v);
        return v;
    }

    /** Village équilibré qui prospère : fermiers, forestiers, bâtisseurs, gardes et un diplomate. */
    public static Scenario prosperous() {
        return new Scenario("prosperous", "Prospère", 7200, v -> {
            add(v, Role.FARMER, 4);
            add(v, Role.FORESTER, 1);
            add(v, Role.BUILDER, 1);
            add(v, Role.GUARD, 1);
            add(v, Role.STEWARD, 1);
            add(v, Role.DIPLOMAT, 1);
            v.setBeds(10);
            v.setStock(Resource.FOOD, 200);
            v.setStock(Resource.WOOD, 40);
        }, new Economy(0.8, 6, 4, 90, 0.5, 2));
    }

    /** Aucun fermier et peu de nourriture : famine, contestation puis exil. */
    public static Scenario famine() {
        return new Scenario("famine", "Famine", 7200, v -> {
            add(v, Role.MINER, 4);
            add(v, Role.FORESTER, 2);
            add(v, Role.BUILDER, 1);
            add(v, Role.GUARD, 2);
            add(v, Role.STEWARD, 1);
            v.setBeds(12);
            v.setStock(Resource.FOOD, 20);
        }, new Economy(0, 6, 4, 90, 0, 2));
    }

    /** Beaucoup plus de villageois que de lits : surpopulation. */
    public static Scenario overpopulation() {
        return new Scenario("overpopulation", "Surpopulation", 7200, v -> {
            add(v, Role.FARMER, 8);
            add(v, Role.GUARD, 3);
            add(v, Role.STEWARD, 2);
            add(v, Role.DIPLOMAT, 1);
            add(v, Role.MINER, 6);
            v.setBeds(5);
            v.setStock(Resource.FOOD, 600);
        }, new Economy(0.8, 0, 4, 90, 0, 2));
    }

    /** Des monstres apparaissent en continu et aucun garde ne les affronte : insécurité. */
    public static Scenario insecurity() {
        return new Scenario("insecurity", "Insécurité", 7200, v -> {
            add(v, Role.FARMER, 5);
            add(v, Role.FORESTER, 1);
            add(v, Role.BUILDER, 1);
            add(v, Role.STEWARD, 1);
            v.setBeds(10);
            v.setStock(Resource.FOOD, 300);
        }, new Economy(0.8, 6, 4, 90, 1.5, 2));
    }

    public static java.util.List<Scenario> all() {
        return java.util.List.of(prosperous(), famine(), overpopulation(), insecurity());
    }

    /** Configuration utilisée par défaut par les scénarios. */
    public static SimConfig config() {
        return SimConfig.defaults();
    }
}
