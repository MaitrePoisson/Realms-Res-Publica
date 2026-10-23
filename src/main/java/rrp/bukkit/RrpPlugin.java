package rrp.bukkit;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import rrp.core.SimConfig;

import java.util.function.LongConsumer;

public final class RrpPlugin extends JavaPlugin {

    private final TickService ticks = new TickService();
    private final VillageRegistry registry = new VillageRegistry();
    private final WarehouseService warehouse = new WarehouseService(registry);
    private SimConfig simConfig = SimConfig.defaults();
    private VillageScanner scanner;
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
        int loaded = persistence.load();
        selftest = new Selftest(this, ticks, lang);
        VillageCases.all().forEach(selftest::register);
        Commands commands = new Commands(this);
        PluginCommand cmd = getCommand("respublica");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
        worldTimer = t -> {
            scanner.onTicks(t);
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

    public VillageRegistry registry() {
        return registry;
    }

    public WarehouseService warehouse() {
        return warehouse;
    }

    public VillageScanner scanner() {
        return scanner;
    }

    public Persistence persistence() {
        return persistence;
    }
}
