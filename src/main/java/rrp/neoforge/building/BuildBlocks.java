package rrp.neoforge.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import rrp.core.building.BlockOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The blocks of a blueprint in building order: air and structure voids left out, then {@link BlockOrder} (bottom-up,
 * snake walk inside a layer). Read once per template from its saved form, so it needs no level, and cached.
 */
public final class BuildBlocks {

    /** One block of the plan, relative to the origin of the site. */
    public record Entry(int x, int y, int z, BlockState state) {
        public BlockPos at(BlockPos origin) {
            return origin.offset(x, y, z);
        }
    }

    private static final Map<StructureTemplate, List<Entry>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private BuildBlocks() {
    }

    /** The blocks to lay, in the order to lay them. */
    public static List<Entry> of(StructureTemplate template) {
        return CACHE.computeIfAbsent(template, BuildBlocks::read);
    }

    private static List<Entry> read(StructureTemplate template) {
        CompoundTag tag = template.save(new CompoundTag());
        var blocks = BuiltInRegistries.BLOCK.asLookup();
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            states.add(NbtUtils.readBlockState(blocks, palette.getCompound(i)));
        }
        List<Entry> entries = new ArrayList<>();
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            BlockState s = states.get(b.getInt("state"));
            if (s.isAir() || s.is(net.minecraft.world.level.block.Blocks.STRUCTURE_VOID)) {
                continue;
            }
            ListTag p = b.getList("pos", Tag.TAG_INT);
            entries.add(new Entry(p.getInt(0), p.getInt(1), p.getInt(2), s));
        }
        List<int[]> positions = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            positions.add(new int[] {e.x, e.y, e.z, i});
        }
        List<Entry> sorted = new ArrayList<>(entries.size());
        for (int[] p : BlockOrder.sort(positions)) {
            sorted.add(entries.get(p[3]));
        }
        return List.copyOf(sorted);
    }
}
