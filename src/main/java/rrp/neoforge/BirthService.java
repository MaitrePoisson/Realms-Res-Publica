package rrp.neoforge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
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
    private final Supplier<MinecraftServer> server;
    private final VillageRegistry registry;
    private final WarehouseService warehouse;
    private long accumulated;
    private long births;

    public BirthService(Supplier<SimConfig> config, Supplier<MinecraftServer> server, VillageRegistry registry,
                        WarehouseService warehouse) {
        this.config = config;
        this.server = server;
        this.registry = registry;
        this.warehouse = warehouse;
    }

    /** Nombre de bébés nés par le mod depuis le démarrage (tests, diagnostic). */
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
            ServerLevel w = Worlds.level(server.get(), site.worldName());
            if (w != null && WarehouseService.loaded(site, w)) {
                stepSite(site, w, elapsed);
            }
        }
    }

    /** Fait avancer la démographie d'un village de {@code ticks} ticks ; renvoie le nombre de naissances. */
    public int stepSite(VillageSite site, ServerLevel world, long ticks) {
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
                        RrpMod.LOG.info("Naissance au village {} ({} nourriture retirée du tonneau)", site.id(), b.foodCost());
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
    public int countBabies(VillageSite site, ServerLevel world) {
        int r = site.radius();
        AABB box = new AABB(site.x() - r, world.getMinBuildHeight(), site.z() - r, site.x() + r, world.getMaxBuildHeight(), site.z() + r);
        String name = Worlds.name(world);
        int n = 0;
        for (Villager v : world.getEntitiesOfClass(Villager.class, box)) {
            if (v.isAlive() && v.isBaby() && registry.find(name, v.getX(), v.getZ()) == site) {
                n++;
            }
        }
        return n;
    }

    /** Fait apparaître un bébé villageois au centre du village. */
    public Villager spawnBaby(VillageSite site, ServerLevel world) {
        Villager v = EntityType.VILLAGER.create(world);
        if (v == null) {
            return null;
        }
        v.moveTo(site.x(), site.y(), site.z(), world.random.nextFloat() * 360F, 0F);
        v.setBaby(true);
        return world.addFreshEntity(v) ? v : null;
    }
}
