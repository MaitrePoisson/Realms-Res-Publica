package rrp.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import rrp.core.Resource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entrepôt communal : le tonneau désigné plus les coffres et tonneaux posés autour de l'Hôtel de Ville.
 * Le contenu réel de ces conteneurs fait foi pour le stock du village.
 */
public final class WarehouseService {

    private static final Set<Item> NOT_FOOD = Set.of(Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH,
            Items.ROTTEN_FLESH, Items.CHORUS_FRUIT, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.SUSPICIOUS_STEW);
    private static final Set<Item> ORES = Set.of(Items.COAL, Items.RAW_IRON, Items.RAW_COPPER, Items.RAW_GOLD,
            Items.IRON_INGOT, Items.COPPER_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD, Items.REDSTONE,
            Items.LAPIS_LAZULI, Items.QUARTZ, Items.CHARCOAL);
    private static final Set<Item> STONES = Set.of(Items.COBBLESTONE, Items.STONE, Items.COBBLED_DEEPSLATE,
            Items.DEEPSLATE, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.STONE_BRICKS);

    private final VillageRegistry registry;

    public WarehouseService(VillageRegistry registry) {
        this.registry = registry;
    }

    /** Ressource représentée par un objet, ou {@code null} s'il n'est pas compté. */
    public static Resource classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Item m = stack.getItem();
        if (m == Items.WHEAT || (stack.has(DataComponents.FOOD) && !NOT_FOOD.contains(m))) {
            return Resource.FOOD;
        }
        if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS)) {
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

    /** Le tonneau de l'entrepôt si son chunk est chargé et si le bloc est bien un tonneau ; sinon {@code null}. */
    public static BarrelBlockEntity barrel(VillageSite site, ServerLevel level) {
        int[] w = site.warehouse();
        if (w == null || level == null || !level.hasChunk(w[0] >> 4, w[2] >> 4)) {
            return null;
        }
        return level.getBlockEntity(new BlockPos(w[0], w[1], w[2])) instanceof BarrelBlockEntity b ? b : null;
    }

    /** Vrai si le chunk du centre (et celui de l'entrepôt) est chargé : sinon rien n'y est observable. */
    public static boolean loaded(VillageSite site, ServerLevel level) {
        if (level == null || !level.hasChunk((int) Math.floor(site.x()) >> 4, (int) Math.floor(site.z()) >> 4)) {
            return false;
        }
        int[] w = site.warehouse();
        return w == null || level.hasChunk(w[0] >> 4, w[2] >> 4);
    }

    /** Désigne le tonneau en {@code pos} comme entrepôt de {@code site}. Renvoie faux si ce n'est pas un tonneau. */
    public boolean set(VillageSite site, ServerLevel level, BlockPos pos) {
        if (level == null || !level.getBlockState(pos).is(Blocks.BARREL)) {
            return false;
        }
        site.setWarehouse(pos.getX(), pos.getY(), pos.getZ());
        registry.markDirty();
        return true;
    }

    /** Rayon (blocs) autour de l'Hôtel de Ville où les coffres et tonneaux font partie du stock communautaire. */
    static int storeRadius() {
        return (int) RrpMod.simConfig().get(rrp.core.SimConfig.Key.STORE_RADIUS);
    }

    /** Conteneurs du stock : l'entrepôt désigné d'abord, puis les coffres/tonneaux autour de l'Hôtel de Ville. */
    public List<Container> containers(VillageSite site, ServerLevel level) {
        List<Container> out = new ArrayList<>();
        BarrelBlockEntity primary = barrel(site, level);
        if (primary != null) {
            out.add(primary);
        }
        if (level == null || !site.hasHall()) {
            return out;
        }
        int[] h = site.hall();
        int r = storeRadius();
        if (!level.hasChunk((h[0] - r) >> 4, (h[2] - r) >> 4) || !level.hasChunk((h[0] + r) >> 4, (h[2] + r) >> 4)) {
            return out;
        }
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockEntity be = level.getBlockEntity(new BlockPos(h[0] + dx, h[1] + dy, h[2] + dz));
                    if ((be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) && be != primary) {
                        out.add((Container) be);
                    }
                }
            }
        }
        return out;
    }

    /** Nombre de coffres/tonneaux communautaires (hors entrepôt désigné). */
    public int communityChests(VillageSite site, ServerLevel level) {
        return Math.max(0, containers(site, level).size() - (barrel(site, level) != null ? 1 : 0));
    }

    /** Si le village a un Hôtel de Ville mais pas d'entrepôt, pose un tonneau à côté et le désigne. */
    public void ensureForHall(VillageSite site, ServerLevel level) {
        if (level == null || !site.hasHall() || site.hasWarehouse()) {
            return;
        }
        BlockPos spot = freeSpotNearHall(site, level);
        if (spot != null) {
            level.setBlock(spot, Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS);
            set(site, level, spot);
        }
    }

    /** Emplacement libre (sol plein, air au-dessus, sans être vivant) à 2-5 blocs de l'Hôtel de Ville. */
    private BlockPos freeSpotNearHall(VillageSite site, ServerLevel level) {
        int[] h = site.hall();
        int r = Math.max(2, storeRadius());
        for (int d = 2; d <= r; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) {
                        continue;
                    }
                    BlockPos p = new BlockPos(h[0] + dx, h[1], h[2] + dz);
                    if (level.hasChunk(p.getX() >> 4, p.getZ() >> 4) && level.getBlockState(p).isAir()
                            && level.getBlockState(p.above()).isAir() && level.getBlockState(p.below()).blocksMotion()
                            && level.getEntitiesOfClass(LivingEntity.class, new AABB(p)).isEmpty()) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    /** Quantités réellement présentes dans le stock (vide si pas d'entrepôt ou chunk non chargé). */
    public Map<Resource, Integer> read(VillageSite site, ServerLevel level) {
        Map<Resource, Integer> out = new EnumMap<>(Resource.class);
        for (Container b : containers(site, level)) {
            for (int i = 0; i < b.getContainerSize(); i++) {
                ItemStack it = b.getItem(i);
                Resource r = classify(it);
                if (r != null) {
                    out.merge(r, it.getCount(), Integer::sum);
                }
            }
        }
        return out;
    }

    /**
     * Recopie le contenu réel dans l'état du village. Si l'entrepôt est inaccessible (chunk non chargé,
     * détruit), le dernier stock connu est conservé. Renvoie vrai si le stock a été lu.
     */
    public boolean refresh(VillageSite site, ServerLevel level) {
        if (barrel(site, level) == null) {
            return false;
        }
        Map<Resource, Integer> read = read(site, level);
        for (Resource r : Resource.values()) {
            site.state().setStock(r, read.getOrDefault(r, 0));
        }
        return true;
    }

    /**
     * Dépose un objet dans le stock (entrepôt puis coffres). Si tout est plein, un nouveau coffre est posé près de
     * l'Hôtel de Ville (dans la limite de {@code max_chests}). Renvoie le reste qui n'a pas pu entrer.
     */
    public ItemStack deposit(VillageSite site, ServerLevel level, ItemStack stack) {
        if (barrel(site, level) == null) {
            return stack;
        }
        ItemStack rest = stack.copy();
        for (int attempt = 0; attempt < 2 && !rest.isEmpty(); attempt++) {
            for (Container c : containers(site, level)) {
                insert(c, rest);
                if (rest.isEmpty()) {
                    break;
                }
            }
            if (!rest.isEmpty() && (attempt > 0 || !addChest(site, level))) {
                break;
            }
        }
        refresh(site, level);
        registry.markDirty();
        return rest;
    }

    private boolean addChest(VillageSite site, ServerLevel level) {
        if (!site.hasHall()
                || communityChests(site, level) >= (int) RrpMod.simConfig().get(rrp.core.SimConfig.Key.MAX_CHESTS)) {
            return false;
        }
        BlockPos spot = freeSpotNearHall(site, level);
        if (spot == null) {
            return false;
        }
        level.setBlock(spot, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
        return true;
    }

    private static void insert(Container b, ItemStack rest) {
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int i = 0; i < b.getContainerSize() && !rest.isEmpty(); i++) {
                ItemStack slot = b.getItem(i);
                if (pass == 0 && !slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, rest)) {
                    int room = Math.min(slot.getMaxStackSize(), b.getMaxStackSize()) - slot.getCount();
                    int move = Math.min(room, rest.getCount());
                    if (move > 0) {
                        slot.grow(move);
                        rest.shrink(move);
                    }
                } else if (pass == 1 && slot.isEmpty()) {
                    int move = Math.min(rest.getCount(), Math.min(rest.getMaxStackSize(), b.getMaxStackSize()));
                    b.setItem(i, rest.split(move));
                }
            }
        }
        b.setChanged();
    }

    /** Retire jusqu'à {@code amount} unités de la ressource ; renvoie la quantité réellement retirée. */
    public int withdraw(VillageSite site, ServerLevel level, Resource resource, int amount) {
        if (barrel(site, level) == null || amount <= 0) {
            return 0;
        }
        int remaining = amount;
        for (Container b : containers(site, level)) {
            for (int i = 0; i < b.getContainerSize() && remaining > 0; i++) {
                ItemStack it = b.getItem(i);
                if (classify(it) != resource) {
                    continue;
                }
                int take = Math.min(remaining, it.getCount());
                remaining -= take;
                if (take == it.getCount()) {
                    b.setItem(i, ItemStack.EMPTY);
                } else {
                    it.shrink(take);
                }
            }
            b.setChanged();
        }
        refresh(site, level);
        registry.markDirty();
        return amount - remaining;
    }
}
