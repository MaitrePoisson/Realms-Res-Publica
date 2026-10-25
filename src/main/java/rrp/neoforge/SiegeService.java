package rrp.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.diplomacy.Siege;
import rrp.neoforge.urban.BlueprintPool;

/**
 * Sièges : un village dont le régiment est mobilisé fait assembler par ses bâtisseurs un bélier (portes et portails
 * en bois) puis une catapulte (blocs de rempart) près de la frontière ennemie. Les machines ont des points de vie,
 * les défenseurs les détruisent, et le nombre total de blocs détruits par siège est plafonné (configuration).
 */
public final class SiegeService {

    /** Machine posée dans le monde. */
    public static final class Machine {
        final Siege.Engine engine;
        final BlockPos origin;
        final BlockPos size;

        Machine(Siege.Engine engine, BlockPos origin, BlockPos size) {
            this.engine = engine;
            this.origin = origin;
            this.size = size;
        }

        public Siege.Kind kind() { return engine.kind(); }
        public double hp() { return engine.hp(); }
        public BlockPos origin() { return origin; }

        double cx() { return origin.getX() + size.getX() / 2.0; }
        double cz() { return origin.getZ() + size.getZ() / 2.0; }

        boolean covers(BlockPos p) {
            return p.getX() >= origin.getX() && p.getX() < origin.getX() + size.getX()
                    && p.getY() >= origin.getY() && p.getY() < origin.getY() + size.getY()
                    && p.getZ() >= origin.getZ() && p.getZ() < origin.getZ() + size.getZ();
        }
    }

    /** Siège d'un village attaquant contre un village ennemi. */
    public static final class State {
        final String attacker;
        final String defender;
        final List<Machine> machines = new ArrayList<>();
        final Siege.Budget budget;
        final List<BlockPos> breaches = new ArrayList<>();

        State(String attacker, String defender, int cap) {
            this.attacker = attacker;
            this.defender = defender;
            this.budget = new Siege.Budget(cap);
        }

        public String attacker() { return attacker; }
        public String defender() { return defender; }
        public List<Machine> machines() { return machines; }
        public Siege.Budget budget() { return budget; }
        public List<BlockPos> breaches() { return breaches; }
    }

    private static final Map<String, State> SIEGES = new HashMap<>();
    private static double since;

    private SiegeService() { }

    public static synchronized void reset() {
        SIEGES.clear();
        since = 0;
    }

    public static synchronized State siege(String attacker) { return SIEGES.get(attacker); }

    public static synchronized List<State> sieges() { return new ArrayList<>(SIEGES.values()); }

    public static synchronized void onTicks(long ticks, SimConfig cfg, VillageRegistry registry) {
        since += ticks / 20.0;
        double interval = cfg.get(SimConfig.Key.SIEGE_INTERVAL_S);
        if (interval > 0 && since >= interval) {
            since = 0;
            update(cfg, registry);
        }
    }

    /** Pour chaque régiment mobilisé : défenseurs, assemblage, tirs. Les sièges sans régiment sont levés. */
    public static synchronized void update(SimConfig cfg, VillageRegistry registry) {
        if (RrpMod.server() == null) {
            return;
        }
        for (State s : new ArrayList<>(SIEGES.values())) {
            var f = MobilizationService.force(s.attacker);
            if (f == null || !f.enemy().equals(s.defender)) {
                VillageSite a = registry.get(s.attacker);
                if (a != null) {
                    RrpMod.crises().tell(a, RrpMod.lang().msg("siege.ended", s.attacker, s.defender));
                }
                SIEGES.remove(s.attacker);
            }
        }
        for (var f : MobilizationService.forces()) {
            VillageSite attacker = registry.get(f.village());
            VillageSite defender = registry.get(f.enemy());
            if (attacker == null || defender == null || !attacker.worldName().equals(defender.worldName())) {
                continue;
            }
            ServerLevel level = Worlds.level(RrpMod.server(), attacker.worldName());
            if (level == null) {
                continue;
            }
            State s = SIEGES.computeIfAbsent(attacker.id(),
                    k -> new State(attacker.id(), defender.id(), (int) cfg.get(SimConfig.Key.SIEGE_BLOCK_CAP)));
            defend(s, defender, level, cfg);
            assemble(s, attacker, defender, level, cfg);
            fire(s, attacker, defender, level, cfg);
        }
    }

    /** Les gardes du village attaqué à portée d'une machine la frappent. */
    private static void defend(State s, VillageSite defender, ServerLevel level, SimConfig cfg) {
        double range = cfg.get(SimConfig.Key.SIEGE_DEFENDER_RANGE);
        double per = cfg.get(SimConfig.Key.SIEGE_DEFENDER_DAMAGE);
        List<Villager> guards = new ArrayList<>();
        for (Villager v : RrpMod.adapter().present(defender, level)) {
            var st = RrpMod.adapter().stateOf(v);
            if (st != null && st.role() == Role.GUARD) {
                guards.add(v);
            }
        }
        for (Machine m : new ArrayList<>(s.machines)) {
            int near = 0;
            for (Villager g : guards) {
                if (Math.hypot(g.getX() - m.cx(), g.getZ() - m.cz()) <= range) {
                    near++;
                }
            }
            if (near > 0) {
                strike(s, m, Siege.defenderDamage(near, per), level);
            }
        }
    }

    /** Inflige des dégâts à la machine du type donné du siège de {@code attacker} ; la détruit à 0 PV. */
    public static synchronized boolean strike(String attacker, Siege.Kind kind, double damage, ServerLevel level) {
        State s = SIEGES.get(attacker);
        if (s == null) {
            return false;
        }
        for (Machine m : new ArrayList<>(s.machines)) {
            if (m.kind() == kind) {
                strike(s, m, damage, level);
                return true;
            }
        }
        return false;
    }

    private static void strike(State s, Machine m, double damage, ServerLevel level) {
        m.engine.hit(damage);
        if (!m.engine.destroyed()) {
            return;
        }
        for (BlockPos p : BlockPos.betweenClosed(m.origin,
                m.origin.offset(m.size.getX() - 1, m.size.getY() - 1, m.size.getZ() - 1))) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        s.machines.remove(m);
        VillageSite d = RrpMod.registry().get(s.defender);
        if (d != null) {
            RrpMod.crises().tell(d, RrpMod.lang().msg("siege.destroyed", s.defender, kindName(m.kind()), s.attacker));
        }
    }

    private static String kindName(Siege.Kind k) {
        return RrpMod.lang().get("siege.kind." + k.name().toLowerCase(Locale.ROOT));
    }

    /** Un bâtisseur présent assemble la prochaine machine si le bois de l'entrepôt suffit. */
    private static void assemble(State s, VillageSite attacker, VillageSite defender, ServerLevel level, SimConfig cfg) {
        boolean ram = false;
        boolean catapult = false;
        for (Machine m : s.machines) {
            ram |= m.kind() == Siege.Kind.RAM;
            catapult |= m.kind() == Siege.Kind.CATAPULT;
        }
        Siege.Kind kind = Siege.next(ram, catapult);
        if (kind == null || s.budget.remaining() <= 0) {
            return;
        }
        boolean builder = false;
        for (Villager v : RrpMod.adapter().present(attacker, level)) {
            var st = RrpMod.adapter().stateOf(v);
            builder |= st != null && st.role() == Role.BUILDER;
        }
        if (!builder) {
            return;
        }
        int wood = (int) cfg.get(kind == Siege.Kind.RAM ? SimConfig.Key.SIEGE_RAM_WOOD : SimConfig.Key.SIEGE_CATAPULT_WOOD);
        if (RrpMod.warehouse().read(attacker, level).getOrDefault(Resource.WOOD, 0) < wood) {
            return;
        }
        BlockPos at = spot(attacker, defender, kind, level, cfg);
        if (at == null) {
            return;
        }
        var template = BlueprintPool.load(level,
                kind == Siege.Kind.RAM ? BlueprintPool.Kind.RAM : BlueprintPool.Kind.CATAPULT, at);
        if (template.isEmpty()) {
            return;
        }
        RrpMod.warehouse().withdraw(attacker, level, Resource.WOOD, wood);
        var size = template.get().getSize();
        template.get().placeInWorld(level, at, at, new StructurePlaceSettings(), level.getRandom(), 2);
        double hp = cfg.get(kind == Siege.Kind.RAM ? SimConfig.Key.SIEGE_RAM_HP : SimConfig.Key.SIEGE_CATAPULT_HP);
        s.machines.add(new Machine(new Siege.Engine(kind, hp), at, new BlockPos(size.getX(), size.getY(), size.getZ())));
        RrpMod.crises().tell(attacker, RrpMod.lang().msg("siege.assembled", attacker.id(), kindName(kind), defender.id()));
    }

    /** Point d'assemblage : sur la ligne ennemi -> attaquant à {@code siege_standoff} blocs, la catapulte en retrait. */
    private static BlockPos spot(VillageSite attacker, VillageSite defender, Siege.Kind kind, ServerLevel level,
                                 SimConfig cfg) {
        int[] hall = defender.hall();
        double hx = hall == null ? defender.x() : hall[0] + 0.5;
        double hz = hall == null ? defender.z() : hall[2] + 0.5;
        double dx = attacker.x() - hx;
        double dz = attacker.z() - hz;
        double len = Math.hypot(dx, dz);
        if (len < 1) {
            dx = 1;
            dz = 0;
            len = 1;
        }
        double dist = cfg.get(SimConfig.Key.SIEGE_STANDOFF) + (kind == Siege.Kind.CATAPULT ? 6 : 0);
        int x = (int) Math.floor(hx + dx / len * dist);
        int z = (int) Math.floor(hz + dz / len * dist);
        if (!level.isLoaded(new BlockPos(x, 0, z))) {
            return null;
        }
        int y0 = (int) Math.floor(defender.y());
        for (int y = y0 + 4; y >= y0 - 8; y--) {
            BlockState below = level.getBlockState(new BlockPos(x, y - 1, z));
            if (!below.isAir() && !below.canBeReplaced() && level.getBlockState(new BlockPos(x, y, z)).canBeReplaced()) {
                return new BlockPos(x, y, z);
            }
        }
        return new BlockPos(x, y0, z);
    }

    /** Chaque machine détruit au plus une cible par mise à jour, tant que le budget du siège le permet. */
    private static void fire(State s, VillageSite attacker, VillageSite defender, ServerLevel level, SimConfig cfg) {
        for (Machine m : s.machines) {
            boolean ram = m.kind() == Siege.Kind.RAM;
            int range = (int) cfg.get(ram ? SimConfig.Key.SIEGE_RAM_RANGE : SimConfig.Key.SIEGE_CATAPULT_RANGE);
            BlockPos target = findTarget(s, m, ram, range, defender, level);
            if (target == null) {
                continue;
            }
            BlockState st = level.getBlockState(target);
            boolean door = st.is(BlockTags.WOODEN_DOORS);
            int need = door ? 2 : 1;
            if (s.budget.remaining() < need) {
                continue;
            }
            s.budget.take(need);
            if (door) {
                BlockPos other = st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? target.above() : target.below();
                level.destroyBlock(other, false);
                s.breaches.add(other.immutable());
            }
            level.destroyBlock(target, false);
            s.breaches.add(target.immutable());
            RrpMod.crises().tell(defender, RrpMod.lang().msg("siege.breach", attacker.id(), defender.id(), need,
                    s.budget.used(), s.budget.cap()));
        }
    }

    private static boolean wallBlock(BlockState st) {
        Block b = st.getBlock();
        return b == Blocks.COBBLESTONE || b == Blocks.MOSSY_COBBLESTONE || b == Blocks.STONE_BRICKS || b == Blocks.STONE
                || b == Blocks.SANDSTONE || b == Blocks.CUT_SANDSTONE || b == Blocks.COBBLESTONE_SLAB
                || b == Blocks.SANDSTONE_SLAB || b == Blocks.STONE_BRICK_SLAB || b == Blocks.MOSSY_COBBLESTONE_SLAB
                || st.is(BlockTags.FENCES);
    }

    private static boolean covered(State s, BlockPos p) {
        for (Machine o : s.machines) {
            if (o.covers(p)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos findTarget(State s, Machine m, boolean ram, int range, VillageSite defender, ServerLevel level) {
        BlockPos center = BlockPos.containing(m.cx(), m.origin.getY() + 1, m.cz());
        int vert = ram ? 3 : 8;
        int floor = (int) Math.floor(defender.y()) - 1;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-range, -vert, -range), center.offset(range, vert, range))) {
            if (p.getY() < floor || covered(s, p) || !level.isLoaded(p)) {
                continue;
            }
            double d = Math.hypot(p.getX() + 0.5 - m.cx(), p.getZ() + 0.5 - m.cz());
            if (d > range || d >= bestD
                    || defender.distance(defender.worldName(), p.getX() + 0.5, p.getZ() + 0.5) > defender.reach() + 4) {
                continue;
            }
            BlockState st = level.getBlockState(p);
            boolean hit = ram ? st.is(BlockTags.WOODEN_DOORS) || st.is(BlockTags.FENCE_GATES) : wallBlock(st);
            if (hit) {
                best = p.immutable();
                bestD = d;
            }
        }
        return best;
    }
}
