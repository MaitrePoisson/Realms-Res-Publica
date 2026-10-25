package rrp.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paramètres d'équilibrage du cœur, chargés depuis une Map plate (config.yml). Les durées sont en
 * secondes de simulation. Chaque clé a un défaut, des bornes, et figure commentée dans config.yml.
 */
public final class SimConfig {

    /** Clé de configuration : nom plat, défaut, bornes incluses. */
    public enum Key {
        // Monde
        VILLAGE_RADIUS(64, 16, 256),
        MAX_SCAN_BLOCKS_PER_TICK(2000, 100, 100000),
        PERSISTENCE_FLUSH_S(60, 5, 3600),
        // Jauges
        SATIETY_DECAY_PER_MIN(3.0, 0, 100),
        SATIETY_PER_FOOD(25, 1, 100),
        EAT_BELOW_SATIETY(50, 1, 100),
        MORALE_SPEED_PER_S(0.5, 0.01, 10),
        MORALE_BASE(10, 0, 100),
        MORALE_W_SATIETY(0.5, 0, 1),
        MORALE_BED_BONUS(20, 0, 100),
        MORALE_SECURITY_BONUS(20, 0, 100),
        MORALE_CROWDING_PENALTY(20, 0, 100),
        MORALE_INSECURITY_PENALTY(30, 0, 100),
        LOYALTY_SPEED_PER_S(0.2, 0.01, 10),
        LOYALTY_DIPLOMAT_BONUS(10, 0, 50),
        LOYALTY_LOW_MORALE(55, 0, 100),
        LOYALTY_LOW_MORALE_DECAY_PER_S(0.4, 0, 10),
        DISOBEY_MORALE(25, 0, 100),
        // Démographie
        BIRTH_INTERVAL_S(300, 1, 86400),
        BIRTH_FOOD_THRESHOLD(10, 0, 10000),
        BIRTH_FOOD_COST(4, 0, 1000),
        MAX_POPULATION(40, 1, 1000),
        // Crises
        DAY_LENGTH_S(1200, 10, 86400),
        FOOD_PER_VILLAGER_PER_DAY(4.0, 0.1, 100),
        FAMINE_DAYS_THRESHOLD(2.0, 0, 100),
        OVERPOPULATION_RATIO(1.0, 0.5, 10),
        INSECURITY_THREATS_PER_GUARD(2.0, 0.1, 100),
        CONTEST_AFTER_S(120, 1, 86400),
        CONTEST_DECAY_PENALTY(2.0, 1, 10),
        EXILE_LOYALTY(30, 0, 100),
        EXILE_AFTER_S(180, 1, 86400),
        EXILE_FOOD_SHARE(0.3, 0, 1),
        CAMP_GUARD_SHARE(0.4, 0, 1),
        EXILE_MAX_SHARE(0.5, 0.05, 1),
        RECONCILE_LOYALTY_BONUS(20, 0, 70),
        CAMP_DISTANCE(80, 20, 1000),
        WAR_AFTER_S(120, 1, 86400),
        WAR_TENSION(60, 0, 200),
        WAR_NO_DIPLOMAT_PENALTY(10, 0, 100),
        TRUCE_RELATIONS(70, 0, 100),
        WAR_DAMAGE(4, 0.5, 100),
        CRISIS_RECOVERY_FACTOR(0.25, 0.01, 10),
        // Rôles
        BEHAVIOR_INTERVAL_S(10, 1, 3600),
        WORK_RADIUS(16, 2, 128),
        BEHAVIORS_PER_TICK(4, 1, 1000),
        RELATIONS_PER_VISIT(5, 0, 100),
        HUT_WOOD(30, 4, 500),
        TOWN_HALL_RADIUS(12, 4, 64),
        STORE_RADIUS(5, 2, 16),
        MAX_CHESTS(6, 1, 30),
        EXPANSION_WOOD_QUOTA(60, 0, 100000),
        EXPANSION_STONE_QUOTA(40, 0, 100000),
        EXPANSION_POPULATION_QUOTA(6, 0, 1000),
        MAX_BUILD_ORDERS(3, 1, 50),
        ROAD_PAVE_STONE_STOCK(60, 0, 100000),
        ROAD_PAVE_POPULATION(8, 0, 1000),
        ROAD_LINK_DISTANCE(160, 0, 1000),
        DEFENSE_POPULATION(12, 0, 1000),
        DEFENSE_THREATS(3, 0, 1000),
        DEFENSE_RING_RADIUS(30, 4, 60),
        DEFENSE_STONE_STOCK(80, 0, 100000),
        DEFENSE_STONE_POPULATION(14, 0, 1000),
        TERRITORY_HALL_RADIUS(4, 1, 16),
        TERRITORY_OUTPOST_RADIUS(2, 1, 16),
        TERRITORY_OWNER_THRESHOLD(0.2, 0.01, 1),
        TERRITORY_CONTEST_MARGIN(0.1, 0, 1),
        TERRITORY_TRESPASS(0, 0, 1),
        PROVOCATION_PER_ACT(1, 0, 100),
        PROVOCATION_WARN(3, 0, 1000),
        PROVOCATION_EXPEL(6, 0, 1000),
        PROVOCATION_WAR(10, 0, 1000),
        PROVOCATION_DECAY_S(0.02, 0, 10),
        RELATION_BASE_TRADE(40, 0, 100),
        RELATION_BASE_HOSTILE(-40, -100, 0),
        RELATION_TRADE_AT(30, 0, 100),
        RELATION_HOSTILE_AT(-30, -100, 0),
        RELATION_INCIDENT_PENALTY(15, 0, 100),
        RELATION_DECAY_S(0.05, 0, 10),
        CARAVAN_INTERVAL_S(120, 0, 100000),
        CARAVAN_SURPLUS(100, 1, 100000),
        CARAVAN_DEFICIT(40, 0, 100000),
        CARAVAN_MAX_LOAD(32, 1, 1000),
        CARAVAN_ESCORTS(2, 0, 4),
        CARAVAN_AMBUSH_CHANCE(0.1, 0, 1),
        CARAVAN_TIMEOUT_S(180, 10, 100000),
        CARAVAN_RELATION_GAIN(8, 0, 100),
        CARAVAN_RELATION_LOSS(10, 0, 100),
        DIPLOMACY_INTERVAL_S(30, 0, 100000),
        DIPLOMACY_ALLIANCE_AT(60, 0, 100),
        DIPLOMACY_WAR_INCIDENTS(3, 1, 1000),
        DIPLOMACY_WAR_DELAY_S(30, 0, 100000),
        DIPLOMACY_CALM_GAIN(25, 0, 100),
        DIPLOMACY_CRITICAL_FOOD(10, 0, 100000),
        MOBILIZATION_INTERVAL_S(10, 0, 100000),
        MOBILIZATION_SHARE(0.5, 0, 1),
        MOBILIZATION_SWORD_ORE(2, 0, 100),
        MOBILIZATION_ARMOR_ORE(4, 0, 100),
        MOBILIZATION_BOW_WOOD(2, 0, 100),
        SIEGE_INTERVAL_S(10, 0, 100000),
        SIEGE_STANDOFF(18, 4, 200),
        SIEGE_BLOCK_CAP(12, 0, 1000),
        SIEGE_RAM_HP(40, 1, 10000),
        SIEGE_CATAPULT_HP(30, 1, 10000),
        SIEGE_RAM_WOOD(8, 0, 1000),
        SIEGE_CATAPULT_WOOD(12, 0, 1000),
        SIEGE_RAM_RANGE(8, 1, 100),
        SIEGE_CATAPULT_RANGE(24, 1, 100),
        SIEGE_DEFENDER_RANGE(12, 1, 100),
        SIEGE_DEFENDER_DAMAGE(10, 0, 1000),
        WAR_RESOLVE_INTERVAL_S(10, 0, 100000),
        WAR_RESOLVE_MIN_S(60, 0, 100000),
        WAR_RESOLVE_MAX_S(600, 0, 1000000),
        WAR_PILLAGE_AT(1.2, 1, 100),
        WAR_ANNEX_AT(2, 1, 100),
        WAR_DESTROY_AT(3, 1, 100),
        WAR_LOOT_SHARE(0.3, 0, 1),
        WAR_LOOT_CAP(64, 0, 100000),
        WAR_TRIBUTE_SHARE(0.1, 0, 1),
        WAR_TRIBUTE_CAP(32, 0, 100000),
        WAR_TRIBUTE_PERIOD_S(1200, 0, 1000000),
        WAR_PEACE_GAIN(30, 0, 200),
        BUILDER_SPARE_BEDS(2, 0, 20),
        TALK_LOYALTY_BONUS(2, 0, 50),
        ROLE_SWITCH_COOLDOWN_S(300, 0, 1000000),
        SHOW_ROLE_NAMES(1, 0, 1),
        SHOW_ACTION_BAR(1, 0, 1);

        private final double def;
        private final double min;
        private final double max;

        Key(double def, double min, double max) {
            this.def = def;
            this.min = min;
            this.max = max;
        }

        /** Nom plat tel qu'écrit dans config.yml (minuscules). */
        public String path() {
            return name().toLowerCase(Locale.ROOT);
        }

        public double def() { return def; }
        public double min() { return min; }
        public double max() { return max; }
    }

    private final EnumMap<Key, Double> values = new EnumMap<>(Key.class);
    private final List<String> warnings = new ArrayList<>();

    private SimConfig() {
        for (Key k : Key.values()) {
            values.put(k, k.def());
        }
    }

    /** Configuration aux valeurs par défaut. */
    public static SimConfig defaults() {
        return new SimConfig();
    }

    /**
     * Charge depuis une Map plate. Une valeur non numérique, NaN ou hors bornes est ignorée (défaut
     * conservé) et signalée dans {@link #warnings()} ; les clés inconnues sont signalées aussi.
     */
    public static SimConfig fromMap(Map<String, ?> raw) {
        SimConfig c = new SimConfig();
        if (raw == null) {
            return c;
        }
        for (Map.Entry<String, ?> e : raw.entrySet()) {
            Key key = find(e.getKey());
            if (key == null) {
                c.warnings.add("clé inconnue : " + e.getKey());
                continue;
            }
            if (!(e.getValue() instanceof Number n)) {
                c.warnings.add(key.path() + " : valeur non numérique (" + e.getValue() + ")");
                continue;
            }
            double v = n.doubleValue();
            if (Double.isNaN(v) || v < key.min() || v > key.max()) {
                c.warnings.add(key.path() + " : " + v + " hors bornes [" + key.min() + "; " + key.max() + "]");
                continue;
            }
            c.values.put(key, v);
        }
        return c;
    }

    private static Key find(String path) {
        for (Key k : Key.values()) {
            if (k.path().equals(path)) {
                return k;
            }
        }
        return null;
    }

    public double get(Key k) {
        return values.get(k);
    }

    public int getInt(Key k) {
        return (int) Math.round(values.get(k));
    }

    /** Avertissements du chargement (vide si tout était valide). */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    /** Copie modifiée d'une seule valeur (tests et simulateur) ; refuse une valeur hors bornes. */
    public SimConfig with(Key k, double v) {
        if (Double.isNaN(v) || v < k.min() || v > k.max()) {
            throw new IllegalArgumentException(k.path() + " hors bornes : " + v);
        }
        SimConfig c = new SimConfig();
        c.values.putAll(values);
        c.values.put(k, v);
        return c;
    }
}
