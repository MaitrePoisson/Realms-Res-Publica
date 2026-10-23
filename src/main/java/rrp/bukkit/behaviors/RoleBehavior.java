package rrp.bukkit.behaviors;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import rrp.bukkit.WarehouseService;
import rrp.core.Gauges;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.VillagerState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Action concrète d'un rôle dans le monde. Une instance sert tous les villageois de ce rôle. */
public abstract class RoleBehavior {

    /** Rôle servi par ce comportement. */
    public abstract Role role();

    /** Exécute une fois l'action, sans tenir compte de la cadence ni du moral. Renvoie vrai si quelque chose a été fait. */
    public abstract boolean runOnce(BehaviorContext ctx);

    /** Désobéissance déterministe : un moral sous {@code disobey_morale} empêche de travailler. */
    public boolean canWork(VillagerState state, SimConfig config) {
        return Gauges.canWork(state, config);
    }

    /**
     * Blocs satisfaisant {@code wanted} dans le rayon de travail (carré horizontal autour du villageois,
     * de {@code below} blocs en dessous à {@code above} au-dessus), du plus proche au plus lointain.
     */
    protected static List<Block> nearby(BehaviorContext ctx, int below, int above, Predicate<Block> wanted) {
        Location at = ctx.villager().getLocation();
        int r = ctx.workRadius();
        int cx = at.getBlockX();
        int cy = at.getBlockY();
        int cz = at.getBlockZ();
        List<Block> found = new ArrayList<>();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (!ctx.world().isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = cy - below; y <= cy + above; y++) {
                    Block b = ctx.world().getBlockAt(x, y, z);
                    if (wanted.test(b)) {
                        found.add(b);
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble((Block b) -> b.getLocation().distanceSquared(at)).thenComparingInt(Block::getY));
        return found;
    }

    /** Dépose un objet compté dans l'entrepôt ; le reste (tonneau plein) tombe au sol à {@code spill}. */
    protected static void store(BehaviorContext ctx, ItemStack stack, Location spill) {
        if (stack == null || stack.getAmount() <= 0 || WarehouseService.classify(stack.getType()) == null) {
            return;
        }
        ItemStack left = ctx.warehouse().deposit(ctx.site(), ctx.world(), stack);
        if (left != null && !left.getType().isAir() && left.getAmount() > 0) {
            ctx.world().dropItemNaturally(spill, left);
        }
    }

    /** Si le villageois a de l'IA, il se dirige vers le bloc (l'action reste immédiate). */
    protected static void walkToward(BehaviorContext ctx, Block target) {
        if (ctx.villager().hasAI()) {
            ctx.villager().getPathfinder().moveTo(target.getLocation().add(0.5, 0, 0.5));
        }
    }
}
