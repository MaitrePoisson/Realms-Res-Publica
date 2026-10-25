package rrp.core.territory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import rrp.core.territory.BiomeClassifier.Info;
import rrp.core.territory.BiomeClassifier.Kind;

class BiomeClassifierTest {

    private static Kind kind(String id) {
        return BiomeClassifier.classify(id).kind();
    }

    @Test
    void vanillaAndTerralithIdsClassify() {
        Map<String, Kind> table = Map.ofEntries(
                Map.entry("minecraft:plains", Kind.PLAINS),
                Map.entry("minecraft:dark_forest", Kind.FOREST),
                Map.entry("minecraft:wooded_badlands", Kind.BADLANDS),
                Map.entry("minecraft:snowy_taiga", Kind.TAIGA),
                Map.entry("minecraft:mangrove_swamp", Kind.SWAMP),
                Map.entry("minecraft:deep_ocean", Kind.OCEAN),
                Map.entry("minecraft:stony_shore", Kind.MOUNTAIN),
                Map.entry("terralith:alpine_highlands", Kind.MOUNTAIN),
                Map.entry("terralith:yellowstone", Kind.UNKNOWN),
                Map.entry("terralith:lavender_forest", Kind.FOREST),
                Map.entry("terralith:sandstone_valley", Kind.DESERT),
                Map.entry("terralith:rocky_jungle", Kind.JUNGLE),
                Map.entry("terralith:volcanic_crater", Kind.MOUNTAIN),
                Map.entry("terralith:shield", Kind.UNKNOWN),
                Map.entry("terralith:blooming_valley", Kind.PLAINS));
        table.forEach((id, k) -> assertEquals(k, kind(id), id));
        assertTrue(BiomeClassifier.classify("terralith:glacial_chasm").cold());
        assertTrue(BiomeClassifier.classify("minecraft:snowy_taiga").cold());
        assertTrue(!BiomeClassifier.classify("minecraft:plains").cold());
    }

    @Test
    void unknownNamespaceUsesTagsThenClimate() {
        assertEquals(Kind.FOREST, BiomeClassifier.classify(
                new Info("weirdmod:zzz", Set.of("is_forest"), Double.NaN, Double.NaN), null).kind());
        assertEquals(Kind.MOUNTAIN, BiomeClassifier.classify(
                new Info("weirdmod:zzz", Set.of("is_mountain"), 0.5, 0.5), null).kind());
        assertEquals(Kind.DESERT, BiomeClassifier.classify(new Info("weirdmod:zzz", Set.of(), 2.0, 0.0), null).kind());
        assertEquals(Kind.SAVANNA, BiomeClassifier.classify(new Info("weirdmod:zzz", Set.of(), 1.2, 0.0), null).kind());
        assertEquals(Kind.JUNGLE, BiomeClassifier.classify(new Info("weirdmod:zzz", Set.of(), 0.95, 0.9), null).kind());
        assertTrue(BiomeClassifier.classify(new Info("weirdmod:zzz", Set.of(), 0.0, 0.5), null).cold());
        assertEquals(Kind.PLAINS, BiomeClassifier.classify(new Info("weirdmod:zzz", Set.of(), 0.6, 0.5), null).kind());
        assertEquals(Kind.UNKNOWN, kind("weirdmod:zzz"));
    }

    @Test
    void neverThrows() {
        assertDoesNotThrow(() -> BiomeClassifier.classify(null, null));
        assertDoesNotThrow(() -> BiomeClassifier.classify(new Info(null, null, Double.NaN, Double.NaN), Map.of()));
        assertDoesNotThrow(() -> BiomeClassifier.classify(":::"));
        assertNull(DoctrineSelector.ofBiome("weirdmod:zzz"));
    }

    @Test
    void overridesWin() {
        Map<String, Kind> o = BiomeClassifier.parseOverrides("terralith:alpine_highlands=desert, bad, x=nonsense; plains=OCEAN");
        assertEquals(2, o.size());
        Info alpine = Info.ofPath("terralith:alpine_highlands");
        assertEquals(Kind.DESERT, BiomeClassifier.classify(alpine, o).kind());
        assertEquals(Kind.OCEAN, BiomeClassifier.classify(Info.ofPath("minecraft:plains"), o).kind());
        assertEquals(Doctrine.MERCHANT_CITY, DoctrineSelector.ofKind(Kind.DESERT));
    }

    @Test
    void doctrineFollowsKinds() {
        assertEquals(Doctrine.WARLIKE_EMPIRE, DoctrineSelector.ofBiome("terralith:scarlet_mountains"));
        assertEquals(Doctrine.ISOLATIONIST_THEOCRACY, DoctrineSelector.ofBiome("terralith:lavender_forest"));
    }
}
