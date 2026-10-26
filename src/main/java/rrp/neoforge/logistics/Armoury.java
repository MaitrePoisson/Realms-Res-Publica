package rrp.neoforge.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.logistics.Forge;
import rrp.core.logistics.Forge.Piece;
import rrp.neoforge.RrpMod;
import rrp.neoforge.VillageSite;
import rrp.neoforge.WarehouseService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The village armoury: a barrel next to the Town Hall that holds the guard kit (iron armour, swords, bows and arrows).
 * It is not part of the community stock. The local smith (the miner) forges the pieces there; a guard walks to it and
 * equips itself. Which pieces are missing is computed from the guards present and the armoury content.
 */
public final class Armoury {

    /** Ticks between two "the armoury is empty" notices for one village. */
    static final long TELL_EVERY_TICKS = 1200;

    private static final Map<String, Long> TOLD = new HashMap<>();
    private static long notices;

    private Armoury() {
    }

    // ---------- the barrel ----------

    public static boolean isArmoury(VillageSite site, BlockPos pos) {
        int[] a = site.armoury();
        return a != null && a[0] == pos.getX() && a[1] == pos.getY() && a[2] == pos.getZ();
    }

    /** The armoury barrel if its chunk is loaded and the block is still a barrel; otherwise {@code null}. */
    public static BarrelBlockEntity barrel(VillageSite site, ServerLevel level) {
        int[] a = site.armoury();
        if (a == null || level == null || !level.hasChunk(a[0] >> 4, a[2] >> 4)) {
            return null;
        }
        return level.getBlockEntity(new BlockPos(a[0], a[1], a[2])) instanceof BarrelBlockEntity b ? b : null;
    }

    /** Puts an armoury barrel next to the Town Hall (a free spot other than the warehouse) when there is none. */
    public static void ensure(VillageSite site, ServerLevel level, WarehouseService warehouse) {
        if (level == null || !site.hasHall() || barrel(site, level) != null) {
            return;
        }
        int[] a = site.armoury();
        if (a != null && !level.hasChunk(a[0] >> 4, a[2] >> 4)) {
            return; // not loaded: it may well be there
        }
        BlockPos spot = warehouse.freeSpotNearHall(site, level);
        if (spot != null) {
            level.setBlock(spot, Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS);
            site.setArmoury(spot.getX(), spot.getY(), spot.getZ());
            RrpMod.registry().markDirty();
        }
    }

    // ---------- the kit ----------

    /** The piece an item is, or {@code null}. */
    public static Piece pieceOf(ItemStack s) {
        if (s.isEmpty()) {
            return null;
        }
        Item i = s.getItem();
        if (i == Items.IRON_CHESTPLATE) return Piece.CHESTPLATE;
        if (i == Items.IRON_HELMET) return Piece.HELMET;
        if (i == Items.IRON_LEGGINGS) return Piece.LEGGINGS;
        if (i == Items.IRON_BOOTS) return Piece.BOOTS;
        if (i == Items.IRON_SWORD) return Piece.SWORD;
        if (i == Items.BOW) return Piece.BOW;
        return null;
    }

    public static ItemStack stackOf(Piece p) {
        return new ItemStack(switch (p) {
            case CHESTPLATE -> Items.IRON_CHESTPLATE;
            case HELMET -> Items.IRON_HELMET;
            case LEGGINGS -> Items.IRON_LEGGINGS;
            case BOOTS -> Items.IRON_BOOTS;
            case SWORD -> Items.IRON_SWORD;
            case BOW -> Items.BOW;
        });
    }

    static EquipmentSlot slotOf(Piece p) {
        return switch (p) {
            case CHESTPLATE -> EquipmentSlot.CHEST;
            case HELMET -> EquipmentSlot.HEAD;
            case LEGGINGS -> EquipmentSlot.LEGS;
            case BOOTS -> EquipmentSlot.FEET;
            case SWORD, BOW -> EquipmentSlot.MAINHAND;
        };
    }

    /** One guard in three is an archer (stable per villager): bow and arrows instead of a sword. */
    public static boolean archer(Villager v) {
        return Math.floorMod(v.getUUID().hashCode(), 3) == 0;
    }

    /** The weapon this guard is meant to carry. */
    public static Piece weaponOf(Villager v) {
        return archer(v) ? Piece.BOW : Piece.SWORD;
    }

    /** True if the villager already wears or holds this piece. */
    public static boolean has(Villager v, Piece p) {
        ItemStack s = v.getItemBySlot(slotOf(p));
        return switch (p) {
            case SWORD -> s.is(ItemTags.SWORDS);
            case BOW -> s.is(Items.BOW);
            default -> pieceOf(s) == p;
        };
    }

    /** A sword or a bow in the main hand. */
    public static boolean hasWeapon(Villager v) {
        ItemStack s = v.getMainHandItem();
        return s.is(ItemTags.SWORDS) || s.is(Items.BOW);
    }

    /** The pieces this guard still lacks, in forging priority order. */
    public static List<Piece> lacking(Villager v) {
        List<Piece> out = new ArrayList<>();
        Piece otherWeapon = archer(v) ? Piece.SWORD : Piece.BOW;
        for (Piece p : Piece.values()) {
            if (p != otherWeapon && !has(v, p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** Fully equipped: the whole armour and its weapon. */
    public static boolean complete(Villager v) {
        return lacking(v).isEmpty();
    }

    /** Per piece, how many of these guards lack it. */
    public static Map<Piece, Integer> lacking(Collection<Villager> guards) {
        Map<Piece, Integer> out = new EnumMap<>(Piece.class);
        for (Villager g : guards) {
            for (Piece p : lacking(g)) {
                out.merge(p, 1, Integer::sum);
            }
        }
        return out;
    }

    /** What the container holds, per piece. */
    public static Map<Piece, Integer> stocked(Container c) {
        Map<Piece, Integer> out = new EnumMap<>(Piece.class);
        for (int i = 0; i < c.getContainerSize(); i++) {
            Piece p = pieceOf(c.getItem(i));
            if (p != null) {
                out.merge(p, c.getItem(i).getCount(), Integer::sum);
            }
        }
        return out;
    }

    /** The guards of the village that are present in the world. */
    public static List<Villager> guards(VillageSite site, ServerLevel level) {
        return RrpMod.adapter().present(site, level).stream()
                .filter(v -> {
                    var st = RrpMod.adapter().stateOf(v);
                    return st != null && st.role() == Role.GUARD;
                }).toList();
    }

    /** Pieces still to forge for the village (what the guards lack minus the armoury stock). */
    public static Map<Piece, Integer> needs(VillageSite site, ServerLevel level) {
        BarrelBlockEntity b = barrel(site, level);
        if (b == null) {
            return Map.of();
        }
        return Forge.shortfall(lacking(guards(site, level)), stocked(b));
    }

    /** True if the container holds a piece this villager lacks. */
    public static boolean canTake(Villager v, Container c) {
        Map<Piece, Integer> held = stocked(c);
        for (Piece p : lacking(v)) {
            if (held.getOrDefault(p, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Takes from the container every piece the villager lacks (one each) and equips it. Returns the pieces taken. */
    public static int equip(Villager v, Container c) {
        int taken = 0;
        for (Piece p : lacking(v)) {
            if (!removeOne(c, p)) {
                continue;
            }
            v.setItemSlot(slotOf(p), stackOf(p));
            taken++;
            if (p == Piece.BOW) {
                takeArrows(v, c);
            }
        }
        c.setChanged();
        return taken;
    }

    private static boolean removeOne(Container c, Piece p) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (pieceOf(c.getItem(i)) == p) {
                c.getItem(i).shrink(1);
                if (c.getItem(i).isEmpty()) {
                    c.setItem(i, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }

    private static void takeArrows(Villager v, Container c) {
        int left = Forge.ARROWS_PER_BOW;
        for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
            ItemStack s = c.getItem(i);
            if (s.is(Items.ARROW)) {
                int n = Math.min(left, s.getCount());
                v.getInventory().addItem(new ItemStack(Items.ARROW, n));
                s.shrink(n);
                if (s.isEmpty()) {
                    c.setItem(i, ItemStack.EMPTY);
                }
                left -= n;
            }
        }
    }

    // ---------- the forge ----------

    /** Ore and wood price of a piece, from the configuration. */
    public static Forge.Cost cost(Piece p, SimConfig cfg) {
        return Forge.cost(p, (int) cfg.get(SimConfig.Key.MOBILIZATION_SWORD_ORE),
                (int) cfg.get(SimConfig.Key.MOBILIZATION_ARMOR_ORE), (int) cfg.get(SimConfig.Key.MOBILIZATION_BOW_WOOD));
    }

    /**
     * Forges the first short piece the smith can pay for from its load, and stocks it in the armoury. Returns the
     * number of items stocked (0 when nothing is needed or the load does not pay for anything).
     */
    public static int forge(Villager smith, VillageSite site, ServerLevel level, SimConfig cfg) {
        BarrelBlockEntity b = barrel(site, level);
        if (b == null) {
            return 0;
        }
        Map<Piece, Integer> need = needs(site, level);
        int ore = CarriedItems.count(smith, Resource.ORE);
        int wood = CarriedItems.count(smith, Resource.WOOD);
        for (Piece p : Piece.values()) {
            Forge.Cost cost = cost(p, cfg);
            if (need.getOrDefault(p, 0) <= 0 || !Forge.affordable(cost, ore, wood)) {
                continue;
            }
            CarriedItems.take(smith, Resource.ORE, cost.ore());
            CarriedItems.take(smith, Resource.WOOD, cost.wood());
            int made = stock(b, level, stackOf(p));
            if (p == Piece.BOW) {
                made += stock(b, level, new ItemStack(Items.ARROW, Forge.ARROWS_PER_BOW));
            }
            b.setChanged();
            BlockPos at = b.getBlockPos();
            level.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, SoundEvents.ANVIL_USE,
                    SoundSource.BLOCKS, 0.6f, 1.0f);
            return made;
        }
        return 0;
    }

    private static int stock(BarrelBlockEntity b, ServerLevel level, ItemStack s) {
        int n = s.getCount();
        ItemStack rest = s.copy();
        WarehouseService.insert(b, rest);
        if (!rest.isEmpty()) {
            Block.popResource(level, b.getBlockPos().above(), rest);
        }
        return n;
    }

    // ---------- notices ----------

    /** Tells the village (rate-limited) that the armoury has nothing for its unarmed guards. */
    public static void reportEmpty(VillageSite site, ServerLevel level) {
        long now = level.getGameTime();
        Long last = TOLD.get(site.id());
        if (last != null && now >= last && now - last < TELL_EVERY_TICKS) {
            return;
        }
        TOLD.put(site.id(), now);
        notices++;
        RrpMod.crises().tell(site, RrpMod.lang().msg("logistics.armoury_empty", site.id()));
    }

    /** "Armoury empty" notices sent since the start (tests, diagnostic). */
    public static long notices() {
        return notices;
    }

    /** Forgets the rate limit (tests). */
    public static void resetNotices() {
        TOLD.clear();
    }
}
