package rrp.neoforge;

import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import rrp.core.Faction;
import rrp.core.SimConfig;
import rrp.core.territory.BorderPlan;
import rrp.core.territory.ChunkPos;
import rrp.core.territory.DoctrineSelector;
import rrp.core.territory.InfluenceMap;

/**
 * Territoire : construit la carte d'influence à partir des villages (chaque village ou campement est
 * une faction à part, identifiée par son id) et pose les bornes frontières (deux blocs de pierre
 * taillée surmontés d'une bannière aux couleurs de la faction). Rien n'est persisté en plus : les
 * marqueurs se déduisent de villages.json, et la pose est idempotente (reprise après redémarrage).
 */
public final class TerritoryService {

    private static final Block[] BANNERS = {
        Blocks.RED_BANNER, Blocks.BLUE_BANNER, Blocks.GREEN_BANNER, Blocks.YELLOW_BANNER, Blocks.PURPLE_BANNER,
        Blocks.ORANGE_BANNER, Blocks.CYAN_BANNER, Blocks.WHITE_BANNER, Blocks.BLACK_BANNER, Blocks.PINK_BANNER,
        Blocks.LIME_BANNER, Blocks.BROWN_BANNER,
    };

    private TerritoryService() {
    }

    public static Block banner(String factionId) {
        return BANNERS[Math.floorMod(factionId.hashCode(), BANNERS.length)];
    }

    /** Carte d'influence d'un monde. */
    public static InfluenceMap build(Collection<VillageSite> sites, String world, SimConfig cfg) {
        InfluenceMap map = new InfluenceMap(cfg.get(SimConfig.Key.TERRITORY_OWNER_THRESHOLD),
                cfg.get(SimConfig.Key.TERRITORY_CONTEST_MARGIN));
        int hallR = (int) cfg.get(SimConfig.Key.TERRITORY_HALL_RADIUS);
        int outR = (int) cfg.get(SimConfig.Key.TERRITORY_OUTPOST_RADIUS);
        for (VillageSite s : sites) {
            if (!s.worldName().equals(world)) {
                continue;
            }
            int bx = s.hasHall() ? s.hall()[0] : (int) Math.floor(s.x());
            int bz = s.hasHall() ? s.hall()[2] : (int) Math.floor(s.z());
            int r = s.state().faction() == Faction.DISSIDENT ? outR : hallR;
            if (s.doctrine() != null && s.state().faction() != Faction.DISSIDENT) {
                r = Math.max(1, (int) Math.round(r * s.doctrine().claimRadiusFactor()));
            }
            String owner = WarResolution.ownerOf(s.id());
            map.put(new InfluenceMap.Marker(s.id(), owner, ChunkPos.ofBlock(bx, bz), r, 1.0));
            if (s.defense().started()) {
                for (int[] t : s.defense().towers()) {
                    map.put(new InfluenceMap.Marker(s.id() + "#t" + t[0] + "," + t[2], owner,
                            ChunkPos.ofBlock(t[0], t[2]), Math.max(1, outR - 1), 0.3));
                }
            }
        }
        return map;
    }

    /**
     * Fixe la doctrine du village d'après les biomes échantillonnés autour de l'Hôtel de Ville
     * (grille de 9x9 points sur 2 x {@code village_radius}). Sans effet si elle est déjà choisie.
     */
    public static void ensureDoctrine(ServerLevel level, VillageSite site) {
        if (level == null || site.doctrine() != null || !site.hasHall()) {
            return;
        }
        int[] h = site.hall();
        int r = Math.min(RrpMod.simConfig().getInt(SimConfig.Key.VILLAGE_RADIUS), 96);
        List<rrp.core.territory.BiomeClassifier.Kind> kinds = new java.util.ArrayList<>();
        for (int i = -4; i <= 4; i++) {
            for (int j = -4; j <= 4; j++) {
                BlockPos p = new BlockPos(h[0] + i * r / 4, h[1], h[2] + j * r / 4);
                level.getChunk(p.getX() >> 4, p.getZ() >> 4); // un chunk non chargé renverrait le biome du générateur
                kinds.add(rrp.neoforge.urban.BiomeAdapter.classify(level.getBiome(p)).kind());
            }
        }
        site.setDoctrine(DoctrineSelector.selectKinds(kinds));
        RrpMod.registry().markDirty();
    }

    public static InfluenceMap build(String world) {
        return build(RrpMod.registry().all(), world, RrpMod.simConfig());
    }

    public static List<int[]> posts(InfluenceMap map, String faction) {
        return BorderPlan.posts(map, faction);
    }

    private static int surface(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** Pose une borne : 2 blocs de pierre taillée et une bannière. Faux si chunk non chargé ou sol liquide. */
    public static boolean placePost(ServerLevel level, int x, int z, String faction) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
            return false;
        }
        int y = surface(level, x, z);
        if (!level.getBlockState(new BlockPos(x, y - 1, z)).getFluidState().isEmpty()) {
            return false;
        }
        level.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x, y + 1, z), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x, y + 2, z), banner(faction).defaultBlockState(), 3);
        return true;
    }

    /** Prochaine borne manquante de la faction, ou {@code null} si toutes sont posées. */
    public static int[] nextMissing(ServerLevel level, InfluenceMap map, String faction) {
        for (int[] p : posts(map, faction)) {
            if (!isMarked(level, p[0], p[1])) {
                return p;
            }
        }
        return null;
    }

    /** Une borne posée = bloc de pierre taillée directement sous une bannière, au-dessus du sol. */
    public static boolean isMarked(ServerLevel level, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
            return true;
        }
        int y = surface(level, x, z);
        for (int dy = 0; dy <= 3; dy++) {
            if (level.getBlockState(new BlockPos(x, y - dy, z)).is(Blocks.CHISELED_STONE_BRICKS)) {
                return true;
            }
        }
        return false;
    }

    /** Grille ASCII des chunks autour d'un centre : lettre = faction, '#' = contesté, '.' = libre. */
    public static List<String> ascii(InfluenceMap map, ChunkPos center, int half, List<String> order) {
        java.util.ArrayList<String> rows = new java.util.ArrayList<>();
        for (int dz = -half; dz <= half; dz++) {
            StringBuilder sb = new StringBuilder();
            for (int dx = -half; dx <= half; dx++) {
                var c = map.claim(new ChunkPos(center.x() + dx, center.z() + dz));
                if (dx == 0 && dz == 0) {
                    sb.append('@');
                } else if (c.free()) {
                    sb.append('.');
                } else if (c.contested()) {
                    sb.append('#');
                } else {
                    int i = order.indexOf(c.owner());
                    sb.append((char) ('A' + Math.max(0, i) % 26));
                }
            }
            rows.add(sb.toString());
        }
        return rows;
    }
}
