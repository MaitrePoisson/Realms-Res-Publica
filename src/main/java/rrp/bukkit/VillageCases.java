package rrp.bukkit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import rrp.core.Faction;
import rrp.core.Resource;
import rrp.core.SimConfig;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static rrp.bukkit.TestWorld.check;

/** Cas de selftest de M9 : villages, entrepôt, lits réels, budget de scan, persistance. */
final class VillageCases {

    private VillageCases() {
    }

    private interface Body {
        void run(TestWorld w, RrpPlugin p) throws Exception;
    }

    /** Cas nommé ; les villages créés pendant le cas sont retirés du registre ensuite. */
    private static SelftestCase of(String name, Body body) {
        return new SelftestCase() {
            public String name() {
                return name;
            }

            public void run(TestWorld w) throws Exception {
                RrpPlugin p = (RrpPlugin) w.plugin();
                Set<String> before = new HashSet<>();
                p.registry().all().forEach(s -> before.add(s.id()));
                try {
                    body.run(w, p);
                } finally {
                    for (VillageSite s : new ArrayList<>(p.registry().all())) {
                        if (!before.contains(s.id())) {
                            p.registry().remove(s.id());
                        }
                    }
                }
            }
        };
    }

    static List<SelftestCase> all() {
        return List.of(warehouseReadsRealContents(), bedsAreCounted(), villageCreatedFromVillagersAndBed(),
                noVillageWithoutBed(), nearbyVillagesMerge(), scanBudgetRespected(), persistenceRoundTrip(),
                persistenceBatchesWrites(), commandsReportVillages());
    }

    private static SelftestCase warehouseReadsRealContents() {
        return of("entrepôt : le stock lu est le contenu réel du tonneau", (w, p) -> {
            Location g = w.ground(400, 400);
            Block barrel = w.placeBarrel(g);
            VillageSite s = p.registry().create(g, 64);
            check(p.warehouse().set(s, barrel), "tonneau refusé comme entrepôt");
            check(!p.warehouse().set(s, g.clone().add(0, -1, 0).getBlock()), "un bloc non tonneau a été accepté");
            check(p.warehouse().refresh(s, w.world()), "stock illisible");
            check(s.state().stock(Resource.FOOD) == 0, "stock initial non nul");
            ((Barrel) barrel.getState(false)).getInventory().addItem(
                    new ItemStack(Material.BREAD, 5), new ItemStack(Material.CARROT, 3), new ItemStack(Material.OAK_LOG, 7),
                    new ItemStack(Material.COBBLESTONE, 9), new ItemStack(Material.IRON_INGOT, 2));
            p.warehouse().refresh(s, w.world());
            check(s.state().stock(Resource.FOOD) == 8, "nourriture attendue 8, lue " + s.state().stock(Resource.FOOD));
            check(s.state().stock(Resource.WOOD) == 7, "bois attendu 7, lu " + s.state().stock(Resource.WOOD));
            check(s.state().stock(Resource.STONE) == 9, "pierre attendue 9");
            check(s.state().stock(Resource.ORE) == 2, "minerai attendu 2");
            int taken = p.warehouse().withdraw(s, w.world(), Resource.FOOD, 6);
            check(taken == 6 && s.state().stock(Resource.FOOD) == 2, "retrait de nourriture incorrect : " + taken);
            ItemStack rest = p.warehouse().deposit(s, w.world(), new ItemStack(Material.APPLE, 4));
            check(rest.getType().isAir() && s.state().stock(Resource.FOOD) == 6, "dépôt incorrect");
            // Le contenu vidé à la main se voit au rafraîchissement suivant.
            ((Barrel) barrel.getState(false)).getInventory().clear();
            p.warehouse().refresh(s, w.world());
            check(s.state().stock(Resource.FOOD) == 0, "tonneau vidé mais stock non nul");
        });
    }

    private static SelftestCase bedsAreCounted() {
        return of("lits : le nombre de lits est celui des blocs réels", (w, p) -> {
            Location g = w.ground(500, 500);
            w.prepare(500, 500, 5);
            VillageSite s = p.registry().create(g, 64);
            w.placeBed(g.clone().add(2, 0, 0), BlockFace.SOUTH);
            w.placeBed(g.clone().add(5, 0, 0), BlockFace.SOUTH);
            Block third = w.placeBed(g.clone().add(8, 0, 0), BlockFace.SOUTH);
            p.scanner().refreshNow(s);
            check(s.state().beds() == 3, "3 lits attendus, comptés " + s.state().beds());
            w.placeBed(g.clone().add(120, 0, 0), BlockFace.SOUTH); // hors rayon : ignoré
            third.setType(Material.AIR, false);
            third.getRelative(BlockFace.SOUTH).setType(Material.AIR, false);
            p.scanner().refreshNow(s);
            check(s.state().beds() == 2, "2 lits attendus après retrait, comptés " + s.state().beds());
        });
    }

    private static SelftestCase villageCreatedFromVillagersAndBed() {
        return of("village : créé dès 2 villageois et 1 lit dans un rayon", (w, p) -> {
            Location g = w.ground(600, 600);
            w.prepare(600, 600, 5);
            w.placeBed(g.clone().add(3, 0, 0), BlockFace.SOUTH);
            w.spawnVillager(g);
            w.spawnVillager(g.clone().add(10, 0, 5));
            check(p.registry().find(g) == null, "un village existe déjà ici");
            p.scanner().enqueueAll();
            p.ticks().advance(40);
            VillageSite s = p.registry().find(g);
            check(s != null, "aucun village créé");
            check(s.radius() == p.simConfig().getInt(SimConfig.Key.VILLAGE_RADIUS), "rayon par défaut non appliqué");
            check(s.state().faction() == Faction.LOYALIST, "faction attendue loyaliste");
            p.scanner().refreshNow(s);
            check(s.state().beds() == 1, "1 lit attendu, compté " + s.state().beds());
        });
    }

    private static SelftestCase noVillageWithoutBed() {
        return of("village : pas de création avec un seul villageois ou sans lit", (w, p) -> {
            Location g = w.ground(700, 700);
            w.prepare(700, 700, 5);
            w.spawnVillager(g);
            w.spawnVillager(g.clone().add(4, 0, 4)); // 2 villageois, aucun lit
            Location h = w.ground(900, 900);
            w.prepare(900, 900, 5);
            w.placeBed(h.clone().add(2, 0, 0), BlockFace.SOUTH);
            w.spawnVillager(h); // 1 villageois, un lit
            p.scanner().enqueueAll();
            p.ticks().advance(60);
            check(p.registry().find(g) == null, "village créé sans lit");
            check(p.registry().find(h) == null, "village créé avec un seul villageois");
        });
    }

    private static SelftestCase nearbyVillagesMerge() {
        return of("village : deux villages proches sont fusionnés", (w, p) -> {
            Location g = w.ground(1100, 1100);
            VillageSite a = p.registry().create(g, 64);
            VillageSite b = p.registry().create(g.clone().add(30, 0, 0), 64);
            VillageSite far = p.registry().create(g.clone().add(400, 0, 0), 64);
            Block barrel = w.placeBarrel(g.clone().add(30, 0, 3));
            p.warehouse().set(b, barrel);
            check(p.registry().mergeNearby() == 1, "une fusion attendue");
            check(p.registry().get(a.id()) != null && p.registry().get(b.id()) == null, "le plus ancien doit absorber");
            check(a.hasWarehouse(), "l'entrepôt du village absorbé n'est pas repris");
            check(p.registry().get(far.id()) != null, "le village éloigné a été fusionné");
        });
    }

    private static SelftestCase scanBudgetRespected() {
        return of("scan : jamais plus de max_scan_blocks_per_tick blocs par tick", (w, p) -> {
            Location g = w.ground(1500, 1500);
            w.prepare(1500, 1500, 5);
            for (int i = 0; i < 40; i++) {
                w.placeBed(g.clone().add(i - 20, 0, (i % 3) * 3), BlockFace.SOUTH);
            }
            VillageSite s = p.registry().create(g, 64);
            int budget = p.scanner().budgetPerTick();
            p.scanner().resetStats();
            p.scanner().enqueueAll();
            p.ticks().advance(200);
            check(p.scanner().maxScannedPerTick() > 0, "aucun bloc examiné");
            check(p.scanner().maxScannedPerTick() <= budget,
                    "budget dépassé : " + p.scanner().maxScannedPerTick() + " > " + budget);
            check(p.scanner().pending() == 0, "travail de scan restant");
            check(s.state().beds() == 40, "40 lits attendus, comptés " + s.state().beds());
        });
    }

    private static SelftestCase persistenceRoundTrip() {
        return of("persistance : villages.json relu à l'identique", (w, p) -> {
            Location g = w.ground(1800, 1800);
            Block barrel = w.placeBarrel(g);
            VillageSite s = p.registry().create(g, 48);
            p.warehouse().set(s, barrel);
            s.state().setBeds(4);
            s.state().setStock(Resource.WOOD, 12);
            s.state().setRelations(33);
            s.state().setContestTimer(17.5);
            check(p.persistence().flush(), "écriture impossible");
            File file = new File(p.getDataFolder(), Persistence.FILE);
            check(file.isFile(), "villages.json absent");
            String id = s.id();
            check(p.persistence().load() >= 1, "rien rechargé");
            VillageSite r = p.registry().get(id);
            check(r != null && r != s, "village non rechargé");
            check(r.radius() == 48 && r.state().beds() == 4, "rayon ou lits perdus");
            check(r.state().stock(Resource.WOOD) == 12, "stock perdu");
            check(r.state().relations() == 33 && r.state().contestTimer() == 17.5, "relations ou minuteur perdus");
            int[] wh = r.warehouse();
            check(wh != null && wh[0] == barrel.getX() && wh[1] == barrel.getY() && wh[2] == barrel.getZ(), "entrepôt perdu");
            check(Math.abs(r.x() - g.getX()) < 1e-6 && r.worldName().equals(w.world().getName()), "position perdue");
            VillageSite next = p.registry().create(g, 64);
            check(!next.id().equals(id), "collision d'identifiant après rechargement");
            check(Files.readString(file.toPath()).contains(id), "id absent du fichier");
        });
    }

    private static SelftestCase persistenceBatchesWrites() {
        return of("persistance : écritures groupées (tampon + intervalle)", (w, p) -> {
            Location g = w.ground(2100, 2100);
            p.persistence().flush();
            int writes = p.persistence().writes();
            for (int i = 0; i < 5; i++) {
                p.registry().create(g.clone().add(i * 300, 0, 0), 32);
            }
            p.persistence().onTicks(20, 60);
            check(p.persistence().writes() == writes, "écriture avant l'intervalle");
            p.persistence().onTicks(60 * 20, 60);
            check(p.persistence().writes() == writes + 1, "une seule écriture groupée attendue");
            p.persistence().onTicks(10 * 60 * 20, 60);
            check(p.persistence().writes() == writes + 1, "écriture sans changement");
        });
    }

    private static SelftestCase commandsReportVillages() {
        return of("commandes : village list et status décrivent le village", (w, p) -> {
            Location g = w.ground(2400, 2400);
            VillageSite s = p.registry().create(g, 64);
            s.state().setBeds(2);
            List<String> lines = new ArrayList<>();
            org.bukkit.command.CommandSender console = (org.bukkit.command.CommandSender) java.lang.reflect.Proxy.newProxyInstance(
                    VillageCases.class.getClassLoader(), new Class<?>[] {org.bukkit.command.CommandSender.class},
                    (proxy, m, a) -> {
                        if (m.getName().equals("sendMessage") && a != null && a.length == 1 && a[0] instanceof String t) {
                            lines.add(t);
                        }
                        return m.getName().equals("hasPermission") || m.getName().equals("isOp") ? Boolean.TRUE
                                : m.getReturnType() == boolean.class ? Boolean.FALSE : null;
                    });
            Commands c = new Commands(p);
            c.onCommand(console, p.getCommand("respublica"), "respublica", new String[] {"village", "list"});
            check(lines.stream().anyMatch(l -> l.contains(s.id()) && l.contains("2 lit")), "village absent de la liste : " + lines);
            lines.clear();
            c.onCommand(console, p.getCommand("respublica"), "respublica", new String[] {"status", s.id()});
            check(lines.stream().anyMatch(l -> l.contains("lits : 2")), "lits absents du statut : " + lines);
            check(lines.stream().noneMatch(l -> l.contains("?")), "clé de langue manquante : " + lines);
        });
    }
}
