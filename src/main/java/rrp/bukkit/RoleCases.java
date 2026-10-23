package rrp.bukkit;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.inventory.ItemStack;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.VillagerState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static rrp.bukkit.TestWorld.check;
import static rrp.bukkit.VillageCases.of;

/** Cas de selftest de M10 : adoption, rôles, jauges réelles, relecture du PDC, commandes. */
final class RoleCases {

    private RoleCases() {
    }

    static List<SelftestCase> all() {
        return List.of(adoptionGivesRolesAndGauges(), rolesSurviveRestart(), vanillaBirthCancelled(),
                gaugesFeedFromRealBarrel(), gaugesFallWithoutFood(), threatsAreCounted(), inspectAndSetRoleCommands());
    }

    /** Village de {@code n} villageois sans IA autour de (x, z), avec un lit. */
    private static VillageSite villageOf(TestWorld w, RrpPlugin p, int x, int z, int n, List<Villager> out) {
        Location g = w.ground(x, z);
        VillageSite s = p.registry().create(g, 64);
        w.placeBed(g.clone().add(2, 0, 0), org.bukkit.block.BlockFace.SOUTH);
        for (int i = 0; i < n; i++) {
            out.add(w.spawnVillager(g.clone().add(i % 4, 0, 5 + i / 4)));
        }
        p.adapter().sync(s, w.world());
        return s;
    }

    private static Map<Role, Integer> roles(VillageSite s) {
        Map<Role, Integer> out = new EnumMap<>(Role.class);
        for (VillagerState v : s.state().villagers()) {
            out.merge(v.role(), 1, Integer::sum);
        }
        return out;
    }

    private static SelftestCase adoptionGivesRolesAndGauges() {
        return of("adoption : rôle, jauges et nom dans le PDC de chaque villageois", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = villageOf(w, p, 2800, 2800, 7, vs);
            check(s.state().population() == 7, "7 villageois attendus, vus " + s.state().population());
            Map<Role, Integer> r = roles(s);
            check(r.getOrDefault(Role.GUARD, 0) >= 1 && r.getOrDefault(Role.STEWARD, 0) >= 1
                    && r.getOrDefault(Role.DIPLOMAT, 0) >= 1, "rôles essentiels manquants : " + r);
            for (Villager v : vs) {
                VillagerState st = p.adapter().read(v);
                check(st != null, "villageois sans rôle dans le PDC");
                check(st.morale() >= 0 && st.morale() <= 100 && st.satiety() >= 0 && st.loyalty() <= 100, "jauges hors bornes");
                String name = v.customName() == null ? "" : PlainTextComponentSerializer.plainText().serialize(v.customName());
                check(name.equals(st.role().label()), "nom affiché « " + name + " » au lieu de " + st.role().label());
            }
            // Une deuxième passe n'adopte personne de nouveau et ne change aucun rôle.
            Map<Role, Integer> before = roles(s);
            check(p.adapter().sync(s, w.world()) == 0, "re-adoption inattendue");
            check(roles(s).equals(before), "les rôles ont changé à la deuxième passe");
        });
    }

    private static SelftestCase rolesSurviveRestart() {
        return of("redémarrage : rôles et jauges relus depuis le PDC", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = villageOf(w, p, 3000, 3000, 6, vs);
            Map<UUID, Role> roleOf = new HashMap<>();
            VillagerState first = s.state().villagers().get(0);
            first.setSatiety(33);
            first.setMorale(41);
            first.setLoyalty(52);
            for (VillagerState v : s.state().villagers()) {
                roleOf.put(v.id(), v.role());
            }
            check(p.adapter().save(s) == 6, "toutes les jauges auraient dû être écrites");
            s.state().villagers().clear(); // état en mémoire perdu, comme après un redémarrage
            check(p.adapter().sync(s, w.world()) == 0, "des villageois ont été ré-adoptés au lieu d'être relus");
            check(s.state().population() == 6, "population relue " + s.state().population());
            for (VillagerState v : s.state().villagers()) {
                check(roleOf.get(v.id()) == v.role(), "rôle changé après relecture");
            }
            VillagerState again = s.state().villagers().stream().filter(v -> v.id().equals(first.id())).findFirst().orElseThrow();
            check(Math.round(again.satiety()) == 33 && Math.round(again.morale()) == 41 && Math.round(again.loyalty()) == 52,
                    "jauges relues incorrectes : " + again.satiety() + "/" + again.morale() + "/" + again.loyalty());
        });
    }

    private static SelftestCase vanillaBirthCancelled() {
        return of("naissance vanilla : EntityBreedEvent d'un villageois est annulé", (w, p) -> {
            Location g = w.ground(3200, 3200);
            Villager a = w.spawnVillager(g);
            Villager b = w.spawnVillager(g.clone().add(1, 0, 0));
            Villager child = w.spawnVillager(g.clone().add(2, 0, 0));
            EntityBreedEvent e = new EntityBreedEvent(child, a, b, null, null, 0);
            org.bukkit.Bukkit.getPluginManager().callEvent(e);
            check(e.isCancelled(), "la naissance vanilla n'a pas été annulée");
        });
    }

    private static SelftestCase gaugesFeedFromRealBarrel() {
        return of("jauges : un villageois affamé mange le vrai stock du tonneau", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = villageOf(w, p, 3400, 3400, 3, vs);
            Block barrel = w.placeBarrel(s.center(w.world()).add(-3, 0, 0));
            p.warehouse().set(s, barrel);
            ((Barrel) barrel.getState(false)).getInventory().addItem(new ItemStack(Material.BREAD, 20));
            p.warehouse().refresh(s, w.world());
            VillagerState hungry = s.state().villagers().get(0);
            hungry.setSatiety(10);
            p.ticks().advance(200);
            check(hungry.satiety() > 10, "la satiété n'a pas remonté : " + hungry.satiety());
            p.warehouse().refresh(s, w.world());
            int left = s.state().stock(Resource.FOOD);
            check(left < 20, "le tonneau n'a pas été entamé : " + left);
            check(left == countBread(barrel), "stock en mémoire et tonneau réel divergent");
        });
    }

    private static int countBread(Block barrel) {
        int n = 0;
        for (ItemStack it : ((Barrel) barrel.getState(false)).getInventory().getContents()) {
            if (it != null && it.getType() == Material.BREAD) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private static SelftestCase gaugesFallWithoutFood() {
        return of("jauges : sans nourriture, satiété et moral baissent", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = villageOf(w, p, 3600, 3600, 3, vs);
            VillagerState v = s.state().villagers().get(0);
            v.setSatiety(60);
            v.setMorale(90);
            long steps = p.gauges().steps();
            p.ticks().advance(6000);
            check(p.gauges().steps() > steps, "le ticker de jauges n'a pas tourné");
            check(v.satiety() < 60, "la satiété n'a pas baissé : " + v.satiety());
            check(v.morale() < 90, "le moral n'a pas baissé : " + v.morale());
            p.adapter().save(s);
            VillagerState stored = p.adapter().read(vs.stream().filter(x -> x.getUniqueId().equals(v.id())).findFirst().orElseThrow());
            check(Math.abs(stored.satiety() - v.satiety()) < 0.01, "PDC non à jour après écriture");
        });
    }

    private static SelftestCase threatsAreCounted() {
        return of("menaces : les monstres hostiles du rayon sont comptés", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = villageOf(w, p, 3800, 3800, 2, vs);
            check(p.gauges().countThreats(s, w.world()) == 0, "menace fantôme");
            Creeper c = w.spawn(s.center(w.world()).add(6, 0, 6), Creeper.class);
            c.setAI(false);
            check(p.gauges().countThreats(s, w.world()) == 1, "le creeper n'est pas compté");
            p.gauges().stepSite(s, w.world(), 20, true);
            check(s.state().threats() == 1, "menaces de l'état : " + s.state().threats());
            Creeper far = w.spawn(s.center(w.world()).add(150, 0, 0), Creeper.class);
            far.setAI(false);
            check(p.gauges().countThreats(s, w.world()) == 1, "un monstre hors rayon est compté");
        });
    }

    private static SelftestCase inspectAndSetRoleCommands() {
        return of("commandes : inspect affiche rôle et jauges, setrole change le rôle", (w, p) -> {
            List<Villager> vs = new ArrayList<>();
            VillageSite s = villageOf(w, p, 4000, 4000, 3, vs);
            Villager v = vs.get(0);
            List<String> lines = new ArrayList<>();
            Commands c = new Commands(p);
            var cmd = p.getCommand("respublica");
            c.onCommand(VillageCases.console(lines), cmd, "respublica", new String[] {"setrole", "batisseur", v.getUniqueId().toString()});
            check(p.adapter().read(v).role() == Role.BUILDER, "setrole n'a pas changé le rôle");
            String name = PlainTextComponentSerializer.plainText().serialize(v.customName());
            check(name.equals("Bâtisseur"), "nom affiché non mis à jour : " + name);
            lines.clear();
            c.onCommand(VillageCases.console(lines), cmd, "respublica", new String[] {"inspect", v.getUniqueId().toString()});
            check(lines.stream().anyMatch(l -> l.contains("Bâtisseur") && l.contains("satiété") && l.contains("moral") && l.contains("loyauté")),
                    "inspect incomplet : " + lines);
            check(lines.stream().noneMatch(l -> l.contains("?")), "clé de langue manquante : " + lines);
            check(s.state().villagers().stream().anyMatch(x -> x.id().equals(v.getUniqueId()) && x.role() == Role.BUILDER),
                    "l'état en mémoire n'a pas suivi");
        });
    }
}
