package rrp.bukkit;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import rrp.core.CrisisEngine;
import rrp.core.CrisisLevel;
import rrp.core.Faction;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.VillagerState;

import java.util.ArrayList;
import java.util.List;

import static rrp.bukkit.TestWorld.check;

/** Cas de selftest de M16 : insécurité réelle (monstres sans gardes) et guerre civile entre village et campement. */
final class WarCases {

    private WarCases() {
    }

    static List<SelftestCase> all() {
        return List.of(insecurityLeadsToContestation(), warEndsByElimination(), truceReconciles(), truceMakesCampAutonomous(), famineChainReachesWar());
    }

    private static void setRole(RrpPlugin p, VillagerState s, Role role) {
        s.setRole(role);
        if (entityOf(s) instanceof Villager v) {
            p.adapter().write(v, s);
        }
    }

    private static Entity entityOf(VillagerState s) {
        return org.bukkit.Bukkit.getEntity(s.id());
    }

    private static SelftestCase insecurityLeadsToContestation() {
        return CrisisCases.of("insécurité : des monstres persistants sans garde → contestation", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = CrisisCases.village(w, p, 12000, 1, 60, vs);
            s.state().villagers().forEach(v -> setRole(p, v, Role.FARMER));
            Creeper c = w.spawn(s.center(w.world()).add(6, 0, 6), Creeper.class);
            c.setAI(false);
            int ended = CrisisCases.runUntil(p, 200, () -> s.state().level() == CrisisLevel.CONTESTATION);
            check(ended >= 0, "pas de contestation malgré un monstre persistant : " + CrisisCases.diag(s));
            CrisisEngine.Indicators i = CrisisEngine.indicators(s.state(), p.simConfig());
            check(i.insecurity() && !i.famine() && !i.overpopulation(),
                    "seule l'insécurité devait être en cause : " + i + " menaces=" + s.state().threats());
            // un garde couvre les menaces : l'indicateur s'éteint
            setRole(p, s.state().villagers().get(0), Role.GUARD);
            check(!CrisisEngine.indicators(s.state(), p.simConfig()).insecurity(), "un garde ne couvre pas la menace");
        });
    }

    /** Village (4 adultes, nourriture, lits supplémentaires) et campement dissident à ~110 blocs, loyauté basse, guerre imminente. */
    private record War(VillageSite village, VillageSite camp, List<Villager> villagers, List<Villager> campers) {
        void pin() {
            village.state().villagers().forEach(v -> v.setLoyalty(5));
            camp.state().villagers().forEach(v -> v.setLoyalty(5));
        }
    }

    private static War warSetup(TestWorld w, RrpPlugin p, int x, int food, int extraBeds, boolean diplomat) {
        List<Villager> vs = new ArrayList<>();
        VillageSite village = CrisisCases.village(w, p, x, 3, food, vs);
        Location g = village.center(w.world());
        for (int i = 0; i < extraBeds; i++) {
            w.placeBed(g.clone().add(4 + 2 * i, 0, 0), BlockFace.SOUTH);
        }
        p.scanner().refreshNow(village);
        List<VillagerState> st = village.state().villagers();
        check(st.size() == 3, "3 villageois attendus : " + st.size());
        setRole(p, st.get(0), Role.GUARD);
        setRole(p, st.get(1), diplomat ? Role.DIPLOMAT : Role.GUARD);
        setRole(p, st.get(2), Role.FARMER);

        w.prepare(x + 110, x, 3);
        Location cl = w.ground(x + 110, x);
        VillageSite camp = p.registry().create(cl, 20, Faction.DISSIDENT);
        camp.state().setParentId(village.id());
        List<Villager> campers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            campers.add(w.spawnVillager(cl.clone().add(i - 1, 0, 4)));
        }
        p.adapter().sync(camp, w.world());
        List<VillagerState> cs = camp.state().villagers();
        check(cs.size() == 3, "3 dissidents attendus : " + cs.size());
        setRole(p, cs.get(0), Role.GUARD);
        setRole(p, cs.get(1), Role.GUARD);
        setRole(p, cs.get(2), Role.FARMER);

        village.state().setLevel(CrisisLevel.EXIL);
        village.state().setExileTimer(p.simConfig().get(SimConfig.Key.EXILE_AFTER_S));
        village.state().setWarTimer(p.simConfig().get(SimConfig.Key.WAR_AFTER_S) - 1);
        return new War(village, camp, vs, campers);
    }

    private static SelftestCase famineChainReachesWar() {
        return CrisisCases.of("chaîne complète : famine → contestation → exil → guerre civile, sans état amorcé", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = CrisisCases.village(w, p, 14400, 6, 0, vs);
            for (VillagerState st : s.state().villagers()) {
                setRole(p, st, Role.GUARD); // des combattants des deux côtés, aucun diplomate : la trêve est impossible
            }
            check(s.state().level() == CrisisLevel.STABLE, "village stable au départ : " + CrisisCases.diag(s));
            long exilesBefore = p.crises().exiles();
            long started = p.war().started();
            boolean[] seen = new boolean[3];
            int steps = CrisisCases.runUntil(p, 3000, () -> {
                seen[0] |= s.state().level() == CrisisLevel.CONTESTATION;
                seen[1] |= p.crises().exiles() > exilesBefore;
                seen[2] |= p.war().started() > started;
                return seen[2];
            });
            check(steps >= 0, "la guerre n'a pas éclaté après 3000 pas de 10 s : " + CrisisCases.diag(s)
                    + " contestation=" + seen[0] + " exil=" + seen[1]);
            check(seen[0] && seen[1], "la chaîne doit passer par la contestation puis l'exil");
        });
    }

    private static SelftestCase warEndsByElimination() {
        return CrisisCases.of("guerre : les gardes des deux camps se battent jusqu'à l'élimination d'un camp", (w, p) -> {
            War war = warSetup(w, p, 12600, 40, 0, false);
            List<Villager> fighters = new ArrayList<>();
            for (VillagerState s : war.village().state().villagers()) {
                if (s.role() == Role.GUARD && entityOf(s) instanceof Villager v) {
                    fighters.add(v);
                }
            }
            for (VillagerState s : war.camp().state().villagers()) {
                if (s.role() == Role.GUARD && entityOf(s) instanceof Villager v) {
                    fighters.add(v);
                }
            }
            check(fighters.size() == 4, "4 combattants attendus : " + fighters.size());
            long started = p.war().started();
            long ended = p.war().ended();
            int steps = CrisisCases.runUntil(p, 120, () -> {
                war.pin();
                return p.war().ended() > ended;
            });
            check(p.war().started() > started, "la guerre n'a pas commencé : " + CrisisCases.diag(war.village()));
            check(steps >= 0, "la guerre ne s'est pas terminée : " + CrisisCases.diag(war.village()) + " / "
                    + CrisisCases.diag(war.camp()) + " coups=" + p.war().hits() + " morts=" + p.war().deaths());
            boolean hurt = fighters.stream().anyMatch(v -> !v.isValid() || v.isDead() || v.getHealth() < 20.0);
            check(hurt && p.war().hits() > 0, "aucun combattant n'a perdu de PV (coups=" + p.war().hits() + ")");
            check(p.war().deaths() >= 1, "aucun mort : la fin par élimination est inexplicable");
            check("elimination".equals(p.war().lastEnd()), "issue attendue « elimination » : " + p.war().lastEnd());
            int vg = war.village().state().count(Role.GUARD);
            int cg = war.camp().state().count(Role.GUARD);
            check(vg == 0 || cg == 0, "un camp devait n'avoir plus de garde : " + vg + "/" + cg);
            check(war.village().state().level() != CrisisLevel.GUERRE_CIVILE, "le village est toujours en guerre");
            check(war.village().reach() == war.village().radius(), "le rayon de guerre n'a pas été rétabli");
        });
    }

    private static SelftestCase truceReconciles() {
        return CrisisCases.of("trêve : diplomate, relations au seuil, cause disparue → réconciliation", (w, p) -> {
            War war = warSetup(w, p, 13200, 60, 5, true);
            long ended = p.war().ended();
            boolean[] truce = {false};
            int steps = CrisisCases.runUntil(p, 60, () -> {
                war.pin();
                if (!truce[0] && war.village().state().level() == CrisisLevel.GUERRE_CIVILE) {
                    war.village().state().setRelations(p.simConfig().get(SimConfig.Key.TRUCE_RELATIONS) + 10);
                    truce[0] = true;
                }
                return p.war().ended() > ended;
            });
            check(steps >= 0 && truce[0], "pas de fin de guerre par trêve : " + CrisisCases.diag(war.village()));
            check("reconciliation".equals(p.war().lastEnd()), "issue attendue « reconciliation » : " + p.war().lastEnd());
            check(p.registry().get(war.camp().id()) == null, "le campement existe encore");
            check(war.village().state().population() == 6, "le village devait compter 6 habitants : " + war.village().state().population());
            for (Villager v : war.campers()) {
                VillagerState s = p.adapter().read(v);
                check(s != null && s.factionId().equals(war.village().id()), "un dissident n'a pas rejoint le village dans son PDC");
                check(v.getLocation().distance(war.village().center(w.world())) < 20, "un dissident n'est pas revenu au village");
            }
            check(war.village().state().level() == CrisisLevel.CONTESTATION, "niveau attendu CONTESTATION : " + war.village().state().level());
        });
    }

    private static SelftestCase truceMakesCampAutonomous() {
        return CrisisCases.of("trêve : la famine persiste → le campement devient autonome", (w, p) -> {
            War war = warSetup(w, p, 13800, 0, 0, true);
            long ended = p.war().ended();
            boolean[] truce = {false};
            int steps = CrisisCases.runUntil(p, 60, () -> {
                war.pin();
                if (!truce[0] && war.village().state().level() == CrisisLevel.GUERRE_CIVILE) {
                    war.village().state().setRelations(p.simConfig().get(SimConfig.Key.TRUCE_RELATIONS) + 10);
                    truce[0] = true;
                }
                return p.war().ended() > ended;
            });
            check(steps >= 0 && truce[0], "pas de fin de guerre par trêve : " + CrisisCases.diag(war.village()));
            check("autonomie".equals(p.war().lastEnd()), "issue attendue « autonomie » : " + p.war().lastEnd());
            VillageSite camp = p.registry().get(war.camp().id());
            check(camp != null && camp.state().parentId() == null && camp.state().population() == 3,
                    "le campement devait rester, autonome et peuplé");
            check(camp.state().level() != CrisisLevel.GUERRE_CIVILE, "le camp est toujours en guerre");
            check(war.village().state().population() == 3, "le village a changé de population");
        });
    }
}
