package rrp.core.tactics;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** The "bandages" of the barracks stock: the item ids of {@code guard_heal_items} that a wounded guard may use. */
public final class HealItems {

    private HealItems() {
    }

    /** Parses a comma-separated list of item ids; a bare name gets the {@code minecraft:} namespace. */
    public static Set<String> parse(String csv) {
        Set<String> out = new LinkedHashSet<>();
        if (csv == null) {
            return out;
        }
        for (String part : csv.split("[,;]")) {
            String id = part.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            out.add(id.indexOf(':') < 0 ? "minecraft:" + id : id);
        }
        return out;
    }
}
