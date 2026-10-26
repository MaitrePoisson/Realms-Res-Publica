package rrp.neoforge.urban;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import rrp.core.logistics.CostShare;
import rrp.neoforge.building.BuildBlocks;

/**
 * Chantier en cours : un plan posé à {@code origin}, nivellement d'abord (une rangée de l'emprise élargie par pas),
 * puis le plan bloc par bloc dans l'ordre de {@link BuildBlocks} (un pas = un bloc, posé avec ses mises à jour de
 * voisinage : lits, clôtures et vitres se raccordent à mesure). Seuls le type, l'origine et le numéro de pas sont
 * persistés : le reste se déduit du plan, donc le chantier reprend après un redémarrage.
 */
public final class ConstructionSite {

    private final BuildType type;
    private final BlockPos origin;
    private int step;
    private final int woodCost;
    private final int stoneCost;

    /** Materials of a step: what the builder carries (it must go and fetch more when {@link #has} says no) and how they are used up. */
    public interface Supply {
        boolean has(int wood, int stone);

        void spend(int wood, int stone);
    }

    /** A site whose materials are already paid (free of charge). */
    public ConstructionSite(BuildType type, BlockPos origin, int step) {
        this(type, origin, step, 0, 0);
    }

    /** A site that costs {@code woodCost} / {@code stoneCost} in total, consumed step after step as the plan goes up. */
    public ConstructionSite(BuildType type, BlockPos origin, int step, int woodCost, int stoneCost) {
        this.type = type;
        this.origin = origin;
        this.step = step;
        this.woodCost = woodCost;
        this.stoneCost = stoneCost;
    }

    public BuildType type() { return type; }
    public BlockPos origin() { return origin; }
    public int step() { return step; }
    public int woodCost() { return woodCost; }
    public int stoneCost() { return stoneCost; }

    /** Wood the next step consumes. */
    public int woodForStep(StructureTemplate t) {
        return CostShare.ofStep(woodCost, totalSteps(t), step);
    }

    /** Stone the next step consumes. */
    public int stoneForStep(StructureTemplate t) {
        return CostShare.ofStep(stoneCost, totalSteps(t), step);
    }

    /** Wood still to be consumed by the steps left. */
    public int woodLeft(StructureTemplate t) {
        return woodCost - CostShare.due(woodCost, totalSteps(t), step);
    }

    /** Stone still to be consumed by the steps left. */
    public int stoneLeft(StructureTemplate t) {
        return stoneCost - CostShare.due(stoneCost, totalSteps(t), step);
    }

    /** Nombre total de pas pour un plan de cette taille. */
    public static int totalSteps(StructureTemplate t) {
        return levelSteps(t) + BuildBlocks.of(t).size();
    }

    private static int levelSteps(StructureTemplate t) {
        return t.getSize().getZ() + 2 * TerrainLeveler.MARGIN;
    }

    public boolean done(StructureTemplate t) {
        return step >= totalSteps(t);
    }

    /** Fraction réalisée, de 0 à 1. */
    public double progress(StructureTemplate t) {
        return Math.min(1.0, step / (double) totalSteps(t));
    }

    /** Why a step did not advance. */
    public enum Result {
        /** The step was made. */
        DONE,
        /** The builder does not carry what the step costs. */
        NO_SUPPLY,
        /** A living being (not the builder) stands where the step works: retried later. */
        BLOCKED,
        /** The builder itself stands in the block to lay (it must step aside). */
        BUILDER_IN_WAY
    }

    /** True while the step to come levels the ground (before the first block of the plan). */
    public boolean levelling(StructureTemplate t) {
        return step < levelSteps(t);
    }

    /** The block the next step lays, or {@code null} while levelling or once the plan is complete. */
    public BuildBlocks.Entry nextBlock(StructureTemplate t) {
        int k = step - levelSteps(t);
        var blocks = BuildBlocks.of(t);
        return k < 0 || k >= blocks.size() ? null : blocks.get(k);
    }

    /**
     * Fait avancer le chantier d'au plus {@code budget} pas. Un pas bloqué par un être vivant (autre que le bâtisseur)
     * n'avance pas : il sera retenté. Un pas dont {@code supply} n'a pas les matériaux n'avance pas non plus : le bâtisseur
     * s'arrête (il doit aller en chercher). Le bâtisseur qui se tient dans un bloc à poser est écarté. Renvoie le nombre
     * de pas réalisés. (Mode instantané ; le mode visible passe par {@link #advanceOne}.)
     */
    public int advance(ServerLevel level, StructureTemplate t, Entity builder, int budget, Supply supply) {
        int made = 0;
        while (made < budget && !done(t) && advanceOne(level, t, builder, supply, true) == Result.DONE) {
            made++;
        }
        return made;
    }

    /** Un seul pas ; {@code moveOut} : écarte d'office le bâtisseur qui gênerait (sinon {@link Result#BUILDER_IN_WAY}). */
    public Result advanceOne(ServerLevel level, StructureTemplate t, Entity builder, Supply supply, boolean moveOut) {
        int wood = woodForStep(t);
        int stone = stoneForStep(t);
        if (!supply.has(wood, stone)) {
            return Result.NO_SUPPLY;
        }
        Result r = step < levelSteps(t) ? levelStep(level, t, builder) : placeStep(level, t, builder, moveOut);
        if (r != Result.DONE) {
            return r;
        }
        supply.spend(wood, stone);
        step++;
        return Result.DONE;
    }

    private Result levelStep(ServerLevel level, StructureTemplate t, Entity builder) {
        var size = t.getSize();
        return TerrainLeveler.levelRow(level, origin, size.getX(), size.getY(), size.getZ(), step, builder)
                ? Result.DONE : Result.BLOCKED;
    }

    private Result placeStep(ServerLevel level, StructureTemplate t, Entity builder, boolean moveOut) {
        BuildBlocks.Entry e = nextBlock(t);
        BlockPos pos = e.at(origin);
        for (LivingEntity l : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos))) {
            if (l != builder) {
                return Result.BLOCKED;
            }
            if (!moveOut) {
                return Result.BUILDER_IN_WAY;
            }
            moveOut(level, l);
        }
        // one block of the plan. It keeps the state of the plan (a bed half or a door half alone would be erased by its own
        // shape update); then fences, panes and walls take the shape their neighbours give them, and the neighbours
        // already standing update in turn, so that beds, fences and panes join as the building goes up
        t.placeInWorld(level, origin, origin,
                new StructurePlaceSettings().setBoundingBox(new BoundingBox(pos)).setIgnoreEntities(true).setKnownShape(true), level.getRandom(), 2 | 16);
        BlockState laid = level.getBlockState(pos);
        if (laid.getBlock() instanceof CrossCollisionBlock || laid.getBlock() instanceof WallBlock) {
            BlockState shaped = Block.updateFromNeighbourShapes(laid, level, pos);
            if (shaped != laid) {
                level.setBlock(pos, shaped, 2 | 16);
                laid = shaped;
            }
        }
        laid.updateNeighbourShapes(level, pos, 2);
        return Result.DONE;
    }

    /** Écarte le bâtisseur de l'emprise (sinon il serait emmuré) : sur le sol, deux blocs à l'ouest du chantier. */
    public void moveOut(ServerLevel level, LivingEntity e) {
        int x = origin.getX() - TerrainLeveler.MARGIN - 1;
        int z = origin.getZ();
        int g = TerrainLeveler.groundY(level, x, z, origin.getY());
        e.teleportTo(x + 0.5, g == TerrainLeveler.INVALID ? origin.getY() + 1 : g + 1, z + 0.5);
    }

    /** Nombre de lits posés dans l'emprise (têtes de lit), pour mettre à jour l'état du village. */
    public int countBeds(ServerLevel level, StructureTemplate t) {
        var size = t.getSize();
        int beds = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    var s = level.getBlockState(p.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z));
                    if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) {
                        beds++;
                    }
                }
            }
        }
        return beds;
    }
}
