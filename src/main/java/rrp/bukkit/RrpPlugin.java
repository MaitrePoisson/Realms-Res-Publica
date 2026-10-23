package rrp.bukkit;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RrpPlugin extends JavaPlugin {

    private final TickService ticks = new TickService();
    private Lang lang;
    private Selftest selftest;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lang = new Lang(this);
        selftest = new Selftest(this, ticks, lang);
        Commands commands = new Commands(ticks, selftest, lang);
        PluginCommand cmd = getCommand("respublica");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
        ticks.start(this);
        getLogger().info("Realms & Res Publica activé");
    }

    @Override
    public void onDisable() {
        ticks.stop();
        getLogger().info("Realms & Res Publica désactivé");
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
}
