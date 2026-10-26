package rrp.neoforge.behaviors;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.VillagerState;
import rrp.neoforge.VillageRegistry;
import rrp.neoforge.VillageSite;
import rrp.neoforge.VillagerAdapter;
import rrp.neoforge.WarehouseService;
import rrp.neoforge.Worlds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Donne à chaque villageois l'occasion d'agir selon son rôle, à la cadence {@code behavior_interval_s}.
 * Un villageois au moral trop bas désobéit (aucune action). Quand le temps est accéléré, au plus
 * {@link #MAX_CATCHUP} cadences sont rattrapées par appel.
 */
public final class BehaviorScheduler {

    static final int MAX_CATCHUP = 3;

    private final Supplier<SimConfig> config;
    private final Supplier<MinecraftServer> server;
    private final VillageRegistry registry;
    private final WarehouseService warehouse;
    private final VillagerAdapter adapter;
    private final Map<Role, RoleBehavior> behaviors = new EnumMap<>(Role.class);
    private final ArrayDeque<Turn> pending = new ArrayDeque<>();
    private long accumulated;
    private long actions;

    public BehaviorScheduler(Supplier<SimConfig> config, Supplier<MinecraftServer> server, VillageRegistry registry,
                             WarehouseService warehouse, VillagerAdapter adapter) {
        this.config = config;
        this.server = server;
        this.registry = registry;
        this.warehouse = warehouse;
        this.adapter = adapter;
        register(new Farmer());
        register(new Forester());
        register(new Miner());
        register(new Steward());
        register(new Guard());
        register(new Builder());
        register(new Diplomat());
    }

    public void register(RoleBehavior behavior) {
        behaviors.put(behavior.role(), behavior);
    }

    public RoleBehavior behavior(Role role) {
        return behaviors.get(role);
    }

    /** Nombre d'actions de rôle menées à bien depuis le démarrage (tests, diagnostic). */
    public long actions() {
        return actions;
    }

    /** Repart d'une cadence neuve (tests). */
    public void resetClock() {
        accumulated = 0;
        pending.clear();
    }

    /** Appelé à chaque avance du temps (ticks réels ou accélérés). */
    public void onTicks(long ticks) {
        accumulated += ticks;
        long interval = (long) (config.get().get(SimConfig.Key.BEHAVIOR_INTERVAL_S) * 20);
        if (accumulated >= interval) {
            long runs = Math.min(MAX_CATCHUP, accumulated / interval);
            accumulated %= interval;
            for (VillageSite site : new ArrayList<>(registry.all())) {
                ServerLevel w = Worlds.level(server.get(), site.worldName());
                if (w == null || !WarehouseService.loaded(site, w)) {
                    continue;
                }
                for (Villager v : adapter.present(site, w)) {
                    pending.add(new Turn(site, w, v, runs));
                }
            }
        }
        // Les actions d'une cadence sont étalées sur plusieurs ticks (behaviors_per_tick par tick réel) :
        // pas de pic de lag toutes les 10 s ; le temps accéléré purge d'un coup tout ce qui est dû.
        long budget = (long) config.get().get(SimConfig.Key.BEHAVIORS_PER_TICK) * Math.max(1, ticks);
        while (budget-- > 0 && !pending.isEmpty()) {
            Turn t = pending.poll();
            if (!t.villager().isAlive() || t.villager().isRemoved()) {
                continue;
            }
            for (long i = 0; i < t.runs(); i++) {
                work(t.site(), t.level(), t.villager());
            }
        }
    }

    /** Tour de travail en attente pour un villageois. */
    private record Turn(VillageSite site, ServerLevel level, Villager villager, long runs) { }

    /** Fait agir un villageois si son rôle a un comportement et s'il obéit. Renvoie vrai s'il a agi. */
    public boolean work(VillageSite site, ServerLevel level, Villager villager) {
        VillagerState st = adapter.stateOf(villager);
        if (st == null) {
            return false;
        }
        if (rrp.neoforge.tactics.CivilianMachine.sheltered(villager)) {
            return false; // alerted or sheltering: no work until the guards have cleared the threat
        }
        var logistics = rrp.neoforge.RrpMod.logistics();
        if (logistics != null && logistics.busy(villager)) {
            return false; // mid-trip to a chest: the trip owns the villager
        }
        RoleBehavior b = behaviors.get(st.role());
        if (b == null || !b.canWork(st, config.get())) {
            return false;
        }
        return run(b, site, level, villager, st);
    }

    /** Exécution forcée de l'action du rôle (sans test de moral ni de cadence) : pour les tests. */
    public boolean runOnce(VillageSite site, ServerLevel level, Villager villager) {
        VillagerState st = adapter.stateOf(villager);
        RoleBehavior b = st == null ? null : behaviors.get(st.role());
        return b != null && run(b, site, level, villager, st);
    }

    private boolean run(RoleBehavior b, VillageSite site, ServerLevel level, Villager villager, VillagerState st) {
        boolean did = b.runOnce(new BehaviorContext(level, villager, st, site, warehouse, config.get(), registry));
        if (did) {
            actions++;
        }
        return did;
    }
}
