package rrp.bukkit.behaviors;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import rrp.core.Role;

import java.util.List;

/** Abat des troncs proches (par le haut du tronc), replante une pousse au pied et dépose le bois. */
public final class Forester extends RoleBehavior {

    /** Troncs coupés au plus à chaque action. */
    static final int MAX_PER_RUN = 4;

    @Override
    public Role role() {
        return Role.FORESTER;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        if (!ctx.site().hasWarehouse()) {
            return false;
        }
        int done = 0;
        for (int i = 0; i < MAX_PER_RUN; i++) {
            List<Block> logs = nearby(ctx, 2, 12, b -> isLog(b) && isTreeTrunk(b));
            if (logs.isEmpty()) {
                break;
            }
            Block log = logs.get(0);
            while (isLog(log.getRelative(0, 1, 0))) {
                log = log.getRelative(0, 1, 0);
            }
            if (done == 0) {
                walkToward(ctx, log);
            }
            Material kind = log.getType();
            for (ItemStack drop : log.getDrops()) {
                store(ctx, drop, log.getLocation());
            }
            log.setType(Material.AIR, false);
            if (Tag.DIRT.isTagged(log.getRelative(0, -1, 0).getType())) {
                log.setType(sapling(kind), false);
            }
            done++;
        }
        return done > 0;
    }

    /**
     * Vrai si le tronc appartient à un arbre : sa colonne porte du feuillage à son sommet. Les piliers
     * des huttes et les cabanes (bois sans feuilles) ne sont jamais abattus.
     */
    static boolean isTreeTrunk(Block b) {
        Block top = b;
        while (isLog(top.getRelative(0, 1, 0))) {
            top = top.getRelative(0, 1, 0);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Tag.LEAVES.isTagged(top.getRelative(dx, dy, dz).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean isLog(Block b) {
        String n = b.getType().name();
        return n.endsWith("_LOG") && !n.startsWith("STRIPPED_");
    }

    /** Pousse correspondant à l'essence du tronc (chêne par défaut). */
    static Material sapling(Material log) {
        Material m = Material.matchMaterial(log.name().replace("_LOG", "_SAPLING"));
        return m != null && Tag.SAPLINGS.isTagged(m) ? m : Material.OAK_SAPLING;
    }
}
