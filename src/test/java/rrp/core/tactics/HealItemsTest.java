package rrp.core.tactics;

import org.junit.jupiter.api.Test;
import rrp.core.SimConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealItemsTest {

    @Test
    void parsesIdsAndAddsTheDefaultNamespace() {
        assertEquals(List.of("minecraft:golden_apple", "mymod:bandage", "minecraft:paper"),
                List.copyOf(HealItems.parse(" Golden_Apple, mymod:bandage ;paper ,, ")));
    }

    @Test
    void nullOrBlankGivesNothing() {
        assertTrue(HealItems.parse(null).isEmpty());
        assertTrue(HealItems.parse("  ").isEmpty());
    }

    @Test
    void configDefaultsCoverTheNewKeys() {
        SimConfig c = SimConfig.defaults();
        assertEquals(6, c.get(SimConfig.Key.GUARD_CREEPER_CLOSE), 1e-9);
        assertEquals(60, c.get(SimConfig.Key.GUARD_CREEPER_REDUCTION), 1e-9);
        assertEquals(6, c.get(SimConfig.Key.GUARD_HEAL_AMOUNT), 1e-9);
        assertTrue(HealItems.parse(c.guardHealItems()).contains("minecraft:paper"));
    }

    @Test
    void configReadsTheHealItemsAndKeepsThemOnCopy() {
        SimConfig c = SimConfig.fromMap(java.util.Map.of("guard_heal_items", "minecraft:bread"));
        assertEquals(List.of("minecraft:bread"), List.copyOf(HealItems.parse(c.guardHealItems())));
        assertEquals("minecraft:bread", c.with(SimConfig.Key.GUARD_HEAL_AMOUNT, 3).guardHealItems());
    }
}
