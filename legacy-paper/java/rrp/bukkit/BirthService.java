package rrp.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import rrp.core.DemographyEngine;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.SimEvent;
import rrp.core.VillageState;

import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * Naissances dans le monde : le cœur ({@link DemographyEngine}) décide d'après le stock réel du tonneau
 * et les lits réels ; ici on retire la nourriture du vrai tonneau et on fait apparaître un bébé
 * villageois. Un bébé occupe déjà un lit (il compte contre les lits libres) ; à maturité, la synchronisation
 * du {@link VillagerAdapter} l'adopte (rôle + jauges).
 */
public final class BirthService {

    /** Un pas de naissance toutes les 5 s de jeu. */
    static final long BIRTH_TICKS = 100;
    /** Bornes de rattrapage quand le temps est accéléré. */
    private static final int MAX_SUBSTEPS = 50;

    private final Supplier<SimConfig> config;
    private final VillageRegistry registry;
    private final WarehouseService warehouse;
    private final VillagerAdapter adapter;
    private long accumulated;
    private long births;

    public BirthService(Supplier<SimConfig> config, VillageRegistry registry, WarehouseService warehouse,
                        VillagerAdapter adapter) {
        this.config = config;
        this.registry = registry;
        this.warehouse = warehouse;
        this.adapter = adapter;
    }

    /** Nombre de bébés nés par le plugin depuis le démarrage (tests, diagnostic). */
    public long births() {
        return births;
    }

    /** Appelé à chaque avance du temps (ticks réels ou accélérés). */
    public void onTicks(long ticks) {
        accumulated += ticks;
        if (accumulated < BIRTH_TICKS) {
            return;
        }
        long elapsed = accumulated;
        accumulated = 0;
        for (VillageSite site : new ArrayList<>(registry.all())) {
            World w = Bukkit.getWorld(site.worldName());
            if (w != null) {
                stepSite(site, w, elapsed);
            }
        }
    }

    /** Fait avancer la démographie d'un village de {@code ticks} ticks ; renvoie le nombre de naissances. */
    public int stepSite(VillageSite site, World world, long ticks) {
        VillageState state = site.state();
        if (state.villagers().isEmpty() || ticks <= 0) {
            return 0;
        }
        SimConfig cfg = config.get();
        double interval = cfg.get(SimConfig.Key.BIRTH_INTERVAL_S);
        if (site.hasWarehouse() && !warehouse.refresh(site, world)) {
            return 0; // tonneau non chargé : pas de naissance sur un stock périmé
        }
        int babies = countBabies(site, world);
        double remaining = ticks / 20.0;
        int made = 0;
        for (int i = 0; i < MAX_SUBSTEPS && remaining > 0; i++) {
            double dt = Math.min(remaining, interval);
            remaining -= dt;
            if (babies >= DemographyEngine.freeBeds(state)) {
                // Tous les lits libres sont réservés par des bébés : on attend, minuteur plafonné.
                state.setBirthTimer(Math.min(interval, state.birthTimer() + dt));
                continue;
            }
            for (SimEvent e : DemographyEngine.step(state, cfg, dt)) {
                if (e instanceof SimEvent.Birth b) {
                    warehouse.withdraw(site, world, Resource.FOOD, b.foodCost());
                    if (spawnBaby(site, world) != null) {
                        babies++;
                        made++;
                        births++;
                    }
                }
            }
        }
        if (made > 0) {
            registry.markDirty();
        }
        return made;
    }

    /** Bébés villageois (non adultes) vivants dans le rayon du village. */
    public int countBabies(VillageSite site, World world) {
        int r = site.radius();
        int n = 0;
        for (Entity e : world.getNearbyEntities(site.center(world), r, 128, r, x -> x instanceof Villager)) {
            Villager v = (Villager) e;
            Location l = v.getLocation();
            if (v.isValid() && !v.isAdult() && registry.find(world.getName(), l.getX(), l.getZ()) == site) {
                n++;
            }
        }
        return n;
    }

    /** Fait apparaître un bébé villageois au centre du village. */
    public Villager spawnBaby(VillageSite site, World world) {
        Location at = site.center(world);
        return world.spawn(at, Villager.class, v -> {
            v.setBaby();
            v.setAgeLock(false);
        });
    }
}
