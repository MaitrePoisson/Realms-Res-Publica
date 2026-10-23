package rrp.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import rrp.core.Gauges;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.VillageState;

import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * Fait vivre les jauges des villageois avec les données réelles du monde : stock lu dans le tonneau,
 * lits comptés par le scanner, monstres hostiles dans le rayon. La nourriture mangée est retirée
 * du vrai tonneau. Les jauges sont écrites dans le PDC par groupes (périodiquement et à l'arrêt).
 */
public final class GaugeTicker {

    /** Un pas de jauges toutes les secondes de jeu. */
    static final long GAUGE_TICKS = 20;
    /** Réconciliation avec le monde (adoption, départs, menaces) toutes les 5 s. */
    static final long SYNC_TICKS = 100;
    /** Un pas de simulation dure au plus 5 s, pour que les ticks accélérés restent précis. */
    private static final double MAX_SUBSTEP_S = 5.0;
    private static final int MAX_SUBSTEPS = 240;

    private final Supplier<SimConfig> config;
    private final VillageRegistry registry;
    private final WarehouseService warehouse;
    private final VillagerAdapter adapter;
    private long sinceGauge;
    private long sinceSync;
    private long sinceSave;
    private long steps;

    public GaugeTicker(Supplier<SimConfig> config, VillageRegistry registry, WarehouseService warehouse,
                       VillagerAdapter adapter) {
        this.config = config;
        this.registry = registry;
        this.warehouse = warehouse;
        this.adapter = adapter;
    }

    /** Nombre de pas de jauges exécutés depuis le démarrage (tests). */
    public long steps() {
        return steps;
    }

    /** Appelé à chaque avance du temps (ticks réels ou accélérés). */
    public void onTicks(long ticks) {
        sinceGauge += ticks;
        sinceSync += ticks;
        sinceSave += ticks;
        if (sinceGauge < GAUGE_TICKS) {
            return;
        }
        long elapsed = sinceGauge;
        sinceGauge = 0;
        boolean sync = sinceSync >= SYNC_TICKS;
        if (sync) {
            sinceSync = 0;
        }
        boolean save = sinceSave >= config.get().get(SimConfig.Key.PERSISTENCE_FLUSH_S) * 20;
        if (save) {
            sinceSave = 0;
        }
        for (VillageSite site : new ArrayList<>(registry.all())) {
            World w = Bukkit.getWorld(site.worldName());
            if (w == null || !site.loaded(w)) { // village non chargé : rien n'y est observable, on gèle son état
                continue;
            }
            if (sync) {
                adapter.sync(site, w);
            }
            stepSite(site, w, elapsed, sync);
            if (save) {
                adapter.save(site);
            }
        }
    }

    /** Un pas de jauges de {@code ticks} ticks pour un village, avec les données réelles. */
    public void stepSite(VillageSite site, World world, long ticks, boolean refreshThreats) {
        VillageState state = site.state();
        if (state.villagers().isEmpty()) {
            return;
        }
        // Tonneau non chargé : stock périmé, on ne fait ni manger ni mourir de faim sur une valeur fantôme.
        boolean fresh = warehouse.refresh(site, world);
        state.setFoodManaged(site.hasWarehouse() && fresh);
        if (refreshThreats) {
            state.setThreats(countThreats(site, world));
        }
        int foodBefore = state.stock(Resource.FOOD);
        double dt = ticks / 20.0;
        int sub = (int) Math.min(MAX_SUBSTEPS, Math.max(1, Math.ceil(dt / MAX_SUBSTEP_S)));
        for (int i = 0; i < sub; i++) {
            Gauges.step(state, config.get(), dt / sub);
        }
        steps++;
        int eaten = foodBefore - state.stock(Resource.FOOD);
        if (eaten > 0) {
            warehouse.withdraw(site, world, Resource.FOOD, eaten);
        }
    }

    /** Monstres hostiles vivants dans le rayon du village. */
    public int countThreats(VillageSite site, World world) {
        int r = site.radius();
        int n = 0;
        for (Entity e : world.getNearbyEntities(site.center(world), r, 64, r, x -> x instanceof Enemy && x.isValid())) {
            if (site.contains(world.getName(), e.getLocation().getX(), e.getLocation().getZ())) {
                n++;
            }
        }
        return n;
    }

    /** Écrit immédiatement les jauges de tous les villages dans les PDC. */
    public int saveAll() {
        int n = 0;
        for (VillageSite site : registry.all()) {
            n += adapter.save(site);
        }
        return n;
    }
}
