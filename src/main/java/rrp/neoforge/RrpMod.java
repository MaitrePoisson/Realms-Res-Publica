package rrp.neoforge;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import rrp.core.SimConfig;

/** Point d'entrée : mod 100 % serveur (solo = serveur intégré, ou serveur dédié), aucun code client. */
@Mod(RrpMod.MOD_ID)
@EventBusSubscriber(modid = RrpMod.MOD_ID)
public final class RrpMod {

    public static final String MOD_ID = "respublica";
    public static final Logger LOG = LogUtils.getLogger();

    private static volatile SimConfig simConfig = SimConfig.defaults();
    private static volatile Lang lang;

    public RrpMod() {
        LOG.info("Realms & Res Publica chargé");
    }

    public static SimConfig simConfig() {
        return simConfig;
    }

    public static Lang lang() {
        return lang;
    }

    /** Relit config.yml dans le cœur ; les valeurs invalides sont signalées et le défaut est conservé. */
    public static void reloadConfig() {
        simConfig = ConfigLoader.load(FMLPaths.CONFIGDIR.get().resolve(MOD_ID), LOG);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        lang = new Lang(LOG);
        reloadConfig();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        LOG.info("Realms & Res Publica activé ({} textes, {} réglages)", lang.size(), SimConfig.Key.values().length);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOG.info("Realms & Res Publica désactivé");
    }
}
