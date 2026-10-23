package rrp.bukkit;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import rrp.core.Resource;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Entrepôt communal : un tonneau désigné dont le contenu réel fait foi pour le stock du village. */
public final class WarehouseService {

    private static final Set<Material> NOT_FOOD = EnumSet.of(Material.SPIDER_EYE, Material.POISONOUS_POTATO,
            Material.PUFFERFISH, Material.ROTTEN_FLESH, Material.CHORUS_FRUIT, Material.GOLDEN_APPLE,
            Material.ENCHANTED_GOLDEN_APPLE, Material.SUSPICIOUS_STEW);
    private static final Set<Material> ORES = EnumSet.of(Material.COAL, Material.RAW_IRON, Material.RAW_COPPER,
            Material.RAW_GOLD, Material.IRON_INGOT, Material.COPPER_INGOT, Material.GOLD_INGOT, Material.DIAMOND,
            Material.EMERALD, Material.REDSTONE, Material.LAPIS_LAZULI, Material.QUARTZ, Material.CHARCOAL);
    private static final Set<Material> STONES = EnumSet.of(Material.COBBLESTONE, Material.STONE, Material.COBBLED_DEEPSLATE,
            Material.DEEPSLATE, Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.STONE_BRICKS);

    private final VillageRegistry registry;

    public WarehouseService(VillageRegistry registry) {
        this.registry = registry;
    }

    /** Ressource représentée par un matériau d'objet, ou {@code null} s'il n'est pas compté. */
    public static Resource classify(Material m) {
        if (m == null || m.isAir()) {
            return null;
        }
        if (m.isEdible() && !NOT_FOOD.contains(m)) {
            return Resource.FOOD;
        }
        if (Tag.LOGS.isTagged(m) || Tag.PLANKS.isTagged(m)) {
            return Resource.WOOD;
        }
        if (ORES.contains(m)) {
            return Resource.ORE;
        }
        if (STONES.contains(m)) {
            return Resource.STONE;
        }
        return null;
    }

    /** Désigne le tonneau `block` comme entrepôt de `site`. Renvoie faux si ce n'est pas un tonneau. */
    public boolean set(VillageSite site, Block block) {
        if (block == null || block.getType() != Material.BARREL) {
            return false;
        }
        site.setWarehouse(block.getX(), block.getY(), block.getZ());
        registry.markDirty();
        return true;
    }

    /** Quantités réellement présentes dans le tonneau (vide si pas d'entrepôt ou chunk non chargé). */
    public Map<Resource, Integer> read(VillageSite site, World world) {
        Map<Resource, Integer> out = new HashMap<>();
        Block b = site.warehouseBlock(world);
        if (b == null) {
            return out;
        }
        for (ItemStack it : ((Barrel) b.getState(false)).getInventory().getContents()) {
            if (it == null) {
                continue;
            }
            Resource r = classify(it.getType());
            if (r != null) {
                out.merge(r, it.getAmount(), Integer::sum);
            }
        }
        return out;
    }

    /**
     * Recopie le contenu réel dans l'état du village. Si le tonneau est inaccessible (chunk non chargé,
     * détruit), le dernier stock connu est conservé. Renvoie vrai si le stock a été lu.
     */
    public boolean refresh(VillageSite site, World world) {
        Block b = site.warehouseBlock(world);
        if (b == null) {
            return false;
        }
        Map<Resource, Integer> read = read(site, world);
        for (Resource r : Resource.values()) {
            site.state().setStock(r, read.getOrDefault(r, 0));
        }
        return true;
    }

    /** Dépose un objet dans l'entrepôt. Renvoie le reste qui n'a pas pu entrer (jamais {@code null}). */
    public ItemStack deposit(VillageSite site, World world, ItemStack stack) {
        Block b = site.warehouseBlock(world);
        if (b == null) {
            return stack;
        }
        Inventory inv = ((Barrel) b.getState(false)).getInventory();
        Map<Integer, ItemStack> left = inv.addItem(stack);
        refresh(site, world);
        registry.markDirty();
        return left.isEmpty() ? new ItemStack(Material.AIR) : left.values().iterator().next();
    }

    /** Retire jusqu'à `amount` unités de la ressource ; renvoie la quantité réellement retirée. */
    public int withdraw(VillageSite site, World world, Resource resource, int amount) {
        Block b = site.warehouseBlock(world);
        if (b == null || amount <= 0) {
            return 0;
        }
        Inventory inv = ((Barrel) b.getState(false)).getInventory();
        int remaining = amount;
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || classify(it.getType()) != resource) {
                continue;
            }
            int take = Math.min(remaining, it.getAmount());
            remaining -= take;
            if (take == it.getAmount()) {
                inv.setItem(i, null);
            } else {
                it.setAmount(it.getAmount() - take);
            }
        }
        refresh(site, world);
        registry.markDirty();
        return amount - remaining;
    }
}
