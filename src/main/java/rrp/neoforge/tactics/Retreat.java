package rrp.neoforge.tactics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.Vec3;
import rrp.core.SimConfig;
import rrp.core.tactics.HealItems;
import rrp.core.tactics.Vec2;
import rrp.neoforge.VillageSite;
import rrp.neoforge.Worlds;
import rrp.neoforge.logistics.Armoury;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tactical retreat of a badly wounded guard (M74). At or under {@code guard_retreat_pct} percent of its health a guard
 * stops fighting and walks (never runs) to a point behind the line of its comrades, then to the barracks stock (the
 * armoury barrel) where it heals with an item of {@code guard_heal_items}; without any it heals slowly. It resumes
 * duty once healed (hysteresis in rrp.core.tactics.Retreat).
 */
public final class Retreat {

    /** Blocks between the line of comrades and the withdrawn guard. */
    static final double MARGIN = 3;
    /** A withdrawn guard heals slowly (health points per tick) when the stock holds nothing for it. */
    static final float SLOW_HEAL_PER_TICK = 0.01F;
    /** Ticks between two items used. */
    static final int ITEM_COOLDOWN = 20;
    /** A hostile this close (blocks) makes a wounded guard fall back behind the line of its comrades. */
    static final double CONTACT = 8;

    private final Map<UUID, Boolean> withdrawing = new HashMap<>();
    private final Map<UUID, Long> nextItem = new HashMap<>();
    private long withdrawals;
    private long healed;

    /** Guards that started a retreat since the start (tests, diagnostic). */
    public long withdrawals() {
        return withdrawals;
    }

    /** Healing items consumed from the stock since the start (tests, diagnostic). */
    public long healed() {
        return healed;
    }

    public boolean isWithdrawing(Villager g) {
        return withdrawing.getOrDefault(g.getUUID(), false);
    }

    public void reset() {
        withdrawing.clear();
        nextItem.clear();
        withdrawals = 0;
        healed = 0;
    }

    /** Updates and returns whether this guard is withdrawn (health under the threshold, until healed). */
    public boolean update(Villager g, SimConfig cfg) {
        boolean was = isWithdrawing(g);
        boolean now = rrp.core.tactics.Retreat.stillWithdrawing(was, g.getHealth(), g.getMaxHealth(),
                cfg.get(SimConfig.Key.GUARD_RETREAT_PCT));
        if (now != was) {
            withdrawing.put(g.getUUID(), now);
            if (now) {
                withdrawals++;
            }
        }
        return now;
    }

    /** One step of a withdrawn guard. {@code comrades} are the guards still on their feet. */
    void step(VillageSite site, ServerLevel level, Villager g, List<Villager> comrades, int ticks, SimConfig cfg) {
        GuardAi.lowerShield(g);
        g.setShiftKeyDown(false);
        Mob enemy = nearestEnemy(site, level, g);
        if (enemy != null && enemy.distanceTo(g) < CONTACT) {
            List<Vec2> line = new ArrayList<>();
            for (Villager c : comrades) {
                line.add(new Vec2(c.getX(), c.getZ()));
            }
            Vec2 p = rrp.core.tactics.Retreat.pointBehindLine(new Vec2(g.getX(), g.getZ()),
                    new Vec2(enemy.getX(), enemy.getZ()), line, MARGIN);
            g.getLookControl().setLookAt(enemy, 30, 30);
            BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(p.x(), 0, p.z()));
            // a measured walk, not a sprint: the line holds while the wounded guard withdraws
            g.getNavigation().moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0.5);
            return;
        }
        BarrelBlockEntity stock = Armoury.barrel(site, level);
        Vec3 rally = rally(site, stock);
        double reach = cfg.get(SimConfig.Key.CHEST_REACH);
        if (g.position().distanceToSqr(rally) > reach * reach) {
            g.getNavigation().moveTo(rally.x, rally.y, rally.z, 0.5);
            return;
        }
        g.getNavigation().stop();
        if (stock != null && heal(level, g, stock, cfg)) {
            return;
        }
        g.heal(SLOW_HEAL_PER_TICK * ticks);
    }

    private static Vec3 rally(VillageSite site, BarrelBlockEntity stock) {
        if (stock != null) {
            return Vec3.atCenterOf(stock.getBlockPos());
        }
        int[] hall = site.hall();
        return hall != null ? Vec3.atCenterOf(new BlockPos(hall[0], hall[1], hall[2])) : new Vec3(site.x(), site.y(), site.z());
    }

    private Mob nearestEnemy(VillageSite site, ServerLevel level, Villager g) {
        String world = Worlds.name(level);
        Mob best = null;
        for (Mob m : level.getEntitiesOfClass(Mob.class, g.getBoundingBox().inflate(CONTACT, 16, CONTACT),
                e -> e instanceof Enemy && e.isAlive())) {
            if (site.contains(world, m.getX(), m.getZ()) && (best == null || m.distanceTo(g) < best.distanceTo(g))) {
                best = m;
            }
        }
        return best;
    }

    /** Uses one healing item of the stock on the guard. Returns true when an item was used (or is still cooling down). */
    private boolean heal(ServerLevel level, Villager g, BarrelBlockEntity stock, SimConfig cfg) {
        long now = level.getGameTime();
        if (nextItem.getOrDefault(g.getUUID(), 0L) > now) {
            return true;
        }
        Set<String> ids = HealItems.parse(cfg.guardHealItems());
        for (int i = 0; i < stock.getContainerSize(); i++) {
            ItemStack s = stock.getItem(i);
            if (!s.isEmpty() && ids.contains(BuiltInRegistries.ITEM.getKey(s.getItem()).toString())) {
                s.shrink(1);
                if (s.isEmpty()) {
                    stock.setItem(i, ItemStack.EMPTY);
                }
                stock.setChanged();
                g.heal((float) cfg.get(SimConfig.Key.GUARD_HEAL_AMOUNT));
                level.playSound(null, g.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8F, 1.0F);
                nextItem.put(g.getUUID(), now + ITEM_COOLDOWN);
                healed++;
                return true;
            }
        }
        return false;
    }
}
