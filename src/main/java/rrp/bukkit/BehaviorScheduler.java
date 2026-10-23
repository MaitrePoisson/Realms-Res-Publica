package rrp.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import rrp.bukkit.behaviors.BehaviorContext;
import rrp.bukkit.behaviors.Farmer;
import rrp.bukkit.behaviors.Forester;
import rrp.bukkit.behaviors.Guard;
import rrp.bukkit.behaviors.Miner;
import rrp.bukkit.behaviors.RoleBehavior;
import rrp.bukkit.behaviors.Steward;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.VillagerState;

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
    private final VillageRegistry registry;
    private final WarehouseService warehouse;
    private final VillagerAdapter adapter;
    private final Map<Role, RoleBehavior> behaviors = new EnumMap<>(Role.class);
    private long accumulated;
    private long actions;

    public BehaviorScheduler(Supplier<SimConfig> config, VillageRegistry registry, WarehouseService warehouse,
                             VillagerAdapter adapter) {
        this.config = config;
        this.registry = registry;
        this.warehouse = warehouse;
        this.adapter = adapter;
        register(new Farmer());
        register(new Forester());
        register(new Miner());
        register(new Steward());
        register(new Guard());
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
    }

    /** Appelé à chaque avance du temps (ticks réels ou accélérés). */
    public void onTicks(long ticks) {
        accumulated += ticks;
        long interval = (long) (config.get().get(SimConfig.Key.BEHAVIOR_INTERVAL_S) * 20);
        if (accumulated < interval) {
            return;
        }
        long runs = Math.min(MAX_CATCHUP, accumulated / interval);
        accumulated %= interval;
        for (VillageSite site : new ArrayList<>(registry.all())) {
            World w = Bukkit.getWorld(site.worldName());
            if (w == null) {
                continue;
            }
            for (Villager v : adapter.present(site, w)) {
                for (long i = 0; i < runs; i++) {
                    work(site, w, v);
                }
            }
        }
    }

    /** Fait agir un villageois si son rôle a un comportement et s'il obéit. Renvoie vrai s'il a agi. */
    public boolean work(VillageSite site, World world, Villager villager) {
        VillagerState st = adapter.stateOf(villager);
        if (st == null) {
            return false;
        }
        RoleBehavior b = behaviors.get(st.role());
        if (b == null || !b.canWork(st, config.get())) {
            return false;
        }
        return run(b, site, world, villager, st);
    }

    /** Exécution forcée de l'action du rôle (sans test de moral ni de cadence) : pour les tests. */
    public boolean runOnce(VillageSite site, World world, Villager villager) {
        VillagerState st = adapter.stateOf(villager);
        RoleBehavior b = st == null ? null : behaviors.get(st.role());
        return b != null && run(b, site, world, villager, st);
    }

    private boolean run(RoleBehavior b, VillageSite site, World world, Villager villager, VillagerState st) {
        boolean did = b.runOnce(new BehaviorContext(world, villager, st, site, warehouse, config.get()));
        if (did) {
            actions++;
        }
        return did;
    }
}
