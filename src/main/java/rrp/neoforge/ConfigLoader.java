package rrp.neoforge;

import org.slf4j.Logger;
import rrp.core.SimConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Lit config/respublica/config.yml (copié depuis le jar au premier lancement) dans le cœur. */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static SimConfig load(Path dir, Logger log) {
        Path file = dir.resolve("config.yml");
        try {
            Files.createDirectories(dir);
            if (!Files.exists(file)) {
                try (InputStream in = ConfigLoader.class.getResourceAsStream("/config.yml")) {
                    Files.write(file, in.readAllBytes());
                }
                log.info("config.yml créé dans {}", dir);
            }
            SimConfig cfg = SimConfig.fromMap(FlatYaml.parse(Files.readString(file, StandardCharsets.UTF_8)));
            cfg.warnings().forEach(w -> log.warn("config.yml : {}", w));
            return cfg;
        } catch (IOException | RuntimeException e) {
            log.error("config.yml illisible, valeurs par défaut : {}", e.getMessage());
            return SimConfig.defaults();
        }
    }
}
