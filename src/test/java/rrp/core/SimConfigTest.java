package rrp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rrp.core.SimConfig.Key;

class SimConfigTest {

    private static List<String> configLines() throws IOException {
        try (InputStream in = SimConfigTest.class.getResourceAsStream("/config.yml")) {
            return List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n"));
        }
    }

    /** Lit les lignes « clé: valeur » plates de config.yml, avec le commentaire qui les précède. */
    private static Map<String, Double> flatValues(List<String> lines) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (String l : lines) {
            if (l.startsWith("#") || l.isBlank()) {
                continue;
            }
            String[] kv = l.split(":", 2);
            m.put(kv[0].trim(), Double.parseDouble(kv[1].trim()));
        }
        return m;
    }

    @Test
    void defaultsMatchConfigYml() throws IOException {
        Map<String, Double> file = flatValues(configLines());
        SimConfig fromFile = SimConfig.fromMap(file);
        assertTrue(fromFile.warnings().isEmpty(), () -> "config.yml invalide : " + fromFile.warnings());
        SimConfig defaults = SimConfig.defaults();
        for (Key k : Key.values()) {
            assertEquals(defaults.get(k), fromFile.get(k), 1e-9, k.path());
        }
    }

    @Test
    void everyKeyIsPresentAndCommentedInConfigYml() throws IOException {
        List<String> lines = configLines();
        Map<String, Double> file = flatValues(lines);
        for (Key k : Key.values()) {
            assertTrue(file.containsKey(k.path()), "clé absente de config.yml : " + k.path());
            int idx = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(k.path() + ":")) {
                    idx = i;
                }
            }
            assertTrue(idx > 0 && lines.get(idx - 1).startsWith("# ") && lines.get(idx - 1).length() > 10,
                    "clé non commentée : " + k.path());
        }
        assertEquals(Key.values().length, file.size(), "config.yml contient des clés inconnues du code");
    }

    @Test
    void defaultsAreWithinBounds() {
        for (Key k : Key.values()) {
            assertTrue(k.def() >= k.min() && k.def() <= k.max(), k.path());
        }
    }

    @Test
    void validValuesAreApplied() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("village_radius", 100);
        raw.put("war_tension", 75.5);
        SimConfig c = SimConfig.fromMap(raw);
        assertEquals(100, c.getInt(Key.VILLAGE_RADIUS));
        assertEquals(75.5, c.get(Key.WAR_TENSION), 1e-9);
        assertTrue(c.warnings().isEmpty());
    }

    @Test
    void invalidValuesFallBackToDefaultWithWarning() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("village_radius", 100000);
        raw.put("exile_food_share", -0.5);
        raw.put("war_tension", "beaucoup");
        raw.put("inconnue", 3);
        raw.put("birth_food_cost", Double.NaN);
        SimConfig c = SimConfig.fromMap(raw);
        SimConfig d = SimConfig.defaults();
        assertEquals(d.get(Key.VILLAGE_RADIUS), c.get(Key.VILLAGE_RADIUS));
        assertEquals(d.get(Key.EXILE_FOOD_SHARE), c.get(Key.EXILE_FOOD_SHARE));
        assertEquals(d.get(Key.WAR_TENSION), c.get(Key.WAR_TENSION));
        assertEquals(d.get(Key.BIRTH_FOOD_COST), c.get(Key.BIRTH_FOOD_COST));
        assertEquals(5, c.warnings().size());
    }

    @Test
    void nullMapGivesDefaults() {
        assertTrue(SimConfig.fromMap(null).warnings().isEmpty());
        assertEquals(64, SimConfig.fromMap(null).getInt(Key.VILLAGE_RADIUS));
    }

    @Test
    void withReplacesOneValueAndChecksBounds() {
        SimConfig base = SimConfig.defaults();
        SimConfig c = base.with(Key.CONTEST_AFTER_S, 10);
        assertEquals(10, c.get(Key.CONTEST_AFTER_S));
        assertEquals(120, base.get(Key.CONTEST_AFTER_S));
        assertThrows(IllegalArgumentException.class, () -> base.with(Key.EXILE_FOOD_SHARE, 2));
    }

    @Test
    void stateObjectsClampGauges() {
        VillagerState v = new VillagerState(java.util.UUID.randomUUID(), Role.FARMER, "f");
        v.setMorale(150);
        v.setSatiety(-5);
        v.setLoyalty(Double.NaN);
        assertEquals(100, v.morale());
        assertEquals(0, v.satiety());
        assertEquals(0, v.loyalty());
        VillageState s = new VillageState("v1", Faction.LOYALIST);
        s.addStock(Resource.FOOD, 5);
        assertEquals(0, s.addStock(Resource.FOOD, -50));
        assertFalse(s.level() != CrisisLevel.STABLE);
        assertEquals(CrisisLevel.GUERRE_CIVILE, CrisisLevel.GUERRE_CIVILE.up());
        assertEquals(CrisisLevel.STABLE, CrisisLevel.CONTESTATION.down());
    }
}
