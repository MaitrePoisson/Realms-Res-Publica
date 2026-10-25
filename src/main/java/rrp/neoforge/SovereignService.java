package rrp.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import rrp.core.SimConfig;
import rrp.core.player.Founding;
import rrp.core.player.PlayerRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A Sovereign founds a village by completing a Town Hall (lectern on a gold block): the village is registered
 * around it, owned by the player, and run by the usual autonomous services (also while the ruler is offline).
 */
@EventBusSubscriber(modid = RrpMod.MOD_ID)
public final class SovereignService {

    private SovereignService() {
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos at = event.getPos();
        BlockPos base;
        if (event.getPlacedBlock().is(Blocks.LECTERN) && level.getBlockState(at.below()).is(Blocks.GOLD_BLOCK)) {
            base = at.below();
        } else if (event.getPlacedBlock().is(Blocks.GOLD_BLOCK) && level.getBlockState(at.above()).is(Blocks.LECTERN)) {
            base = at;
        } else {
            return;
        }
        if (PlayerService.roleOf(p.getUUID()) != PlayerRole.SOVEREIGN) {
            return;
        }
        found(p, level, base);
    }

    /** Founds a village at the Town Hall {@code base}; tells the player and returns the verdict. */
    public static Founding.Verdict found(ServerPlayer p, ServerLevel level, BlockPos base) {
        SimConfig cfg = RrpMod.simConfig();
        Lang lang = RrpMod.lang();
        UUID id = p.getUUID();
        double nearest = Double.MAX_VALUE;
        for (VillageSite s : RrpMod.registry().all()) {
            nearest = Math.min(nearest, s.distance(Worlds.name(level), base.getX(), base.getZ()));
        }
        long cost = Math.round(cfg.get(SimConfig.Key.FOUND_COST));
        Founding.Verdict v = Founding.check(PlayerService.roleOf(id), PlayerService.ledger().purse(id), cost, nearest,
                cfg.get(SimConfig.Key.FOUND_MIN_DISTANCE), ownedBy(id).size(), (int) cfg.get(SimConfig.Key.FOUND_MAX_PER_PLAYER));
        if (v != Founding.Verdict.OK) {
            p.sendSystemMessage(Component.literal(lang.msg("found." + v.name().toLowerCase(Locale.ROOT), cost,
                    Math.round(cfg.get(SimConfig.Key.FOUND_MIN_DISTANCE)))));
            return v;
        }
        PlayerService.ledger().withdraw(id, cost);
        VillageSite site = RrpMod.registry().create(Worlds.name(level), base.getX() + 0.5, base.getY(), base.getZ() + 0.5,
                (int) cfg.get(SimConfig.Key.VILLAGE_RADIUS));
        site.setHall(base.getX(), base.getY(), base.getZ());
        site.setOwner(id.toString());
        RrpMod.scanner().refreshNow(site);
        RrpMod.registry().markDirty();
        p.sendSystemMessage(Component.literal(lang.msg("found.ok", site.id())));
        return v;
    }

    public static List<VillageSite> ownedBy(UUID id) {
        List<VillageSite> out = new ArrayList<>();
        for (VillageSite s : RrpMod.registry().all()) {
            if (id.toString().equals(s.owner())) {
                out.add(s);
            }
        }
        return out;
    }
}
