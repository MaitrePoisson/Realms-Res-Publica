package rrp.neoforge.urban;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import rrp.neoforge.RrpMod;

import java.util.Optional;

/**
 * Réserve de plans de bâtiments (fichiers {@code .nbt} générés par {@code tools/gen_blueprints.py}) : un plan par
 * type et par famille de biome, chargé par le {@link StructureTemplateManager} de Minecraft.
 */
public final class BlueprintPool {

    /** Les plans disponibles. */
    public enum Kind {
        HOUSE("house"), BIG_HOUSE("big_house"), FARM("farm"), WORKSHOP("workshop"),
        WATCHTOWER("watchtower"), WALL_SEGMENT("wall_segment"), GATE("gate"),
        MARKET("market"), BARRACKS("barracks"), MONUMENT("monument"), BREWERY("brewery"),
        RAM("ram"), CATAPULT("catapult");

        private final String file;

        Kind(String file) { this.file = file; }

        public String file() { return file; }

        /** Plan correspondant à un ordre de construction. */
        public static Kind of(BuildType type) {
            return switch (type) {
                case HOUSE -> HOUSE;
                case FARM -> FARM;
                case WORKSHOP -> WORKSHOP;
                case TOWER -> WATCHTOWER;
                case WALL -> WALL_SEGMENT;
                case MARKET -> MARKET;
                case BARRACKS -> BARRACKS;
                case MONUMENT -> MONUMENT;
                case BREWERY -> BREWERY;
            };
        }
    }

    private BlueprintPool() {
    }

    public static ResourceLocation id(Kind kind, BiomeStyle style) {
        return ResourceLocation.fromNamespaceAndPath(RrpMod.MOD_ID, kind.file() + "_" + style.key());
    }

    public static Optional<StructureTemplate> load(ServerLevel level, Kind kind, BiomeStyle style) {
        return level.getServer().getStructureManager().get(id(kind, style));
    }

    /** Plan du type demandé dans le style du biome situé à {@code at}. */
    public static Optional<StructureTemplate> load(ServerLevel level, Kind kind, BlockPos at) {
        return load(level, kind, BiomeStyle.of(level.getBiome(at)));
    }
}
