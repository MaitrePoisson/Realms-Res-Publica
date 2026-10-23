package rrp.neoforge.urban;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BiomeStyleTest {

    @Test
    void biomesMapToFamilies() {
        assertEquals(BiomeStyle.PLAINS, BiomeStyle.fromBiomePath("plains"));
        assertEquals(BiomeStyle.PLAINS, BiomeStyle.fromBiomePath("dark_forest"));
        assertEquals(BiomeStyle.DESERT, BiomeStyle.fromBiomePath("desert"));
        assertEquals(BiomeStyle.DESERT, BiomeStyle.fromBiomePath("eroded_badlands"));
        assertEquals(BiomeStyle.SNOW, BiomeStyle.fromBiomePath("snowy_taiga"));
        assertEquals(BiomeStyle.SNOW, BiomeStyle.fromBiomePath("frozen_river"));
        assertEquals(BiomeStyle.SAVANNA, BiomeStyle.fromBiomePath("savanna_plateau"));
        assertEquals(BiomeStyle.JUNGLE, BiomeStyle.fromBiomePath("bamboo_jungle"));
        assertEquals(BiomeStyle.PLAINS, BiomeStyle.fromBiomePath(null));
    }

    @Test
    void everyBuildTypeHasABlueprintKind() {
        for (BuildType t : BuildType.values()) {
            BlueprintPool.Kind.of(t);
        }
        assertEquals("watchtower", BlueprintPool.Kind.of(BuildType.TOWER).file());
    }
}
