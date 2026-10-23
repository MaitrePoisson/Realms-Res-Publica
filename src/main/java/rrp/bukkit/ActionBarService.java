package rrp.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.VillageState;

import java.util.function.Supplier;

/**
 * Barre d'action des joueurs qui se trouvent dans un village : nom, population, nourriture et niveau de
 * crise, rafraîchis toutes les 2 secondes de jeu (désactivable par `show_action_bar`).
 */
public final class ActionBarService {

    static final long PERIOD_TICKS = 40;

    private final Supplier<SimConfig> config;
    private final VillageRegistry registry;
    private final Lang lang;
    private long since;

    public ActionBarService(Supplier<SimConfig> config, VillageRegistry registry, Lang lang) {
        this.config = config;
        this.registry = registry;
        this.lang = lang;
    }

    public void onTicks(long ticks) {
        since += ticks;
        if (since < PERIOD_TICKS) {
            return;
        }
        since = 0;
        if (config.get().getInt(SimConfig.Key.SHOW_ACTION_BAR) != 1) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            VillageSite site = registry.find(l.getWorld().getName(), l.getX(), l.getZ());
            if (site != null) {
                p.sendActionBar(net.kyori.adventure.text.Component.text(line(site)));
            }
        }
    }

    /** Texte de la barre pour un village. */
    String line(VillageSite site) {
        VillageState st = site.state();
        return lang.get("actionbar.line", site.id(), st.population(), st.stock(Resource.FOOD), st.level().label());
    }
}
