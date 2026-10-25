package rrp.neoforge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import rrp.core.territory.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import rrp.core.player.ZoneType;
import rrp.core.player.Zones;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Urban planning of a player-ruled village: the ruler draws plots (residential, farm, market, barracks, forbidden)
 * over the claimed land, with a wooden shovel (two corners) or with {@code /rrp plan zone}. The plots live in
 * {@link PlayerService#zones()} and are read by the steward's expansion planner.
 */
@EventBusSubscriber(modid = RrpMod.MOD_ID)
public final class PlanService {

    /** Largest side of a plot, in blocks. */
    public static final int MAX_SIDE = 96;

    public enum Verdict { OK, TOO_BIG, OUTSIDE }

    private record Pending(ZoneType type, BlockPos corner) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private PlanService() {
    }

    public static void reset() {
        PENDING.clear();
    }

    /** Adds a plot to {@code site} when it fits the size limit and both corners lie on land claimed by the village. */
    public static Verdict addZone(VillageSite site, ZoneType type, int x1, int z1, int x2, int z2) {
        if (Math.abs(x1 - x2) >= MAX_SIDE || Math.abs(z1 - z2) >= MAX_SIDE) {
            return Verdict.TOO_BIG;
        }
        var map = TerritoryService.build(RrpMod.registry().all(), site.worldName(), RrpMod.simConfig());
        for (int[] c : new int[][] {{x1, z1}, {x2, z2}}) {
            if (!WarResolution.ownerOf(site.id()).equals(map.claim(ChunkPos.ofBlock(c[0], c[1])).owner())) {
                return Verdict.OUTSIDE;
            }
        }
        PlayerService.zones().add(site.id(), Zones.Zone.of(type, x1, z1, x2, z2));
        RrpMod.registry().markDirty();
        return Verdict.OK;
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND
                || !p.getMainHandItem().is(Items.WOODEN_SHOVEL) || RrpMod.registry() == null) {
            return;
        }
        Pending pending = PENDING.get(p.getUUID());
        if (pending == null) {
            return;
        }
        event.setCanceled(true);
        BlockPos at = event.getPos();
        if (pending.corner() == null) {
            PENDING.put(p.getUUID(), new Pending(pending.type(), at));
            say(p, "plan.corner", at.getX(), at.getZ());
            return;
        }
        PENDING.remove(p.getUUID());
        var owned = SovereignService.ownedBy(p.getUUID());
        if (owned.isEmpty()) {
            say(p, "law.not_ruler");
            return;
        }
        Verdict v = addZone(owned.get(0), pending.type(), pending.corner().getX(), pending.corner().getZ(), at.getX(), at.getZ());
        say(p, v == Verdict.OK ? "plan.added" : "plan." + v.name().toLowerCase(Locale.ROOT), pending.type().name().toLowerCase(Locale.ROOT));
    }

    private static void say(ServerPlayer p, String key, Object... args) {
        p.sendSystemMessage(Component.literal(RrpMod.lang().msg(key, args)));
    }

    // ----- commands -------------------------------------------------------------------------------------------

    static void install() {
        Commands.register("plan", PlanService::command);
    }

    private static void command(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            Commands.reply(src, lang.msg("role.players_only"));
            return;
        }
        var owned = SovereignService.ownedBy(p.getUUID());
        if (owned.isEmpty()) {
            Commands.reply(src, lang.msg("law.not_ruler"));
            return;
        }
        VillageSite site = owned.get(0);
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "show";
        switch (action) {
            case "show" -> {
                List<Zones.Zone> zones = PlayerService.zones().peek(site.id());
                if (zones.isEmpty()) {
                    Commands.reply(src, lang.msg("plan.none", site.id()));
                }
                for (Zones.Zone z : zones) {
                    Commands.reply(src, lang.msg("plan.line", z.type().name().toLowerCase(Locale.ROOT), z.minX(), z.minZ(), z.maxX(), z.maxZ()));
                }
            }
            case "clear" -> {
                PlayerService.zones().clear(site.id());
                RrpMod.registry().markDirty();
                Commands.reply(src, lang.msg("plan.cleared"));
            }
            case "zone" -> {
                ZoneType type = args.length > 2 ? ZoneType.parse(args[2]) : null;
                if (type == null) {
                    Commands.reply(src, lang.msg("plan.usage"));
                } else if (args.length >= 7) {
                    try {
                        Verdict v = addZone(site, type, Integer.parseInt(args[3]), Integer.parseInt(args[4]),
                                Integer.parseInt(args[5]), Integer.parseInt(args[6]));
                        Commands.reply(src, lang.msg(v == Verdict.OK ? "plan.added" : "plan." + v.name().toLowerCase(Locale.ROOT),
                                type.name().toLowerCase(Locale.ROOT)));
                    } catch (NumberFormatException e) {
                        Commands.reply(src, lang.msg("plan.usage"));
                    }
                } else {
                    PENDING.put(p.getUUID(), new Pending(type, null));
                    Commands.reply(src, lang.msg("plan.armed", type.name().toLowerCase(Locale.ROOT)));
                }
            }
            default -> Commands.reply(src, lang.msg("plan.usage"));
        }
    }
}
