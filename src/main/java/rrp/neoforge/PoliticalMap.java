package rrp.neoforge;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import rrp.core.SimConfig;
import rrp.core.diplomacy.Treaty.State;
import rrp.core.player.MapRaster;
import rrp.core.territory.ChunkPos;
import rrp.core.territory.Claim;
import rrp.core.territory.InfluenceMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The political map: right-clicking a cartography table gives a vanilla filled map painted server-side (faction
 * colours matching the border banners, borders, war hatching, caravan routes, village markers), or repaints the
 * political map already held. Open to every player, rulers or not.
 */
@EventBusSubscriber(modid = RrpMod.MOD_ID)
public final class PoliticalMap {

    /** Name of the maps this class paints (only those are ever repainted). */
    public static final String NAME = "Carte politique";
    private static final byte SCALE = 3;
    private static final MapColor[] PALETTE = {
        MapColor.COLOR_RED, MapColor.COLOR_BLUE, MapColor.COLOR_GREEN, MapColor.COLOR_YELLOW, MapColor.COLOR_PURPLE,
        MapColor.COLOR_ORANGE, MapColor.COLOR_CYAN, MapColor.SNOW, MapColor.COLOR_BLACK, MapColor.COLOR_PINK,
        MapColor.COLOR_LIGHT_GREEN, MapColor.COLOR_BROWN,
    };

    private PoliticalMap() {
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer p)
                || !(event.getLevel() instanceof ServerLevel level) || p.isShiftKeyDown()
                || !level.getBlockState(event.getPos()).is(Blocks.CARTOGRAPHY_TABLE)) {
            return;
        }
        use(p, level);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Repaints the political map in the main hand, or gives a new one centred on the player. */
    public static ItemStack use(ServerPlayer p, ServerLevel level) {
        SimConfig cfg = RrpMod.simConfig();
        ItemStack held = p.getMainHandItem();
        if (held.is(Items.FILLED_MAP) && isPolitical(held)) {
            MapItemSavedData data = MapItem.getSavedData(held, level);
            if (data != null && data.dimension.equals(level.dimension())) {
                paint(data, level, cfg);
                p.sendSystemMessage(Component.literal(RrpMod.lang().msg("map.refreshed")));
                return held;
            }
        }
        ItemStack map = MapItem.create(level, p.getBlockX(), p.getBlockZ(), SCALE, false, true);
        MapItem.lockMap(level, map); // locked: vanilla must not repaint the terrain over the faction colours
        map.set(DataComponents.CUSTOM_NAME, Component.literal(NAME));
        MapItemSavedData data = MapItem.getSavedData(map, level);
        if (data != null) {
            paint(data, level, cfg);
        }
        ItemStack given = map.copy(); // Inventory.add empties the stack it takes
        if (!p.getInventory().add(map)) {
            p.drop(map, false);
        }
        p.sendSystemMessage(Component.literal(RrpMod.lang().msg("map.given")));
        return given;
    }

    private static boolean isPolitical(ItemStack s) {
        Component n = s.get(DataComponents.CUSTOM_NAME);
        return n != null && NAME.equals(n.getString());
    }

    /** Paints the whole map from the villages' claims, treaties and caravans. */
    public static void paint(MapItemSavedData data, ServerLevel level, SimConfig cfg) {
        String world = Worlds.name(level);
        List<VillageSite> sites = new ArrayList<>(RrpMod.registry().all());
        sites.removeIf(s -> !s.worldName().equals(world));
        sites.sort((a, b) -> a.id().compareTo(b.id()));
        Map<String, Integer> ids = new HashMap<>();
        for (VillageSite s : sites) {
            ids.put(s.id(), ids.size() + 1);
        }
        InfluenceMap influence = TerritoryService.build(sites, world, cfg);
        Set<Integer> atWar = new HashSet<>();
        for (VillageSite s : sites) {
            for (String o : DiplomacyService.treaties().partners(s.id(), State.WAR)) {
                if (ids.containsKey(o)) {
                    atWar.add(ids.get(s.id()));
                    atWar.add(ids.get(o));
                }
            }
        }
        List<int[]> markers = new ArrayList<>();
        for (VillageSite s : sites) {
            markers.add(new int[] {(int) Math.floor(s.x()), (int) Math.floor(s.z())});
        }
        int bpp = 1 << data.scale;
        int[] cells = MapRaster.render(128, bpp, data.centerX, data.centerZ, (cx, cz) -> {
            Claim c = influence.claim(new ChunkPos(cx, cz));
            return c.free() || c.contested() ? 0 : ids.getOrDefault(c.owner(), 0);
        }, atWar, CaravanService.routes(world), markers);
        int[] palette = new int[ids.size() + 1];
        for (VillageSite s : sites) {
            palette[ids.get(s.id())] = Math.floorMod(s.id().hashCode(), PALETTE.length);
        }
        for (int i = 0; i < cells.length; i++) {
            data.setColor(i % 128, i / 128, color(cells[i], palette));
        }
    }

    private static byte color(int cell, int[] palette) {
        return switch (cell) {
            case MapRaster.EMPTY -> MapColor.COLOR_LIGHT_GRAY.getPackedId(MapColor.Brightness.HIGH);
            case MapRaster.BORDER -> MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.LOWEST);
            case MapRaster.WAR -> MapColor.FIRE.getPackedId(MapColor.Brightness.NORMAL);
            case MapRaster.ROUTE -> MapColor.GOLD.getPackedId(MapColor.Brightness.HIGH);
            case MapRaster.MARKER -> MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH);
            default -> PALETTE[palette[cell]].getPackedId(MapColor.Brightness.NORMAL);
        };
    }
}
