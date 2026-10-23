package rrp.neoforge.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import rrp.neoforge.urban.BlueprintPool;
import rrp.neoforge.urban.BuildOrder;
import rrp.neoforge.urban.ConstructionSite;
import rrp.neoforge.urban.RoadNetwork;
import rrp.neoforge.urban.TerrainLeveler;

import java.util.Optional;

/**
 * Mène les chantiers du village : prend l'ordre de l'intendant, choisit et nivelle un emplacement dans les
 * frontières, puis pose le plan rangée par rangée sur plusieurs actions, avec les matériaux du stock communautaire.
 * Sans ordre, il consomme du bois de l'entrepôt pour bâtir une petite hutte (4×3, murs de 2 blocs, toit, porte ouverte
 * à l'ouest) contenant un vrai lit. Il ne bâtit que s'il manque des lits au village.
 */
public final class Builder extends RoleBehavior {

    static final int WIDTH = 4;
    static final int DEPTH = 3;
    /** Hauteur libre exigée au-dessus du sol (murs de 2 + toit). */
    static final int HEIGHT = 3;

    /** Pas de chantier (rangées) réalisés à chaque action du bâtisseur. */
    static final int STEPS_PER_ACTION = 6;
    /** Distance maximale (anneaux) de la recherche d'emplacement autour de la cible de l'ordre. */
    static final int SEARCH_RING = 14;
    /** Nombre maximal de routes par village. */
    static final int MAX_ROADS = 24;

    @Override
    public Role role() {
        return Role.BUILDER;
    }

    @Override
    public boolean runOnce(BehaviorContext ctx) {
        if (!ctx.site().hasHall()) {
            return buildTownHall(ctx);
        }
        if (ctx.site().hasWarehouse() && (ctx.site().construction() != null || !ctx.site().orders().isEmpty())
                && construct(ctx)) {
            return true;
        }
        if (ctx.site().hasWarehouse() && ctx.site().construction() == null && workOnRoads(ctx)) {
            return true;
        }
        if (ctx.site().hasWarehouse() && (ctx.site().construction() != null || !ctx.site().orders().isEmpty())) {
            return false;
        }
        if (!ctx.site().hasWarehouse() || ctx.site().state().beds()
                >= ctx.site().state().population() + (int) ctx.config().get(SimConfig.Key.BUILDER_SPARE_BEDS)) {
            return false;
        }
        int cost = (int) ctx.config().get(SimConfig.Key.HUT_WOOD);
        if (ctx.warehouse().read(ctx.site(), ctx.level()).getOrDefault(Resource.WOOD, 0) < cost) {
            return false;
        }
        BlockPos origin = findSpot(ctx);
        if (origin == null) {
            return false;
        }
        if (ctx.warehouse().withdraw(ctx.site(), ctx.level(), Resource.WOOD, cost) < cost) {
            return false;
        }
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
        walkToward(ctx, cs.origin());
        int made = cs.advance(ctx.level(), template.get(), ctx.villager(), STEPS_PER_ACTION);
        if (cs.done(template.get())) {
            finish(ctx, cs, template.get());
            return true;
        }
        if (made > 0 || started) {
            ctx.registry().markDirty();
        }
        return made > 0 || started;
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
        if (stock.getOrDefault(Resource.WOOD, 0) < order.wood() || stock.getOrDefault(Resource.STONE, 0) < order.stone()) {
            return false;
        }
        var size = template.get().getSize();
        Optional<BlockPos> spot = TerrainLeveler.findSpot(ctx.level(), site, order.x(), order.z(),
                size.getX(), size.getY(), size.getZ(), SEARCH_RING, order.y(), ctx.villager());
        if (spot.isEmpty()) {
            return drop(ctx, order);
        }
        // le stock vient d'être vérifié : on retire les deux matériaux d'un coup
        ctx.warehouse().withdraw(site, ctx.level(), Resource.WOOD, order.wood());
        ctx.warehouse().withdraw(site, ctx.level(), Resource.STONE, order.stone());
        site.setConstruction(new ConstructionSite(order.type(), spot.get(), 0));
        ctx.registry().markDirty();
        return true;
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
        for (var road : new java.util.ArrayList<>(site.roads())) {
            if (!road.hasPath() && !RoadNetwork.plan(ctx.level(), road, ground)) {
                site.roads().remove(road);
                ctx.registry().markDirty();
                continue;
            }
            if (!road.laidDone()) {
                int i = Math.min(road.laid(), road.length() - 1);
                walkToward(ctx, new BlockPos(road.path()[2 * i], ground, road.path()[2 * i + 1]));
                RoadNetwork.lay(ctx.level(), road, RoadNetwork.TILES_PER_STEP, ground);
                ctx.registry().markDirty();
                return true;
            }
            if (!road.pavedDone() && canPave(ctx)) {
                int batch = Math.min(RoadNetwork.TILES_PER_STEP, road.length() - road.paved());
                int cost = (batch + 1) / 2;
                if (ctx.warehouse().withdraw(site, ctx.level(), Resource.STONE, cost) < cost) {
                    continue;
                }
                RoadNetwork.pave(ctx.level(), road, batch, ground);
                ctx.registry().markDirty();
                return true;
            }
        }
        return false;
    }

    private static boolean canPave(BehaviorContext ctx) {
        return ctx.site().state().population() >= (int) ctx.config().get(SimConfig.Key.ROAD_PAVE_POPULATION)
                && ctx.warehouse().read(ctx.site(), ctx.level()).getOrDefault(Resource.STONE, 0)
                >= (int) ctx.config().get(SimConfig.Key.ROAD_PAVE_STONE_STOCK);
    }

    /** Tout village a un Hôtel de Ville : à défaut, le bâtisseur en pose un près du centre. */
    private boolean buildTownHall(BehaviorContext ctx) {
        BlockPos base = TownHall.findSpot(ctx.level(), ctx.site());
        if (base == null) {
            return false;
        }
        walkToward(ctx, base);
        TownHall.build(ctx.level(), base);
        ctx.site().setHall(base.getX(), base.getY(), base.getZ());
        ctx.warehouse().ensureForHall(ctx.site(), ctx.level());
        ctx.registry().markDirty();
        return true;
    }

    /** Pose la hutte dont l'angle ouest-nord est {@code origin} (premier bloc libre au-dessus du sol). */
    static void build(ServerLevel level, BlockPos origin) {
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                boolean perimeter = dx == 0 || dz == 0 || dx == WIDTH - 1 || dz == DEPTH - 1;
                boolean door = dx == 0 && dz == DEPTH / 2;
                if (perimeter && !door) {
                    level.setBlock(origin.offset(dx, 0, dz), planks, Block.UPDATE_CLIENTS);
                    level.setBlock(origin.offset(dx, 1, dz), planks, Block.UPDATE_CLIENTS);
                }
                level.setBlock(origin.offset(dx, 2, dz), planks, Block.UPDATE_CLIENTS);
            }
        }
        BlockPos foot = origin.offset(1, 0, DEPTH / 2);
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_CLIENTS);
        level.setBlock(foot.east(), bed.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS);
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
