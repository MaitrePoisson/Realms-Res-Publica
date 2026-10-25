package rrp.core.territory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Classifies any biome id (vanilla, {@code terralith:*}, a modded or unknown namespace) into a {@link Kind}. Order of
 * evidence: config override, biome tags ({@code is_forest}, {@code is_mountain}...), name keywords, temperature and
 * downfall. It never throws: an id with no evidence at all is {@link Kind#UNKNOWN}.
 */
public final class BiomeClassifier {

    public enum Kind { PLAINS, FOREST, TAIGA, JUNGLE, SWAMP, MOUNTAIN, BADLANDS, DESERT, SAVANNA, OCEAN, RIVER, BEACH, UNKNOWN }

    /** @param cold snowy or frozen (building style), independent of the kind (a snowy taiga is TAIGA + cold) */
    public record Result(Kind kind, boolean cold) {
    }

    /** Facts known about a biome; tags are paths without namespace ({@code is_forest}); NaN climate = unknown. */
    public record Info(String id, Set<String> tags, double temperature, double downfall) {
        public static Info ofPath(String id) {
            return new Info(id, Set.of(), Double.NaN, Double.NaN);
        }
    }

    private BiomeClassifier() {
    }

    public static Result classify(Info info, Map<String, Kind> overrides) {
        String id = info == null || info.id() == null ? "" : info.id().toLowerCase(Locale.ROOT);
        String path = id.substring(id.indexOf(':') + 1);
        Set<String> tags = info == null || info.tags() == null ? Set.of() : info.tags();
        double t = info == null ? Double.NaN : info.temperature();
        double d = info == null ? Double.NaN : info.downfall();

        boolean cold = hasAny(path, "snow", "frozen", "ice", "glacier", "glacial", "grove", "peaks", "tundra")
                || tags.contains("is_snowy") || (!Double.isNaN(t) && t <= 0.15);

        if (overrides != null) {
            Kind o = overrides.get(id);
            if (o == null) {
                o = overrides.get(path);
            }
            if (o != null) {
                return new Result(o, cold);
            }
        }
        Kind k = byName(path);
        if (k == Kind.UNKNOWN) {
            k = byTags(tags);
        }
        if (k == Kind.UNKNOWN) {
            k = byClimate(t, d);
        }
        return new Result(k, cold);
    }

    public static Result classify(String id) {
        return classify(Info.ofPath(id), null);
    }

    private static Kind byName(String p) {
        if (hasAny(p, "swamp", "mangrove", "marsh", "bog", "fen")) return Kind.SWAMP;
        if (hasAny(p, "jungle", "bamboo", "rainforest", "tropical")) return Kind.JUNGLE;
        if (hasAny(p, "badlands", "mesa", "canyon")) return Kind.BADLANDS;
        if (hasAny(p, "desert", "dune", "sand")) return Kind.DESERT;
        if (p.contains("savanna")) return Kind.SAVANNA;
        if (p.contains("taiga")) return Kind.TAIGA;
        if (hasAny(p, "peaks", "mountain", "windswept", "stony", "hills", "grove", "highlands", "alpine", "cliff",
                "crag", "volcan", "caldera", "tepui")) return Kind.MOUNTAIN;
        if (hasAny(p, "forest", "wood", "old_growth", "pale_garden", "birch", "pine", "fir", "cedar")) return Kind.FOREST;
        if (hasAny(p, "ocean", "sea", "lagoon", "reef")) return Kind.OCEAN;
        if (p.contains("river")) return Kind.RIVER;
        if (hasAny(p, "beach", "shore")) return Kind.BEACH;
        if (hasAny(p, "plains", "meadow", "cherry", "steppe", "prairie", "shrubland", "valley", "field", "flower", "lavender")) return Kind.PLAINS;
        return Kind.UNKNOWN;
    }

    private static Kind byTags(Set<String> tags) {
        if (tags.contains("is_jungle")) return Kind.JUNGLE;
        if (tags.contains("is_badlands")) return Kind.BADLANDS;
        if (tags.contains("is_savanna")) return Kind.SAVANNA;
        if (tags.contains("is_taiga")) return Kind.TAIGA;
        if (tags.contains("is_mountain") || tags.contains("is_hill")) return Kind.MOUNTAIN;
        if (tags.contains("is_forest")) return Kind.FOREST;
        if (tags.contains("is_ocean") || tags.contains("is_deep_ocean")) return Kind.OCEAN;
        if (tags.contains("is_river")) return Kind.RIVER;
        if (tags.contains("is_beach")) return Kind.BEACH;
        return Kind.UNKNOWN;
    }

    private static Kind byClimate(double t, double d) {
        if (Double.isNaN(t) || Double.isNaN(d)) {
            return Kind.UNKNOWN;
        }
        if (t >= 1.5 && d < 0.2) return Kind.DESERT;
        if (t >= 1.0 && d < 0.4) return Kind.SAVANNA;
        if (t >= 0.8 && d >= 0.85) return Kind.JUNGLE;
        if (d >= 0.8) return Kind.SWAMP;
        return Kind.PLAINS;
    }

    private static boolean hasAny(String p, String... words) {
        for (String w : words) {
            if (w.length() <= 3 ? java.util.Arrays.asList(p.split("[_/]")).contains(w) : p.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses {@code "namespace:id=kind, other=kind"}; bad entries are skipped. Keys are lower-case ids, the namespace
     * being optional.
     */
    public static Map<String, Kind> parseOverrides(String text) {
        Map<String, Kind> m = new HashMap<>();
        if (text == null) {
            return m;
        }
        for (String part : text.split("[,;]")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                m.put(part.substring(0, eq).strip().toLowerCase(Locale.ROOT),
                        Kind.valueOf(part.substring(eq + 1).strip().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // unknown kind name: entry skipped
            }
        }
        return m;
    }
}
