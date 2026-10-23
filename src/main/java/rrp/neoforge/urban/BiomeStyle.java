package rrp.neoforge.urban;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.Locale;

/** Familles de style de construction, déduites du biome du site. */
public enum BiomeStyle {
    PLAINS, DESERT, SNOW, SAVANNA, JUNGLE;

    /** Suffixe utilisé dans les noms de fichiers des plans. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Famille d'après le chemin du biome (ex. {@code snowy_taiga}, {@code badlands}). */
    public static BiomeStyle fromBiomePath(String path) {
        String p = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (p.contains("jungle") || p.contains("bamboo") || p.contains("swamp") || p.contains("mangrove")) return JUNGLE;
        if (p.contains("desert") || p.contains("badlands") || p.contains("beach")) return DESERT;
        if (p.contains("savanna")) return SAVANNA;
        if (p.contains("snow") || p.contains("frozen") || p.contains("ice") || p.contains("taiga")
                || p.contains("grove") || p.contains("peaks")) return SNOW;
        return PLAINS;
    }

    public static BiomeStyle of(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> fromBiomePath(k.location().getPath())).orElse(PLAINS);
    }
}
