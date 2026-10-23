package rrp.neoforge.urban;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * Chantier en cours : un plan posé à {@code origin}, nivellement d'abord (une rangée de l'emprise élargie par pas),
 * puis le plan rangée par rangée (un pas = une rangée {@code x} à un étage donné, sans mise à jour des formes), et enfin
 * une passe qui repose le plan entier pour raccorder lits, clôtures et vitres. Seuls le type, l'origine et le
 * numéro de pas sont persistés : le reste se déduit du plan, donc le chantier reprend après un redémarrage.
 */
public final class ConstructionSite {

    private final BuildType type;
    private final BlockPos origin;
    private int step;

    public ConstructionSite(BuildType type, BlockPos origin, int step) {
        this.type = type;
        this.origin = origin;
        this.step = step;
    }

    public BuildType type() { return type; }
    public BlockPos origin() { return origin; }
    public int step() { return step; }

    /** Nombre total de pas pour un plan de cette taille. */
    public static int totalSteps(StructureTemplate t) {
        return levelSteps(t) + t.getSize().getY() * t.getSize().getZ() + 1;
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

    /**
     * Fait avancer le chantier d'au plus {@code budget} pas. Un pas bloqué par un être vivant (autre que le bâtisseur)
     * n'avance pas : il sera retenté. Renvoie le nombre de pas réalisés.
     */
    public int advance(ServerLevel level, StructureTemplate t, Entity builder, int budget) {
        int made = 0;
        while (made < budget && !done(t)) {
            boolean ok = step < levelSteps(t) ? levelStep(level, t, builder) : placeStep(level, t, builder);
            if (!ok) {
                break;
            }
            step++;
            made++;
        }
        return made;
    }

    private boolean levelStep(ServerLevel level, StructureTemplate t, Entity builder) {
        var size = t.getSize();
        return TerrainLeveler.levelRow(level, origin, size.getX(), size.getY(), size.getZ(), step, builder);
    }

    private boolean placeStep(ServerLevel level, StructureTemplate t, Entity builder) {
        var size = t.getSize();
        int k = step - levelSteps(t);
        if (k == size.getY() * size.getZ()) {
            // passe finale : repose le plan entier, les formes (lits, clôtures, vitres) se raccordent enfin
            t.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
            return true;
        }
        int y = k / size.getZ();
        int z = k % size.getZ();
        BoundingBox row = new BoundingBox(origin.getX(), origin.getY() + y, origin.getZ() + z,
                origin.getX() + size.getX() - 1, origin.getY() + y, origin.getZ() + z);
        AABB box = new AABB(row.minX(), row.minY(), row.minZ(), row.maxX() + 1, row.maxY() + 1, row.maxZ() + 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == builder) {
                moveOut(level, e);
            } else {
                return false;
            }
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setBoundingBox(row);
        t.placeInWorld(level, origin, origin, settings, level.getRandom(), 2 | 16);
        return true;
    }

    /** Écarte le bâtisseur de l'emprise (sinon il serait emmuré) : sur le sol, deux blocs à l'ouest du chantier. */
    private void moveOut(ServerLevel level, LivingEntity e) {
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
