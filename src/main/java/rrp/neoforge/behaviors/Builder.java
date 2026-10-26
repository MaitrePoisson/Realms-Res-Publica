package rrp.neoforge.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.neoforge.TownHall;
import rrp.neoforge.Worlds;
import rrp.neoforge.logistics.CarriedItems;
import rrp.neoforge.urban.BlueprintPool;
import rrp.neoforge.urban.BuildOrder;
import rrp.neoforge.urban.ConstructionSite;
import rrp.neoforge.urban.DefensePlan;
import rrp.neoforge.urban.DefensePlanner;
import rrp.neoforge.urban.RoadNetwork;
import rrp.neoforge.urban.TerrainLeveler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Mène les chantiers du village : prend l'ordre de l'intendant, choisit et nivelle un emplacement dans les
 * frontières, puis pose le plan rangée par rangée sur plusieurs actions, avec les matériaux du stock communautaire.
 * Sans ordre, il consomme du bois de l'entrepôt pour bâtir une petite hutte (4×3, murs de 2 blocs, toit, porte ouverte
 * à l'ouest) contenant un vrai lit. Il ne bâtit que s'il manque des lits au village.
 * <p>
 * Les matériaux ne quittent jamais l'entrepôt à distance : le bâtisseur les <b>porte</b> (au plus {@code carry_capacity}
 * unités), chaque pas de chantier en consomme une part proportionnelle au coût du plan, et à court il s'arrête, retourne
 * au tonneau, charge puis revient au chantier. Seul le coût total et le numéro de pas sont gardés dans le chantier.
 */
public final class Builder extends RoleBehavior {

    static final int WIDTH = 4;
    static final int DEPTH = 3;
    /** Hauteur libre exigée au-dessus du sol (murs de 2 + toit). */
    static final int HEIGHT = 3;

    /** Pas de chantier (rangées de nivellement ou blocs) réalisés à chaque action du bâtisseur en mode instantané ({@code build_block_ticks} = 0). */
    static final int STEPS_PER_ACTION = 40;
    /** Distance maximale (anneaux) de la recherche d'emplacement autour de la cible de l'ordre. */
    static final int SEARCH_RING = 14;
    /** Nombre maximal de routes par village. */
    static final int MAX_ROADS = 24;
    /** Distance horizontale (blocs) à laquelle le bâtisseur doit se trouver du chantier pour le faire avancer. */
    static final double SITE_REACH = 12;
    /** Actions passées à rejoindre le chantier avant de renoncer à marcher (chemin impossible) et de bâtir à distance. */
    static final int MAX_APPROACH = 12;

    /** Where the builder stands in the supply chain of a job. */
    enum Supply {
        /** The needed units are in the builder's hands. */
        READY,
        /** A load was just taken or a trip to the warehouse is on: the action is used up. */
        FETCHING,
        /** Nothing to fetch: the stock is empty (the builder waits). */
        EMPTY
    }

    /** Actions each builder has spent walking to its site (transient: a restart just starts counting again). */
    private static final Map<UUID, Integer> APPROACH = new HashMap<>();

    @Override
    public Role role() {
        return Role.BUILDER;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        boolean did = work(ctx);
        // nothing left to build: the leftover materials go back to the barrel
        if (!did && ctx.site().construction() == null && ctx.site().orders().isEmpty()
                && CarriedItems.total(ctx.villager()) > 0) {
            return unload(ctx);
        }
        return did;
    }

    private boolean work(BehaviorContext ctx) {
        var driver = rrp.neoforge.RrpMod.build();
        if (driver != null && driver.listJob(ctx.villager()) != null) {
            return true; // laying a hut, the Town Hall or a border post block by block
        }
        if (!ctx.site().hasHall()) {
            return buildTownHall(ctx);
        }
        if (ctx.site().hasWarehouse() && (ctx.site().construction() != null || !ctx.site().orders().isEmpty())
                && construct(ctx)) {
            return true;
        }
        if (ctx.site().hasWarehouse() && ctx.site().construction() == null && workOnDefense(ctx)) {
            return true;
        }
        if (ctx.site().hasWarehouse() && ctx.site().construction() == null && workOnRoads(ctx)) {
            return true;
        }
        if (ctx.site().hasWarehouse() && (ctx.site().construction() != null || !ctx.site().orders().isEmpty())) {
            return false;
        }
        if (!ctx.site().hasWarehouse()) {
            return false;
        }
        if (ctx.site().state().beds()
                >= ctx.site().state().population() + (int) ctx.config().get(SimConfig.Key.BUILDER_SPARE_BEDS)) {
            return workOnBorders(ctx);
        }
        // the whole hut is paid from one load: a hut dearer than the arms can carry costs a full load
        int cost = Math.min((int) ctx.config().get(SimConfig.Key.HUT_WOOD), ctx.logistics().access().capacity());
        if (ctx.warehouse().read(ctx.site(), ctx.level()).getOrDefault(Resource.WOOD, 0)
                + CarriedItems.count(ctx.villager(), Resource.WOOD) < cost) {
            ActiveWait.hold(ctx, Resource.WOOD); // a hut is due but the chest holds too little wood
            return false;
        }
        Supply supply = ensure(ctx, Resource.WOOD, cost, cost);
        if (supply != Supply.READY) {
            return supply == Supply.FETCHING;
        }
        BlockPos origin = findSpot(ctx);
        if (origin == null) {
            return false;
        }
        if (driver != null && driver.visible()) {
            // visible mode: the hut goes up block by block and the wood leaves the arms with the blocks
            var site = ctx.site();
            driver.startList(ctx.villager(), site, ctx.level(), hutInOrder(origin), cost, () -> {
                site.state().setBeds(site.state().beds() + 1);
                ctx.registry().markDirty();
            });
            ctx.registry().markDirty();
            return true;
        }
        CarriedItems.take(ctx.villager(), Resource.WOOD, cost);
        walkToward(ctx, origin);
        build(ctx.level(), origin);
        ctx.site().state().setBeds(ctx.site().state().beds() + 1);
        ctx.registry().markDirty();
        return true;
    }

    /** Démarre (si besoin) puis fait avancer le chantier. Renvoie vrai si quelque chose a été fait. */
    private boolean construct(BehaviorContext ctx) {
        var site = ctx.site();
        boolean started = false;
        if (site.construction() == null) {
            if (!startSite(ctx)) {
                return false;
            }
            started = true;
        }
        ConstructionSite cs = site.construction();
        var template = BlueprintPool.load(ctx.level(), BlueprintPool.Kind.of(cs.type()), cs.origin());
        if (template.isEmpty()) {
            site.setConstruction(null);
            ctx.registry().markDirty();
            return started;
        }
        if (!reachSite(ctx, cs)) {
            return true; // on its way to the site
        }
        var build = rrp.neoforge.RrpMod.build();
        if (build != null && build.visible()) {
            return layBlockByBlock(ctx, build, cs, template.get(), started);
        }
        int made = cs.advance(ctx.level(), template.get(), ctx.villager(), STEPS_PER_ACTION, supplyOf(ctx));
        if (cs.done(template.get())) {
            finish(ctx, cs, template.get());
            return true;
        }
        if (made < STEPS_PER_ACTION && fetchFor(ctx, cs, template.get())) {
            ctx.registry().markDirty();
            return true; // short of materials: off to the warehouse
        }
        if (made > 0 || started) {
            ctx.registry().markDirty();
        }
        return made > 0 || started;
    }

    /**
     * Visible mode: the {@link rrp.neoforge.building.BuildDriver} lays the site one block every {@code build_block_ticks}.
     * This action only hands it the site once the builder carries what the next block costs; short of materials it goes
     * to the warehouse instead (the driver stops by itself when the arms run empty and calls this again).
     */
    private boolean layBlockByBlock(BehaviorContext ctx, rrp.neoforge.building.BuildDriver build, ConstructionSite cs,
                                    net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate t, boolean started) {
        if (cs.done(t)) {
            finish(ctx, cs, t);
            return true;
        }
        if (build.busy(ctx.villager())) {
            return true;
        }
        var supply = supplyOf(ctx);
        if (supply.has(cs.woodForStep(t), cs.stoneForStep(t))) {
            build.start(ctx.villager(), ctx.site(), ctx.level(), cs, t, supply);
            ctx.registry().markDirty();
            return true;
        }
        if (fetchFor(ctx, cs, t)) {
            ctx.registry().markDirty();
            return true; // short of materials: off to the warehouse
        }
        return started;
    }

    /** Prend le premier ordre, trouve son emplacement et retire les matériaux du stock. Faux si impossible pour l'instant. */
    private boolean startSite(BehaviorContext ctx) {
        var site = ctx.site();
        BuildOrder order = site.orders().get(0);
        BlockPos target = new BlockPos(order.x(), order.y(), order.z());
        var template = BlueprintPool.load(ctx.level(), BlueprintPool.Kind.of(order.type()), target);
        if (template.isEmpty()) {
            return drop(ctx, order);
        }
        var stock = ctx.warehouse().read(site, ctx.level());
        boolean noWood = stock.getOrDefault(Resource.WOOD, 0) < order.wood();
        if (noWood || stock.getOrDefault(Resource.STONE, 0) < order.stone()) {
            ActiveWait.hold(ctx, noWood ? Resource.WOOD : Resource.STONE); // the order waits for the stock
            return false;
        }
        ActiveWait.end(ctx.villager());
        var size = template.get().getSize();
        Optional<BlockPos> spot = TerrainLeveler.findSpot(ctx.level(), site, order.x(), order.z(),
                size.getX(), size.getY(), size.getZ(), SEARCH_RING, order.y(), ctx.villager());
        if (spot.isEmpty()) {
            return drop(ctx, order);
        }
        // nothing leaves the warehouse here: the builder fetches the materials step after step, on foot
        site.setConstruction(new ConstructionSite(order.type(), spot.get(), 0, order.wood(), order.stone()));
        ctx.registry().markDirty();
        return true;
    }

    /** What the builder carries pays the steps of the plan: checked before a step, used up once it is done. */
    private static ConstructionSite.Supply supplyOf(BehaviorContext ctx) {
        return new ConstructionSite.Supply() {
            @Override
            public boolean has(int wood, int stone) {
                return CarriedItems.count(ctx.villager(), Resource.WOOD) >= wood
                        && CarriedItems.count(ctx.villager(), Resource.STONE) >= stone;
            }

            @Override
            public void spend(int wood, int stone) {
                CarriedItems.take(ctx.villager(), Resource.WOOD, wood);
                CarriedItems.take(ctx.villager(), Resource.STONE, stone);
            }
        };
    }

    /**
     * The builder is out of what the next step needs: goes to the warehouse for the missing material (the share of its
     * arms that the rest of the plan needs of it). True if it set off or loaded something, false if it lacks nothing
     * (the step is blocked by something else) or the stock is empty.
     */
    private static boolean fetchFor(BehaviorContext ctx, ConstructionSite cs, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate t) {
        var v = ctx.villager();
        int needWood = cs.woodForStep(t);
        int needStone = cs.stoneForStep(t);
        boolean wood = CarriedItems.count(v, Resource.WOOD) < needWood;
        if (!wood && CarriedItems.count(v, Resource.STONE) >= needStone) {
            return false;
        }
        int leftWood = cs.woodLeft(t);
        int leftStone = cs.stoneLeft(t);
        int left = wood ? leftWood : leftStone;
        int cap = ctx.logistics().access().capacity();
        int hold = Math.min(left, Math.max(1, (int) ((long) cap * left / Math.max(1, leftWood + leftStone))));
        return ensure(ctx, wood ? Resource.WOOD : Resource.STONE, wood ? needWood : needStone, hold) == Supply.FETCHING;
    }

    /**
     * Gets {@code need} units of {@code r} into the builder's arms, up to {@code hold} units when it loads. Takes them
     * at once when the builder stands within reach of the barrel, otherwise sets off to it (the load is taken when it
     * arrives). If its arms are full of something else it first puts that back.
     */
    static Supply ensure(BehaviorContext ctx, Resource r, int need, int hold) {
        var v = ctx.villager();
        var access = ctx.logistics().access();
        int cap = access.capacity();
        need = Math.min(need, cap);
        int have = CarriedItems.count(v, r);
        if (have >= need) {
            ActiveWait.end(v);
            return Supply.READY;
        }
        int want = Math.max(need, Math.min(hold, cap)) - have;
        int free = CarriedItems.free(v, cap);
        if (free < want) {
            want = free;
            if (have + want < need) {
                return unload(ctx) ? Supply.FETCHING : Supply.EMPTY;
            }
        }
        var result = access.withdraw(v, ctx.site(), ctx.level(), r, want, true);
        if (result.moved() > 0 && CarriedItems.count(v, r) >= need) {
            ActiveWait.end(v);
            return Supply.READY;
        }
        if (result.moved() > 0 || result.tripRequested()) {
            ActiveWait.end(v);
            return Supply.FETCHING;
        }
        ActiveWait.hold(ctx, r); // the chest holds none: complain, clear the land or sit
        return Supply.EMPTY;
    }

    /**
     * True once the builder stands at the site (or has no AI to walk with); otherwise heads there. After
     * {@link #MAX_APPROACH} actions without arriving it gives up walking and builds from where it is.
     */
    private boolean reachSite(BehaviorContext ctx, ConstructionSite cs) {
        var v = ctx.villager();
        double dx = v.getX() - (cs.origin().getX() + 0.5);
        double dz = v.getZ() - (cs.origin().getZ() + 0.5);
        if (v.isNoAi() || dx * dx + dz * dz <= SITE_REACH * SITE_REACH && Math.abs(v.getY() - cs.origin().getY()) <= 8) {
            APPROACH.remove(v.getUUID());
            return true;
        }
        if (APPROACH.merge(v.getUUID(), 1, Integer::sum) > MAX_APPROACH) {
            APPROACH.remove(v.getUUID());
            return true;
        }
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(cs.origin(), 0.6f, 3));
        v.getNavigation().moveTo(cs.origin().getX() + 0.5, cs.origin().getY(), cs.origin().getZ() + 0.5, 0.6);
        return false;
    }

    /** Ordre irréalisable (plan absent, aucun emplacement) : on l'abandonne, l'intendant en émettra un autre. */
    private boolean drop(BehaviorContext ctx, BuildOrder order) {
        ctx.site().orders().remove(order);
        ctx.registry().markDirty();
        return false;
    }

    private void finish(BehaviorContext ctx, ConstructionSite cs, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate t) {
        var site = ctx.site();
        site.orders().stream().filter(o -> o.type() == cs.type()).findFirst().ifPresent(site.orders()::remove);
        site.built().merge(cs.type(), 1, Integer::sum);
        site.state().setBeds(site.state().beds() + cs.countBeds(ctx.level(), t));
        site.setConstruction(null);
        if (cs.type() == rrp.neoforge.urban.BuildType.TOWER) {
            var size = t.getSize();
            site.defense().towers().add(new int[] {cs.origin().getX() + size.getX() / 2,
                    cs.origin().getY() + size.getY() - 1, cs.origin().getZ() + size.getZ() / 2});
        }
        addRoad(ctx, cs.origin().getX() + t.getSize().getX() / 2, cs.origin().getZ() + t.getSize().getZ() / 2, true);
        ctx.registry().markDirty();
    }

    /** Commande une route de l'Hôtel de Ville vers {@code (x,z)} si aucune n'y mène déjà (plafond {@link #MAX_ROADS}). */
    static void addRoad(BehaviorContext ctx, int x, int z, boolean wide) {
        var site = ctx.site();
        int[] hall = site.hall();
        if (hall == null || site.roads().size() >= MAX_ROADS || site.roads().stream().anyMatch(r -> r.sameTarget(x, z))) {
            return;
        }
        site.roads().add(new RoadNetwork.Road(hall[0], hall[2], x, z, wide, null, 0, 0));
    }

    /** Relie l'Hôtel de Ville aux villages et campements voisins de la même dimension. */
    private static void linkNeighbours(BehaviorContext ctx) {
        int max = (int) ctx.config().get(SimConfig.Key.ROAD_LINK_DISTANCE);
        var site = ctx.site();
        for (var other : ctx.registry().all()) {
            if (other == site || max <= 0 || !other.worldName().equals(site.worldName())) {
                continue;
            }
            int[] oh = other.hall();
            int ox = oh != null ? oh[0] : (int) other.x();
            int oz = oh != null ? oh[2] : (int) other.z();
            if (Math.hypot(ox - site.x(), oz - site.z()) <= max) {
                addRoad(ctx, ox, oz, false);
            }
        }
    }

    /** Pose le sentier d'une route, puis la pave quand la pierre et la population le permettent. Vrai si quelque chose a été fait. */
    private boolean workOnRoads(BehaviorContext ctx) {
        var site = ctx.site();
        linkNeighbours(ctx);
        int ground = site.hall()[1] - 1;
        var lay = rrp.neoforge.RrpMod.build();
        if (lay != null && lay.visible() && lay.kindActive(site, "roads")) {
            return false; // another builder is on the roads: the counters follow one job at a time
        }
        for (var road : new java.util.ArrayList<>(site.roads())) {
            if (!road.hasPath() && !RoadNetwork.plan(ctx.level(), road, ground)) {
                site.roads().remove(road);
                ctx.registry().markDirty();
                continue;
            }
            var driver = rrp.neoforge.RrpMod.build();
            boolean visible = driver != null && driver.visible();
            if (!road.laidDone() && visible) {
                // visible mode: the builder walks along the path and lays the tiles one block at a time
                var units = new rrp.neoforge.building.UnitProgress(n -> RoadNetwork.advanceLaid(road, n));
                var blocks = RoadNetwork.layBlocks(ctx.level(), road, RoadNetwork.TILES_PER_STEP, ground, units);
                driver.startList(ctx.villager(), site, ctx.level(), blocks, 0, Resource.STONE, units, units::finish).kind("roads");
                ctx.registry().markDirty();
                return true;
            }
            if (!road.laidDone()) {
                int i = Math.min(road.laid(), road.length() - 1);
                walkToward(ctx, new BlockPos(road.path()[2 * i], ground, road.path()[2 * i + 1]));
                RoadNetwork.lay(ctx.level(), road, RoadNetwork.TILES_PER_STEP, ground);
                ctx.registry().markDirty();
                return true;
            }
            // paving that has begun goes on: what the other builders carry away must not stop it halfway
            if (!road.pavedDone() && (road.paved() > 0 || canPave(ctx))) {
                int batch = Math.min(RoadNetwork.TILES_PER_STEP, road.length() - road.paved());
                int cost = (batch + 1) / 2;
                int hold = Math.min(ctx.logistics().access().capacity(), (road.length() - road.paved() + 1) / 2);
                Supply supply = ensure(ctx, Resource.STONE, cost, hold);
                    if (supply == Supply.EMPTY) {
                    continue;
                }
                if (supply == Supply.FETCHING) {
                    return true;
                }
                if (visible) {
                    // the stone leaves the arms with the tiles, one block at a time
                    var units = new rrp.neoforge.building.UnitProgress(n -> RoadNetwork.advancePaved(road, n));
                    var blocks = RoadNetwork.paveBlocks(ctx.level(), road, batch, ground, units);
                    driver.startList(ctx.villager(), site, ctx.level(), blocks, cost, Resource.STONE, units, units::finish).kind("roads");
                    ctx.registry().markDirty();
                    return true;
                }
                CarriedItems.take(ctx.villager(), Resource.STONE, cost);
                RoadNetwork.pave(ctx.level(), road, batch, ground);
                ctx.registry().markDirty();
                return true;
            }
        }
        return false;
    }

    private static DefensePlan.Rules defenseRules(BehaviorContext ctx) {
        var c = ctx.config();
        return new DefensePlan.Rules((int) c.get(SimConfig.Key.DEFENSE_POPULATION), (int) c.get(SimConfig.Key.DEFENSE_THREATS),
                (int) c.get(SimConfig.Key.DEFENSE_RING_RADIUS), (int) c.get(SimConfig.Key.DEFENSE_STONE_STOCK),
                (int) c.get(SimConfig.Key.DEFENSE_STONE_POPULATION));
    }

    /**
     * Fortifie le village : déclenche l'enceinte (population ou menaces), commande les tours aux angles, pose la palissade
     * de bois puis la remplace par des murs de pierre, avec une porte à chaque croisement de route. Vrai si quelque chose a été fait.
     */
    private boolean workOnDefense(BehaviorContext ctx) {
        var site = ctx.site();
        var d = site.defense();
        var rules = defenseRules(ctx);
        if (!d.started()) {
            if (!DefensePlan.shouldStart(site.state().population(), site.state().threats(), rules)) {
                return false;
            }
            d.start(Math.min(rules.ringRadius(), Math.max(4, site.radius() - 2)));
            ctx.registry().markDirty();
        }
        int[] hall = site.hall();
        if (d.towersIssued() < DefensePlan.towerTarget(site.state().population())
                && site.orders().size() < (int) ctx.config().get(SimConfig.Key.MAX_BUILD_ORDERS)) {
            int[] at = DefensePlan.towerSite(hall[0], hall[2], d.radius(), d.towersIssued());
            site.orders().add(BuildOrder.of(rrp.neoforge.urban.BuildType.TOWER, at[0], hall[1], at[1]));
            d.towerIssued();
            ctx.registry().markDirty();
            return false;
        }
        java.util.List<int[]> paths = new java.util.ArrayList<>();
        site.roads().stream().filter(RoadNetwork.Road::hasPath).forEach(r -> paths.add(r.path()));
        var rings = rrp.neoforge.RrpMod.build();
        boolean ringBusy = rings != null && rings.visible() && rings.kindActive(site, "defense");
        if (!d.palisadeDone()) {
            if (ringBusy) {
                return false; // another builder is on the ring: the counters follow one job at a time
            }
            int n = Math.min(DefensePlan.COLUMNS_PER_STEP, d.length() - d.laid());
            int cost = (n + 2) / 3;
            Supply supply = ensure(ctx, Resource.WOOD, cost, ctx.logistics().access().capacity());
            if (supply != Supply.READY) {
                return supply == Supply.FETCHING;
            }
            var driver = rrp.neoforge.RrpMod.build();
            if (driver != null && driver.visible()) {
                // visible mode: the builder walks along the ring and sets the fence column after column
                var units = new rrp.neoforge.building.UnitProgress(d::advanceLaid);
                var blocks = DefensePlanner.ringBlocks(ctx.level(), d, hall[0], hall[2], hall[1], paths, d.laid(), n, false, units);
                driver.startList(ctx.villager(), site, ctx.level(), blocks, cost, Resource.WOOD, units, units::finish).kind("defense");
                ctx.registry().markDirty();
                return true;
            }
            CarriedItems.take(ctx.villager(), Resource.WOOD, cost);
            int[] c = DefensePlan.ringAt(hall[0], hall[2], d.radius(), d.laid());
            walkToward(ctx, new BlockPos(c[0], hall[1], c[1]));
            d.advanceLaid(DefensePlanner.work(ctx.level(), d, hall[0], hall[2], hall[1], paths, d.laid(), n, false));
            ctx.registry().markDirty();
            return true;
        }
        int stone = ctx.warehouse().read(site, ctx.level()).getOrDefault(Resource.STONE, 0)
                + inTransit(ctx, Resource.STONE);
        if (DefensePlan.shouldUpgrade(d, stone, site.state().population(), rules)) {
            d.startUpgrade();
            ctx.registry().markDirty();
        }
        if (d.stage() == rrp.neoforge.urban.Defense.STONE && !d.stoneDone()) {
            if (ringBusy) {
                return false;
            }
            int n = Math.min(DefensePlan.COLUMNS_PER_STEP, d.length() - d.upgraded());
            int cost = (n + 1) / 2;
            Supply supply = ensure(ctx, Resource.STONE, cost, ctx.logistics().access().capacity());
            if (supply != Supply.READY) {
                return supply == Supply.FETCHING;
            }
            var driver = rrp.neoforge.RrpMod.build();
            if (driver != null && driver.visible()) {
                // visible mode: the walls replace the fence column after column, the stone leaving the arms with the blocks
                var units = new rrp.neoforge.building.UnitProgress(d::advanceUpgraded);
                var blocks = DefensePlanner.ringBlocks(ctx.level(), d, hall[0], hall[2], hall[1], paths, d.upgraded(), n, true, units);
                driver.startList(ctx.villager(), site, ctx.level(), blocks, cost, Resource.STONE, units, units::finish).kind("defense");
                ctx.registry().markDirty();
                return true;
            }
            CarriedItems.take(ctx.villager(), Resource.STONE, cost);
            int[] c = DefensePlan.ringAt(hall[0], hall[2], d.radius(), d.upgraded());
            walkToward(ctx, new BlockPos(c[0], hall[1], c[1]));
            d.advanceUpgraded(DefensePlanner.work(ctx.level(), d, hall[0], hall[2], hall[1], paths, d.upgraded(), n, true));
            ctx.registry().markDirty();
            return true;
        }
        return false;
    }

    /** Pose les bornes frontières manquantes du territoire du village (une par action, sans matériaux). */
    private boolean workOnBorders(BehaviorContext ctx) {
        var site = ctx.site();
        var map = rrp.neoforge.TerritoryService.build(ctx.registry().all(), site.worldName(), ctx.config());
        int[] p = rrp.neoforge.TerritoryService.nextMissing(ctx.level(), map, site.id());
        if (p == null) {
            return false;
        }
        var driver = rrp.neoforge.RrpMod.build();
        if (driver != null && driver.visible()) {
            // visible mode: the builder walks to the post and lays its three blocks one by one (no material cost)
            var blocks = rrp.neoforge.TerritoryService.postBlocks(ctx.level(), p[0], p[1], site.id());
            if (blocks.isEmpty()) {
                return false;
            }
            driver.startList(ctx.villager(), site, ctx.level(), blocks, 0, () -> { });
            ctx.registry().markDirty();
            return true;
        }
        walkToward(ctx, new BlockPos(p[0], site.hall()[1], p[1]));
        return rrp.neoforge.TerritoryService.placePost(ctx.level(), p[0], p[1], site.id());
    }

    /** What the village's villagers carry of {@code r}: still the village's, even if it left the barrel. */
    private static int inTransit(BehaviorContext ctx, Resource r) {
        int n = CarriedItems.count(ctx.villager(), r);
        for (var v : rrp.neoforge.RrpMod.adapter().present(ctx.site(), ctx.level())) {
            if (v != ctx.villager()) {
                n += CarriedItems.count(v, r);
            }
        }
        return n;
    }

    private static boolean canPave(BehaviorContext ctx) {
        return ctx.site().state().population() >= (int) ctx.config().get(SimConfig.Key.ROAD_PAVE_POPULATION)
                && ctx.warehouse().read(ctx.site(), ctx.level()).getOrDefault(Resource.STONE, 0)
                + inTransit(ctx, Resource.STONE)
                >= (int) ctx.config().get(SimConfig.Key.ROAD_PAVE_STONE_STOCK);
    }

    /** Tout village a un Hôtel de Ville : à défaut, le bâtisseur en pose un près du centre. */
    private boolean buildTownHall(BehaviorContext ctx) {
        BlockPos base = TownHall.findSpot(ctx.level(), ctx.site());
        if (base == null) {
            return false;
        }
        var driver = rrp.neoforge.RrpMod.build();
        if (driver != null && driver.visible()) {
            startTownHallJob(ctx, driver, base);
            return true;
        }
        walkToward(ctx, base);
        TownHall.build(ctx.level(), base);
        ctx.site().setHall(base.getX(), base.getY(), base.getZ());
        ctx.warehouse().ensureForHall(ctx.site(), ctx.level());
        ctx.registry().markDirty();
        return true;
    }

    /**
     * Visible mode: the builder lays the Town Hall (gold block, lectern) and the warehouse and armoury barrels beside it,
     * block by block; the hall, the warehouse and the armoury are registered when the last block is down.
     */
    private static void startTownHallJob(BehaviorContext ctx, rrp.neoforge.building.BuildDriver driver, BlockPos base) {
        var site = ctx.site();
        var level = ctx.level();
        var blocks = new java.util.ArrayList<>(TownHall.blocks(base));
        int[] at = {base.getX(), base.getY(), base.getZ()};
        BlockPos store = null;
        BlockPos armoury = null;
        if (!site.hasWarehouse()) {
            store = ctx.warehouse().freeSpotAround(at, level, java.util.List.of());
        }
        if (site.armoury() == null) {
            armoury = ctx.warehouse().freeSpotAround(at, level, store == null ? java.util.List.of() : java.util.List.of(store));
        }
        for (BlockPos barrel : java.util.Arrays.asList(store, armoury)) {
            if (barrel != null) {
                blocks.add(new rrp.neoforge.building.BuildJob.Placement(barrel, Blocks.BARREL.defaultBlockState()));
            }
        }
        final BlockPos storeAt = store;
        final BlockPos armouryAt = armoury;
        driver.startList(ctx.villager(), site, level, blocks, 0, () -> {
            site.setHall(base.getX(), base.getY(), base.getZ());
            if (storeAt != null) {
                ctx.warehouse().set(site, level, storeAt);
            }
            if (armouryAt != null && level.getBlockState(armouryAt).is(Blocks.BARREL)) {
                site.setArmoury(armouryAt.getX(), armouryAt.getY(), armouryAt.getZ());
            }
            ctx.warehouse().ensureForHall(site, level);
            ctx.registry().markDirty();
        });
        ctx.registry().markDirty();
    }

    /** The blocks of the hut whose west-north corner is {@code origin}, in no particular order. */
    public static java.util.List<rrp.neoforge.building.BuildJob.Placement> hutBlocks(BlockPos origin) {
        var out = new java.util.ArrayList<rrp.neoforge.building.BuildJob.Placement>();
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                boolean perimeter = dx == 0 || dz == 0 || dx == WIDTH - 1 || dz == DEPTH - 1;
                boolean door = dx == 0 && dz == DEPTH / 2;
                if (perimeter && !door) {
                    out.add(new rrp.neoforge.building.BuildJob.Placement(origin.offset(dx, 0, dz), planks));
                    out.add(new rrp.neoforge.building.BuildJob.Placement(origin.offset(dx, 1, dz), planks));
                }
                out.add(new rrp.neoforge.building.BuildJob.Placement(origin.offset(dx, 2, dz), planks));
            }
        }
        BlockPos foot = origin.offset(1, 0, DEPTH / 2);
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        out.add(new rrp.neoforge.building.BuildJob.Placement(foot, bed.setValue(BedBlock.PART, BedPart.FOOT)));
        out.add(new rrp.neoforge.building.BuildJob.Placement(foot.east(), bed.setValue(BedBlock.PART, BedPart.HEAD)));
        return out;
    }

    /** The hut blocks in building order: bottom layer first, a snake walk inside a layer ({@code BlockOrder}). */
    static java.util.List<rrp.neoforge.building.BuildJob.Placement> hutInOrder(BlockPos origin) {
        var blocks = hutBlocks(origin);
        var positions = new java.util.ArrayList<int[]>();
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos p = blocks.get(i).pos().subtract(origin);
            positions.add(new int[] {p.getX(), p.getY(), p.getZ(), i});
        }
        var sorted = new java.util.ArrayList<rrp.neoforge.building.BuildJob.Placement>();
        for (int[] p : rrp.core.building.BlockOrder.sort(positions)) {
            sorted.add(blocks.get(p[3]));
        }
        return sorted;
    }

    /** Pose la hutte dont l'angle ouest-nord est {@code origin} (premier bloc libre au-dessus du sol), d'un coup (mode instantané). */
    static void build(ServerLevel level, BlockPos origin) {
        for (var p : hutBlocks(origin)) {
            level.setBlock(p.pos(), p.state(), Block.UPDATE_CLIENTS);
        }
    }

    /** Emplacement plat et libre le plus proche du bâtisseur, dans son rayon de travail et dans le village. */
    private static BlockPos findSpot(BehaviorContext ctx) {
        BlockPos at = ctx.villager().blockPosition();
        int r = ctx.workRadius();
        for (int d = 3; d <= r; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) {
                        continue;
                    }
                    BlockPos o = at.offset(dx, 0, dz);
                    if (fits(ctx, o)) {
                        return o;
                    }
                }
            }
        }
        return null;
    }

    private static boolean fits(BehaviorContext ctx, BlockPos o) {
        ServerLevel w = ctx.level();
        String name = Worlds.name(w);
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                int x = o.getX() + dx;
                int z = o.getZ() + dz;
                if (!w.hasChunk(x >> 4, z >> 4) || !ctx.site().contains(name, x + 0.5, z + 0.5)) {
                    return false;
                }
                if (!w.getBlockState(new BlockPos(x, o.getY() - 1, z)).blocksMotion()) {
                    return false;
                }
                for (int dy = 0; dy < HEIGHT; dy++) {
                    if (!w.getBlockState(new BlockPos(x, o.getY() + dy, z)).isAir()) {
                        return false;
                    }
                }
            }
        }
        // jamais sur un être vivant : la hutte l'emmurerait (il mourrait étouffé)
        return w.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(o.getX(), o.getY(), o.getZ(), o.getX() + WIDTH, o.getY() + HEIGHT, o.getZ() + DEPTH)).isEmpty();
    }
}
