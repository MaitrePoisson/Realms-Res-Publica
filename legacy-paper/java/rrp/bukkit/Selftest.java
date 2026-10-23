package rrp.bukkit;

import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Exécute les cas de selftest sur le serveur réel et écrit `selftest-result.json`
 * dans le dossier du plugin (lu par tools/run_server_test.sh).
 */
public final class Selftest {

    public static final String RESULT_FILE = "selftest-result.json";

    private final RrpPlugin plugin;
    private final TickService ticks;
    private final Lang lang;
    private final List<SelftestCase> cases = new ArrayList<>();

    public Selftest(RrpPlugin plugin, TickService ticks, Lang lang) {
        this.plugin = plugin;
        this.ticks = ticks;
        this.lang = lang;
        cases.addAll(BaseCases.all());
    }

    /** Les milestones suivants y ajoutent leurs cas. */
    public void register(SelftestCase c) {
        cases.add(c);
    }

    /** Noms des cas enregistrés. */
    public List<String> names() {
        return cases.stream().map(SelftestCase::name).toList();
    }

    private record Outcome(String name, boolean ok, String message, long millis) {
    }

    /** Exécute tous les cas (thread principal) ; renvoie le nombre d'échecs. */
    public int runAll(CommandSender out) {
        out.sendMessage(lang.msg("selftest.start", cases.size()));
        List<Outcome> results = new ArrayList<>();
        plugin.visuals().reset();
        for (SelftestCase c : cases) {
            TestWorld world = new TestWorld(plugin, ticks);
            long t0 = System.nanoTime();
            boolean ok = true;
            String message = "";
            try {
                c.run(world);
            } catch (Throwable t) {
                ok = false;
                message = t.getClass().getSimpleName() + ": " + t.getMessage();
                plugin.getLogger().warning("selftest ÉCHEC " + c.name() + " : " + message);
            } finally {
                try {
                    world.cleanup();
                } catch (Throwable t) {
                    plugin.getLogger().warning("selftest nettoyage de « " + c.name() + " » : " + t);
                }
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            results.add(new Outcome(c.name(), ok, message, ms));
            out.sendMessage(ok ? lang.msg("selftest.case_ok", c.name()) : lang.msg("selftest.case_fail", c.name(), message));
        }
        int failed = (int) results.stream().filter(o -> !o.ok()).count();
        int passed = results.size() - failed;
        File file = new File(plugin.getDataFolder(), RESULT_FILE);
        try {
            plugin.getDataFolder().mkdirs();
            Files.writeString(file.toPath(), toJson(results, passed, failed), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().severe("selftest : écriture de " + file + " impossible : " + e.getMessage());
            failed++;
        }
        try {
            Files.writeString(new File(plugin.getDataFolder(), VisualExport.FILE).toPath(),
                    plugin.visuals().html(passed, failed), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("selftest : écriture de " + VisualExport.FILE + " impossible : " + e.getMessage());
        }
        out.sendMessage(lang.msg("selftest.done", passed, failed, file.getName()));
        plugin.getLogger().info("selftest terminé : " + passed + " réussi(s), " + failed + " échoué(s)");
        return failed;
    }

    private static String toJson(List<Outcome> results, int passed, int failed) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"total\": ").append(results.size())
                .append(",\n  \"passed\": ").append(passed)
                .append(",\n  \"failed\": ").append(failed)
                .append(",\n  \"cases\": [\n");
        for (int i = 0; i < results.size(); i++) {
            Outcome o = results.get(i);
            sb.append("    {\"name\": ").append(quote(o.name()))
                    .append(", \"status\": \"").append(o.ok() ? "OK" : "FAIL").append('"')
                    .append(", \"message\": ").append(quote(o.message()))
                    .append(", \"millis\": ").append(o.millis()).append('}');
            sb.append(i + 1 < results.size() ? ",\n" : "\n");
        }
        return sb.append("  ]\n}\n").toString();
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
