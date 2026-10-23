package rrp.bukkit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.inventory.ItemStack;
import rrp.core.CrisisEngine;
import rrp.core.CrisisLevel;
import rrp.core.Faction;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.SimEvent;
import rrp.core.VillagerState;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static rrp.bukkit.TestWorld.check;

/** Cas de selftest de M15 : contestation, exil et campement dissident dans le monde réel. */
final class CrisisCases {

    private CrisisCases() {
    }

    /** Comme {@link VillageCases#of}, et fait enregistrer à CampBuilder les blocs qu'il modifie pour tout restaurer. */
    static SelftestCase of(String name, VillageCases.Body body) {
        return VillageCases.of(name, (w, p) -> {
            p.crises().camps().setRecorder(w::remember);
            try {
                body.run(w, p);
            } finally {
                p.crises().camps().setRecorder(null);
            }
        });
    }

    static List<SelftestCase> all() {
        return List.of(famineLeadsToContestationThenExile(), exileBuildsRealCamp(), secondExileJoinsSameCamp(),
                overpopulationGivesContestation(), campAvoidsWater());
    }

    /** Village de {@code n} adultes, un tonneau-entrepôt rempli de {@code food} pains, un seul lit réel. */
    static VillageSite village(TestWorld w, RrpPlugin p, int x, int n, int food, List<Villager> vs) {
        w.prepare(x, x, 4);
        VillageSite s = RoleCases.villageOf(w, p, x, x, n, vs);
        Location g = s.center(w.world());
        p.warehouse().set(s, w.placeBarrel(g.clone().add(-3, 0, 0)));
        if (food > 0) {
            p.warehouse().deposit(s, w.world(), new ItemStack(Material.BREAD, food));
        }
        p.warehouse().refresh(s, w.world());
        p.scanner().refreshNow(s);
        return s;
    }

    private static VillageSite campOf(RrpPlugin p, VillageSite village) {
        VillageSite found = null;
        for (VillageSite s : p.registry().all()) {
            if (s.state().faction() == Faction.DISSIDENT && village.id().equals(s.state().parentId())) {
                check(found == null, "plusieurs campements pour " + village.id());
                found = s;
            }
        }
        return found;
    }

    /** Avance le temps de 10 s par pas jusqu'à ce que la condition soit vraie ; renvoie le nombre de pas, ou -1. */
    static int runUntil(RrpPlugin p, int maxSteps, BooleanSupplier done) {
        for (int i = 0; i < maxSteps; i++) {
            if (done.getAsBoolean()) {
                return i;
            }
            p.ticks().advance(200);
        }
        return done.getAsBoolean() ? maxSteps : -1;
    }

    private static int countAround(World world, VillageSite camp, java.util.function.Predicate<Material> test) {
        Location c = camp.center(world);
        int n = 0;
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -14; dz <= 14; dz++) {
                for (int dy = -2; dy <= 5; dy++) {
                    if (test.test(c.clone().add(dx, dy, dz).getBlock().getType())) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    static String diag(VillageSite s) {
        return "niveau=" + s.state().level() + " pop=" + s.state().population() + " stock="
                + s.state().stock(Resource.FOOD) + " loyauté=" + Math.round(s.state().averageLoyalty())
                + " moral=" + Math.round(s.state().averageMorale()) + " minuteurs=" + Math.round(s.state().contestTimer())
                + "/" + Math.round(s.state().exileTimer());
    }

    /** Pousse le village au seuil de l'exil : contesté, minuteur presque écoulé, villageois très peu loyaux. */
    private static void primeForExile(RrpPlugin p, VillageSite s) {
        s.state().setLevel(CrisisLevel.CONTESTATION);
        s.state().setContestTimer(p.simConfig().get(SimConfig.Key.CONTEST_AFTER_S));
        s.state().setExileTimer(p.simConfig().get(SimConfig.Key.EXILE_AFTER_S) - 1);
    }

    private static SelftestCase famineLeadsToContestationThenExile() {
        return of("crise : famine soutenue → contestation puis exil, avec un campement réel", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = village(w, p, 9600, 4, 0, vs);
            int[] contestStep = {-1};
            long exilesBefore = p.crises().exiles();
            int[] step = {0};
            int ended = runUntil(p, 1500, () -> {
                if (contestStep[0] < 0 && s.state().level() == CrisisLevel.CONTESTATION) {
                    contestStep[0] = step[0];
                }
                step[0]++;
                return p.crises().exiles() > exilesBefore;
            });
            check(ended >= 0, "aucun exil après 1500 pas de 10 s : " + diag(s));
            check(contestStep[0] >= 0 && contestStep[0] < step[0] - 1,
                    "la contestation doit précéder l'exil (contestation au pas " + contestStep[0] + ", exil au pas " + step[0] + ")");
            check(s.state().level() == CrisisLevel.EXIL, "niveau attendu EXIL : " + diag(s));
            VillageSite camp = campOf(p, s);
            check(camp != null, "pas de campement dissident");
            check(camp.state().population() >= 1 && s.state().population() >= 1, "répartition des villageois : " + diag(s));
            check(countAround(w.world(), camp, m -> m == Material.CAMPFIRE) == 1, "un seul feu de camp attendu");
        });
    }

    private static SelftestCase exileBuildsRealCamp() {
        return of("exil : feux, tentes de laine, lits, tonneau, nourriture, mémoires effacées, persistance", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = village(w, p, 10000, 6, 30, vs);
            Location home = s.center(w.world());
            for (Villager v : vs) {
                v.setMemory(MemoryKey.HOME, home);
                v.setMemory(MemoryKey.JOB_SITE, home);
            }
            for (VillagerState st : s.state().villagers()) {
                st.setLoyalty(10);
            }
            primeForExile(p, s);
            List<SimEvent> events = p.crises().advance(100);
            SimEvent.Exile exile = events.stream().filter(e -> e instanceof SimEvent.Exile).map(e -> (SimEvent.Exile) e)
                    .findFirst().orElse(null);
            check(exile != null, "aucun exil émis : " + diag(s));
            int n = exile.villagerIds().size();
            check(n >= 2 && n <= 3, "2 à 3 exilés attendus (plafond exile_max_share = 50 % de 6), vus " + n);
            check(s.state().level() == CrisisLevel.EXIL && s.state().population() == 6 - n, "village après exil : " + diag(s));

            VillageSite camp = campOf(p, s);
            check(camp != null, "campement non enregistré");
            World world = w.world();
            check(camp.state().faction() == Faction.DISSIDENT && camp.state().population() == n, "camp : " + diag(camp));
            check(camp.state().count(Role.GUARD) >= 1, "au moins 1 garde attendu, vus " + camp.state().count(Role.GUARD));
            check(camp.distance(s.worldName(), s.x(), s.z()) >= s.radius(), "campement dans le rayon du village");
            check(countAround(world, camp, m -> m == Material.CAMPFIRE) == 1, "un feu de camp attendu");
            check(countAround(world, camp, Tag.WOOL::isTagged) >= n * 6, "tentes de laine manquantes");
            check(camp.warehouseBlock(world) != null, "tonneau du camp absent");
            int share = (int) Math.floor(30 * p.simConfig().get(SimConfig.Key.EXILE_FOOD_SHARE));
            check(p.warehouse().read(camp, world).getOrDefault(Resource.FOOD, 0) == share,
                    "nourriture du camp : " + p.warehouse().read(camp, world) + " au lieu de " + share);
            check(p.warehouse().read(s, world).getOrDefault(Resource.FOOD, 0) == 30 - share,
                    "tonneau du village : " + p.warehouse().read(s, world));
            p.scanner().refreshNow(camp);
            check(camp.state().beds() == n, n + " lits réels attendus au camp, comptés " + camp.state().beds());

            for (VillagerState st : camp.state().villagers()) {
                Entity e = org.bukkit.Bukkit.getEntity(st.id());
                check(e instanceof Villager, "exilé introuvable");
                Villager v = (Villager) e;
                check(v.getMemory(MemoryKey.HOME) == null && v.getMemory(MemoryKey.JOB_SITE) == null,
                        "mémoires HOME/JOB_SITE non effacées");
                check(camp.contains(world.getName(), v.getLocation().getX(), v.getLocation().getZ()), "exilé hors du campement");
                VillagerState stored = p.adapter().read(v);
                check(stored != null && camp.id().equals(stored.factionId()), "faction du PDC non mise à jour");
            }
            check(p.adapter().sync(s, world) == 0 && s.state().population() == 6 - n, "le village a réadopté un exilé");
            check(p.adapter().sync(camp, world) == 0 && camp.state().population() == n, "le camp a perdu un exilé");

            check(p.persistence().flush(), "écriture de villages.json impossible");
            String json = Files.readString(new File(p.getDataFolder(), Persistence.FILE).toPath());
            check(json.contains(camp.id()) && json.contains("DISSIDENT") && json.contains(s.id()), "campement absent de villages.json");
        });
    }

    private static SelftestCase secondExileJoinsSameCamp() {
        return of("exil : un second exil agrandit le même campement (un feu, tentes en plus)", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = village(w, p, 10400, 8, 0, vs);
            for (int i = 0; i < s.state().villagers().size(); i++) {
                s.state().villagers().get(i).setLoyalty(i < 6 ? 5 : 60);
            }
            primeForExile(p, s);
            p.crises().advance(100);
            VillageSite camp = campOf(p, s);
            int first = camp == null ? 0 : camp.state().population();
            check(first >= 2 && first <= 4, "premier exil (2 à 4 sur 8) : " + (camp == null ? "aucun camp" : diag(camp)));
            for (VillagerState st : s.state().villagers()) {
                st.setLoyalty(10);
            }
            primeForExile(p, s);
            p.crises().advance(100);
            check(campOf(p, s) == camp, "un second campement a été créé");
            check(camp.state().population() > first && camp.state().population() + s.state().population() == 8, "second exil : " + diag(camp) + " / " + diag(s));
            p.scanner().refreshNow(camp);
            check(camp.state().beds() == camp.state().population(), camp.state().population() + " lits attendus, comptés " + camp.state().beds());
            check(countAround(w.world(), camp, m -> m == Material.CAMPFIRE) == 1, "un seul feu de camp attendu");
        });
    }

    private static SelftestCase overpopulationGivesContestation() {
        return of("crise : surpopulation (villageois > lits × ratio) → contestation, sans famine", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = village(w, p, 10800, 4, 200, vs);
            CrisisEngine.Indicators i = CrisisEngine.indicators(s.state(), p.simConfig());
            check(i.overpopulation() && !i.famine(), "indicateurs attendus : surpopulation seule, vus " + i);
            int steps = runUntil(p, 200, () -> s.state().level() == CrisisLevel.CONTESTATION);
            check(steps >= 0, "pas de contestation par surpopulation : " + diag(s));
            check(steps * 10 >= p.simConfig().get(SimConfig.Key.CONTEST_AFTER_S) - 10, "contestation trop précoce : " + steps * 10 + " s");
        });
    }

    private static SelftestCase campAvoidsWater() {
        return of("campement : l'emplacement évite l'eau (candidat noyé écarté)", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = village(w, p, 11200, 2, 0, vs);
            int dist = (int) p.simConfig().get(SimConfig.Key.CAMP_DISTANCE);
            CampBuilder camps = p.crises().camps();
            Location first = camps.findSite(w.world(), s, dist);
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -15; dz <= 14; dz++) {
                    w.setBlock(first.clone().add(dx, -1, dz), Material.WATER);
                }
            }
            Location second = camps.findSite(w.world(), s, dist);
            check(first.distance(second) > 20, "le camp reste sur l'eau : " + first + " → " + second);
            Block ground = second.clone().add(0, -1, 0).getBlock();
            check(ground.getType().isSolid(), "sol du campement non solide : " + ground.getType());
        });
    }
}
