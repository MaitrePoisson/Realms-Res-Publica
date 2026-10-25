package rrp.neoforge.urban;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import rrp.core.territory.BiomeClassifier;

import java.util.Locale;

/** Familles de style de construction, déduites du biome du site. */
public enum BiomeStyle {
    PLAINS, DESERT, SNOW, SAVANNA, JUNGLE;

    /** Suffixe utilisé dans les noms de fichiers des plans. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Family from a biome path or id (any namespace), e.g. {@code snowy_taiga}, {@code terralith:alpine_highlands}. */
    public static BiomeStyle fromBiomePath(String path) {
        return of(BiomeClassifier.classify(path));
    }

    public static BiomeStyle of(BiomeClassifier.Result r) {
        if (r.cold()) return SNOW;
        return switch (r.kind()) {
            case JUNGLE, SWAMP -> JUNGLE;
            case DESERT, BADLANDS, BEACH -> DESERT;
            case SAVANNA -> SAVANNA;
            case TAIGA -> SNOW;
            default -> PLAINS;
        };
    }

    public static BiomeStyle of(Holder<Biome> biome) {
        return of(BiomeAdapter.classify(biome));
    }
}
