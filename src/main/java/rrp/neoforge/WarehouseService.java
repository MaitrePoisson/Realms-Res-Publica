package rrp.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import rrp.core.Resource;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Entrepôt communal : un tonneau désigné dont le contenu réel fait foi pour le stock du village. */
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

    /** Quantités réellement présentes dans le tonneau (vide si pas d'entrepôt ou chunk non chargé). */
    public Map<Resource, Integer> read(VillageSite site, ServerLevel level) {
        Map<Resource, Integer> out = new EnumMap<>(Resource.class);
        BarrelBlockEntity b = barrel(site, level);
        if (b == null) {
            return out;
        }
        for (int i = 0; i < b.getContainerSize(); i++) {
            ItemStack it = b.getItem(i);
            Resource r = classify(it);
            if (r != null) {
                out.merge(r, it.getCount(), Integer::sum);
            }
        }
        return out;
    }

    /**
     * Recopie le contenu réel dans l'état du village. Si le tonneau est inaccessible (chunk non chargé,
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

    /** Dépose un objet dans l'entrepôt. Renvoie le reste qui n'a pas pu entrer (jamais {@code null}). */
    public ItemStack deposit(VillageSite site, ServerLevel level, ItemStack stack) {
        BarrelBlockEntity b = barrel(site, level);
        if (b == null) {
            return stack;
        }
        ItemStack rest = stack.copy();
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
        refresh(site, level);
        registry.markDirty();
        return rest;
    }

    /** Retire jusqu'à {@code amount} unités de la ressource ; renvoie la quantité réellement retirée. */
    public int withdraw(VillageSite site, ServerLevel level, Resource resource, int amount) {
        BarrelBlockEntity b = barrel(site, level);
        if (b == null || amount <= 0) {
            return 0;
        }
        int remaining = amount;
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
        refresh(site, level);
        registry.markDirty();
        return amount - remaining;
    }
}
