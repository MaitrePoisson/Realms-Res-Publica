package rrp.neoforge;

import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Textes en français chargés depuis lang_fr.yml (clés plates, paramètres {0}, {1}…). */
public final class Lang {

    private final Map<String, Object> texts;

    public Lang(Logger log) {
        Map<String, Object> loaded = Map.of();
        try (InputStream in = Lang.class.getResourceAsStream("/lang_fr.yml")) {
            if (in == null) {
                throw new IllegalStateException("ressource absente");
            }
            loaded = FlatYaml.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("lang_fr.yml illisible : {}", e.getMessage());
        }
        texts = loaded;
    }

    public String get(String key, Object... args) {
        Object text = texts.get(key);
        if (text == null) {
            return "?" + key + "?";
        }
        String s = String.valueOf(text);
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }

    /** Message préfixé par [Res Publica]. */
    public String msg(String key, Object... args) {
        return get("prefix") + " " + get(key, args);
    }

    public int size() {
        return texts.size();
    }
}
