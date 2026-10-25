package rrp.neoforge;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import rrp.core.abstractsim.AbstractMarch;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.diplomacy.RelationMatrix;
import rrp.core.diplomacy.TradePlanner;

/**
 * Caravanes marchandes : un village en surplus envoie un marchand (escorté de gardes) échanger ses biens contre
 * ceux dont il manque auprès d'un partenaire de relation assez bonne. Le marchand emporte les biens retirés de
 * l'entrepôt, les échange à l'arrivée et rapporte la contrepartie. Une embuscade perd la cargaison.
 */
public final class CaravanService {

    /** Mise à jour des caravanes toutes les secondes de jeu. */
    private static final long STEP_TICKS = 20;
    private static final double ARRIVAL = 6.0;

    /** Une caravane en route. */
    public static final class Caravan {
        final String from;
        final String to;
        final TradePlanner.Offer offer;
        final boolean ambush;
        WanderingTrader trader;
        final List<Villager> escorts = new ArrayList<>();
        /** Set while the caravan travels as a point on the map (no entity); null while it is physical. */
        AbstractMarch march;
        int abstractEscorts;
        Resource carried;
        int amount;
        int unsold;
        boolean returning;
        double age;

        Caravan(String from, String to, TradePlanner.Offer offer, boolean ambush) {
            this.from = from;
            this.to = to;
            this.offer = offer;
            this.ambush = ambush;
            this.carried = offer.give();
            this.amount = offer.giveAmount();
        }

        public String from() { return from; }
        public String to() { return to; }
        public boolean returning() { return returning; }
        public int escorts() { return Math.max(escorts.size(), abstractEscorts); }
        public boolean isAbstract() { return trader == null && march != null; }
        public AbstractMarch march() { return march; }
    }

    private static final List<Caravan> ACTIVE = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static long accumulated;
    private static double sinceDispatch;
    private static long completed;
    private static long ambushed;

    private CaravanService() { }

    public static synchronized void reset() {
        ACTIVE.clear();
        accumulated = 0;
        sinceDispatch = 0;
        completed = 0;
        ambushed = 0;
    }

    public static synchronized int active() { return ACTIVE.size(); }
    public static synchronized long completed() { return completed; }
    public static synchronized long ambushed() { return ambushed; }

    /** Routes {x1, z1, x2, z2} (blocks) of the caravans on the road in a world, for the political map. */
    public static synchronized java.util.List<int[]> routes(String world) {
        java.util.List<int[]> out = new ArrayList<>();
        for (Caravan c : ACTIVE) {
            VillageSite a = RrpMod.registry().get(c.from);
            VillageSite b = RrpMod.registry().get(c.to);
            if (a != null && b != null && a.worldName().equals(world) && b.worldName().equals(world)) {
                out.add(new int[] {(int) a.x(), (int) a.z(), (int) b.x(), (int) b.z()});
            }
        }
        return out;
    }

    public static ItemStack stackOf(Resource r, int n) {
        return new ItemStack(switch (r) {
            case FOOD -> Items.BREAD;
            case WOOD -> Items.OAK_LOG;
            case ORE -> Items.IRON_INGOT;
            case STONE -> Items.COBBLESTONE;
        }, n);
    }

    public static synchronized void onTicks(long ticks, SimConfig cfg, VillageRegistry registry) {
        accumulated += ticks;
        if (accumulated < STEP_TICKS) {
            return;
        }
        double seconds = accumulated / 20.0;
        accumulated = 0;
        for (Caravan c : new ArrayList<>(ACTIVE)) {
            update(c, seconds, cfg, registry);
        }
        sinceDispatch += seconds;
        double interval = cfg.get(SimConfig.Key.CARAVAN_INTERVAL_S);
        if (interval > 0 && sinceDispatch >= interval) {
            sinceDispatch = 0;
            autoDispatch(cfg, registry);
        }
    }

    private static void autoDispatch(SimConfig cfg, VillageRegistry registry) {
        var all = new ArrayList<>(registry.all());
        for (VillageSite a : all) {
            for (VillageSite b : all) {
                if (a.id().compareTo(b.id()) < 0 && a.worldName().equals(b.worldName())
                        && RelationService.index(a, b, cfg) >= cfg.get(SimConfig.Key.RELATION_TRADE_AT)
                        && !DiplomacyService.atWar(a.id(), b.id()) && !busy(a.id(), b.id())) {
                    dispatch(a, b, cfg);
                }
            }
        }
    }

    private static boolean busy(String a, String b) {
        String key = RelationMatrix.key(a, b);
        for (Caravan c : ACTIVE) {
            if (RelationMatrix.key(c.from, c.to).equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** Prépare et envoie une caravane si un troc est possible dans un sens ou dans l'autre. */
    public static synchronized Optional<Caravan> dispatch(VillageSite a, VillageSite b, SimConfig cfg) {
        ServerLevel level = Worlds.level(RrpMod.server(), a.worldName());
        if (LawService.embargo(a.id(), b.id())) {
            return Optional.empty();
        }
        if (level == null || !a.worldName().equals(b.worldName()) || !ready(a, level) || !ready(b, level)) {
            return Optional.empty();
        }
        refresh(a, level);
        refresh(b, level);
        int surplus = (int) cfg.get(SimConfig.Key.CARAVAN_SURPLUS);
        int deficit = (int) cfg.get(SimConfig.Key.CARAVAN_DEFICIT);
        int load = (int) cfg.get(SimConfig.Key.CARAVAN_MAX_LOAD);
        VillageSite from = a;
        VillageSite to = b;
        var offer = TradePlanner.plan(stocks(a), stocks(b), surplus, deficit, load);
        if (offer.isEmpty()) {
            offer = TradePlanner.plan(stocks(b), stocks(a), surplus, deficit, load);
            from = b;
            to = a;
        }
        if (offer.isEmpty()) {
            return Optional.empty();
        }
        TradePlanner.Offer o = offer.get();
        int taken = take(from, level, o.give(), o.giveAmount());
        if (taken <= 0) {
            return Optional.empty();
        }
        int maxEscorts = (int) cfg.get(SimConfig.Key.CARAVAN_ESCORTS);
        List<Villager> guards = new ArrayList<>();
        int escortCount;
        if (from.isAbstract()) {
            escortCount = Math.min(maxEscorts, from.state().count(Role.GUARD));
        } else {
            for (Villager v : RrpMod.adapter().present(from, level)) {
                var st = RrpMod.adapter().stateOf(v);
                if (st != null && st.role() == Role.GUARD && guards.size() < maxEscorts) {
                    guards.add(v);
                }
            }
            escortCount = guards.size();
        }
        double chance = TradePlanner.ambushChance(cfg.get(SimConfig.Key.CARAVAN_AMBUSH_CHANCE), escortCount);
        Caravan c = new Caravan(from.id(), to.id(), new TradePlanner.Offer(o.give(), taken, o.get(), o.getAmount()),
                RANDOM.nextDouble() < chance);
        if (from.isAbstract()) {
            c.march = AbstractRoutes.between(from, to, cfg);
            c.abstractEscorts = escortCount;
        } else {
            spawnTrader(c, from, level);
            c.escorts.addAll(guards);
        }
        ACTIVE.add(c);
        RrpMod.crises().tell(from, RrpMod.lang().msg("caravan.depart", from.id(), to.id(), c.offer.give().label(), taken,
                c.offer.get().label(), c.offer.getAmount()));
        return Optional.of(c);
    }

    private static void spawnTrader(Caravan c, VillageSite from, ServerLevel level) {
        BlockPos start = warehousePos(from);
        spawnTrader(c, from, level, start.getX() + 0.5, start.getY() + 1, start.getZ() + 0.5);
    }

    private static void spawnTrader(Caravan c, VillageSite from, ServerLevel level, double x, double y, double z) {
        WanderingTrader t = new WanderingTrader(EntityType.WANDERING_TRADER, level);
        t.moveTo(x, y, z);
        t.setCustomName(Component.literal(RrpMod.lang().msg("caravan.name", from.id())));
        t.setDespawnDelay(0);
        level.addFreshEntity(t);
        c.trader = t;
    }

    /** A village can take part in a trade when it is asleep (in-memory stock) or its warehouse is readable. */
    private static boolean ready(VillageSite s, ServerLevel level) {
        return s.isAbstract() || (s.hasWarehouse() && WarehouseService.loaded(s, level));
    }

    private static void refresh(VillageSite s, ServerLevel level) {
        if (!s.isAbstract()) {
            RrpMod.warehouse().refresh(s, level);
        }
    }

    /** Takes goods from a village: the in-memory stock when it is ABSTRACT, its warehouse otherwise. */
    private static int take(VillageSite s, ServerLevel level, Resource r, int n) {
        if (s.isAbstract()) {
            int t = Math.max(0, Math.min(n, s.state().stock(r)));
            s.state().addStock(r, -t);
            return t;
        }
        return RrpMod.warehouse().withdraw(s, level, r, n);
    }

    /** Puts goods into a village (in memory when ABSTRACT); returns the quantity refused. */
    private static int put(VillageSite s, ServerLevel level, Resource r, int n) {
        if (s.isAbstract()) {
            s.state().addStock(r, n);
            return 0;
        }
        return RrpMod.warehouse().deposit(s, level, stackOf(r, n)).getCount();
    }

    private static Map<Resource, Integer> stocks(VillageSite s) {
        Map<Resource, Integer> m = new EnumMap<>(Resource.class);
        for (Resource r : Resource.values()) {
            m.put(r, s.state().stock(r));
        }
        return m;
    }

    private static BlockPos warehousePos(VillageSite s) {
        int[] w = s.warehouse();
        return w == null ? BlockPos.containing(s.x(), s.y(), s.z()) : new BlockPos(w[0], w[1], w[2]);
    }

    private static void update(Caravan c, double seconds, SimConfig cfg, VillageRegistry registry) {
        VillageSite origin = registry.get(c.from);
        VillageSite dest = registry.get(c.to);
        if (c.trader == null && c.march != null && origin != null && dest != null) {
            updateAbstract(c, origin, dest, seconds, cfg);
            return;
        }
        if (origin == null || dest == null || c.trader == null || !c.trader.isAlive()) {
            fail(c, origin, dest, cfg, "caravan.lost");
            return;
        }
        VillageSite target = c.returning ? origin : dest;
        ServerLevel level = (ServerLevel) c.trader.level();
        c.age += seconds;
        double timeout = cfg.get(SimConfig.Key.CARAVAN_TIMEOUT_S);
        if (c.ambush && !c.returning && c.age >= timeout / 3) {
            ambushed++;
            fail(c, origin, dest, cfg, "caravan.ambush");
            return;
        }
        if (target.isAbstract() && !inActiveArea(level, c.trader.getX(), c.trader.getZ(), 16)) {
            // the party left every active area on its way to a sleeping village: it continues as a point on the map
            c.march = AbstractRoutes.from(c.trader.getX(), c.trader.getZ(), c.returning ? dest : origin, target, cfg);
            c.trader.discard();
            c.trader = null;
            c.escorts.clear();
            return;
        }
        BlockPos goal = warehousePos(target);
        c.trader.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 0.6);
        for (Villager e : c.escorts) {
            if (e.isAlive() && !e.isNoAi()) {
                e.getNavigation().moveTo(c.trader.getX(), c.trader.getY(), c.trader.getZ(), 0.6);
            }
        }
        boolean near = c.trader.blockPosition().distSqr(goal) <= ARRIVAL * ARRIVAL;
        if (!near && c.age < timeout) {
            return;
        }
        if (!near) {
            c.trader.teleportTo(goal.getX() + 0.5, goal.getY() + 1, goal.getZ() + 0.5);
        }
        c.age = 0;
        if (!c.returning) {
            trade(c, dest, level);
        } else {
            finish(c, origin, dest, level, cfg);
        }
    }

    /** True when (x, z) lies inside the radius (+ margin) of a village whose chunks are live. */
    private static boolean inActiveArea(ServerLevel level, double x, double z, double margin) {
        for (VillageSite v : RrpMod.registry().all()) {
            if (!v.isAbstract() && v.worldName().equals(Worlds.name(level))
                    && Math.hypot(x - v.x(), z - v.z()) <= v.radius() + margin
                    && level.hasChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
                return true;
            }
        }
        return false;
    }

    /** One step of a caravan travelling as a point: ambush, advance, materialise near live villages, trade. */
    private static void updateAbstract(Caravan c, VillageSite origin, VillageSite dest, double seconds, SimConfig cfg) {
        VillageSite target = c.returning ? origin : dest;
        c.age += seconds;
        double timeout = cfg.get(SimConfig.Key.CARAVAN_TIMEOUT_S);
        if (c.ambush && !c.returning && c.age >= timeout / 3) {
            ambushed++;
            fail(c, origin, dest, cfg, "caravan.ambush");
            return;
        }
        c.march.advance(seconds);
        ServerLevel level = Worlds.level(RrpMod.server(), target.worldName());
        if (level == null) {
            return;
        }
        if (!c.march.arrived() && inActiveArea(level, c.march.x(), c.march.z(), 0)) {
            int bx = (int) Math.floor(c.march.x());
            int bz = (int) Math.floor(c.march.z());
            spawnTrader(c, origin, level, bx + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz), bz + 0.5);
            c.march = null;
            return;
        }
        if (!c.march.arrived() || (!target.isAbstract() && !ready(target, level))) {
            return;
        }
        c.age = 0;
        if (!c.returning) {
            trade(c, dest, level);
            c.march = AbstractRoutes.between(dest, origin, cfg);
        } else {
            finish(c, origin, dest, level, cfg);
        }
    }

    /** À l'arrivée : la cargaison est vendue, la contrepartie est chargée. Ce qui ne rentre pas revient. */
    private static void trade(Caravan c, VillageSite dest, ServerLevel level) {
        int refused = put(dest, level, c.offer.give(), c.amount);
        // si le partenaire manque de place, l'invendu revient et la contrepartie est réduite
        int sold = c.amount - refused;
        int wanted = refused == 0 ? c.offer.getAmount() : c.offer.getAmount() * sold / Math.max(1, c.amount);
        int bought = take(dest, level, c.offer.get(), wanted);
        c.unsold = refused;
        c.carried = c.offer.get();
        c.amount = bought;
        c.returning = true;
    }

    private static void finish(Caravan c, VillageSite origin, VillageSite dest, ServerLevel level, SimConfig cfg) {
        if (c.amount > 0) {
            put(origin, level, c.carried, c.amount);
        }
        if (c.unsold > 0) {
            put(origin, level, c.offer.give(), c.unsold);
        }
        if (c.trader != null) {
            c.trader.discard();
        }
        ACTIVE.remove(c);
        completed++;
        RelationService.change(origin, dest, cfg.get(SimConfig.Key.CARAVAN_RELATION_GAIN), cfg);
        RrpMod.registry().markDirty();
        RrpMod.crises().tell(origin, RrpMod.lang().msg("caravan.arrive", origin.id(), dest.id(), c.offer.give().label(),
                c.offer.giveAmount() - c.unsold, c.offer.get().label(), c.amount));
    }

    private static void fail(Caravan c, VillageSite origin, VillageSite dest, SimConfig cfg, String key) {
        if (c.trader != null) {
            c.trader.discard();
        }
        ACTIVE.remove(c);
        if (origin != null && dest != null) {
            RelationService.change(origin, dest, -cfg.get(SimConfig.Key.CARAVAN_RELATION_LOSS), cfg);
            RrpMod.crises().tell(origin, RrpMod.lang().msg(key, c.from, c.to));
        }
    }

    /** Arrêt du serveur : les marchandises en transit retournent à l'entrepôt d'origine. */
    public static synchronized void abortAll(VillageRegistry registry) {
        for (Caravan c : new ArrayList<>(ACTIVE)) {
            VillageSite origin = registry.get(c.from);
            if (origin != null && (c.trader != null || c.march != null)) {
                ServerLevel level = Worlds.level(RrpMod.server(), origin.worldName());
                if (origin.isAbstract() || (level != null && WarehouseService.loaded(origin, level))) {
                    if (c.amount > 0) {
                        put(origin, level, c.carried, c.amount);
                    }
                    if (c.unsold > 0) {
                        put(origin, level, c.offer.give(), c.unsold);
                    }
                }
            }
            if (c.trader != null) {
                c.trader.discard();
            }
        }
        ACTIVE.clear();
    }
}
