package rrp.bukkit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.VillagerState;

import java.util.ArrayList;
import java.util.List;

import static rrp.bukkit.TestWorld.check;

import org.bukkit.World;
import org.bukkit.entity.Entity;

/** Cas de selftest de M14 : naissances liées au stock réel du tonneau et aux lits réels. */
final class BirthCases {

    private BirthCases() {
    }

    /** Centres des villages de test du cas en cours : leurs bébés (non suivis par TestWorld) sont retirés ensuite. */
    private static final List<Location> USED = new ArrayList<>();

    private static void clearBabies(World world, Location around) {
        for (Entity e : world.getNearbyEntities(around, 70, 128, 70, x -> x instanceof Villager v && !v.isAdult())) {
            e.remove();
        }
    }

    /** Comme {@link VillageCases#of}, mais retire avant et après les bébés nés dans les villages du cas. */
    private static SelftestCase of(String name, VillageCases.Body body) {
        return VillageCases.of(name, (w, p) -> {
            USED.clear();
            try {
                body.run(w, p);
            } finally {
                USED.forEach(l -> clearBabies(w.world(), l));
                USED.clear();
            }
        });
    }

    static List<SelftestCase> all() {
        return List.of(birthConsumesRealFood(), noBirthWithoutFood(), noBirthWithoutFreeBed(),
                babyReservesItsBed(), babyGetsRoleWhenGrown(), birthsFollowAcceleratedTime());
    }

    /** Ticks d'un intervalle de naissance complet. */
    private static long interval(RrpPlugin p) {
        return (long) (p.simConfig().get(SimConfig.Key.BIRTH_INTERVAL_S) * 20) + 20;
    }

    /** Village de deux adultes, un tonneau-entrepôt, {@code beds} lits déclarés, {@code food} pains. */
    private static VillageSite village(TestWorld w, RrpPlugin p, int x, int beds, int food) {
        w.prepare(x, x, 4);
        List<Villager> vs = new ArrayList<>();
        VillageSite s = RoleCases.villageOf(w, p, x, x, 2, vs);
        Location g = s.center(w.world());
        clearBabies(w.world(), g);
        USED.add(g);
        Block barrel = w.placeBarrel(g.clone().add(-3, 0, 0));
        for (int i = 1; i < beds; i++) { // lits réels, pour que le scanner compte comme nous
            w.placeBed(g.clone().add(2 + 2 * i, 0, 0), org.bukkit.block.BlockFace.SOUTH);
        }
        p.warehouse().set(s, barrel);
        if (food > 0) {
            p.warehouse().deposit(s, w.world(), new ItemStack(Material.BREAD, food));
        }
        p.warehouse().refresh(s, w.world());
        s.state().setBeds(beds);
        for (VillagerState st : s.state().villagers()) {
            st.setSatiety(100);
            st.setMorale(80);
        }
        return s;
    }

    private static String diag(RrpPlugin p, TestWorld w, VillageSite s) {
        return "stock=" + s.state().stock(Resource.FOOD) + " lits=" + s.state().beds() + " pop=" + s.state().population()
                + " bébés=" + p.births().countBabies(s, w.world()) + " minuteur=" + s.state().birthTimer();
    }

    private static int barrelFood(RrpPlugin p, TestWorld w, VillageSite s) {
        return p.warehouse().read(s, w.world()).getOrDefault(Resource.FOOD, 0);
    }

    private static SelftestCase birthConsumesRealFood() {
        return of("naissance : avec nourriture et lit libre, un bébé apparaît et le tonneau est entamé", (w, p) -> {
            VillageSite s = village(w, p, 6400, 3, 20);
            int cost = p.simConfig().getInt(SimConfig.Key.BIRTH_FOOD_COST);
            check(p.births().stepSite(s, w.world(), interval(p)) == 1, "une naissance était attendue : " + diag(p, w, s));
            check(p.births().countBabies(s, w.world()) == 1, "aucun bébé dans le monde");
            check(barrelFood(p, w, s) == 20 - cost, "tonneau réel : " + barrelFood(p, w, s) + " au lieu de " + (20 - cost));
            check(s.state().stock(Resource.FOOD) == 20 - cost, "stock de l'état divergent");
        });
    }

    private static SelftestCase noBirthWithoutFood() {
        return of("naissance : sans nourriture suffisante, rien sur une longue durée accélérée", (w, p) -> {
            VillageSite none = village(w, p, 6600, 4, 0);
            check(p.births().stepSite(none, w.world(), interval(p) * 5) == 0, "naissance sans nourriture");
            check(p.births().countBabies(none, w.world()) == 0, "bébé sans nourriture");
            VillageSite low = village(w, p, 6800, 4, p.simConfig().getInt(SimConfig.Key.BIRTH_FOOD_THRESHOLD) - 1);
            check(p.births().stepSite(low, w.world(), interval(p) * 5) == 0, "naissance sous le seuil de nourriture");
        });
    }

    private static SelftestCase noBirthWithoutFreeBed() {
        return of("naissance : sans lit libre, rien même avec beaucoup de nourriture", (w, p) -> {
            VillageSite s = village(w, p, 7000, 2, 60);
            check(p.births().stepSite(s, w.world(), interval(p) * 5) == 0, "naissance sans lit libre");
            check(p.births().countBabies(s, w.world()) == 0, "bébé sans lit libre");
            check(barrelFood(p, w, s) == 60, "nourriture consommée sans naissance");
        });
    }

    private static SelftestCase babyReservesItsBed() {
        return of("naissance : un bébé réserve son lit, un seul lit libre = un seul bébé", (w, p) -> {
            VillageSite s = village(w, p, 7200, 3, 60);
            check(p.births().stepSite(s, w.world(), interval(p) * 5) == 1, "exactement une naissance attendue");
            check(p.births().countBabies(s, w.world()) == 1, "un seul bébé attendu");
        });
    }

    private static SelftestCase babyGetsRoleWhenGrown() {
        return of("naissance : à maturité le bébé reçoit rôle et jauges", (w, p) -> {
            VillageSite s = village(w, p, 8000, 3, 20);
            check(p.births().stepSite(s, w.world(), interval(p)) == 1, "une naissance était attendue : " + diag(p, w, s));
            Villager baby = w.world().getNearbyEntities(s.center(w.world()), 8, 8, 8, e -> e instanceof Villager v && !v.isAdult())
                    .stream().map(e -> (Villager) e).findFirst().orElseThrow();
            check(p.adapter().sync(s, w.world()) == 0 && s.state().population() == 2, "un bébé a été adopté trop tôt");
            check(p.adapter().read(baby) == null, "le bébé a un rôle avant maturité");
            baby.setAge(0);
            check(baby.isAdult(), "setAge(0) devrait rendre adulte");
            check(p.adapter().sync(s, w.world()) == 1, "le jeune adulte n'a pas été adopté");
            VillagerState st = p.adapter().read(baby);
            check(st != null && st.morale() > 0 && st.satiety() > 0, "rôle ou jauges absents à maturité");
            check(s.state().population() == 3, "population " + s.state().population());
            baby.remove(); // devenu adulte, il n'est plus retiré par clearBabies
        });
    }

    private static SelftestCase birthsFollowAcceleratedTime() {
        return of("naissance : le temps accéléré (/respublica tick) déclenche la naissance", (w, p) -> {
            VillageSite s = village(w, p, 7600, 3, 40);
            long before = p.births().births();
            p.ticks().advance(interval(p));
            check(p.births().births() > before, "aucune naissance après le temps accéléré");
            check(p.births().countBabies(s, w.world()) >= 1, "pas de bébé dans le monde");
        });
    }
}
