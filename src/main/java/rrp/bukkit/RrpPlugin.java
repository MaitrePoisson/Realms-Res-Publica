package rrp.bukkit;

import org.bukkit.plugin.java.JavaPlugin;

public final class RrpPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Realms & Res Publica activé");
    }

    @Override
    public void onDisable() {
        getLogger().info("Realms & Res Publica désactivé");
    }
}
