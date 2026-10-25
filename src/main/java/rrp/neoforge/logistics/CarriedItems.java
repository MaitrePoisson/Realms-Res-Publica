package rrp.neoforge.logistics;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import rrp.core.Resource;
import rrp.neoforge.WarehouseService;

/**
 * What a villager physically carries: the counted items (food, wood, ore, stone) of its vanilla inventory, which is
 * saved with the entity (so the load survives a restart). The main hand shows the carried stack, which vanilla renders.
 */
public final class CarriedItems {

    private CarriedItems() {
    }

    /** Units of counted resources in the inventory. */
    public static int total(Villager v) {
        int n = 0;
        SimpleContainer inv = v.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (WarehouseService.classify(inv.getItem(i)) != null) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    /** Units of {@code r} in the inventory. */
    public static int count(Villager v, Resource r) {
        int n = 0;
        SimpleContainer inv = v.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (WarehouseService.classify(inv.getItem(i)) == r) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    /** Units the villager can still pick up before reaching {@code capacity}. */
    public static int free(Villager v, int capacity) {
        return Math.max(0, capacity - total(v));
    }

    /** Puts up to the free capacity of {@code stack} in the inventory and returns how many units were accepted. */
    public static int add(Villager v, ItemStack stack, int capacity) {
        int room = Math.min(stack.getCount(), free(v, capacity));
        if (room <= 0) {
            return 0;
        }
        ItemStack part = stack.copyWithCount(room);
        ItemStack rest = v.getInventory().addItem(part);
        return room - rest.getCount();
    }

    /**
     * Moves the carried stacks of {@code r} ({@code null} = every counted resource) into {@code target}, as far as it
     * has room. Returns the units moved.
     */
    public static int depositInto(Villager v, Resource r, Container target) {
        int moved = 0;
        SimpleContainer inv = v.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack slot = inv.getItem(i);
            Resource kind = WarehouseService.classify(slot);
            if (kind == null || (r != null && kind != r)) {
                continue;
            }
            ItemStack rest = slot.copy();
            WarehouseService.insert(target, rest);
            moved += slot.getCount() - rest.getCount();
            inv.setItem(i, rest);
        }
        return moved;
    }

    /** Shows the biggest carried stack in the main hand, or clears the hand when nothing counted is carried. */
    public static void showInHand(Villager v) {
        ItemStack hand = v.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!hand.isEmpty() && WarehouseService.classify(hand) == null) {
            return; // holds something else (a tool, a weapon): leave it alone
        }
        ItemStack best = ItemStack.EMPTY;
        SimpleContainer inv = v.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (WarehouseService.classify(s) != null && s.getCount() > best.getCount()) {
                best = s;
            }
        }
        v.setItemSlot(EquipmentSlot.MAINHAND, best.isEmpty() ? ItemStack.EMPTY : best.copy());
        v.setDropChance(EquipmentSlot.MAINHAND, 0f); // the hand is only a display copy of the inventory
    }
}
