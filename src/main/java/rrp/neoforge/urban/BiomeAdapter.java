package rrp.neoforge.urban;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import rrp.core.territory.BiomeClassifier;
import rrp.neoforge.RrpMod;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Bridges a Minecraft biome holder (vanilla, Terralith or any mod) to the core {@link BiomeClassifier}. */
public final class BiomeAdapter {

    private static volatile String cachedText = null;
    private static volatile Map<String, BiomeClassifier.Kind> cached = Map.of();

    private BiomeAdapter() {
    }

    public static BiomeClassifier.Result classify(Holder<Biome> biome) {
        try {
            String id = biome.unwrapKey().map(k -> k.location().toString()).orElse("");
            Set<String> tags = biome.tags().map(t -> t.location().getPath()).collect(Collectors.toSet());
            Biome b = biome.value();
            double downfall = b.hasPrecipitation() ? 0.5 : 0.1;
            return BiomeClassifier.classify(new BiomeClassifier.Info(id, tags, b.getBaseTemperature(), downfall), overrides());
        } catch (RuntimeException e) {
            return new BiomeClassifier.Result(BiomeClassifier.Kind.UNKNOWN, false);
        }
    }

    private static Map<String, BiomeClassifier.Kind> overrides() {
        String text = RrpMod.simConfig().biomeOverrides();
        if (!text.equals(cachedText)) {
            cached = BiomeClassifier.parseOverrides(text);
            cachedText = text;
        }
        return cached;
    }
}
