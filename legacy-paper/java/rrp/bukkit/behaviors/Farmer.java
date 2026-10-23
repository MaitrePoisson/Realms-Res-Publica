package rrp.bukkit.behaviors;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import rrp.core.Role;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Récolte les cultures mûres proches, les replante (âge 0) et dépose la récolte à l'entrepôt. */
public final class Farmer extends RoleBehavior {

    static final Set<Material> CROPS = EnumSet.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);
    /** Cultures récoltées au plus à chaque action. */
    static final int MAX_PER_RUN = 4;

    @Override
    public Role role() {
        return Role.FARMER;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        if (!ctx.site().hasWarehouse()) {
            return false;
        }
        List<Block> ripe = nearby(ctx, 3, 3, Farmer::isRipe);
        int done = 0;
        for (Block crop : ripe) {
            if (done >= MAX_PER_RUN) {
                break;
            }
            if (done == 0) {
                walkToward(ctx, crop);
            }
            for (ItemStack drop : crop.getDrops()) {
                store(ctx, drop, crop.getLocation());
            }
            Ageable data = (Ageable) crop.getBlockData();
            data.setAge(0);
            crop.setBlockData((BlockData) data, false);
            done++;
        }
        return done > 0;
    }

    static boolean isRipe(Block b) {
        if (!CROPS.contains(b.getType())) {
            return false;
        }
        BlockData d = b.getBlockData();
        return d instanceof Ageable a && a.getAge() >= a.getMaximumAge();
    }
}
