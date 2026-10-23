package rrp.bukkit;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import rrp.core.SimConfig;

import java.util.function.LongConsumer;

public final class RrpPlugin extends JavaPlugin {

    private final TickService ticks = new TickService();
    private final VillageRegistry registry = new VillageRegistry();
    private final WarehouseService warehouse = new WarehouseService(registry);
    private final VisualExport visuals = new VisualExport();
    private SimConfig simConfig = SimConfig.defaults();
    private VillageScanner scanner;
    private ActionBarService actionBar;
    private VillagerAdapter adapter;
    private GaugeTicker gauges;
    private BehaviorScheduler behaviors;
    private BirthService births;
    private CrisisService crises;
    private WarService war;
    private Persistence persistence;
    private Lang lang;
    private Selftest selftest;
    private LongConsumer worldTimer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSimConfig();
        lang = new Lang(this);
        persistence = new Persistence(registry, getDataFolder(), getLogger());
        scanner = new VillageScanner(() -> simConfig, registry, warehouse);
        adapter = new VillagerAdapter(() -> simConfig, registry);
        gauges = new GaugeTicker(() -> simConfig, registry, warehouse, adapter);
        behaviors = new BehaviorScheduler(() -> simConfig, registry, warehouse, adapter);
        births = new BirthService(() -> simConfig, registry, warehouse, adapter);
        crises = new CrisisService(() -> simConfig, registry, warehouse, adapter, lang, getLogger());
        actionBar = new ActionBarService(() -> simConfig, registry, lang);
        war = new WarService(() -> simConfig, registry, adapter, crises, lang, getLogger());
        getServer().getPluginManager().registerEvents(adapter, this);
        int loaded = persistence.load();
        selftest = new Selftest(this, ticks, lang);
        VillageCases.all().forEach(selftest::register);
        RoleCases.all().forEach(selftest::register);
        BehaviorCases.all().forEach(selftest::register);
        BirthCases.all().forEach(selftest::register);
        CrisisCases.all().forEach(selftest::register);
        WarCases.all().forEach(selftest::register);
        PerfCases.all().forEach(selftest::register);
        CommandCases.all().forEach(selftest::register);
        selftest.register(CommandCases.coverage());
        Commands commands = new Commands(this);
        PluginCommand cmd = getCommand("respublica");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
        worldTimer = t -> {
            scanner.onTicks(t);
            gauges.onTicks(t);
            crises.onTicks(t);
            war.onTicks(t);
            actionBar.onTicks(t);
            behaviors.onTicks(t);
            births.onTicks(t);
            persistence.onTicks(t, simConfig.get(SimConfig.Key.PERSISTENCE_FLUSH_S));
        };
        ticks.register(worldTimer);
        ticks.start(this);
        getLogger().info(loaded + " village(s) rechargé(s)");
        getLogger().info("Realms & Res Publica activé");
    }

    @Override
    public void onDisable() {
        ticks.stop();
        if (worldTimer != null) {
            ticks.unregister(worldTimer);
        }
        if (gauges != null) {
            gauges.saveAll();
        }
        if (persistence != null) {
            persistence.flush();
        }
        getLogger().info("Realms & Res Publica désactivé");
    }

    /** Relit config.yml dans le cœur ; les valeurs invalides sont signalées et le défaut est conservé. */
    public void loadSimConfig() {
        reloadConfig();
        simConfig = SimConfig.fromMap(getConfig().getValues(false));
        simConfig.warnings().forEach(w -> getLogger().warning("config.yml : " + w));
    }

    public SimConfig simConfig() {
        return simConfig;
    }

    public TickService ticks() {
        return ticks;
    }

    public Lang lang() {
        return lang;
    }

    public Selftest selftest() {
        return selftest;
    }

    public VisualExport visuals() {
        return visuals;
    }

    public VillageRegistry registry() {
        return registry;
    }

    public WarehouseService warehouse() {
        return warehouse;
    }

    public ActionBarService actionBar() {
        return actionBar;
    }

    public VillageScanner scanner() {
        return scanner;
    }

    public VillagerAdapter adapter() {
        return adapter;
    }

    public GaugeTicker gauges() {
        return gauges;
    }

    public BehaviorScheduler behaviors() {
        return behaviors;
    }

    public BirthService births() {
        return births;
    }

    public CrisisService crises() {
        return crises;
    }

    public WarService war() {
        return war;
    }

    public Persistence persistence() {
        return persistence;
    }
}
