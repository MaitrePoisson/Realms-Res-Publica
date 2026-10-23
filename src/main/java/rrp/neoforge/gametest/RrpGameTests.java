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

    @GameTest(template = ARENA)
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
}
