package rrp.neoforge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.VillageState;

import java.util.function.Supplier;

/**
 * Barre d'action des joueurs qui se trouvent dans un village : nom, population, nourriture et niveau de
 * crise, rafraîchis toutes les 2 secondes de jeu (désactivable par {@code show_action_bar}).
 */
public final class ActionBarService {

    static final long PERIOD_TICKS = 40;

    private final Supplier<SimConfig> config;
    private final Supplier<MinecraftServer> server;
    private final VillageRegistry registry;
    private final Supplier<Lang> lang;
    private long since;

    public ActionBarService(Supplier<SimConfig> config, Supplier<MinecraftServer> server, VillageRegistry registry,
                            Supplier<Lang> lang) {
        this.config = config;
        this.server = server;
        this.registry = registry;
        this.lang = lang;
    }

    public void onTicks(long ticks) {
        since += ticks;
        if (since < PERIOD_TICKS) {
            return;
        }
        since = 0;
        MinecraftServer srv = server.get();
        if (srv == null || config.get().getInt(SimConfig.Key.SHOW_ACTION_BAR) != 1) {
            return;
        }
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            VillageSite site = registry.find(Worlds.name((ServerLevel) p.level()), p.getX(), p.getZ());
            if (site != null) {
                p.displayClientMessage(Component.literal(line(site)), true);
            }
        }
    }

    /** Texte de la barre pour un village. */
    String line(VillageSite site) {
        VillageState st = site.state();
        return lang.get().get("actionbar.line", site.id(), st.population(), st.stock(Resource.FOOD), st.level().label());
    }
}
