package rrp.neoforge.gametest;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rrp.core.Role;
import rrp.core.SimEvent;
import rrp.core.Resource;
import rrp.neoforge.TownHall;
import rrp.neoforge.WarehouseService;
import rrp.neoforge.Worlds;
import rrp.neoforge.RrpMod;
import rrp.neoforge.VillageSite;
import rrp.neoforge.urban.BiomeStyle;
import rrp.neoforge.urban.BlueprintPool;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GameTests du mod, exécutés par le serveur de test sans tête ({@code gradlew runGameTestServer}).
 * Chaque test est synchrone (un seul tick) : l'état global du mod est remis à zéro au début, de sorte que les tests
 * posés côte à côte ne se gênent pas. Le temps du mod est accéléré avec {@code TickService.advance}.
 */
@GameTestHolder(RrpMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RrpGameTests {

    private static final String ARENA = "arena";

    private RrpGameTests() {
    }

    // ---------- outils ----------

    private static final class Capture implements CommandSource {
        final List<String> lines = new ArrayList<>();

        @Override public void sendSystemMessage(Component c) { lines.add(c.getString()); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }

        String text() { return String.join("\n", lines); }
    }

    /** Exécute une commande {@code /rrp ...} à la position de l'arène et rend ce qu'elle a répondu. */
    private static String cmd(GameTestHelper h, String command) {
        Capture cap = new Capture();
        BlockPos at = h.absolutePos(new BlockPos(12, 1, 12));
        CommandSourceStack src = new CommandSourceStack(cap, at.getCenter(), Vec2.ZERO, h.getLevel(), 4, "gametest",
                Component.literal("gametest"), h.getLevel().getServer(), null);
        h.getLevel().getServer().getCommands().performPrefixedCommand(src, command);
        return cap.text();
    }

    private static void reset() {
        RrpMod.registry().clear();
    }

    private static Villager villager(GameTestHelper h, int x, int z) {
        Villager v = h.spawn(EntityType.VILLAGER, new BlockPos(x, 1, z));
        v.setNoAi(true);
        v.setPersistenceRequired();
        return v;
    }

    private static void bed(GameTestHelper h, int x, int z) {
        h.setBlock(new BlockPos(x, 1, z), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT));
        h.setBlock(new BlockPos(x + 1, 1, z), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD));
    }

    private static BlockPos barrel(GameTestHelper h, int x, int z, int bread) {
        BlockPos p = new BlockPos(x, 1, z);
        h.setBlock(p, Blocks.BARREL);
        if (bread > 0 && h.getBlockEntity(p) instanceof BarrelBlockEntity b) {
            ((Container) b).setItem(0, new ItemStack(Items.BREAD, bread));
        }
        return h.absolutePos(p);
    }

    /** Village complet : villageois, lits, tonneau-entrepôt désigné. Rend le site. */
    private static VillageSite village(GameTestHelper h, int villagers, int beds, int bread) {
        reset();
        for (int i = 0; i < villagers; i++) {
            villager(h, 10 + i % 4, 10 + i / 4);
        }
        for (int i = 0; i < beds; i++) {
            bed(h, 4, 3 + 2 * i);
        }
        BlockPos b = barrel(h, 14, 14, bread);
        RrpMod.scanner().scanNow();
        VillageSite site = RrpMod.registry().all().stream().findFirst().orElse(null);
        h.assertTrue(site != null, "no village created by the scan");
        RrpMod.warehouse().set(site, h.getLevel(), b);
        RrpMod.warehouse().refresh(site, h.getLevel());
        return site;
    }

    /** Retire halls, lecterns and chests de l'arène : les arènes voisines partagent le monde et le scanner les verrait. */
    private static void wipeTown(GameTestHelper h) {
        AABB b = h.getBounds();
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(b.minX, b.minY, b.minZ), BlockPos.containing(b.maxX, b.maxY, b.maxZ))) {
            var st = h.getLevel().getBlockState(p);
            if (st.is(Blocks.LECTERN) || st.is(Blocks.GOLD_BLOCK) || st.is(Blocks.CHEST)) {
                h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void setAll(GameTestHelper h, Role role) {
        for (Villager v : h.getLevel().getEntitiesOfClass(Villager.class, h.getBounds())) {
            RrpMod.adapter().setRole(v, role);
        }
    }

    private static boolean act(GameTestHelper h, VillageSite site) {
        boolean did = false;
        for (Villager v : RrpMod.adapter().present(site, h.getLevel())) {
            did |= RrpMod.behaviors().runOnce(site, h.getLevel(), v);
        }
        return did;
    }

    // ---------- tests ----------

    @GameTest(template = ARENA)
    public static void base(GameTestHelper h) {
        h.assertTrue(RrpMod.server() != null && RrpMod.lang() != null && RrpMod.lang().size() > 0, "mod not active");
        h.assertTrue(RrpMod.simConfig().get(rrp.core.SimConfig.Key.PERSISTENCE_FLUSH_S) > 0, "config not loaded");
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void commands(GameTestHelper h) {
        reset();
        h.assertTrue(cmd(h, "rrp help").contains("Commandes disponibles"), "help");
        h.assertTrue(cmd(h, "rrp bogus").contains("Sous-commande inconnue"), "unknown sub-command");
        h.assertTrue(cmd(h, "respublica reload").contains("config.yml relu"), "reload");
        h.assertTrue(cmd(h, "rrp village list").contains("Aucun village connu"), "empty village list");
        h.assertTrue(cmd(h, "rrp tick x").contains("Usage"), "tick usage");
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void village(GameTestHelper h) {
        VillageSite site = village(h, 2, 1, 32);
        h.assertTrue(site.state().population() == 2, "population " + site.state().population());
        h.assertTrue(site.state().beds() >= 1, "beds " + site.state().beds());
        h.assertTrue(site.hasWarehouse(), "warehouse");
        h.assertTrue(cmd(h, "rrp village list").contains(site.id()), "village list");
        h.assertTrue(cmd(h, "rrp status " + site.id()).contains("Villageois : 2"), "status");
        h.succeed();
    }

    @GameTest(template = ARENA, batch = "townHall")
    public static void townHall(GameTestHelper h) {
        try {
            townHallBody(h);
        } finally {
            wipeTown(h);
        }
    }

    private static void townHallBody(GameTestHelper h) {
        VillageSite site = village(h, 2, 2, 32);
        h.assertTrue(!site.hasHall(), "no hall yet");
        h.assertTrue(cmd(h, "rrp townhall info").contains("pas encore"), "info without hall");
        h.assertTrue(cmd(h, "rrp townhall build").contains("posé"), "hall built by command");
        h.assertTrue(site.hasHall(), "hall registered");
        int[] p = site.hall();
        h.assertTrue(TownHall.isHall(h.getLevel(), new BlockPos(p[0], p[1], p[2])), "gold block under lectern");
        // the scanner adopts a hall placed in the world
        site.clearHall();
        RrpMod.scanner().refreshNow(site);
        h.assertTrue(site.hasHall(), "scanner detects the hall");
        // destroyed hall is forgotten, then the builder makes a new one
        h.getLevel().setBlock(new BlockPos(p[0], p[1], p[2]), Blocks.AIR.defaultBlockState(), 3);
        RrpMod.scanner().refreshNow(site);
        h.assertTrue(!site.hasHall(), "destroyed hall forgotten");
        setAll(h, Role.BUILDER);
        h.assertTrue(act(h, site), "builder did nothing");
        h.assertTrue(site.hasHall(), "builder rebuilt the hall");
        h.assertTrue(site.inCore(Worlds.name(h.getLevel()), site.hall()[0], site.hall()[2], 12), "core contains hall");
        h.succeed();
    }

    @GameTest(template = ARENA, batch = "planner")
    public static void planner(GameTestHelper h) {
        try {
            VillageSite site = village(h, 8, 1, 0);
            BlockPos hall = h.absolutePos(new BlockPos(18, 1, 18));
            TownHall.build(h.getLevel(), hall);
            site.setHall(hall.getX(), hall.getY(), hall.getZ());
            setAll(h, Role.STEWARD);
            var store = WarehouseService.barrel(site, h.getLevel());
            store.setItem(0, new ItemStack(Items.OAK_LOG, 64));
            store.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
            store.setItem(2, new ItemStack(Items.OAK_LOG, 64));
            RrpMod.warehouse().refresh(site, h.getLevel());
            h.assertTrue(site.orders().isEmpty(), "no order yet");
            for (int i = 0; i < 3; i++) {
                act(h, site);
            }
            h.assertTrue(!site.orders().isEmpty(), "steward issued no order for a bed shortage");
            // the doctrine comes from the biome at this (varying) spot and may put its own building first: the
            // house-first rule of the neutral planner is covered by ExpansionPlannerTest
            h.assertTrue(site.orders().size() <= 3, "orders capped");
            h.succeed();
        } finally {
            wipeTown(h);
        }
    }

    @GameTest(template = ARENA, batch = "communityStore")
    public static void communityStore(GameTestHelper h) {
        try {
            communityStoreBody(h);
        } finally {
            wipeTown(h);
        }
    }

    private static void communityStoreBody(GameTestHelper h) {
        VillageSite site = village(h, 2, 2, 0);
        ServerLevel level = h.getLevel();
        BlockPos hall = h.absolutePos(new BlockPos(18, 2, 18));
        TownHall.build(level, hall);
        site.setHall(hall.getX(), hall.getY(), hall.getZ());
        BlockPos chestPos = hall.offset(2, 0, 0);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        // fill the barrel warehouse so that further items must go to the chest
        BarrelBlockEntity barrel = WarehouseService.barrel(site, level);
        for (int i = 0; i < barrel.getContainerSize(); i++) {
            barrel.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        ItemStack left = RrpMod.warehouse().deposit(site, level, new ItemStack(Items.OAK_LOG, 10));
        h.assertTrue(left.isEmpty(), "wood went into the community chest");
        h.assertTrue(level.getBlockEntity(chestPos) instanceof Container c && c.countItem(Items.OAK_LOG) == 10, "chest holds the logs");
        h.assertTrue(site.state().stock(Resource.WOOD) == 10, "wood stock " + site.state().stock(Resource.WOOD));
        h.assertTrue(site.state().stock(Resource.STONE) > 0, "stone counted from the barrel");
        // withdraw crosses containers
        h.assertTrue(RrpMod.warehouse().withdraw(site, level, Resource.WOOD, 4) == 4, "withdraw wood");
        h.assertTrue(site.state().stock(Resource.WOOD) == 6, "wood after withdraw");
        // a full store makes the collectors add a new chest
        Container chest = (Container) level.getBlockEntity(chestPos);
        for (int i = 0; i < chest.getContainerSize(); i++) {
            chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        int before = RrpMod.warehouse().communityChests(site, level);
        ItemStack more = RrpMod.warehouse().deposit(site, level, new ItemStack(Items.IRON_INGOT, 5));
        h.assertTrue(more.isEmpty(), "ore stored after auto chest");
        h.assertTrue(RrpMod.warehouse().communityChests(site, level) == before + 1, "a new chest was placed");
        h.assertTrue(cmd(h, "rrp status " + site.id()).contains("Coffres communautaires"), "status shows chests");
        h.succeed();
    }

    @GameTest(template = ARENA, batch = "blueprints")
    public static void blueprints(GameTestHelper h) {
        for (BlueprintPool.Kind k : BlueprintPool.Kind.values()) {
            for (BiomeStyle s : BiomeStyle.values()) {
                var t = BlueprintPool.load(h.getLevel(), k, s);
                if (t.isEmpty()) { h.fail("blueprint missing: " + BlueprintPool.id(k, s)); return; }
                if (t.get().getSize().getX() < 1 || t.get().getSize().getY() < 1) { h.fail("empty blueprint " + k + "/" + s); return; }
            }
        }
        // place a desert house and a snow house: the palette follows the family
        BlockPos desert = h.absolutePos(new BlockPos(2, 1, 2));
        BlockPos snow = h.absolutePos(new BlockPos(13, 1, 2));
        place(h, BlueprintPool.Kind.HOUSE, BiomeStyle.DESERT, desert);
        place(h, BlueprintPool.Kind.HOUSE, BiomeStyle.SNOW, snow);
        h.assertTrue(h.getLevel().getBlockState(desert.offset(0, 1, 0)).is(Blocks.CUT_SANDSTONE), "desert corner is sandstone");
        h.assertTrue(h.getLevel().getBlockState(snow.offset(0, 1, 0)).is(Blocks.SPRUCE_LOG), "snow corner is spruce");
        h.assertTrue(h.getLevel().getBlockState(snow.offset(1, 1, 4)).getBlock() instanceof BedBlock, "house has a bed");
        h.succeed();
    }

    private static void place(GameTestHelper h, BlueprintPool.Kind k, BiomeStyle s, BlockPos at) {
        BlueprintPool.load(h.getLevel(), k, s).orElseThrow().placeInWorld(h.getLevel(), at, at,
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                h.getLevel().getRandom(), 2);
    }

    @GameTest(template = ARENA, batch = "builders")
    public static void builders(GameTestHelper h) {
        try {
            buildersBody(h);
        } finally {
            wipeTown(h);
        }
    }

    private static void buildersBody(GameTestHelper h) {
        VillageSite site = village(h, 2, 2, 0);
        ServerLevel level = h.getLevel();
        BlockPos hall = h.absolutePos(new BlockPos(18, 1, 18));
        TownHall.build(level, hall);
        site.setHall(hall.getX(), hall.getY(), hall.getZ());
        // a bump of earth and a hole: the leveler must cut and fill; a tree is cleared
        h.setBlock(new BlockPos(6, 2, 6), Blocks.DIRT);
        h.setBlock(new BlockPos(6, 3, 6), Blocks.DIRT);
        h.setBlock(new BlockPos(7, 2, 6), Blocks.OAK_LOG);
        h.setBlock(new BlockPos(7, 3, 6), Blocks.OAK_LEAVES);
        h.setBlock(new BlockPos(9, 1, 6), Blocks.AIR);
        BlockPos at = h.absolutePos(new BlockPos(8, 1, 6));
        site.orders().add(rrp.neoforge.urban.BuildOrder.of(rrp.neoforge.urban.BuildType.HOUSE, at.getX(), at.getY(), at.getZ()));
        setAll(h, Role.BUILDER);
        // no materials: the order waits
        h.assertTrue(!act(h, site), "builder started without materials");
        h.assertTrue(site.construction() == null && site.orders().size() == 1, "order kept while waiting");
        BarrelBlockEntity barrel = WarehouseService.barrel(site, level);
        barrel.setItem(0, new ItemStack(Items.OAK_LOG, 64));
        RrpMod.warehouse().refresh(site, level);
        int woodBefore = site.state().stock(Resource.WOOD);
        int beds = site.state().beds();
        h.assertTrue(act(h, site), "builder did not start");
        var cs = site.construction();
        h.assertTrue(cs != null, "construction registered");
        h.assertTrue(site.state().stock(Resource.WOOD) < woodBefore, "materials consumed from the store");
        // it takes several actions, and a reload in the middle resumes the same site
        h.assertTrue(site.construction() != null, "still under construction after the first action");
        BlockPos origin = cs.origin();
        h.assertTrue(site.contains(Worlds.name(level), origin.getX(), origin.getZ()), "inside the borders");
        h.assertTrue(rrp.neoforge.RrpMod.persistence().flush(), "flush");
        RrpMod.persistence().load();
        VillageSite again = RrpMod.registry().all().stream().findFirst().orElseThrow();
        h.assertTrue(again.construction() != null && again.construction().origin().equals(origin), "resumed after reload");
        int guard = 0;
        while (again.construction() != null && guard++ < 80) {
            for (Villager v : RrpMod.adapter().present(again, level)) {
                RrpMod.behaviors().runOnce(again, level, v);
            }
        }
        h.assertTrue(again.construction() == null, "construction finished");
        h.assertTrue(guard > 1, "construction took several actions");
        h.assertTrue(again.orders().isEmpty(), "order completed");
        h.assertTrue(again.built().getOrDefault(rrp.neoforge.urban.BuildType.HOUSE, 0) == 1, "built counter");
        h.assertTrue(again.state().beds() > beds, "beds counted");
        h.assertTrue(level.getBlockState(origin.offset(0, 1, 0)).is(Blocks.OAK_LOG), "house corner placed");
        h.assertTrue(level.getBlockState(origin.offset(1, 1, 4)).getBlock() instanceof BedBlock, "bed placed");
        h.assertTrue(level.getBlockState(origin.offset(-1, 1, -1)).isAir() || level.getBlockState(origin.offset(-1, 1, -1)).canBeReplaced(),
                "margin cleared");
        h.assertTrue(level.getBlockState(new BlockPos(hall.getX(), hall.getY(), hall.getZ())).is(Blocks.GOLD_BLOCK), "hall untouched");
        for (Villager v : level.getEntitiesOfClass(Villager.class, h.getBounds())) {
            var feet = level.getBlockState(v.blockPosition());
            h.assertTrue(!feet.is(net.minecraft.tags.BlockTags.PLANKS) && !feet.is(net.minecraft.tags.BlockTags.LOGS), "villager walled in");
        }
        h.succeed();
    }

    @GameTest(template = ARENA, batch = "defense")
    public static void defense(GameTestHelper h) {
        try {
            defenseBody(h);
        } finally {
            wipeTown(h);
        }
    }

    private static void defenseBody(GameTestHelper h) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
                for (int y = 2; y < 12; y++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        VillageSite site = village(h, 14, 2, 0);
        ServerLevel level = h.getLevel();
        BlockPos hall = h.absolutePos(new BlockPos(12, 2, 12));
        TownHall.build(level, hall);
        site.setHall(hall.getX(), hall.getY(), hall.getZ());
        // a finished road leaving the hall to the north: the ring must open a gate where it crosses
        int[] path = new int[2 * 12];
        for (int i = 0; i < 12; i++) {
            path[2 * i] = hall.getX();
            path[2 * i + 1] = hall.getZ() - i;
        }
        site.roads().add(new rrp.neoforge.urban.RoadNetwork.Road(hall.getX(), hall.getZ(), hall.getX(), hall.getZ() - 11, false, path, 12, 12));
        BarrelBlockEntity barrel = WarehouseService.barrel(site, level);
        barrel.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
        barrel.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        barrel.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
        RrpMod.warehouse().refresh(site, level);
        setAll(h, Role.BUILDER);
        var d = site.defense();
        h.assertTrue(site.state().population() >= 14 && !d.started(), "setup: population " + site.state().population());
        // trigger by population: the first guard tower is ordered on the borders
        RrpMod.behaviors().runOnce(site, level, RrpMod.adapter().present(site, level).get(0));
        h.assertTrue(d.started() && d.towersIssued() == 1 && site.orders().size() == 1
                && site.orders().get(0).type() == rrp.neoforge.urban.BuildType.TOWER, "first tower not ordered");
        site.orders().clear();
        d.restore(rrp.neoforge.urban.Defense.PALISADE, 9, 0, 0, 99);
        int guard = 0;
        var builder = RrpMod.adapter().present(site, level).get(0);
        while (!d.palisadeDone() && guard++ < 60) {
            RrpMod.behaviors().runOnce(site, level, builder);
        }
        h.assertTrue(d.palisadeDone(), "palisade not finished");
        int cx = hall.getX();
        int cz = hall.getZ();
        int gates = 0;
        for (int i = 0; i < d.length(); i++) {
            int[] c = rrp.neoforge.urban.DefensePlan.ringAt(cx, cz, 9, i);
            h.assertTrue(rrp.neoforge.urban.DefensePlanner.closed(level, c[0], c[1], hall.getY()), "palisade hole at ring " + i);
            var b = level.getBlockState(new BlockPos(c[0], hall.getY(), c[1]));
            if (b.is(Blocks.OAK_FENCE_GATE)) {
                gates++;
            } else {
                h.assertTrue(b.is(Blocks.OAK_FENCE), "palisade block at ring " + i);
            }
        }
        h.assertTrue(gates >= 1 && level.getBlockState(new BlockPos(cx, hall.getY(), cz - 9)).is(Blocks.OAK_FENCE_GATE), "gate at the road crossing");
        // upgrade to stone once the stone stock and the population allow it
        guard = 0;
        while (!d.stoneDone() && guard++ < 80) {
            act(h, site);
        }
        h.assertTrue(d.stage() == rrp.neoforge.urban.Defense.STONE && d.stoneDone(), "stone upgrade not finished");
        for (int i = 0; i < d.length(); i++) {
            int[] c = rrp.neoforge.urban.DefensePlan.ringAt(cx, cz, 9, i);
            var b = level.getBlockState(new BlockPos(c[0], hall.getY(), c[1]));
            h.assertTrue(b.is(Blocks.STONE_BRICK_WALL) || b.is(Blocks.OAK_FENCE_GATE), "stone wall at ring " + i);
        }
        h.assertTrue(rrp.neoforge.RrpMod.persistence().flush(), "flush");
        RrpMod.persistence().load();
        VillageSite again = RrpMod.registry().all().stream().findFirst().orElseThrow();
        h.assertTrue(again.defense().stage() == rrp.neoforge.urban.Defense.STONE && again.defense().radius() == 9
                && again.defense().upgraded() == d.length(), "defense persisted");
        h.succeed();
    }

    @GameTest(template = ARENA, batch = "territory")
    public static void territory(GameTestHelper h) {
        try {
            territoryBody(h);
        } finally {
            wipeTown(h);
        }
    }

    private static void territoryBody(GameTestHelper h) {
        VillageSite site = village(h, 4, 2, 0);
        ServerLevel level = h.getLevel();
        BlockPos hall = h.absolutePos(new BlockPos(12, 2, 12));
        TownHall.build(level, hall);
        site.setHall(hall.getX(), hall.getY(), hall.getZ());
        var map = rrp.neoforge.TerritoryService.build(RrpMod.registry().all(), site.worldName(), RrpMod.simConfig());
        var chunk = rrp.core.territory.ChunkPos.ofBlock(hall.getX(), hall.getZ());
        h.assertTrue(site.id().equals(map.owner(chunk)), "hall chunk claimed by its village");
        h.assertTrue(map.owner(new rrp.core.territory.ChunkPos(chunk.x() + 20, chunk.z())) == null, "far chunk is free");
        var posts = rrp.neoforge.TerritoryService.posts(map, site.id());
        h.assertTrue(!posts.isEmpty(), "border posts planned");
        // a builder places posts one by one until none is missing for the chunks around the arena
        setAll(h, Role.BUILDER);
        site.state().setBeds(20);
        BarrelBlockEntity barrel = WarehouseService.barrel(site, level);
        barrel.setItem(0, new ItemStack(Items.OAK_PLANKS, 1));
        RrpMod.warehouse().refresh(site, level);
        var builder = RrpMod.adapter().present(site, level).get(0);
        int[] first = rrp.neoforge.TerritoryService.nextMissing(level, map, site.id());
        h.assertTrue(first != null, "posts missing at start");
        RrpMod.behaviors().runOnce(site, level, builder);
        h.assertTrue(rrp.neoforge.TerritoryService.isMarked(level, first[0], first[1]), "first post placed");
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, first[0], first[1]);
        h.assertTrue(level.getBlockState(new BlockPos(first[0], y - 1, first[1])).getBlock() instanceof net.minecraft.world.level.block.BannerBlock,
                "banner on top of the post");
        h.succeed();
    }

    @GameTest(template = ARENA, batch = "territory")
    public static void exploitation(GameTestHelper h) {
        rrp.core.SimConfig saved = RrpMod.simConfig();
        try {
            exploitationBody(h);
        } finally {
            RrpMod.setSimConfig(saved);
            rrp.neoforge.ExploitationWatcher.reset();
            wipeTown(h);
        }
    }

    private static void exploitationBody(GameTestHelper h) {
        rrp.neoforge.ExploitationWatcher.reset();
        VillageSite site = village(h, 2, 2, 16);
        ServerLevel level = h.getLevel();
        level.setDayTime(6000);
        rrp.core.SimConfig saved = RrpMod.simConfig();
        RrpMod.setSimConfig(saved.with(rrp.core.SimConfig.Key.TERRITORY_HALL_RADIUS, 8)
                .with(rrp.core.SimConfig.Key.PROVOCATION_WARN, 2).with(rrp.core.SimConfig.Key.PROVOCATION_EXPEL, 4)
                .with(rrp.core.SimConfig.Key.PROVOCATION_WAR, 4));
        // intruder's hall is far away; a neighbour owns the arena's chunk
        BlockPos far = h.absolutePos(new BlockPos(320, 2, 12));
        site.setHall(far.getX(), far.getY(), far.getZ());
        BlockPos ownerHall = h.absolutePos(new BlockPos(60, 2, 12));
        VillageSite owner = RrpMod.registry().create(site.worldName(), ownerHall.getX(), ownerHall.getY(), ownerHall.getZ(), 5);
        owner.setHall(ownerHall.getX(), ownerHall.getY(), ownerHall.getZ());
        var map = rrp.neoforge.TerritoryService.build(RrpMod.registry().all(), site.worldName(), RrpMod.simConfig());
        BlockPos wheat = new BlockPos(12, 1, 12);
        h.assertTrue(owner.id().equals(map.owner(rrp.core.territory.ChunkPos.ofBlock(
                h.absolutePos(wheat).getX(), h.absolutePos(wheat).getZ()))), "neighbour owns the arena chunk");
        h.setBlock(new BlockPos(12, 0, 12), Blocks.FARMLAND);
        h.setBlock(wheat, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        setAll(h, Role.FARMER);
        // 1. peaceful doctrine: the farmer avoids the foreign chunk
        h.assertTrue(!act(h, site), "farmer worked in a foreign chunk");
        h.assertTrue(h.getBlockState(wheat).getValue(CropBlock.AGE) == 7, "wheat untouched");
        h.assertTrue(rrp.neoforge.ExploitationWatcher.provocation(RrpMod.simConfig()).score(owner.id(), site.id()) == 0,
                "no provocation without trespass");
        // 2. aggressive: it works there and the owner's provocation climbs to a border war
        RrpMod.setSimConfig(RrpMod.simConfig().with(rrp.core.SimConfig.Key.TERRITORY_TRESPASS, 1));
        for (int i = 0; i < 8; i++) {
            h.setBlock(wheat, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
            act(h, site);
        }
        var prov = rrp.neoforge.ExploitationWatcher.provocation(RrpMod.simConfig());
        h.assertTrue(prov.score(owner.id(), site.id()) >= 4, "provocation raised: " + prov.score(owner.id(), site.id()));
        h.assertTrue(prov.stage(owner.id(), site.id()) == rrp.core.territory.Provocation.Stage.WAR, "stage war");
        h.assertTrue(rrp.neoforge.ExploitationWatcher.atWar(owner.id(), site.id()), "border war declared");
        // 3. expulsion: the owner's guards send the intruder's villagers on its land back home
        for (Villager v : level.getEntitiesOfClass(Villager.class, h.getBounds())) {
            v.teleportTo(h.absolutePos(wheat).getX() + 0.5, h.absolutePos(wheat).getY(), h.absolutePos(wheat).getZ() + 0.5);
        }
        int n = rrp.neoforge.ExploitationWatcher.expel(level, owner, site, RrpMod.simConfig(), RrpMod.registry());
        h.assertTrue(n >= 1, "villagers expelled: " + n);
        h.assertTrue(level.getEntitiesOfClass(Villager.class, h.getBounds()).isEmpty(), "arena emptied by the expulsion");
        h.succeed();
    }

    @GameTest(template = ARENA, batch = "roads")
    public static void doctrineBehaviours(GameTestHelper h) {
        try {
            rrp.neoforge.ExploitationWatcher.reset();
            VillageSite site = village(h, 2, 2, 16);
            ServerLevel level = h.getLevel();
            rrp.core.SimConfig saved = RrpMod.simConfig();
            try {
                RrpMod.setSimConfig(saved.with(rrp.core.SimConfig.Key.TERRITORY_HALL_RADIUS, 8));
                BlockPos far = h.absolutePos(new BlockPos(320, 2, 12));
                site.setHall(far.getX(), far.getY(), far.getZ());
                BlockPos ownerHall = h.absolutePos(new BlockPos(60, 2, 12));
                VillageSite owner = RrpMod.registry().create(site.worldName(), ownerHall.getX(), ownerHall.getY(), ownerHall.getZ(), 5);
                owner.setHall(ownerHall.getX(), ownerHall.getY(), ownerHall.getZ());
                // build priorities differ per doctrine (same situation, same stocks)
                var situation = new rrp.neoforge.urban.ExpansionPlanner.Situation(16, 16, 500, 500, new int[] {0, 64, 0},
                        java.util.Map.of(), java.util.List.of());
                var quotas = new rrp.neoforge.urban.ExpansionPlanner.Quotas(60, 40, 6, 3);
                var kinds = new java.util.HashSet<rrp.neoforge.urban.BuildType>();
                for (var d : rrp.core.territory.Doctrine.values()) {
                    kinds.add(rrp.neoforge.urban.ExpansionPlanner.plan(situation, quotas, 2, d).orElseThrow().type());
                }
                h.assertTrue(kinds.size() == 3, "three doctrines, three first buildings: " + kinds);
                // merchant city never trespasses, empire always does
                owner.setDoctrine(rrp.core.territory.Doctrine.ISOLATIONIST_THEOCRACY);
                site.setDoctrine(rrp.core.territory.Doctrine.MERCHANT_CITY);
                RrpMod.setSimConfig(RrpMod.simConfig().with(rrp.core.SimConfig.Key.TERRITORY_TRESPASS, 1));
                h.assertTrue(!rrp.neoforge.ExploitationWatcher.aggressive(site, RrpMod.simConfig()), "merchant stays neutral");
                // theocracy expels strangers standing on its chunks
                for (Villager v : level.getEntitiesOfClass(Villager.class, h.getBounds())) {
                    v.teleportTo(h.absolutePos(new BlockPos(12, 1, 12)).getX() + 0.5, h.absolutePos(new BlockPos(12, 1, 12)).getY(),
                            h.absolutePos(new BlockPos(12, 1, 12)).getZ() + 0.5);
                }
                int n = rrp.neoforge.ExploitationWatcher.expelStrangers(level, owner, RrpMod.simConfig(), RrpMod.registry());
                h.assertTrue(n >= 1, "theocracy expelled strangers: " + n);
                VillageSite empire = RrpMod.registry().create(site.worldName(), ownerHall.getX() + 400, ownerHall.getY(), ownerHall.getZ(), 5);
                empire.setDoctrine(rrp.core.territory.Doctrine.WARLIKE_EMPIRE);
                h.assertTrue(rrp.neoforge.ExploitationWatcher.expelStrangers(level, empire, RrpMod.simConfig(), RrpMod.registry()) == 0,
                        "empire does not expel");
                h.assertTrue(rrp.neoforge.ExploitationWatcher.aggressive(empire,
                        RrpMod.simConfig().with(rrp.core.SimConfig.Key.TERRITORY_TRESPASS, 0)), "empire is aggressive by doctrine");
            } finally {
                RrpMod.setSimConfig(saved);
                rrp.neoforge.ExploitationWatcher.reset();
            }
            h.succeed();
        } finally {
            wipeTown(h);
        }
    }

    @GameTest(template = ARENA, batch = "roads")
    public static void roads(GameTestHelper h) {
        try {
            roadsBody(h);
        } finally {
            wipeTown(h);
        }
    }

    private static void roadsBody(GameTestHelper h) {
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
                h.setBlock(new BlockPos(x, 11, z), Blocks.AIR);
            }
        }
        VillageSite site = village(h, 8, 2, 0);
        ServerLevel level = h.getLevel();
        BlockPos hall = h.absolutePos(new BlockPos(18, 2, 18));
        TownHall.build(level, hall);
        site.setHall(hall.getX(), hall.getY(), hall.getZ());
        BlockPos target = h.absolutePos(new BlockPos(3, 2, 4));
        // a small pond between hall and target: the road must go around it
        for (int x = 9; x <= 11; x++) {
            for (int z = 8; z <= 14; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
            }
        }
        var road = new rrp.neoforge.urban.RoadNetwork.Road(hall.getX(), hall.getZ(), target.getX(), target.getZ(), true, null, 0, 0);
        site.roads().add(road);
        BarrelBlockEntity barrel = WarehouseService.barrel(site, level);
        barrel.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        barrel.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        RrpMod.warehouse().refresh(site, level);
        setAll(h, Role.BUILDER);
        h.assertTrue(act(h, site), "builder did not start the road");
        h.assertTrue(road.hasPath() && road.laid() > 0, "path planned and tiles laid");
        h.assertTrue(rrp.neoforge.RrpMod.persistence().flush(), "flush");
        RrpMod.persistence().load();
        VillageSite again = RrpMod.registry().all().stream().findFirst().orElseThrow();
        h.assertTrue(again.roads().size() == 1 && again.roads().get(0).length() == road.length()
                && again.roads().get(0).laid() == road.laid(), "road persisted");
        var r = again.roads().get(0);
        int guard = 0;
        while (!r.laidDone() && guard++ < 60) {
            act(h, again);
        }
        h.assertTrue(r.laidDone(), "road laid");
        int[] p = r.path();
        int y = hall.getY() - 1;
        for (int i = 0; i < r.length(); i++) {
            h.assertTrue(level.getBlockState(new BlockPos(p[2 * i], y, p[2 * i + 1])).is(Blocks.DIRT_PATH)
                    || p[2 * i] == hall.getX() && p[2 * i + 1] == hall.getZ()
                    || !level.getBlockState(new BlockPos(p[2 * i], y, p[2 * i + 1])).is(Blocks.WATER), "dirt path at step " + i);
            h.assertTrue(!level.getBlockState(new BlockPos(p[2 * i], y, p[2 * i + 1])).is(Blocks.WATER), "road in the water");
            if (i > 0) {
                h.assertTrue(Math.abs(p[2 * i] - p[2 * i - 2]) + Math.abs(p[2 * i + 1] - p[2 * i - 1]) == 1, "road connected");
            }
        }
        h.assertTrue(Math.abs(p[p.length - 2] - target.getX()) <= 6 && Math.abs(p[p.length - 1] - target.getZ()) <= 6, "reaches the target");
        h.assertTrue(Math.hypot(p[0] - hall.getX(), p[1] - hall.getZ()) <= 8, "starts at the hall");
        // wide road: the lateral neighbour is laid too
        int dirt = 0;
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) {
                if (level.getBlockState(h.absolutePos(new BlockPos(x, 1, z))).is(Blocks.DIRT_PATH)
                        || rrp.neoforge.urban.RoadNetwork.isRoadBlock(level.getBlockState(h.absolutePos(new BlockPos(x, 1, z))))) {
                    dirt++;
                }
            }
        }
        h.assertTrue(dirt >= r.length() * 3 / 2, "two tiles wide: " + dirt + " tiles for " + r.length() + " positions");
        // upgrade: enough stone and population -> paved
        guard = 0;
        while (!r.pavedDone() && guard++ < 60) {
            act(h, again);
        }
        h.assertTrue(r.pavedDone(), "road paved");
        h.assertTrue(rrp.neoforge.urban.RoadNetwork.allPaved(level, r, hall.getY() - 1), "every tile is stone");
        RrpMod.warehouse().refresh(again, level);
        h.assertTrue(again.state().stock(Resource.STONE) < 128, "stone consumed by the paving");
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void roles(GameTestHelper h) {
        village(h, 2, 2, 32);
        Villager v = h.getLevel().getEntitiesOfClass(Villager.class, h.getBounds()).get(0);
        for (Role r : Role.values()) {
            RrpMod.adapter().setRole(v, r);
            h.assertTrue(RrpMod.adapter().stateOf(v).role() == r, "role " + r);
        }
        RrpMod.adapter().setRole(v, Role.DIPLOMAT);
        h.assertTrue(cmd(h, "rrp inspect " + v.getUUID()).contains("Diplomate"), "inspect shows the role");
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void births(GameTestHelper h) {
        VillageSite site = village(h, 2, 4, 64);
        long before = RrpMod.births().births();
        for (int i = 0; i < 6 && RrpMod.births().births() == before; i++) {
            RrpMod.ticks().advance(12000);
            RrpMod.scanner().refreshNow(site);
        }
        h.assertTrue(RrpMod.births().births() > before, "no birth with food and free beds");
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void behaviors(GameTestHelper h) {
        VillageSite site = village(h, 2, 2, 16);
        ServerLevel level = h.getLevel();
        level.setDayTime(6000);
        BlockPos wheat = new BlockPos(12, 1, 12);
        BlockPos log = new BlockPos(8, 1, 12);
        BlockPos ore = new BlockPos(11, 1, 8);
        h.setBlock(new BlockPos(12, 0, 12), Blocks.FARMLAND);
        h.setBlock(wheat, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        setAll(h, Role.FARMER);
        h.assertTrue(act(h, site), "farmer did nothing");
        h.assertTrue(!h.getBlockState(wheat).is(Blocks.WHEAT) || h.getBlockState(wheat).getValue(CropBlock.AGE) < 7,
                "wheat not harvested");

        h.setBlock(log, Blocks.OAK_LOG);
        h.setBlock(log.above(), Blocks.OAK_LOG);
        h.setBlock(log.above(2), Blocks.OAK_LEAVES);
        setAll(h, Role.FORESTER);
        act(h, site);
        h.assertTrue(!h.getBlockState(log).is(Blocks.OAK_LOG) || !h.getBlockState(log.above()).is(Blocks.OAK_LOG),
                "forester left the tree");

        h.setBlock(ore, Blocks.IRON_ORE);
        setAll(h, Role.MINER);
        act(h, site);
        h.assertTrue(h.getBlockState(ore).isAir(), "miner left the ore");

        Creeper creeper = h.spawn(EntityType.CREEPER, new BlockPos(14, 1, 8));
        creeper.setNoAi(true);
        setAll(h, Role.GUARD);
        act(h, site);
        h.assertTrue(!creeper.isAlive(), "guard left the creeper alive");
        h.succeed();
    }

    @GameTest(template = ARENA, batch = "war")
    public static void crisesAndWar(GameTestHelper h) {
        VillageSite site = village(h, 6, 1, 0);
        setAll(h, Role.GUARD);
        List<SimEvent> events = new ArrayList<>();
        RrpMod.crises().addListener(events::add);
        h.assertTrue(!cmd(h, "rrp famine " + site.id()).isEmpty(), "famine command silent");
        for (int i = 0; i < 8; i++) {
            RrpMod.ticks().advance(12000);
        }
        h.assertTrue(events.stream().anyMatch(e -> e instanceof SimEvent.CrisisChanged), "no crisis change");
        h.assertTrue(events.stream().anyMatch(e -> e instanceof SimEvent.Exile), "no exile");
        h.assertTrue(events.stream().anyMatch(e -> e instanceof SimEvent.WarStarted), "no civil war");
        h.assertTrue(events.stream().anyMatch(e -> e instanceof SimEvent.Exile x && x.campId() != null), "no dissident camp");
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void perf(GameTestHelper h) {
        village(h, 4, 3, 32);
        RrpMod.ticks().resetCost();
        RrpMod.ticks().advance(2000);
        double ms = RrpMod.ticks().averageMsPerTick();
        h.assertTrue(ms < 5.0, "mod cost " + ms + " ms/tick");
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void diplomacy(GameTestHelper h) {
        VillageSite a = village(h, 2, 2, 10);
        rrp.core.SimConfig saved = RrpMod.simConfig();
        try {
            RrpMod.setSimConfig(saved.with(rrp.core.SimConfig.Key.DIPLOMACY_INTERVAL_S, 0)
                    .with(rrp.core.SimConfig.Key.CARAVAN_INTERVAL_S, 0));
            rrp.neoforge.DiplomacyService.reset();
            var cfg = RrpMod.simConfig();
            VillageSite b = RrpMod.registry().create(a.worldName(), a.x() + 40, a.y(), a.z(), 5);
            VillageSite c = RrpMod.registry().create(a.worldName(), a.x(), a.y(), a.z() + 40, 5);
            for (VillageSite s : new VillageSite[] {a, b, c}) {
                s.state().setStock(Resource.FOOD, 100);
            }
            rrp.neoforge.RelationService.change(a, b, 100, cfg);
            rrp.neoforge.RelationService.change(a, c, -100, cfg);
            rrp.neoforge.RelationService.change(b, c, -100, cfg);
            rrp.neoforge.DiplomacyService.evaluate(cfg, RrpMod.registry());
            h.assertTrue(rrp.neoforge.DiplomacyService.state(a.id(), b.id()) == rrp.core.diplomacy.Treaty.State.DEFENSIVE_ALLIANCE,
                    "alliance against the common enemy");
            h.assertTrue(rrp.neoforge.DiplomacyService.pending().isEmpty(), "no war while fed");
            c.state().setStock(Resource.FOOD, 0);
            rrp.neoforge.DiplomacyService.evaluate(cfg, RrpMod.registry());
            h.assertTrue(rrp.neoforge.DiplomacyService.pending().size() == 2, "ultimatums announced");
            h.assertTrue(rrp.neoforge.DiplomacyService.calm(a, c, cfg), "a diplomat cancels the declaration");
            h.assertTrue(rrp.neoforge.DiplomacyService.pending().size() == 1, "the other one stands");
            rrp.neoforge.DiplomacyService.evaluate(cfg, RrpMod.registry());
            h.assertTrue(rrp.neoforge.DiplomacyService.pending().size() == 2, "declared again");
            RrpMod.ticks().advance((long) (cfg.get(rrp.core.SimConfig.Key.DIPLOMACY_WAR_DELAY_S) * 20) + 20);
            h.assertTrue(rrp.neoforge.DiplomacyService.atWar(c.id(), a.id()), "war opened with a");
            h.assertTrue(rrp.neoforge.DiplomacyService.atWar(c.id(), b.id()), "ally joined the war");
            h.assertTrue(rrp.neoforge.DiplomacyService.state(a.id(), b.id()) == rrp.core.diplomacy.Treaty.State.DEFENSIVE_ALLIANCE,
                    "alliance kept");
        } finally {
            rrp.neoforge.DiplomacyService.reset();
            reset();
            RrpMod.setSimConfig(saved);
        }
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void caravan(GameTestHelper h) {
        VillageSite a = village(h, 2, 2, 10);
        ServerLevel level = h.getLevel();
        rrp.core.SimConfig saved = RrpMod.simConfig();
        try {
            RrpMod.setSimConfig(saved.with(rrp.core.SimConfig.Key.CARAVAN_AMBUSH_CHANCE, 0)
                    .with(rrp.core.SimConfig.Key.CARAVAN_INTERVAL_S, 0));
            rrp.neoforge.CaravanService.reset();
            Container ba = (Container) h.getBlockEntity(new BlockPos(14, 1, 14));
            ba.setItem(1, new ItemStack(Items.IRON_INGOT, 64));
            ba.setItem(2, new ItemStack(Items.IRON_INGOT, 56));
            BlockPos bp = barrel(h, 18, 18, 0);
            VillageSite b = RrpMod.registry().create(a.worldName(), bp.getX(), bp.getY(), bp.getZ(), 5);
            h.assertTrue(RrpMod.warehouse().set(b, level, bp), "second warehouse");
            Container bb = (Container) level.getBlockEntity(bp);
            for (int i = 0; i < 4; i++) {
                bb.setItem(i, new ItemStack(Items.BREAD, 50));
            }
            bb.setItem(5, new ItemStack(Items.IRON_INGOT, 5));
            RrpMod.warehouse().refresh(a, level);
            RrpMod.warehouse().refresh(b, level);
            int foodBefore = RrpMod.warehouse().read(a, level).getOrDefault(Resource.FOOD, 0);
            var sent = rrp.neoforge.CaravanService.dispatch(a, b, RrpMod.simConfig());
            h.assertTrue(sent.isPresent(), "a caravan should leave: iron for wheat");
            h.assertTrue(rrp.neoforge.CaravanService.active() == 1, "one caravan on the road");
            h.assertTrue(RrpMod.warehouse().read(a, level).getOrDefault(Resource.ORE, 0) < 120, "cargo left the warehouse");
            RrpMod.ticks().advance(4000);
            RrpMod.ticks().advance(4000);
            h.assertTrue(rrp.neoforge.CaravanService.completed() == 1, "caravan completed");
            h.assertTrue(rrp.neoforge.CaravanService.active() == 0, "no caravan left");
            int foodAfter = RrpMod.warehouse().read(a, level).getOrDefault(Resource.FOOD, 0);
            h.assertTrue(foodAfter > foodBefore, "wheat arrived: " + foodBefore + " -> " + foodAfter);
            h.assertTrue(RrpMod.warehouse().read(b, level).getOrDefault(Resource.ORE, 0) > 5, "iron delivered");
        } finally {
            rrp.neoforge.CaravanService.reset();
            reset();
            RrpMod.setSimConfig(saved);
        }
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void mobilization(GameTestHelper h) {
        VillageSite a = village(h, 4, 4, 10);
        ServerLevel level = h.getLevel();
        rrp.core.SimConfig saved = RrpMod.simConfig();
        try {
            RrpMod.setSimConfig(saved.with(rrp.core.SimConfig.Key.MOBILIZATION_INTERVAL_S, 0));
            var cfg = RrpMod.simConfig();
            rrp.neoforge.DiplomacyService.reset();
            rrp.neoforge.MobilizationService.reset();
            setAll(h, Role.GUARD);
            Container ba = (Container) h.getBlockEntity(new BlockPos(14, 1, 14));
            ba.setItem(1, new ItemStack(Items.IRON_INGOT, 12));
            ba.setItem(2, new ItemStack(Items.OAK_LOG, 4));
            RrpMod.warehouse().refresh(a, level);
            VillageSite b = RrpMod.registry().create(a.worldName(), a.x() + 40, a.y(), a.z(), 5);
            rrp.neoforge.MobilizationService.update(cfg, RrpMod.registry());
            h.assertTrue(rrp.neoforge.MobilizationService.force(a.id()) == null, "no regiment at peace");
            rrp.neoforge.DiplomacyService.open(a.id(), b.id(), RrpMod.registry());
            rrp.neoforge.MobilizationService.update(cfg, RrpMod.registry());
            var f = rrp.neoforge.MobilizationService.force(a.id());
            h.assertTrue(f != null, "regiment formed at war");
            h.assertTrue(f.size() == 2, "regiment capped at half the population: " + f.size());
            h.assertTrue(f.loadout().swords() == 2 && f.loadout().armors() == 2 && f.loadout().bows() == 2,
                    "fully equipped from stock: " + f.loadout());
            h.assertTrue(f.strength() == 2 * 3.5, "strength " + f.strength());
            h.assertTrue(RrpMod.warehouse().read(a, level).getOrDefault(Resource.ORE, 0) == 0, "iron consumed");
            int armed = 0;
            for (Villager v : level.getEntitiesOfClass(Villager.class, h.getBounds())) {
                if (v.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).is(Items.IRON_SWORD)) {
                    armed++;
                }
            }
            h.assertTrue(armed == 2, "two villagers carry a sword: " + armed);
            rrp.neoforge.DiplomacyService.treaties().set(a.id(), b.id(), rrp.core.diplomacy.Treaty.State.PEACE);
            rrp.neoforge.MobilizationService.update(cfg, RrpMod.registry());
            h.assertTrue(rrp.neoforge.MobilizationService.force(a.id()) == null, "demobilised at peace");
            for (Villager v : level.getEntitiesOfClass(Villager.class, h.getBounds())) {
                h.assertTrue(v.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty(), "gear removed");
            }
        } finally {
            rrp.neoforge.MobilizationService.reset();
            rrp.neoforge.DiplomacyService.reset();
            reset();
            RrpMod.setSimConfig(saved);
        }
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void siege(GameTestHelper h) {
        VillageSite a = village(h, 4, 4, 10);
        ServerLevel level = h.getLevel();
        rrp.core.SimConfig saved = RrpMod.simConfig();
        try {
            RrpMod.setSimConfig(saved.with(rrp.core.SimConfig.Key.MOBILIZATION_INTERVAL_S, 0)
                    .with(rrp.core.SimConfig.Key.SIEGE_INTERVAL_S, 0).with(rrp.core.SimConfig.Key.SIEGE_STANDOFF, 4)
                    .with(rrp.core.SimConfig.Key.SIEGE_BLOCK_CAP, 5));
            var cfg = RrpMod.simConfig();
            rrp.neoforge.DiplomacyService.reset();
            rrp.neoforge.MobilizationService.reset();
            rrp.neoforge.SiegeService.reset();
            int n = 0;
            for (Villager v : level.getEntitiesOfClass(Villager.class, h.getBounds())) {
                RrpMod.adapter().setRole(v, n++ % 2 == 0 ? Role.GUARD : Role.BUILDER);
            }
            Container ba = (Container) h.getBlockEntity(new BlockPos(14, 1, 14));
            ba.setItem(2, new ItemStack(Items.OAK_LOG, 40));
            RrpMod.warehouse().refresh(a, level);
            VillageSite b = RrpMod.registry().create(a.worldName(), a.x(), a.y(), a.z() - 8, 5);
            int y = h.absolutePos(new BlockPos(0, 1, 0)).getY();
            int ax = (int) Math.floor(a.x());
            int az = (int) Math.floor(a.z());
            for (BlockPos p : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, -2, 0)), h.absolutePos(new BlockPos(20, 8, 20)))) {
                if (level.getBlockState(p).is(Blocks.STONE)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            BlockPos door = new BlockPos(ax, y, az - 6);
            level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                            net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 3);
            level.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                            net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
            for (int x = ax - 4; x <= ax + 4; x++) {
                level.setBlock(new BlockPos(x, y, az - 7), Blocks.COBBLESTONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y + 1, az - 7), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
            rrp.neoforge.SiegeService.update(cfg, RrpMod.registry());
            h.assertTrue(rrp.neoforge.SiegeService.siege(a.id()) == null, "no siege at peace");
            rrp.neoforge.DiplomacyService.open(a.id(), b.id(), RrpMod.registry());
            rrp.neoforge.MobilizationService.update(cfg, RrpMod.registry());
            rrp.neoforge.SiegeService.update(cfg, RrpMod.registry());
            var s = rrp.neoforge.SiegeService.siege(a.id());
            h.assertTrue(s != null && s.machines().size() == 1
                    && s.machines().get(0).kind() == rrp.core.diplomacy.Siege.Kind.RAM, "the ram is assembled first");
            h.assertTrue(level.getBlockState(door).isAir() && level.getBlockState(door.above()).isAir(), "the ram broke the door");
            h.assertTrue(s.budget().used() == 2, "a door costs two blocks of the cap: " + s.budget().used());
            for (int i = 0; i < 12; i++) {
                rrp.neoforge.SiegeService.update(cfg, RrpMod.registry());
            }
            boolean cat = false;
            for (var m : s.machines()) {
                cat |= m.kind() == rrp.core.diplomacy.Siege.Kind.CATAPULT;
            }
            h.assertTrue(cat, "the catapult is assembled after the ram");
            int missing = 0;
            for (int x = ax - 4; x <= ax + 4; x++) {
                for (int dy = 0; dy < 2; dy++) {
                    missing += level.getBlockState(new BlockPos(x, y + dy, az - 7)).isAir() ? 1 : 0;
                }
            }
            h.assertTrue(s.budget().used() == 5 && s.breaches().size() == 5, "cap reached, never beyond: " + s.budget().used());
            h.assertTrue(missing == 3, "the catapult breached the rampart within the cap: " + missing);
            var ram = s.machines().get(0);
            var origin = ram.origin();
            h.assertTrue(rrp.neoforge.SiegeService.strike(a.id(), rrp.core.diplomacy.Siege.Kind.RAM, 100, level), "ram hit");
            h.assertTrue(level.getBlockState(origin.above()).isAir(), "destroyed ram leaves no blocks");
            h.assertTrue(s.machines().size() == 1, "only the catapult is left");
            rrp.neoforge.SiegeService.update(cfg, RrpMod.registry());
            h.assertTrue(s.machines().size() == 1, "no new ram once the cap is spent");
            rrp.neoforge.DiplomacyService.treaties().set(a.id(), b.id(), rrp.core.diplomacy.Treaty.State.PEACE);
            rrp.neoforge.MobilizationService.update(cfg, RrpMod.registry());
            rrp.neoforge.SiegeService.update(cfg, RrpMod.registry());
            h.assertTrue(rrp.neoforge.SiegeService.siege(a.id()) == null, "siege lifted at peace");
        } finally {
            rrp.neoforge.SiegeService.reset();
            rrp.neoforge.MobilizationService.reset();
            rrp.neoforge.DiplomacyService.reset();
            reset();
            RrpMod.setSimConfig(saved);
        }
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void warresolution(GameTestHelper h) {
        VillageSite a = village(h, 4, 4, 10);
        ServerLevel level = h.getLevel();
        rrp.core.SimConfig saved = RrpMod.simConfig();
        try {
            RrpMod.setSimConfig(saved.with(rrp.core.SimConfig.Key.MOBILIZATION_INTERVAL_S, 0)
                    .with(rrp.core.SimConfig.Key.WAR_LOOT_CAP, 20).with(rrp.core.SimConfig.Key.WAR_TRIBUTE_CAP, 10)
                    .with(rrp.core.SimConfig.Key.WAR_TRIBUTE_SHARE, 0.5));
            var cfg = RrpMod.simConfig();
            rrp.neoforge.DiplomacyService.reset();
            rrp.neoforge.MobilizationService.reset();
            rrp.neoforge.SiegeService.reset();
            rrp.neoforge.WarResolution.reset();
            setAll(h, Role.GUARD);
            BlockPos bp = barrel(h, 18, 18, 0);
            VillageSite b = RrpMod.registry().create(a.worldName(), bp.getX(), bp.getY(), bp.getZ(), 5);
            h.assertTrue(RrpMod.warehouse().set(b, level, bp), "second warehouse");
            Container bb = (Container) level.getBlockEntity(bp);
            for (int i = 0; i < 4; i++) {
                bb.setItem(i, new ItemStack(Items.BREAD, 50));
            }
            RrpMod.warehouse().refresh(a, level);
            RrpMod.warehouse().refresh(b, level);
            rrp.neoforge.DiplomacyService.open(a.id(), b.id(), RrpMod.registry());
            rrp.neoforge.MobilizationService.update(cfg, RrpMod.registry());
            h.assertTrue(rrp.neoforge.MobilizationService.force(a.id()) != null, "a regiment is mobilised");
            double rel = rrp.neoforge.RelationService.index(a, b, cfg);
            var r = rrp.neoforge.WarResolution.resolve(a, b, cfg, RrpMod.registry());
            h.assertTrue(r.winner().equals(a.id()) && r.kind() == rrp.core.diplomacy.WarOutcome.Kind.ANNEXATION,
                    "an unopposed regiment annexes: " + r);
            h.assertTrue(rrp.neoforge.DiplomacyService.state(a.id(), b.id()) == rrp.core.diplomacy.Treaty.State.PEACE,
                    "ceasefire after the verdict");
            h.assertTrue(rrp.neoforge.RelationService.index(a, b, cfg) > rel, "relation partly restored");
            h.assertTrue(a.id().equals(rrp.neoforge.WarResolution.ownerOf(b.id())), "claims transferred to the winner");
            int foodA = RrpMod.warehouse().read(a, level).getOrDefault(Resource.FOOD, 0);
            int foodB = RrpMod.warehouse().read(b, level).getOrDefault(Resource.FOOD, 0);
            int paid = rrp.neoforge.WarResolution.payTribute(cfg, RrpMod.registry());
            int foodA2 = RrpMod.warehouse().read(a, level).getOrDefault(Resource.FOOD, 0);
            int foodB2 = RrpMod.warehouse().read(b, level).getOrDefault(Resource.FOOD, 0);
            h.assertTrue(paid > 0 && paid <= 10 * 4 && foodA2 > foodA && foodB2 < foodB,
                    "tribute moves stock to the winner within the cap: " + paid);
            int loot = rrp.neoforge.WarResolution.pillage(a, b, cfg);
            int foodB3 = RrpMod.warehouse().read(b, level).getOrDefault(Resource.FOOD, 0);
            h.assertTrue(loot > 0 && loot <= 20 * 4 && foodB3 < foodB2, "pillage takes a capped share: " + loot);
        } finally {
            rrp.neoforge.WarResolution.reset();
            rrp.neoforge.SiegeService.reset();
            rrp.neoforge.MobilizationService.reset();
            rrp.neoforge.DiplomacyService.reset();
            reset();
            RrpMod.setSimConfig(saved);
        }
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void sovereignfounding(GameTestHelper h) {
        reset();
        rrp.neoforge.PlayerService.ledger().clear();
        var player = h.makeMockServerPlayerInLevel();
        java.util.UUID id = player.getUUID();
        ServerLevel level = h.getLevel();
        BlockPos base = h.absolutePos(new BlockPos(6, 2, 6));
        rrp.neoforge.TownHall.build(level, base);
        h.assertTrue(rrp.neoforge.SovereignService.found(player, level, base) == rrp.core.player.Founding.Verdict.NOT_SOVEREIGN,
                "an observer cannot found");
        rrp.neoforge.PlayerService.ledger().switchRole(id, rrp.core.player.PlayerRole.SOVEREIGN, 0, 0);
        h.assertTrue(rrp.neoforge.SovereignService.found(player, level, base) == rrp.core.player.Founding.Verdict.TOO_POOR,
                "founding costs emeralds");
        rrp.neoforge.PlayerService.ledger().deposit(id, 40);
        h.assertTrue(rrp.neoforge.SovereignService.found(player, level, base) == rrp.core.player.Founding.Verdict.OK, "founded");
        VillageSite v = rrp.neoforge.SovereignService.ownedBy(id).stream().findFirst().orElse(null);
        h.assertTrue(v != null && id.toString().equals(v.owner()), "the player owns the new village");
        h.assertTrue(v.hasHall(), "the Town Hall is registered");
        h.assertTrue(rrp.neoforge.PlayerService.ledger().purse(id) == 8, "cost paid from the purse");
        BlockPos far = base.offset(10, 0, 0);
        h.assertTrue(rrp.neoforge.SovereignService.found(player, level, far) == rrp.core.player.Founding.Verdict.TOO_CLOSE,
                "min distance enforced");
        rrp.neoforge.PlayerService.ledger().clear();
        reset();
        wipeTown(h);
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void lawsandtaxes(GameTestHelper h) {
        VillageSite a = village(h, 4, 4, 10);
        ServerLevel level = h.getLevel();
        rrp.core.SimConfig saved = RrpMod.simConfig();
        rrp.neoforge.PlayerService.ledger().clear();
        rrp.neoforge.PlayerService.laws().clear();
        try {
            RrpMod.setSimConfig(saved.with(rrp.core.SimConfig.Key.CARAVAN_AMBUSH_CHANCE, 0));
            var cfg = RrpMod.simConfig();
            var player = h.makeMockServerPlayerInLevel();
            a.setOwner(java.util.UUID.randomUUID().toString());
            var laws = rrp.neoforge.PlayerService.laws().of(a.id());
            // taxes: a levy reaches the warehouse, a heavy burden costs loyalty
            laws.setTax("FOOD", 0.4);
            int before = RrpMod.warehouse().read(a, level).getOrDefault(Resource.FOOD, 0);
            int paid = rrp.neoforge.LawService.collect(cfg, RrpMod.registry());
            int after = RrpMod.warehouse().read(a, level).getOrDefault(Resource.FOOD, 0);
            h.assertTrue(paid > 0 && after - before == paid, "tax paid into the warehouse: " + paid);
            double loyal = a.state().villagers().get(0).loyalty();
            laws.setTax("ORE", 0.5);
            rrp.neoforge.LawService.collect(cfg, RrpMod.registry());
            h.assertTrue(a.state().villagers().get(0).loyalty() < loyal, "a heavy burden lowers loyalty");
            // conscription share is capped by config
            laws.setConscription(0.9);
            h.assertTrue(rrp.neoforge.LawService.conscription(a.id(), cfg) == cfg.get(rrp.core.SimConfig.Key.LAW_CONSCRIPTION_MAX),
                    "conscription capped");
            // embargo stops the caravans
            Container ba = (Container) h.getBlockEntity(new BlockPos(14, 1, 14));
            ba.setItem(1, new ItemStack(Items.IRON_INGOT, 64));
            ba.setItem(2, new ItemStack(Items.IRON_INGOT, 56));
            BlockPos bp = barrel(h, 18, 18, 0);
            VillageSite b = RrpMod.registry().create(a.worldName(), bp.getX(), bp.getY(), bp.getZ(), 5);
            h.assertTrue(RrpMod.warehouse().set(b, level, bp), "second warehouse");
            Container bb = (Container) level.getBlockEntity(bp);
            for (int i = 0; i < 4; i++) {
                bb.setItem(i, new ItemStack(Items.BREAD, 50));
            }
            bb.setItem(5, new ItemStack(Items.IRON_INGOT, 5));
            RrpMod.warehouse().refresh(a, level);
            RrpMod.warehouse().refresh(b, level);
            rrp.neoforge.CaravanService.reset();
            laws.toggleEmbargo(b.id());
            h.assertTrue(rrp.neoforge.CaravanService.dispatch(a, b, cfg).isEmpty(), "embargo stops the caravan");
            laws.toggleEmbargo(b.id());
            h.assertTrue(rrp.neoforge.CaravanService.dispatch(a, b, cfg).isPresent(), "caravan leaves once lifted");
            // foreigners' building ban
            BlockPos inside = h.absolutePos(new BlockPos(12, 2, 12));
            rrp.neoforge.PlayerService.ledger().switchRole(player.getUUID(), rrp.core.player.PlayerRole.MERCHANT, 0, 0);
            h.assertTrue(!rrp.neoforge.LawService.bans(player, level, inside), "no ban by default");
            laws.setForeignersBuildBan(true);
            h.assertTrue(rrp.neoforge.LawService.bans(player, level, inside), "foreign builder refused");
            rrp.neoforge.PlayerService.ledger().switchRole(player.getUUID(), rrp.core.player.PlayerRole.OBSERVER, 0, 0);
            h.assertTrue(!rrp.neoforge.LawService.bans(player, level, inside), "observers are never targeted");
        } finally {
            rrp.neoforge.CaravanService.reset();
            rrp.neoforge.PlayerService.ledger().clear();
            rrp.neoforge.PlayerService.laws().clear();
            reset();
            RrpMod.setSimConfig(saved);
        }
        h.succeed();
    }

    @GameTest(template = ARENA, batch = "planner")
    public static void urbanplanning(GameTestHelper h) {
        rrp.neoforge.PlayerService.zones().clear();
        try {
            VillageSite site = village(h, 8, 1, 0);
            BlockPos hall = h.absolutePos(new BlockPos(18, 1, 18));
            TownHall.build(h.getLevel(), hall);
            site.setHall(hall.getX(), hall.getY(), hall.getZ());
            setAll(h, Role.STEWARD);
            var store = WarehouseService.barrel(site, h.getLevel());
            store.setItem(0, new ItemStack(Items.OAK_LOG, 64));
            store.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
            store.setItem(2, new ItemStack(Items.OAK_LOG, 64));
            RrpMod.warehouse().refresh(site, h.getLevel());
            int hx = hall.getX();
            int hz = hall.getZ();
            h.assertTrue(rrp.neoforge.PlanService.addZone(site, rrp.core.player.ZoneType.FARM, hx + 8, hz - 30, hx + 30, hz - 8)
                    == rrp.neoforge.PlanService.Verdict.OK, "farm plot accepted");
            h.assertTrue(rrp.neoforge.PlanService.addZone(site, rrp.core.player.ZoneType.FORBIDDEN, hx - 30, hz - 8, hx - 8, hz + 8)
                    == rrp.neoforge.PlanService.Verdict.OK, "forbidden plot accepted");
            h.assertTrue(rrp.neoforge.PlanService.addZone(site, rrp.core.player.ZoneType.FARM, hx, hz, hx + 500, hz)
                    == rrp.neoforge.PlanService.Verdict.TOO_BIG, "oversized plot refused");
            h.assertTrue(rrp.neoforge.PlanService.addZone(site, rrp.core.player.ZoneType.FARM, hx + 3000, hz, hx + 3010, hz + 5)
                    == rrp.neoforge.PlanService.Verdict.OUTSIDE, "unclaimed land refused");
            h.assertTrue(rrp.neoforge.PlayerService.zones().peek(site.id()).size() == 2, "two plots kept");
            for (int i = 0; i < 6; i++) {
                act(h, site);
            }
            h.assertTrue(!site.orders().isEmpty(), "steward issued orders");
            var plots = rrp.neoforge.PlayerService.zones().peek(site.id());
            for (var o : site.orders()) {
                var at = rrp.core.player.Zones.typeAt(plots, o.x(), o.z());
                h.assertTrue(at != rrp.core.player.ZoneType.FORBIDDEN, "nothing in the forbidden plot: " + o);
                if (o.type() == rrp.neoforge.urban.BuildType.FARM) {
                    h.assertTrue(at == rrp.core.player.ZoneType.FARM, "farm stands in the farm plot: " + o);
                } else {
                    h.assertTrue(at == null, o.type() + " stays outside every plot: " + o);
                }
            }
        } finally {
            rrp.neoforge.PlayerService.zones().clear();
            wipeTown(h);
        }
        h.succeed();
    }

    private static int emeralds(VillageSite site, GameTestHelper h) {
        return RrpMod.warehouse().count(site, h.getLevel(), Items.EMERALD);
    }

    private static VillageSite rawCamp(GameTestHelper h, VillageSite parent, int x, int z, int raiders) {
        BlockPos at = h.absolutePos(new BlockPos(x, 1, z));
        VillageSite camp = RrpMod.registry().create(parent.worldName(), at.getX(), at.getY(), at.getZ(), 6, rrp.core.Faction.DISSIDENT);
        camp.state().setParentId(parent.id());
        for (int i = 0; i < raiders; i++) {
            Villager v = villager(h, x + i, z);
            camp.state().villagers().add(new rrp.core.VillagerState(v.getUUID(), Role.GUARD, camp.id()));
        }
        return camp;
    }

    @GameTest(template = ARENA)
    public static void mercenarycontracts(GameTestHelper h) {
        VillageSite a = village(h, 4, 4, 10);
        ServerLevel level = h.getLevel();
        rrp.core.SimConfig saved = RrpMod.simConfig();
        var ledger = rrp.neoforge.PlayerService.ledger();
        var book = rrp.neoforge.PlayerService.contracts();
        ledger.clear();
        book.clear();
        rrp.neoforge.ContractService.reset();
        try {
            var cfg = RrpMod.simConfig();
            Container ba = (Container) h.getBlockEntity(new BlockPos(14, 1, 14));
            ba.setItem(6, new ItemStack(Items.EMERALD, 40));
            RrpMod.warehouse().refresh(a, level);
            var merc = h.makeMockServerPlayerInLevel();
            ledger.switchRole(merc.getUUID(), rrp.core.player.PlayerRole.MERCENARY, 0, 0);
            // a camp born of the village: the village posts a contract and escrows the pay
            VillageSite camp = rawCamp(h, a, 26, 20, 2);
            h.assertTrue(rrp.neoforge.ContractService.autoPost(cfg, RrpMod.registry()) == 1, "one contract posted");
            var c = book.live(a.id(), rrp.core.player.Contract.Type.CLEAR_CAMP, camp.id());
            h.assertTrue(c != null && c.pay() == 18, "pay = base + 2 raiders");
            h.assertTrue(emeralds(a, h) == 22, "pay escrowed out of the warehouse: " + emeralds(a, h));
            h.assertTrue(rrp.neoforge.ContractService.autoPost(cfg, RrpMod.registry()) == 0, "no duplicate");
            h.assertTrue(rrp.neoforge.ContractService.accept(merc, c.id(), cfg) == rrp.core.player.Contracts.Accept.OK, "accepted");
            rrp.neoforge.ContractService.update(1, cfg, RrpMod.registry());
            h.assertTrue(c.state() == rrp.core.player.Contract.State.ACCEPTED, "raiders still alive");
            // the mercenary clears the camp
            for (var s : new ArrayList<>(camp.state().villagers())) {
                rrp.neoforge.ContractService.onKill(merc.getUUID(), s.id());
                level.getEntity(s.id()).discard();
            }
            h.assertTrue(c.kills() == 2, "kills counted: " + c.kills());
            rrp.neoforge.ContractService.update(2, cfg, RrpMod.registry());
            h.assertTrue(c.state() == rrp.core.player.Contract.State.DONE && c.settled(), "contract done: " + c.state());
            h.assertTrue(ledger.purse(merc.getUUID()) == 18, "mercenary paid: " + ledger.purse(merc.getUUID()));
            h.assertTrue(book.reputation(merc.getUUID()) == 1, "reputation earned");
            RrpMod.registry().remove(camp.id()); // an emptied camp leaves the registry
            // a second camp, but the mercenary is too slow: the escrow goes back to the village
            VillageSite camp2 = rawCamp(h, a, 26, 24, 1);
            h.assertTrue(rrp.neoforge.ContractService.autoPost(cfg, RrpMod.registry()) == 1, "second contract");
            var c2 = book.live(a.id(), rrp.core.player.Contract.Type.CLEAR_CAMP, camp2.id());
            int before = emeralds(a, h);
            rrp.neoforge.ContractService.accept(merc, c2.id(), cfg);
            rrp.neoforge.ContractService.update(1e9, cfg, RrpMod.registry());
            h.assertTrue(c2.state() == rrp.core.player.Contract.State.FAILED && c2.settled(), "late contract fails: " + c2.state());
            h.assertTrue(emeralds(a, h) == before + c2.pay(), "escrow returned");
            h.assertTrue(ledger.purse(merc.getUUID()) == 18 && book.reputation(merc.getUUID()) == 0, "no pay, reputation lost");
            // war contracts: posting needs a war, victory plus a kill pays, a lost war refunds
            VillageSite b = RrpMod.registry().create(a.worldName(), a.x() + 60, a.y(), a.z(), 5);
            UUID foe = UUID.randomUUID();
            b.state().villagers().add(new rrp.core.VillagerState(foe, Role.GUARD, b.id()));
            h.assertTrue(rrp.neoforge.ContractService.post(a, rrp.core.player.Contract.Type.WAR_ATTACK, b.id(), 8, cfg)
                    == rrp.neoforge.ContractService.Verdict.UNKNOWN_OBJECTIVE, "no war, no contract");
            rrp.neoforge.DiplomacyService.treaties().set(a.id(), b.id(), rrp.core.diplomacy.Treaty.State.WAR);
            before = emeralds(a, h);
            h.assertTrue(rrp.neoforge.ContractService.post(a, rrp.core.player.Contract.Type.WAR_ATTACK, b.id(), 8, cfg)
                    == rrp.neoforge.ContractService.Verdict.OK, "war contract posted");
            h.assertTrue(emeralds(a, h) == before - 8, "war pay escrowed");
            var w = book.live(a.id(), rrp.core.player.Contract.Type.WAR_ATTACK, b.id());
            rrp.neoforge.ContractService.accept(merc, w.id(), cfg);
            rrp.neoforge.ContractService.onKill(merc.getUUID(), foe);
            UUID foe2 = UUID.randomUUID();
            b.state().villagers().add(new rrp.core.VillagerState(foe2, Role.GUARD, b.id()));
            rrp.neoforge.ContractService.onKill(merc.getUUID(), foe2);
            rrp.neoforge.DiplomacyService.treaties().set(a.id(), b.id(), rrp.core.diplomacy.Treaty.State.PEACE);
            rrp.neoforge.WarResolution.record(new rrp.neoforge.WarResolution.Result(a.id(), b.id(), rrp.core.diplomacy.WarOutcome.Kind.PILLAGE, 0));
            rrp.neoforge.ContractService.update(3, cfg, RrpMod.registry());
            h.assertTrue(w.state() == rrp.core.player.Contract.State.DONE, "war won: " + w.state());
            h.assertTrue(ledger.purse(merc.getUUID()) == 26, "war pay received: " + ledger.purse(merc.getUUID()));
            // a ceasefire without a verdict cancels and refunds, without blame
            rrp.neoforge.DiplomacyService.treaties().set(a.id(), b.id(), rrp.core.diplomacy.Treaty.State.WAR);
            before = emeralds(a, h);
            rrp.neoforge.ContractService.post(a, rrp.core.player.Contract.Type.WAR_DEFEND, b.id(), 6, cfg);
            var w2 = book.live(a.id(), rrp.core.player.Contract.Type.WAR_DEFEND, b.id());
            rrp.neoforge.ContractService.accept(merc, w2.id(), cfg);
            int rep = book.reputation(merc.getUUID());
            rrp.neoforge.DiplomacyService.treaties().set(a.id(), b.id(), rrp.core.diplomacy.Treaty.State.PEACE);
            rrp.neoforge.ContractService.update(4, cfg, RrpMod.registry());
            h.assertTrue(w2.state() == rrp.core.player.Contract.State.CANCELLED && emeralds(a, h) == before, "ceasefire refunds");
            h.assertTrue(book.reputation(merc.getUUID()) == rep, "no blame");
        } finally {
            rrp.neoforge.DiplomacyService.treaties().clear();
            rrp.neoforge.ContractService.reset();
            ledger.clear();
            book.clear();
            reset();
            RrpMod.setSimConfig(saved);
        }
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void freemerchant(GameTestHelper h) {
        VillageSite a = village(h, 4, 4, 10);
        ServerLevel level = h.getLevel();
        rrp.core.SimConfig saved = RrpMod.simConfig();
        var ledger = rrp.neoforge.PlayerService.ledger();
        ledger.clear();
        rrp.neoforge.MarketService.reset();
        try {
            var cfg = RrpMod.simConfig();
            Container ba = (Container) h.getBlockEntity(new BlockPos(14, 1, 14));
            for (int i = 0; i < 4; i++) {
                ba.setItem(i, new ItemStack(Items.BREAD, 64));
            }
            RrpMod.warehouse().refresh(a, level);
            BlockPos bp = barrel(h, 18, 18, 0);
            VillageSite b = RrpMod.registry().create(a.worldName(), bp.getX(), bp.getY(), bp.getZ(), 5);
            h.assertTrue(RrpMod.warehouse().set(b, level, bp), "second warehouse");
            var p = h.makeMockServerPlayerInLevel();
            ledger.deposit(p.getUUID(), 50);
            h.assertTrue(rrp.neoforge.MarketService.buy(p, a, Resource.FOOD, 5, cfg).verdict()
                    == rrp.neoforge.MarketService.Verdict.NOT_MERCHANT, "observers cannot trade");
            ledger.switchRole(p.getUUID(), rrp.core.player.PlayerRole.MERCHANT, 0, 0);
            double rel = rrp.neoforge.RelationService.matrix().get(a.id(), b.id());
            // buy low in the village with a surplus, sell high in the starving one
            var bought = rrp.neoforge.MarketService.buy(p, a, Resource.FOOD, 30, cfg);
            h.assertTrue(bought.verdict() == rrp.neoforge.MarketService.Verdict.OK && bought.quantity() == 30, "bought: " + bought);
            h.assertTrue(ledger.purse(p.getUUID()) == 50 - bought.emeralds(), "purse charged");
            var sold = rrp.neoforge.MarketService.sell(p, b, Resource.FOOD, 30, cfg);
            h.assertTrue(sold.verdict() == rrp.neoforge.MarketService.Verdict.OK && sold.quantity() == 30, "sold: " + sold);
            h.assertTrue(sold.emeralds() > bought.emeralds(), "arbitrage profit: paid " + bought.emeralds() + ", earned " + sold.emeralds());
            h.assertTrue(rrp.neoforge.MarketService.stock(b, Resource.FOOD, level) == 30, "goods reached the second warehouse");
            h.assertTrue(rrp.neoforge.RelationService.matrix().get(a.id(), b.id()) > rel, "trading between villages improves relations");
            // saturation: the daily cap stops the exploit
            int limit = (int) cfg.get(rrp.core.SimConfig.Key.MARKET_DAILY_LIMIT);
            ledger.deposit(p.getUUID(), 1000);
            var more = rrp.neoforge.MarketService.buy(p, a, Resource.FOOD, 1000, cfg);
            h.assertTrue(more.quantity() == limit - 30, "capped at the daily limit: " + more);
            h.assertTrue(rrp.neoforge.MarketService.buy(p, a, Resource.FOOD, 1, cfg).verdict()
                    == rrp.neoforge.MarketService.Verdict.LIMIT, "no more today");
            // a poor merchant cannot buy
            ledger.clear();
            ledger.switchRole(p.getUUID(), rrp.core.player.PlayerRole.MERCHANT, 0, 0);
            h.assertTrue(rrp.neoforge.MarketService.buy(p, b, Resource.FOOD, 5, cfg).verdict()
                    == rrp.neoforge.MarketService.Verdict.NO_FUNDS, "empty purse");
        } finally {
            rrp.neoforge.MarketService.reset();
            ledger.clear();
            reset();
            RrpMod.setSimConfig(saved);
        }
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void abstracteconomy(GameTestHelper h) {
        VillageSite a = village(h, 6, 12, 40);
        ServerLevel level = h.getLevel();
        try {
            var villagers = a.state().villagers();
            for (int i = 0; i < villagers.size(); i++) {
                villagers.get(i).setRole(i < 3 ? rrp.core.Role.FARMER : rrp.core.Role.FORESTER);
            }
            h.assertTrue(RrpMod.warehouse().read(a, level).getOrDefault(rrp.core.Resource.FOOD, 0) == 40, "start food 40");
            a.setLodForced(rrp.core.abstractsim.Lod.ABSTRACT);
            RrpMod.activity().evaluate(a, 0);
            h.assertTrue(a.isAbstract() && a.abstractBaseline() != null, "asleep with a baseline");
            int births = RrpMod.abstractEconomy().advance(a, 20L * 400);
            int wood = a.state().stock(rrp.core.Resource.WOOD);
            int food = a.state().stock(rrp.core.Resource.FOOD);
            h.assertTrue(wood > 20 && wood < 400, "abstract foresters produced wood: " + wood);
            h.assertTrue(births == a.pendingBirths() && births > 0, "abstract births recorded: " + births);
            h.assertTrue(RrpMod.warehouse().read(a, level).getOrDefault(rrp.core.Resource.WOOD, 0) == 0,
                    "the real warehouse is untouched while abstract");
            a.setLodForced(rrp.core.abstractsim.Lod.ACTIVE);
            RrpMod.activity().evaluate(a, 0);
            var real = RrpMod.warehouse().read(a, level);
            h.assertTrue(!a.isAbstract() && a.abstractBaseline() == null, "awake, ledger cleared");
            h.assertTrue(real.getOrDefault(rrp.core.Resource.WOOD, 0) == wood, "wood reconciled exactly: " + real + " vs " + wood);
            h.assertTrue(real.getOrDefault(rrp.core.Resource.FOOD, 0) == food, "food reconciled exactly: " + real + " vs " + food);
            int babies = RrpMod.births().countBabies(a, level);
            h.assertTrue(babies == Math.min(births, 12 - 6), "babies materialised: " + babies);
            for (var v : level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, h.getBounds().inflate(40), e -> e.isBaby())) {
                v.discard();
            }
        } finally {
            a.setLodForced(null);
            a.setLod(rrp.core.abstractsim.Lod.ACTIVE);
        }
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void lodswitch(GameTestHelper h) {
        VillageSite a = village(h, 4, 4, 10);
        ServerLevel level = h.getLevel();
        try {
            h.assertTrue(a.lod() == rrp.core.abstractsim.Lod.ACTIVE && WarehouseService.loaded(a, level), "new village is active");
            a.setLodForced(rrp.core.abstractsim.Lod.ABSTRACT);
            RrpMod.activity().evaluate(a, 0);
            h.assertTrue(a.isAbstract() && !WarehouseService.loaded(a, level), "abstract village is not physically simulated");
            long before = RrpMod.behaviors().actions();
            RrpMod.behaviors().resetClock();
            RrpMod.ticks().advance(12000);
            h.assertTrue(RrpMod.behaviors().actions() == before, "no physical role action in an abstract village");
            a.setLodForced(null);
            // automatic mode: a hall in an unloaded chunk turns abstract after the grace period, then wakes up at once
            VillageSite far = RrpMod.registry().create(a.worldName(), 900000, 70, 900000, 5);
            RrpMod.activity().evaluate(far, 1);
            h.assertTrue(far.lod() == rrp.core.abstractsim.Lod.ACTIVE, "grace period not over yet");
            RrpMod.activity().evaluate(far, 100000);
            h.assertTrue(far.isAbstract(), "unloaded village turned abstract");
            far.setLodForced(rrp.core.abstractsim.Lod.ACTIVE);
            RrpMod.activity().evaluate(far, 0);
            h.assertTrue(far.lod() == rrp.core.abstractsim.Lod.ACTIVE, "forced active wakes it up");
            RrpMod.registry().remove(far.id());
        } finally {
            a.setLodForced(null);
            a.setLod(rrp.core.abstractsim.Lod.ACTIVE);
        }
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void abstractcaravan(GameTestHelper h) {
        VillageSite a = village(h, 4, 4, 10);
        rrp.core.SimConfig saved = RrpMod.simConfig();
        VillageSite b = null;
        try {
            RrpMod.setSimConfig(saved.with(rrp.core.SimConfig.Key.CARAVAN_AMBUSH_CHANCE, 0)
                    .with(rrp.core.SimConfig.Key.CARAVAN_INTERVAL_S, 0));
            var cfg = RrpMod.simConfig();
            rrp.neoforge.CaravanService.reset();
            b = RrpMod.registry().create(a.worldName(), (int) a.x() + 40, (int) a.y(), (int) a.z(), 5);
            a.setLodForced(rrp.core.abstractsim.Lod.ABSTRACT);
            b.setLodForced(rrp.core.abstractsim.Lod.ABSTRACT);
            RrpMod.activity().evaluate(a, 0);
            RrpMod.activity().evaluate(b, 0);
            a.state().setStock(Resource.ORE, 120);
            a.state().setStock(Resource.FOOD, 10);
            b.state().setStock(Resource.FOOD, 200);
            b.state().setStock(Resource.ORE, 5);
            int oreB = b.state().stock(Resource.ORE);
            int foodA = a.state().stock(Resource.FOOD);
            var sent = rrp.neoforge.CaravanService.dispatch(a, b, cfg);
            h.assertTrue(sent.isPresent() && sent.get().isAbstract(), "an abstract caravan leaves with no entity");
            double eta = sent.get().march().etaSeconds();
            h.assertTrue(Math.abs(eta - 40 / cfg.get(rrp.core.SimConfig.Key.ABSTRACT_MARCH_SPEED)) < 0.1, "ETA from distance and speed: " + eta);
            h.assertTrue(a.state().stock(Resource.ORE) < 120, "cargo left the in-memory stock");
            h.assertTrue(level(h).getEntitiesOfClass(net.minecraft.world.entity.npc.WanderingTrader.class, h.getBounds().inflate(80)).isEmpty(),
                    "no trader entity while abstract");
            rrp.neoforge.CaravanService.onTicks(20L * 5, cfg, RrpMod.registry());
            h.assertTrue(rrp.neoforge.CaravanService.completed() == 0 && !sent.get().returning(), "still travelling after 5 s");
            for (int i = 0; i < 3; i++) {
                rrp.neoforge.CaravanService.onTicks(20L * 60, cfg, RrpMod.registry());
            }
            h.assertTrue(rrp.neoforge.CaravanService.completed() == 1 && rrp.neoforge.CaravanService.active() == 0, "caravan delivered");
            h.assertTrue(b.state().stock(Resource.ORE) > oreB, "iron delivered to the sleeping partner");
            h.assertTrue(a.state().stock(Resource.FOOD) > foodA, "wheat brought back to the sleeping origin");
        } finally {
            rrp.neoforge.CaravanService.reset();
            a.setLodForced(null);
            a.setLod(rrp.core.abstractsim.Lod.ACTIVE);
            if (b != null) {
                RrpMod.registry().remove(b.id());
            }
            reset();
            RrpMod.setSimConfig(saved);
        }
        h.succeed();
    }

    private static ServerLevel level(GameTestHelper h) {
        return h.getLevel();
    }

    @GameTest(template = ARENA)
    public static void abstractwar(GameTestHelper h) {
        VillageSite a = village(h, 4, 4, 10);
        rrp.core.SimConfig saved = RrpMod.simConfig();
        VillageSite b = null;
        try {
            RrpMod.setSimConfig(saved.with(rrp.core.SimConfig.Key.WAR_RESOLVE_MIN_S, 0));
            var cfg = RrpMod.simConfig();
            rrp.neoforge.DiplomacyService.reset();
            rrp.neoforge.MobilizationService.reset();
            rrp.neoforge.SiegeService.reset();
            rrp.neoforge.WarResolution.reset();
            setAll(h, Role.GUARD);
            b = RrpMod.registry().create(a.worldName(), (int) a.x() + 80, (int) a.y(), (int) a.z(), 5);
            a.setLodForced(rrp.core.abstractsim.Lod.ABSTRACT);
            b.setLodForced(rrp.core.abstractsim.Lod.ABSTRACT);
            RrpMod.activity().evaluate(a, 0);
            RrpMod.activity().evaluate(b, 0);
            rrp.neoforge.DiplomacyService.open(a.id(), b.id(), RrpMod.registry());
            rrp.neoforge.MobilizationService.update(cfg, RrpMod.registry(), 1);
            var f = rrp.neoforge.MobilizationService.force(a.id());
            h.assertTrue(f != null && f.isAbstract() && f.size() > 0, "an abstract regiment is raised from the state");
            h.assertTrue(!f.arrived() && f.march().etaSeconds() > 10, "it is still on the road: " + f.march().etaSeconds());
            rrp.neoforge.WarResolution.update(10, cfg, RrpMod.registry());
            h.assertTrue(rrp.neoforge.DiplomacyService.state(a.id(), b.id()) == rrp.core.diplomacy.Treaty.State.WAR,
                    "the siege cannot resolve before the army arrives");
            rrp.neoforge.MobilizationService.update(cfg, RrpMod.registry(), f.march().etaSeconds() + 1);
            h.assertTrue(f.arrived(), "the regiment arrived");
            rrp.neoforge.WarResolution.update(10, cfg, RrpMod.registry());
            h.assertTrue(rrp.neoforge.DiplomacyService.state(a.id(), b.id()) == rrp.core.diplomacy.Treaty.State.PEACE,
                    "the abstract war ended");
            h.assertTrue(a.id().equals(rrp.neoforge.WarResolution.ownerOf(b.id())), "the unopposed army annexed its target");
        } finally {
            rrp.neoforge.WarResolution.reset();
            rrp.neoforge.SiegeService.reset();
            rrp.neoforge.MobilizationService.reset();
            rrp.neoforge.DiplomacyService.reset();
            a.setLodForced(null);
            a.setLod(rrp.core.abstractsim.Lod.ACTIVE);
            if (b != null) {
                RrpMod.registry().remove(b.id());
            }
            reset();
            RrpMod.setSimConfig(saved);
        }
        h.succeed();
    }

    @GameTest(template = ARENA)
    public static void politicalmap(GameTestHelper h) {
        VillageSite a = village(h, 4, 4, 10);
        ServerLevel level = h.getLevel();
        rrp.core.SimConfig saved = RrpMod.simConfig();
        try {
            var cfg = RrpMod.simConfig();
            BlockPos bp = barrel(h, 30, 30, 0);
            VillageSite b = RrpMod.registry().create(a.worldName(), bp.getX(), bp.getY(), bp.getZ(), 5);
            var ruler = h.makeMockServerPlayerInLevel();
            var other = h.makeMockServerPlayerInLevel();
            a.setOwner(ruler.getUUID().toString());
            ruler.moveTo(a.x(), a.y(), a.z());
            rrp.neoforge.DiplomacyService.treaties().set(a.id(), b.id(), rrp.core.diplomacy.Treaty.State.WAR);
            // the map is a vanilla filled map painted with faction colours, war hatching and village markers
            var stack = rrp.neoforge.PoliticalMap.use(ruler, level);
            h.assertTrue(stack.is(Items.FILLED_MAP), "a filled map is given");
            var data = net.minecraft.world.item.MapItem.getSavedData(stack, level);
            h.assertTrue(data != null, "map data exists");
            java.util.Set<Byte> seen = new java.util.HashSet<>();
            for (byte c : data.colors) {
                seen.add(c);
            }
            byte marker = net.minecraft.world.level.material.MapColor.SNOW.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.HIGH);
            byte hatch = net.minecraft.world.level.material.MapColor.FIRE.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.NORMAL);
            byte border = net.minecraft.world.level.material.MapColor.COLOR_BLACK.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.LOWEST);
            h.assertTrue(seen.contains(marker), "village markers painted");
            h.assertTrue(seen.contains(hatch), "war zones hatched");
            h.assertTrue(seen.contains(border), "borders painted");
            h.assertTrue(seen.size() >= 5, "several colours: " + seen.size());
            // refreshed on interaction: wipe it, hold it, use the table again
            java.util.Arrays.fill(data.colors, (byte) 0);
            ruler.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            var again = rrp.neoforge.PoliticalMap.use(ruler, level);
            h.assertTrue(again == stack && data.colors[64 * 128 + 64] != 0, "held political map repainted in place");
            // diplomatic register: ruler gets clickable proposals, observer reads only
            rrp.neoforge.DiplomacyService.treaties().set(a.id(), b.id(), rrp.core.diplomacy.Treaty.State.PEACE);
            boolean clickable = false;
            for (var line : rrp.neoforge.DiplomaticRegister.lines(ruler, cfg)) {
                for (var part : line.toFlatList(line.getStyle())) {
                    clickable |= part.getStyle().getClickEvent() != null;
                }
            }
            h.assertTrue(clickable, "ruler sees clickable actions");
            for (var line : rrp.neoforge.DiplomaticRegister.lines(other, cfg)) {
                for (var part : line.toFlatList(line.getStyle())) {
                    h.assertTrue(part.getStyle().getClickEvent() == null, "observer register is read-only");
                }
            }
            h.assertTrue(rrp.neoforge.DiplomaticRegister.propose(other, a, b, rrp.core.player.Proposals.Kind.TRADE, cfg)
                    == rrp.neoforge.DiplomaticRegister.Result.NOT_RULER, "only the ruler sends an emissary");
            // trade proposal answered by the decider: refused when cold, accepted when warm
            rrp.neoforge.RelationService.change(a, b, -100 - rrp.neoforge.RelationService.index(a, b, cfg), cfg);
            h.assertTrue(rrp.neoforge.DiplomaticRegister.propose(ruler, a, b, rrp.core.player.Proposals.Kind.TRADE, cfg)
                    == rrp.neoforge.DiplomaticRegister.Result.REFUSED, "hostile village refuses trade");
            rrp.neoforge.RelationService.change(a, b, 60 - rrp.neoforge.RelationService.index(a, b, cfg), cfg);
            h.assertTrue(rrp.neoforge.DiplomaticRegister.propose(ruler, a, b, rrp.core.player.Proposals.Kind.TRADE, cfg)
                    == rrp.neoforge.DiplomaticRegister.Result.ACCEPTED, "friendly village accepts trade");
            h.assertTrue(rrp.neoforge.DiplomacyService.state(a.id(), b.id()) == rrp.core.diplomacy.Treaty.State.TRADE_TREATY, "treaty signed");
            // ultimatum then ceasefire
            rrp.neoforge.RelationService.change(a, b, -20 - rrp.neoforge.RelationService.index(a, b, cfg), cfg);
            h.assertTrue(rrp.neoforge.DiplomaticRegister.propose(ruler, a, b, rrp.core.player.Proposals.Kind.ULTIMATUM, cfg)
                    == rrp.neoforge.DiplomaticRegister.Result.ACCEPTED, "ultimatum delivered");
            h.assertTrue(!rrp.neoforge.DiplomacyService.pending().isEmpty(), "countdown started");
            rrp.neoforge.DiplomacyService.open(a.id(), b.id(), RrpMod.registry());
            h.assertTrue(rrp.neoforge.DiplomacyService.atWar(a.id(), b.id()), "war opened");
            rrp.neoforge.RelationService.change(a, b, -40 - rrp.neoforge.RelationService.index(a, b, cfg), cfg);
            h.assertTrue(rrp.neoforge.DiplomaticRegister.propose(ruler, a, b, rrp.core.player.Proposals.Kind.CEASEFIRE, cfg)
                    == rrp.neoforge.DiplomaticRegister.Result.ACCEPTED, "ceasefire accepted");
            h.assertTrue(!rrp.neoforge.DiplomacyService.atWar(a.id(), b.id()), "war ended");
            // real emissaries cost purse emeralds and need a cooldown
            rrp.neoforge.DiplomaticRegister.resetCooldowns();
            rrp.neoforge.PlayerService.ledger().withdraw(ruler.getUUID(), rrp.neoforge.PlayerService.ledger().purse(ruler.getUUID()));
            h.assertTrue(rrp.neoforge.DiplomaticRegister.send(ruler, a, b, rrp.core.player.Proposals.Kind.TRADE, cfg)
                    == rrp.neoforge.DiplomaticRegister.Result.NO_FUNDS, "an emissary needs emeralds");
            rrp.neoforge.PlayerService.ledger().deposit(ruler.getUUID(), 10);
            rrp.neoforge.RelationService.change(a, b, 60 - rrp.neoforge.RelationService.index(a, b, cfg), cfg);
            h.assertTrue(rrp.neoforge.DiplomaticRegister.send(ruler, a, b, rrp.core.player.Proposals.Kind.TRADE, cfg)
                    == rrp.neoforge.DiplomaticRegister.Result.ACCEPTED, "paid emissary arrives");
            h.assertTrue(rrp.neoforge.PlayerService.ledger().purse(ruler.getUUID()) == 8, "emissary cost charged");
            h.assertTrue(rrp.neoforge.DiplomaticRegister.send(ruler, a, b, rrp.core.player.Proposals.Kind.ALLIANCE, cfg)
                    == rrp.neoforge.DiplomaticRegister.Result.COOLDOWN, "second emissary must wait");
            h.assertTrue(!rrp.neoforge.DiplomaticRegister.lines(ruler, cfg, 2).isEmpty(), "register pages never come back empty");
        } finally {
            rrp.neoforge.DiplomacyService.reset();
            reset();
            RrpMod.setSimConfig(saved);
        }
        h.succeed();
    }
}
