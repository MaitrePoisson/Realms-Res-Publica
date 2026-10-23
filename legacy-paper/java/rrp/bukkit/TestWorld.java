package rrp.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aides des cas de selftest : monde plat, chunks forcés, téléportation, villageois, lit, tonneau.
 * Tout ce qui est posé est mémorisé et retiré par {@link #cleanup()}.
 */
public final class TestWorld {

    private final JavaPlugin plugin;
    private final World world;
    private final TickService ticks;
    private final List<Entity> entities = new ArrayList<>();
    private final Map<Block, BlockData> changed = new LinkedHashMap<>();
    private final List<Chunk> forced = new ArrayList<>();

    public TestWorld(JavaPlugin plugin, TickService ticks) {
        this.plugin = plugin;
        this.ticks = ticks;
        this.world = Bukkit.getWorlds().get(0);
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public World world() {
        return world;
    }

    public TickService ticks() {
        return ticks;
    }

    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Position de test au sol (sur l'herbe du monde plat) au-dessus de (x, z). */
    public Location ground(int x, int z) {
        prepare(x, z, 1);
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    /** Charge et force (forceload) les chunks dans un carré de `radiusChunks` autour de (x, z). */
    public void prepare(int x, int z, int radiusChunks) {
        int cx = Math.floorDiv(x, 16);
        int cz = Math.floorDiv(z, 16);
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                Chunk c = world.getChunkAt(cx + dx, cz + dz);
                c.load(true);
                if (!c.isForceLoaded()) {
                    c.setForceLoaded(true);
                    forced.add(c);
                }
            }
        }
    }

    public boolean teleport(Entity entity, Location target) {
        return entity.teleport(target);
    }

    /** Fait apparaître un villageois (IA désactivée par défaut pour des tests déterministes). */
    public Villager spawnVillager(Location at, boolean ai) {
        Villager v = (Villager) world.spawnEntity(at, EntityType.VILLAGER);
        v.setAI(ai);
        entities.add(v);
        return v;
    }

    public Villager spawnVillager(Location at) {
        return spawnVillager(at, false);
    }

    public <T extends Entity> T spawn(Location at, Class<T> type) {
        T e = world.spawn(at, type);
        entities.add(e);
        return e;
    }

    /** Pose un lit (pied en `foot`, tête dans la direction `facing`) ; renvoie le bloc du pied. */
    public Block placeBed(Location foot, BlockFace facing) {
        Block f = foot.getBlock();
        Block h = f.getRelative(facing);
        remember(f);
        remember(h);
        Bed data = (Bed) Bukkit.createBlockData(Material.RED_BED);
        data.setFacing(facing);
        data.setPart(Bed.Part.FOOT);
        f.setBlockData(data, false);
        Bed head = (Bed) data.clone();
        head.setPart(Bed.Part.HEAD);
        h.setBlockData(head, false);
        return f;
    }

    public Block placeBarrel(Location at) {
        return setBlock(at, Material.BARREL);
    }

    public Block setBlock(Location at, Material type) {
        Block b = at.getBlock();
        remember(b);
        b.setType(type, false);
        return b;
    }

    /** Mémorise (pour {@link #cleanup()}) les blocs d'un carré autour de `center`, de 1 sous le sol à 4 au-dessus. */
    public void rememberArea(Location center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -1; y <= 4; y++) {
                    remember(center.clone().add(x, y, z).getBlock());
                }
            }
        }
    }

    /** Fait retirer par {@link #cleanup()} une entité apparue hors de ce helper (par exemple par une commande). */
    public void track(Entity e) {
        entities.add(e);
    }

    /** Mémorise l'état d'un bloc avant qu'un service le modifie, pour {@link #cleanup()}. */
    public void remember(Block b) {
        changed.putIfAbsent(b, b.getBlockData());
    }

    /** Retire entités et blocs posés, relâche les chunks forcés. */
    public void cleanup() {
        for (Entity e : entities) {
            if (e.isValid()) {
                e.remove();
            }
        }
        entities.clear();
        changed.forEach((b, data) -> b.setBlockData(data, false));
        changed.clear();
        for (Chunk c : forced) {
            c.setForceLoaded(false);
        }
        forced.clear();
    }
}
