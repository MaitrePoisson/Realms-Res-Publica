package rrp.bukkit.behaviors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;

/**
 * Consomme du bois de l'entrepôt pour bâtir une petite hutte (4×3, murs de 2 blocs, toit, porte ouverte
 * à l'ouest) contenant un vrai lit. Il ne bâtit que s'il manque des lits au village.
 */
public final class Builder extends RoleBehavior {

    static final int WIDTH = 4;
    static final int DEPTH = 3;
    /** Hauteur libre exigée au-dessus du sol (murs de 2 + toit). */
    static final int HEIGHT = 3;

    @Override
    public Role role() {
        return Role.BUILDER;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        if (!ctx.site().hasWarehouse() || ctx.site().state().beds() >= ctx.site().state().population()) {
            return false;
        }
        int cost = (int) ctx.config().get(SimConfig.Key.HUT_WOOD);
        if (ctx.warehouse().read(ctx.site(), ctx.world()).getOrDefault(Resource.WOOD, 0) < cost) {
            return false;
        }
        Block origin = findSpot(ctx);
        if (origin == null) {
            return false;
        }
        if (ctx.warehouse().withdraw(ctx.site(), ctx.world(), Resource.WOOD, cost) < cost) {
            return false;
        }
        walkToward(ctx, origin);
        build(ctx.world(), origin);
        ctx.site().state().setBeds(ctx.site().state().beds() + 1);
        ctx.registry().markDirty();
        return true;
    }

    /** Pose la hutte dont l'angle ouest-nord est {@code origin} (premier bloc libre au-dessus du sol). */
    static void build(World world, Block origin) {
        int x0 = origin.getX();
        int y = origin.getY();
        int z0 = origin.getZ();
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                boolean perimeter = dx == 0 || dz == 0 || dx == WIDTH - 1 || dz == DEPTH - 1;
                boolean door = dx == 0 && dz == DEPTH / 2;
                if (perimeter && !door) {
                    world.getBlockAt(x0 + dx, y, z0 + dz).setType(Material.OAK_PLANKS, false);
                    world.getBlockAt(x0 + dx, y + 1, z0 + dz).setType(Material.OAK_PLANKS, false);
                }
                world.getBlockAt(x0 + dx, y + 2, z0 + dz).setType(Material.OAK_PLANKS, false);
            }
        }
        placeBed(world.getBlockAt(x0 + 1, y, z0 + DEPTH / 2), BlockFace.EAST);
    }

    private static void placeBed(Block foot, BlockFace facing) {
        Bed data = (Bed) Bukkit.createBlockData(Material.RED_BED);
        data.setFacing(facing);
        data.setPart(Bed.Part.FOOT);
        foot.setBlockData(data, false);
        Bed head = (Bed) data.clone();
        head.setPart(Bed.Part.HEAD);
        foot.getRelative(facing).setBlockData(head, false);
    }

    /** Emplacement plat et libre le plus proche du bâtisseur, dans son rayon de travail et dans le village. */
    private static Block findSpot(BehaviorContext ctx) {
        Location at = ctx.villager().getLocation();
        int cx = at.getBlockX();
        int cy = at.getBlockY();
        int cz = at.getBlockZ();
        int r = ctx.workRadius();
        for (int d = 3; d <= r; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) {
                        continue;
                    }
                    Block o = ctx.world().getBlockAt(cx + dx, cy, cz + dz);
                    if (fits(ctx, o)) {
                        return o;
                    }
                }
            }
        }
        return null;
    }

    private static boolean fits(BehaviorContext ctx, Block o) {
        World w = ctx.world();
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                int x = o.getX() + dx;
                int z = o.getZ() + dz;
                if (!w.isChunkLoaded(x >> 4, z >> 4) || !ctx.site().contains(w.getName(), x + 0.5, z + 0.5)) {
                    return false;
                }
                Block ground = w.getBlockAt(x, o.getY() - 1, z);
                if (!ground.getType().isSolid()) {
                    return false;
                }
                for (int dy = 0; dy < HEIGHT; dy++) {
                    Block b = w.getBlockAt(x, o.getY() + dy, z);
                    if (!b.getType().isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
