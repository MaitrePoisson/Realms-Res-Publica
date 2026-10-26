package rrp.neoforge;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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

    private static MinecraftServer server;
    private static final TickService TICKS = new TickService();
    private static final VillageRegistry REGISTRY = new VillageRegistry();
    private static final WarehouseService WAREHOUSE = new WarehouseService(REGISTRY);
    private static VillagerAdapter adapter;
    private static VillageScanner scanner;
    private static GaugeTicker gauges;
    private static ActivityTracker activity;
    private static AbstractService abstractEconomy;
    private static BirthService births;
    private static ActionBarService actionBar;
    private static rrp.neoforge.behaviors.BehaviorScheduler behaviors;
    private static rrp.neoforge.logistics.LogisticsDriver logistics;
    private static rrp.neoforge.building.BuildDriver build;
    private static rrp.neoforge.tactics.GuardAi guardAi;
    private static CrisisService crises;
    private static WarService war;
    private static Persistence persistence;

    public RrpMod(IEventBus modBus) {
        RrpAttachments.REGISTER.register(modBus);
        VillageCommands.install();
        CrisisCommands.install();
        PlayerCommands.install();
        LawService.install();
        PlanService.install();
        ContractService.install();
        MarketService.install();
        DiplomaticRegister.install();
        ProbeDriver.install();
        LodCommands.install();
        LOG.info("Realms & Res Publica chargé");
    }

    public static MinecraftServer server() { return server; }
    public static TickService ticks() { return TICKS; }
    public static VillageRegistry registry() { return REGISTRY; }
    public static WarehouseService warehouse() { return WAREHOUSE; }
    public static VillagerAdapter adapter() { return adapter; }
    public static VillageScanner scanner() { return scanner; }
    public static GaugeTicker gauges() { return gauges; }
    public static ActivityTracker activity() { return activity; }
    public static AbstractService abstractEconomy() { return abstractEconomy; }
    public static BirthService births() { return births; }
    public static ActionBarService actionBar() { return actionBar; }
    public static rrp.neoforge.behaviors.BehaviorScheduler behaviors() { return behaviors; }
    public static rrp.neoforge.logistics.LogisticsDriver logistics() { return logistics; }
    public static rrp.neoforge.building.BuildDriver build() { return build; }
    public static rrp.neoforge.tactics.GuardAi guardAi() { return guardAi; }
    public static CrisisService crises() { return crises; }
    public static WarService war() { return war; }
    public static Persistence persistence() { return persistence; }

    /** Remplace la configuration en mémoire (tests). */
    public static void setSimConfig(SimConfig c) {
        simConfig = c;
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
        server = event.getServer();
        adapter = new VillagerAdapter(RrpMod::simConfig, RrpMod::server, REGISTRY);
        scanner = new VillageScanner(RrpMod::simConfig, RrpMod::server, REGISTRY, WAREHOUSE, adapter);
        activity = new ActivityTracker(RrpMod::simConfig, RrpMod::server, REGISTRY);
        abstractEconomy = new AbstractService(RrpMod::simConfig, RrpMod::server, REGISTRY, WAREHOUSE);
        gauges = new GaugeTicker(RrpMod::simConfig, RrpMod::server, REGISTRY, WAREHOUSE, adapter);
        births = new BirthService(RrpMod::simConfig, RrpMod::server, REGISTRY, WAREHOUSE);
        actionBar = new ActionBarService(RrpMod::simConfig, RrpMod::server, REGISTRY, RrpMod::lang);
        behaviors = new rrp.neoforge.behaviors.BehaviorScheduler(RrpMod::simConfig, RrpMod::server, REGISTRY, WAREHOUSE, adapter);
        logistics = new rrp.neoforge.logistics.LogisticsDriver(RrpMod::simConfig,
                new rrp.neoforge.logistics.ChestAccess(RrpMod::simConfig, WAREHOUSE, REGISTRY));
        build = new rrp.neoforge.building.BuildDriver(RrpMod::simConfig);
        guardAi = new rrp.neoforge.tactics.GuardAi(RrpMod::simConfig, RrpMod::server, REGISTRY, adapter);
        crises = new CrisisService(RrpMod::simConfig, RrpMod::server, REGISTRY, WAREHOUSE, adapter, RrpMod::lang, LOG);
        war = new WarService(RrpMod::simConfig, RrpMod::server, REGISTRY, adapter, crises, RrpMod::lang, LOG);
        persistence = new Persistence(REGISTRY, server.getWorldPath(LevelResource.ROOT).resolve(MOD_ID), LOG);
        int loaded = persistence.load();
        TICKS.register(worldTimer);
        LOG.info("{} village(s) rechargé(s)", loaded);
    }

    /** Minuteur du mod : tout ce qui avance avec le temps passe par ici (tick réel ou accéléré). */
    private static final java.util.function.LongConsumer worldTimer = t -> {
        scanner.onTicks(t);
        activity.onTicks(t);
        abstractEconomy.onTicks(t);
        gauges.onTicks(t);
        actionBar.onTicks(t);
        births.onTicks(t);
        logistics.onTicks(t);
        build.onTicks(t);
        guardAi.onTicks(t);
        behaviors.onTicks(t);
        crises.onTicks(t);
        war.onTicks(t);
        CaravanService.onTicks(t, simConfig, REGISTRY);
        RelationService.onTicks(t, simConfig, REGISTRY);
        DiplomacyService.onTicks(t, simConfig, REGISTRY);
        MobilizationService.onTicks(t, simConfig, REGISTRY);
        SiegeService.onTicks(t, simConfig, REGISTRY);
        WarResolution.onTicks(t, simConfig, REGISTRY);
        ExploitationWatcher.onTicks(t, simConfig);
        LawService.onTicks(t, simConfig, REGISTRY);
        ContractService.onTicks(t, simConfig, REGISTRY);
        MarketService.onTicks(t, simConfig);
        persistence.onTicks(t, simConfig.get(SimConfig.Key.PERSISTENCE_FLUSH_S), adapter::saveAll);
    };

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (server != null && persistence != null) {
            TICKS.advance(1);
        }
    }

    /** Les naissances vanilla sont remplacées par celles du mod (conditions réelles). */
    @SubscribeEvent
    public static void onBaby(BabyEntitySpawnEvent event) {
        if (event.getChild() instanceof Villager) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (adapter != null) {
            adapter.onLeave(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        LOG.info("Realms & Res Publica activé ({} textes, {} réglages)", lang.size(), SimConfig.Key.values().length);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TICKS.unregister(worldTimer);
        if (logistics != null) {
            logistics.closeAll();
        }
        if (build != null) {
            build.closeAll();
        }
        if (guardAi != null) {
            guardAi.reset();
        }
        CaravanService.abortAll(REGISTRY);
        if (persistence != null) {
            adapter.saveAll();
            persistence.flush();
        }
        scanner.clear();
        server = null;
        LOG.info("Realms & Res Publica désactivé");
    }
}
