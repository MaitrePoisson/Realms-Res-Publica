package rrp.bukkit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Villager;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

import static rrp.bukkit.TestWorld.check;

/** Cas de selftest du socle (M8) : plugin chargé, minuteurs, aides de test. */
final class BaseCases {

    private BaseCases() {
    }

    static List<SelftestCase> all() {
        return List.of(pluginLoaded(), tickAdvancesTimers(), worldHelpers());
    }

    private static SelftestCase pluginLoaded() {
        return new SelftestCase() {
            public String name() {
                return "plugin chargé";
            }

            public void run(TestWorld w) {
                check(w.plugin().isEnabled(), "le plugin n'est pas activé");
                check(w.plugin().getCommand("respublica") != null, "commande /respublica absente");
                check(w.plugin().getConfig().contains("village_radius"), "config.yml non chargé");
            }
        };
    }

    private static SelftestCase tickAdvancesTimers() {
        return new SelftestCase() {
            public String name() {
                return "tick avance les minuteurs";
            }

            public void run(TestWorld w) {
                AtomicLong seen = new AtomicLong();
                LongConsumer l = seen::addAndGet;
                w.ticks().register(l);
                try {
                    long before = w.ticks().total();
                    w.ticks().advance(100);
                    check(seen.get() >= 100, "le minuteur n'a vu que " + seen.get() + " ticks");
                    check(w.ticks().total() - before >= 100, "le total n'a pas avancé de 100");
                } finally {
                    w.ticks().unregister(l);
                }
            }
        };
    }

    private static SelftestCase worldHelpers() {
        return new SelftestCase() {
            public String name() {
                return "aides de test du monde (villageois, lit, tonneau)";
            }

            public void run(TestWorld w) {
                try {
                    Location g = w.ground(300, 300);
                    check(g.getBlock().getType() == Material.AIR, "le sol de test n'est pas libre");
                    check(g.clone().add(0, -1, 0).getBlock().getType().isSolid(), "pas de sol sous la position");
                    Block barrel = w.placeBarrel(g.clone().add(2, 0, 0));
                    check(barrel.getType() == Material.BARREL, "tonneau non posé");
                    Block foot = w.placeBed(g.clone().add(0, 0, 2), BlockFace.SOUTH);
                    check(foot.getBlockData() instanceof Bed bed && bed.getPart() == Bed.Part.FOOT, "pied de lit absent");
                    check(foot.getRelative(BlockFace.SOUTH).getBlockData() instanceof Bed h && h.getPart() == Bed.Part.HEAD,
                            "tête de lit absente");
                    Villager v = w.spawnVillager(g);
                    check(v.isValid(), "villageois non apparu");
                    Location dest = w.ground(310, 300);
                    check(w.teleport(v, dest), "téléportation refusée");
                    check(v.getLocation().distance(dest) < 1.5, "villageois pas arrivé : " + v.getLocation());
                } finally {
                    w.cleanup();
                }
                Location after = w.ground(302, 300);
                check(after.getBlock().getType() == Material.AIR, "tonneau non retiré par cleanup");
            }
        };
    }
}
