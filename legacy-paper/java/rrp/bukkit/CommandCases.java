package rrp.bukkit;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import rrp.core.CrisisEngine;
import rrp.core.CrisisLevel;
import rrp.core.Resource;
import rrp.core.Role;

import java.util.ArrayList;
import java.util.List;

import static rrp.bukkit.TestWorld.check;

/** Cas de selftest de M17 : commandes de debug de crise (famine, overpopulate, insecurity, reload, help) et couverture. */
final class CommandCases {

    private CommandCases() {
    }

    static List<SelftestCase> all() {
        return List.of(famineCommand(), overpopulateCommand(), insecurityCommand(), reloadAndHelp());
    }

    private static List<String> run(RrpPlugin p, String... args) {
        List<String> lines = new ArrayList<>();
        new Commands(p).onCommand(VillageCases.console(lines), p.getCommand("respublica"), "respublica", args);
        check(lines.stream().noneMatch(l -> l.contains("?")), "clé de langue manquante : " + lines);
        return lines;
    }

    /** Fait retirer par le nettoyage les entités apparues autour du village pendant une commande. */
    private static void trackAround(TestWorld w, VillageSite s) {
        Location c = s.center(w.world());
        for (Entity e : w.world().getNearbyEntities(c, s.radius() + 10, 64, s.radius() + 10,
                x -> x instanceof Villager || x instanceof Zombie)) {
            w.track(e);
        }
    }

    private static SelftestCase famineCommand() {
        return CrisisCases.of("commande famine : vide le vrai tonneau, la contestation suit", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = CrisisCases.village(w, p, 13200, 4, 120, vs);
            check(p.warehouse().read(s, w.world()).getOrDefault(Resource.FOOD, 0) == 120, "le tonneau devait contenir 120 pains");
            List<String> lines = run(p, "famine", s.id());
            check(lines.stream().anyMatch(l -> l.contains("120")), "retrait non annoncé : " + lines);
            check(p.warehouse().read(s, w.world()).getOrDefault(Resource.FOOD, 0) == 0, "le tonneau n'est pas vide");
            check(s.state().stock(Resource.FOOD) == 0, "l'état du village garde du stock : " + CrisisCases.diag(s));
            check(CrisisEngine.indicators(s.state(), p.simConfig()).famine(), "l'indicateur de famine devait être levé");
            int steps = CrisisCases.runUntil(p, 600, () -> s.state().level() == CrisisLevel.CONTESTATION);
            check(steps >= 0, "pas de contestation après la famine provoquée : " + CrisisCases.diag(s));
            check(run(p, "famine", "inconnu").stream().anyMatch(l -> l.contains("Usage")), "village inconnu non signalé");
        });
    }

    private static SelftestCase overpopulateCommand() {
        return CrisisCases.of("commande overpopulate : de vrais villageois en plus, plus que lits × ratio", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = CrisisCases.village(w, p, 14000, 2, 200, vs);
            Location g = s.center(w.world());
            for (int i = 0; i < 3; i++) {
                w.placeBed(g.clone().add(4 + 2 * i, 0, 0), org.bukkit.block.BlockFace.SOUTH);
            }
            p.scanner().refreshNow(s);
            int before = s.state().population();
            check(!CrisisEngine.indicators(s.state(), p.simConfig()).overpopulation(), "surpopulation avant la commande");
            List<String> lines = run(p, "overpopulate", s.id());
            trackAround(w, s);
            check(s.state().population() > before, "aucun villageois ajouté : " + lines);
            check(CrisisEngine.indicators(s.state(), p.simConfig()).overpopulation(),
                    "surpopulation attendue : " + s.state().population() + " villageois, " + s.state().beds() + " lits");
            long real = w.world().getNearbyEntities(s.center(w.world()), s.radius() + 10, 64, s.radius() + 10,
                    e -> e instanceof Villager).size();
            check(real >= s.state().population(), "les villageois de l'état ne sont pas de vraies entités : " + real);
            int steps = CrisisCases.runUntil(p, 300, () -> s.state().level() == CrisisLevel.CONTESTATION);
            check(steps >= 0, "pas de contestation après la surpopulation provoquée : " + CrisisCases.diag(s));
        });
    }

    private static SelftestCase insecurityCommand() {
        return CrisisCases.of("commande insecurity : monstres réels persistants, plus que les gardes ne couvrent", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = CrisisCases.village(w, p, 14800, 3, 120, vs);
            s.state().villagers().forEach(v -> {
                v.setRole(Role.FARMER);
                if (org.bukkit.Bukkit.getEntity(v.id()) instanceof Villager e) {
                    p.adapter().write(e, v);
                }
            });
            List<String> lines = run(p, "insecurity", s.id());
            trackAround(w, s);
            check(s.state().threats() > 0, "aucune menace comptée : " + lines);
            CrisisEngine.Indicators i = CrisisEngine.indicators(s.state(), p.simConfig());
            check(i.insecurity() && !i.famine(), "insécurité seule attendue : " + i);
            for (Entity e : w.world().getNearbyEntities(s.center(w.world()), s.radius() + 10, 64, s.radius() + 10, x -> x instanceof Zombie)) {
                check(e.isPersistent(), "un monstre provoqué doit être persistant");
            }
            int steps = CrisisCases.runUntil(p, 200, () -> s.state().level() == CrisisLevel.CONTESTATION);
            check(steps >= 0, "pas de contestation après l'insécurité provoquée : " + CrisisCases.diag(s));
        });
    }

    private static SelftestCase reloadAndHelp() {
        return CrisisCases.of("commandes : reload relit config.yml, help liste toutes les sous-commandes", (w, p) -> {
            List<String> lines = run(p, "reload");
            check(lines.stream().anyMatch(l -> l.contains("relu")), "reload muet : " + lines);
            check(p.simConfig() != null, "configuration perdue");
            lines = run(p, "help");
            for (String sub : List.of("selftest", "tick", "village", "warehouse", "status", "inspect", "setrole", "famine",
                    "overpopulate", "insecurity", "reload")) {
                check(lines.stream().anyMatch(l -> l.contains("/respublica " + sub)), "aide sans " + sub + " : " + lines);
            }
            check(lines.stream().anyMatch(l -> l.contains("/respublica help")), "l'aide ne s'inclut pas elle-même");
            List<Villager> vs = new ArrayList<>();
            VillageSite s = CrisisCases.village(w, p, 13600, 4, 50, vs);
            check(run(p, "status", s.id()).stream().anyMatch(l -> l.contains("crise Stable")),
                    "status sans libellé de crise lisible");
            String bar = p.actionBar().line(s);
            check(bar.contains(s.id()) && bar.contains("Stable") && !bar.contains("?"), "barre d'action incorrecte : " + bar);
        });
    }

    /** Dernier cas : le selftest couvre bien croissance, stagnation, les 7 rôles, les trois crises, l'exil et la guerre. */
    static SelftestCase coverage() {
        return new SelftestCase() {
            public String name() {
                return "couverture : croissance, stagnation, 7 rôles, crises, exil, guerre";
            }

            public void run(TestWorld w) {
                List<String> names = ((RrpPlugin) w.plugin()).selftest().names();
                for (String key : List.of("naissance : avec nourriture", "naissance : sans nourriture", "naissance : sans lit",
                        "fermier :", "forestier :", "mineur :", "intendant :", "garde :", "bâtisseur :", "diplomate :",
                        "famine soutenue", "surpopulation", "insécurité", "exil :", "guerre :", "trêve :")) {
                    check(names.stream().anyMatch(n -> n.contains(key)), "aucun cas ne couvre « " + key + " »");
                }
            }
        };
    }
}
