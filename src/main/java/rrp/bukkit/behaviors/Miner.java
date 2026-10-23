package rrp.bukkit.behaviors;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import rrp.core.Role;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Casse minerais (en priorité) et pierre naturelle proches de son poste, et dépose le butin. */
public final class Miner extends RoleBehavior {

    private static final Set<Material> NATURAL_STONE = EnumSet.of(Material.STONE, Material.DEEPSLATE,
            Material.ANDESITE, Material.DIORITE, Material.GRANITE);
    private static final ItemStack PICKAXE = new ItemStack(Material.IRON_PICKAXE);
    /** Blocs cassés au plus à chaque action. */
    static final int MAX_PER_RUN = 3;

    @Override
    public Role role() {
        return Role.MINER;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        if (!ctx.site().hasWarehouse()) {
            return false;
        }
        List<Block> targets = new ArrayList<>(nearby(ctx, 6, 3, Miner::isOre));
        targets.addAll(nearby(ctx, 6, 3, b -> NATURAL_STONE.contains(b.getType())));
        int done = 0;
        for (Block b : targets) {
            if (done >= MAX_PER_RUN) {
                break;
            }
            if (done == 0) {
                walkToward(ctx, b);
            }
            for (ItemStack drop : b.getDrops(PICKAXE)) {
                store(ctx, drop, b.getLocation());
            }
            b.setType(Material.AIR, false);
            done++;
        }
        return done > 0;
    }

    static boolean isOre(Block b) {
        return b.getType().name().endsWith("_ORE");
    }
}
