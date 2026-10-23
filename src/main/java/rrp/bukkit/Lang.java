package rrp.bukkit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/** Textes en français chargés depuis lang_fr.yml (clés plates, paramètres {0}, {1}…). */
public final class Lang {

    private final YamlConfiguration yaml = new YamlConfiguration();

    public Lang(JavaPlugin plugin) {
        try (Reader r = new InputStreamReader(plugin.getResource("lang_fr.yml"), StandardCharsets.UTF_8)) {
            yaml.load(r);
        } catch (Exception e) {
            plugin.getLogger().severe("lang_fr.yml illisible : " + e.getMessage());
        }
    }

    public String get(String key, Object... args) {
        String text = yaml.getString(key);
        if (text == null) {
            return "?" + key + "?";
        }
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }

    /** Message préfixé par [Res Publica]. */
    public String msg(String key, Object... args) {
        return get("prefix") + " " + get(key, args);
    }
}
