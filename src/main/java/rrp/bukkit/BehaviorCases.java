package rrp.bukkit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.World;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.VillagerState;

import java.util.ArrayList;
import java.util.List;

import static rrp.bukkit.TestWorld.check;
import static rrp.bukkit.VillageCases.of;

/** Cas de selftest de M11 : Fermier, Forestier, Mineur agissent vraiment sur les blocs et le tonneau. */
final class BehaviorCases {

    private BehaviorCases() {
    }

    static List<SelftestCase> all() {
        return List.of(farmerHarvestsAndReplants(), foresterFellsAndReplants(), minerBreaksOreAndStone(),
                lowMoraleMeansNoAction(), actionsStayInWorkRadius(), actionsFollowCadence(),
                stewardFeedsHungryFromBarrel(), stewardWithoutFoodOrMorale(), guardKillsNearbyMonster(),
                guardIgnoresFarMonsterAndDisobeys(), builderRaisesHutWithBed(), builderNeedsWoodAndBeds(),
                diplomatVisitsNeighbour(), diplomatTalksWithoutNeighbour());
    }

    /** Un village de trois villageois dont le premier a le rôle testé, avec un tonneau-entrepôt vide. */
    private record Rig(VillageSite site, Villager worker, Block barrel, Location ground) {
        Location at(int dx, int dz) {
            return ground.clone().add(dx, 0, dz);
        }
    }

    private static Rig rig(TestWorld w, RrpPlugin p, int x, int z, Role role) {
        w.prepare(x, z, 4);
        List<Villager> vs = new ArrayList<>();
        VillageSite s = RoleCases.villageOf(w, p, x, z, 3, vs);
        Location g = s.center(w.world());
        Block barrel = w.placeBarrel(g.clone().add(-3, 0, 0));
        p.warehouse().set(s, barrel);
        p.warehouse().refresh(s, w.world());
        for (Villager v : vs) {
            p.adapter().setRole(v, Role.DIPLOMAT);
        }
        p.adapter().setRole(vs.get(0), role);
        for (VillagerState st : s.state().villagers()) {
            st.setSatiety(100);
            st.setMorale(80);
        }
        p.behaviors().resetClock();
        return new Rig(s, vs.get(0), barrel, g);
    }

    private static int stock(Rig r, RrpPlugin p, World world, Resource res) {
        return p.warehouse().read(r.site(), world).getOrDefault(res, 0);
    }

    private static Block crop(TestWorld w, Location at, Material type, int age) {
        w.setBlock(at.clone().add(0, -1, 0), Material.FARMLAND);
        Block b = w.setBlock(at, type);
        Ageable data = (Ageable) b.getBlockData();
        data.setAge(Math.min(age, data.getMaximumAge()));
        b.setBlockData(data, false);
        return b;
    }

    private static SelftestCase farmerHarvestsAndReplants() {
        return of("fermier : récolte la culture mûre, replante, le tonneau gagne de la nourriture", (w, p) -> {
            Rig r = rig(w, p, 4400, 4400, Role.FARMER);
            Block ripe1 = crop(w, r.at(0, 8), Material.WHEAT, 99);
            Block ripe2 = crop(w, r.at(2, 8), Material.CARROTS, 99);
            Block young = crop(w, r.at(4, 8), Material.WHEAT, 3);
            check(stock(r, p, w.world(), Resource.FOOD) == 0, "le tonneau devrait être vide");
            check(p.behaviors().runOnce(r.site(), w.world(), r.worker()), "le fermier n'a rien fait");
            check(stock(r, p, w.world(), Resource.FOOD) >= 2, "nourriture au tonneau : " + stock(r, p, w.world(), Resource.FOOD));
            check(r.site().state().stock(Resource.FOOD) >= 2, "stock de l'état non mis à jour");
            check(((Ageable) ripe1.getBlockData()).getAge() == 0 && ripe1.getType() == Material.WHEAT, "blé non replanté");
            check(((Ageable) ripe2.getBlockData()).getAge() == 0 && ripe2.getType() == Material.CARROTS, "carotte non replantée");
            check(((Ageable) young.getBlockData()).getAge() == 3, "la culture non mûre a été touchée");
            check(!p.behaviors().runOnce(r.site(), w.world(), r.worker()), "rien à récolter : aucune action attendue");
        });
    }

    private static SelftestCase foresterFellsAndReplants() {
        return of("forestier : coupe un tronc, replante une pousse, le tonneau gagne du bois", (w, p) -> {
            Rig r = rig(w, p, 4600, 4600, Role.FORESTER);
            Location base = r.at(3, 8);
            for (int i = 0; i < 3; i++) {
                w.setBlock(base.clone().add(0, i, 0), Material.OAK_LOG);
            }
            check(p.behaviors().runOnce(r.site(), w.world(), r.worker()), "le forestier n'a rien fait");
            check(stock(r, p, w.world(), Resource.WOOD) >= 3, "bois au tonneau : " + stock(r, p, w.world(), Resource.WOOD));
            for (int i = 1; i < 3; i++) {
                check(base.clone().add(0, i, 0).getBlock().getType() == Material.AIR, "tronc resté en l'air à la hauteur " + i);
            }
            check(base.getBlock().getType() == Material.OAK_SAPLING, "pas de pousse : " + base.getBlock().getType());
        });
    }

    private static SelftestCase minerBreaksOreAndStone() {
        return of("mineur : casse un minerai et de la pierre, le tonneau gagne le butin", (w, p) -> {
            Rig r = rig(w, p, 4800, 4800, Role.MINER);
            Block iron = w.setBlock(r.at(2, 8), Material.IRON_ORE);
            Block coal = w.setBlock(r.at(3, 8), Material.COAL_ORE);
            Block stone = w.setBlock(r.at(4, 8), Material.STONE);
            Block bricks = w.setBlock(r.at(6, 8), Material.STONE_BRICKS);
            check(p.behaviors().runOnce(r.site(), w.world(), r.worker()), "le mineur n'a rien fait");
            check(iron.getType() == Material.AIR && coal.getType() == Material.AIR, "minerais non cassés");
            check(stone.getType() == Material.AIR, "pierre non cassée");
            check(bricks.getType() == Material.STONE_BRICKS, "une construction a été minée");
            check(stock(r, p, w.world(), Resource.ORE) >= 2, "minerai au tonneau : " + stock(r, p, w.world(), Resource.ORE));
            check(stock(r, p, w.world(), Resource.STONE) >= 1, "pierre au tonneau : " + stock(r, p, w.world(), Resource.STONE));
        });
    }

    private static SelftestCase lowMoraleMeansNoAction() {
        return of("désobéissance : moral bas, aucune action ; moral rétabli, travail", (w, p) -> {
            Rig r = rig(w, p, 5000, 5000, Role.FARMER);
            Block ripe = crop(w, r.at(0, 8), Material.WHEAT, 99);
            VillagerState st = p.adapter().stateOf(r.worker());
            st.setMorale(5);
            long before = p.behaviors().actions();
            check(!p.behaviors().work(r.site(), w.world(), r.worker()), "un villageois démoralisé a travaillé");
            check(p.behaviors().actions() == before, "une action a été comptée");
            check(((Ageable) ripe.getBlockData()).getAge() == ((Ageable) ripe.getBlockData()).getMaximumAge(), "la culture a été récoltée");
            check(stock(r, p, w.world(), Resource.FOOD) == 0, "le tonneau a reçu quelque chose");
            st.setMorale(80);
            check(p.behaviors().work(r.site(), w.world(), r.worker()), "le villageois remotivé n'a pas travaillé");
            check(stock(r, p, w.world(), Resource.FOOD) >= 1, "pas de récolte après remotivation");
        });
    }

    private static SelftestCase actionsStayInWorkRadius() {
        return of("rayon : une culture hors du rayon de travail n'est pas récoltée", (w, p) -> {
            Rig r = rig(w, p, 5200, 5200, Role.FARMER);
            int radius = (int) p.simConfig().get(rrp.core.SimConfig.Key.WORK_RADIUS);
            Block far = crop(w, r.at(0, 5 + radius + 6), Material.WHEAT, 99);
            check(!p.behaviors().runOnce(r.site(), w.world(), r.worker()), "action hors rayon");
            check(((Ageable) far.getBlockData()).getAge() == ((Ageable) far.getBlockData()).getMaximumAge(), "culture lointaine touchée");
            Block near = crop(w, r.at(0, 5 + radius - 4), Material.WHEAT, 99);
            check(p.behaviors().runOnce(r.site(), w.world(), r.worker()), "culture à portée ignorée");
            check(((Ageable) near.getBlockData()).getAge() == 0, "culture à portée non récoltée");
        });
    }

    private static SelftestCase actionsFollowCadence() {
        return of("cadence : l'action n'a lieu qu'à chaque intervalle de comportement", (w, p) -> {
            Rig r = rig(w, p, 5400, 5400, Role.FARMER);
            crop(w, r.at(0, 8), Material.WHEAT, 99);
            long interval = (long) (p.simConfig().get(rrp.core.SimConfig.Key.BEHAVIOR_INTERVAL_S) * 20);
            p.ticks().advance(interval - 1);
            check(stock(r, p, w.world(), Resource.FOOD) == 0, "action avant l'intervalle");
            p.ticks().advance(1);
            check(stock(r, p, w.world(), Resource.FOOD) >= 1, "pas d'action à l'intervalle");
        });
    }

    private static VillagerState other(Rig r, RrpPlugin p) {
        return r.site().state().villagers().stream().filter(v -> !v.id().equals(r.worker().getUniqueId())).findFirst().orElseThrow();
    }

    private static SelftestCase stewardFeedsHungryFromBarrel() {
        return of("intendant : retire de la nourriture du tonneau, la satiété d'un affamé remonte", (w, p) -> {
            Rig r = rig(w, p, 5600, 5600, Role.STEWARD);
            p.warehouse().deposit(r.site(), w.world(), new ItemStack(Material.BREAD, 10));
            VillagerState hungry = other(r, p);
            hungry.setSatiety(20);
            check(p.behaviors().runOnce(r.site(), w.world(), r.worker()), "l'intendant n'a rien fait");
            check(hungry.satiety() > 20, "satiété non remontée : " + hungry.satiety());
            int left = stock(r, p, w.world(), Resource.FOOD);
            check(left < 10, "le tonneau n'a pas été entamé : " + left);
            check(r.site().state().stock(Resource.FOOD) == left, "stock de l'état et tonneau réel divergent");
            check(!p.behaviors().runOnce(r.site(), w.world(), r.worker()) || hungry.satiety() <= 100, "satiété hors bornes");
        });
    }

    private static SelftestCase stewardWithoutFoodOrMorale() {
        return of("intendant : sans nourriture ou démoralisé, personne n'est servi", (w, p) -> {
            Rig r = rig(w, p, 5800, 5800, Role.STEWARD);
            VillagerState hungry = other(r, p);
            hungry.setSatiety(20);
            check(!p.behaviors().runOnce(r.site(), w.world(), r.worker()), "action sans nourriture");
            check(hungry.satiety() == 20, "satiété changée sans nourriture");
            p.warehouse().deposit(r.site(), w.world(), new ItemStack(Material.BREAD, 10));
            p.adapter().stateOf(r.worker()).setMorale(5);
            check(!p.behaviors().work(r.site(), w.world(), r.worker()), "intendant démoralisé au travail");
            check(hungry.satiety() == 20 && stock(r, p, w.world(), Resource.FOOD) == 10, "nourriture distribuée malgré la désobéissance");
        });
    }

    private static SelftestCase guardKillsNearbyMonster() {
        return of("garde : tue un monstre proche, les menaces du village baissent de 1", (w, p) -> {
            Rig r = rig(w, p, 6000, 6000, Role.GUARD);
            Creeper c = w.spawn(r.at(0, 9), Creeper.class);
            c.setAI(false);
            p.gauges().stepSite(r.site(), w.world(), 20, true);
            check(r.site().state().threats() == 1, "menaces avant : " + r.site().state().threats());
            check(p.behaviors().runOnce(r.site(), w.world(), r.worker()), "le garde n'a rien fait");
            check(c.isDead() || !c.isValid(), "le monstre est toujours vivant");
            check(r.site().state().threats() == 0, "menaces après : " + r.site().state().threats());
            check(!p.behaviors().runOnce(r.site(), w.world(), r.worker()), "action sans monstre");
        });
    }

    private static SelftestCase guardIgnoresFarMonsterAndDisobeys() {
        return of("garde : ignore un monstre hors rayon de travail et ne frappe pas démoralisé", (w, p) -> {
            Rig r = rig(w, p, 6200, 6200, Role.GUARD);
            int radius = (int) p.simConfig().get(rrp.core.SimConfig.Key.WORK_RADIUS);
            Creeper far = w.spawn(r.at(0, 5 + radius + 8), Creeper.class);
            far.setAI(false);
            check(!p.behaviors().runOnce(r.site(), w.world(), r.worker()), "action hors rayon");
            check(far.isValid() && !far.isDead(), "monstre lointain tué");
            Creeper near = w.spawn(r.at(0, 9), Creeper.class);
            near.setAI(false);
            p.adapter().stateOf(r.worker()).setMorale(5);
            check(!p.behaviors().work(r.site(), w.world(), r.worker()), "garde démoralisé au travail");
            check(near.isValid() && !near.isDead(), "monstre tué malgré la désobéissance");
        });
    }
    private static void stockWood(RrpPlugin p, Rig r, World world, int planks) {
        p.warehouse().deposit(r.site(), world, new ItemStack(Material.OAK_PLANKS, planks));
    }

    /** Compte les blocs de ce type et les têtes de lit dans le carré de `radius` autour de `center`. */
    private static int[] countAround(World world, Location center, int radius) {
        int planks = 0;
        int beds = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y <= 3; y++) {
                    Block b = center.clone().add(x, y, z).getBlock();
                    if (b.getType() == Material.OAK_PLANKS) {
                        planks++;
                    } else if (Tag.BEDS.isTagged(b.getType()) && ((Bed) b.getBlockData()).getPart() == Bed.Part.HEAD) {
                        beds++;
                    }
                }
            }
        }
        return new int[] {planks, beds};
    }

    private static SelftestCase builderRaisesHutWithBed() {
        return of("bâtisseur : consomme du bois du tonneau et pose une hutte avec un vrai lit (lits +1)", (w, p) -> {
            Rig r = rig(w, p, 7000, 7000, Role.BUILDER);
            w.rememberArea(r.ground(), 14);
            stockWood(p, r, w.world(), 64);
            int cost = (int) p.simConfig().get(rrp.core.SimConfig.Key.HUT_WOOD);
            r.site().state().setBeds(1);
            int[] before = countAround(w.world(), r.ground(), 14);
            check(before[0] == 0 && before[1] == 1, "décor inattendu : " + before[0] + " planches, " + before[1] + " lits");
            check(p.behaviors().runOnce(r.site(), w.world(), r.worker()), "le bâtisseur n'a rien fait");
            check(stock(r, p, w.world(), Resource.WOOD) == 64 - cost, "bois au tonneau : " + stock(r, p, w.world(), Resource.WOOD));
            check(r.site().state().beds() == 2, "lits de l'état : " + r.site().state().beds());
            int[] after = countAround(w.world(), r.ground(), 14);
            check(after[0] == 30, "planches posées : " + after[0]);
            check(after[1] == 2, "lits réels : " + after[1]);
        });
    }

    private static SelftestCase builderNeedsWoodAndBeds() {
        return of("bâtisseur : ne bâtit ni sans assez de bois, ni sans manque de lits, ni démoralisé", (w, p) -> {
            Rig r = rig(w, p, 7300, 7300, Role.BUILDER);
            w.rememberArea(r.ground(), 14);
            int cost = (int) p.simConfig().get(rrp.core.SimConfig.Key.HUT_WOOD);
            r.site().state().setBeds(1);
            stockWood(p, r, w.world(), cost - 1);
            check(!p.behaviors().runOnce(r.site(), w.world(), r.worker()), "bâti sans assez de bois");
            stockWood(p, r, w.world(), 64);
            int wood = stock(r, p, w.world(), Resource.WOOD);
            r.site().state().setBeds(r.site().state().population());
            check(!p.behaviors().runOnce(r.site(), w.world(), r.worker()), "bâti alors qu'il y a assez de lits");
            r.site().state().setBeds(1);
            p.adapter().stateOf(r.worker()).setMorale(5);
            check(!p.behaviors().work(r.site(), w.world(), r.worker()), "bâtisseur démoralisé au travail");
            check(stock(r, p, w.world(), Resource.WOOD) == wood, "du bois a été consommé sans construire");
            check(countAround(w.world(), r.ground(), 14)[0] == 0, "des planches ont été posées");
        });
    }

    private static SelftestCase diplomatVisitsNeighbour() {
        return of("diplomate : visite le village voisin et les relations montent de relations_per_visit", (w, p) -> {
            Rig r = rig(w, p, 7600, 7600, Role.DIPLOMAT);
            List<Villager> others = new ArrayList<>();
            VillageSite neighbour = RoleCases.villageOf(w, p, 7800, 7600, 3, others);
            double gain = p.simConfig().get(rrp.core.SimConfig.Key.RELATIONS_PER_VISIT);
            check(r.site().state().relations() == 0 && neighbour.state().relations() == 0, "relations de départ non nulles");
            check(p.behaviors().runOnce(r.site(), w.world(), r.worker()), "le diplomate n'a rien fait");
            check(r.site().state().relations() == gain, "relations : " + r.site().state().relations());
            check(neighbour.state().relations() == gain, "relations du voisin : " + neighbour.state().relations());
            p.adapter().stateOf(r.worker()).setMorale(5);
            check(!p.behaviors().work(r.site(), w.world(), r.worker()), "diplomate démoralisé au travail");
            check(r.site().state().relations() == gain, "relations changées malgré la désobéissance");
        });
    }

    private static SelftestCase diplomatTalksWithoutNeighbour() {
        return of("diplomate : sans voisin, parle aux villageois et renforce leur loyauté", (w, p) -> {
            Rig r = rig(w, p, 8000, 8000, Role.DIPLOMAT);
            double bonus = p.simConfig().get(rrp.core.SimConfig.Key.TALK_LOYALTY_BONUS);
            for (VillagerState st : r.site().state().villagers()) {
                st.setLoyalty(50);
            }
            check(p.behaviors().runOnce(r.site(), w.world(), r.worker()), "le diplomate n'a rien fait");
            int talkedTo = 0;
            for (VillagerState st : r.site().state().villagers()) {
                if (st.loyalty() == 50 + bonus) {
                    talkedTo++;
                }
            }
            check(talkedTo == r.site().state().population() - 1, "villageois ayant gagné en loyauté : " + talkedTo);
            check(r.site().state().relations() == 0, "relations modifiées sans voisin");
        });
    }
}
