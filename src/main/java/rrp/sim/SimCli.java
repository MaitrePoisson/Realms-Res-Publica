package rrp.sim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import rrp.core.SimConfig;
import rrp.sim.ScenarioRunner.Result;

/**
 * Simulateur en ligne de commande : exécute les 4 scénarios et écrit {@code sim-<nom>.csv} et
 * {@code sim-<nom>.svg} dans le dossier donné (défaut {@code .athanor/screenshots}).
 * Usage : {@code SimCli [dossier] [scénario]}.
 */
public final class SimCli {
    private SimCli() { }

    public static void main(String[] args) throws IOException {
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        Path dir = Path.of(args.length > 0 ? args[0] : ".athanor/screenshots");
        String only = args.length > 1 ? args[1] : null;
        Files.createDirectories(dir);
        SimConfig cfg = SimConfig.defaults();
        for (Scenario sc : Scenario.all()) {
            if (only != null && !sc.name().equals(only)) {
                continue;
            }
            Result r = ScenarioRunner.run(sc, cfg);
            Files.writeString(dir.resolve("sim-" + sc.name() + ".csv"), Report.csv(r), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("sim-" + sc.name() + ".svg"), Report.svg(r), StandardCharsets.UTF_8);
            System.out.println(Report.summary(r));
        }
    }
}
