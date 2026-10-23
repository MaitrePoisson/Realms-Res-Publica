package rrp.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Villager;
import rrp.core.SimConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * Découverte des villages et comptage des lits réels, sans jamais dépasser `max_scan_blocks_per_tick`.
 * Le travail est découpé en tâches (une par village à rafraîchir, une par groupe de villageois
 * candidats) qui avancent par tranches budgétées. Seuls les chunks déjà chargés sont lus, et on
 * n'examine que leurs blocs à état (lits, tonneaux…), pas chaque bloc.
 */
public final class VillageScanner {

    /** Période (ticks) entre deux passes de découverte / rafraîchissement. */
    static final long RESCAN_TICKS = 200;
    /** Coût d'un chunk sans bloc à état (borne le nombre de chunks vus par tick). */
    private static final int EMPTY_CHUNK_COST = 1;

    private final Supplier<SimConfig> config;
    private final VillageRegistry registry;
    private final WarehouseService warehouse;
    private final Deque<Job> jobs = new ArrayDeque<>();
    private long sinceRescan;
    private int scannedThisStep;
    private int maxScannedPerStep;
    private long totalScanned;

    public VillageScanner(Supplier<SimConfig> config, VillageRegistry registry, WarehouseService warehouse) {
        this.config = config;
        this.registry = registry;
        this.warehouse = warehouse;
    }

    /** Travail d'un comptage de lits autour d'un point, éventuellement pour un village à créer. */
    private static final class Job {
        final String worldName;
        final double x;
        final double y;
        final double z;
        final int radius;
        final String siteId; // null : candidat à la création
        final int villagers;
        final Deque<int[]> chunks = new ArrayDeque<>();
        BlockState[] current;
        int index;
        int beds;

        Job(String worldName, double x, double y, double z, int radius, String siteId, int villagers) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.siteId = siteId;
            this.villagers = villagers;
            int minCx = (int) Math.floor((x - radius) / 16);
            int maxCx = (int) Math.floor((x + radius) / 16);
            int minCz = (int) Math.floor((z - radius) / 16);
            int maxCz = (int) Math.floor((z + radius) / 16);
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    chunks.add(new int[] {cx, cz});
                }
            }
        }
    }

    public int budgetPerTick() {
        return config.get().getInt(SimConfig.Key.MAX_SCAN_BLOCKS_PER_TICK);
    }

    /** Plus grand nombre de blocs examinés pendant un seul tick depuis le démarrage. */
    public int maxScannedPerTick() {
        return maxScannedPerStep;
    }

    public long totalScanned() {
        return totalScanned;
    }

    public int pending() {
        return jobs.size();
    }

    public void resetStats() {
        maxScannedPerStep = 0;
        totalScanned = 0;
    }

    /** Appelé à chaque avance du temps (`ticks` ticks serveur, réels ou accélérés). */
    public void onTicks(long ticks) {
        sinceRescan += ticks;
        if (sinceRescan >= RESCAN_TICKS) {
            sinceRescan = 0;
            enqueueAll();
        }
        long steps = ticks;
        while (steps-- > 0 && !jobs.isEmpty()) {
            step();
        }
    }

    /** Programme une passe complète : rafraîchit chaque village et cherche de nouveaux groupes. */
    public void enqueueAll() {
        for (VillageSite s : registry.all()) {
            enqueueRefresh(s);
        }
        enqueueDiscovery();
    }

    private void enqueueRefresh(VillageSite s) {
        for (Job j : jobs) {
            if (s.id().equals(j.siteId)) {
                return;
            }
        }
        jobs.add(new Job(s.worldName(), s.x(), s.y(), s.z(), s.radius(), s.id(), 0));
    }

    /** Regroupe les villageois situés hors de tout village et programme un comptage par groupe. */
    private void enqueueDiscovery() {
        int radius = config.get().getInt(SimConfig.Key.VILLAGE_RADIUS);
        for (World w : Bukkit.getWorlds()) {
            List<Villager> free = new ArrayList<>();
            for (Villager v : w.getEntitiesByClass(Villager.class)) {
                Location l = v.getLocation();
                if (registry.find(w.getName(), l.getX(), l.getZ()) == null) {
                    free.add(v);
                }
            }
            List<Villager> rest = new ArrayList<>(free);
            while (!rest.isEmpty()) {
                Villager seed = rest.get(0);
                List<Villager> group = new ArrayList<>();
                for (Villager v : rest) {
                    if (v.getLocation().distance(seed.getLocation()) <= radius) {
                        group.add(v);
                    }
                }
                rest.removeAll(group);
                if (group.size() >= 2) {
                    double sx = 0;
                    double sy = 0;
                    double sz = 0;
                    for (Villager v : group) {
                        sx += v.getLocation().getX();
                        sy += v.getLocation().getY();
                        sz += v.getLocation().getZ();
                    }
                    int n = group.size();
                    jobs.add(new Job(w.getName(), sx / n, sy / n, sz / n, radius, null, n));
                }
            }
        }
    }

    /** Une tranche : au plus `budgetPerTick()` blocs examinés. */
    private void step() {
        int budget = budgetPerTick();
        scannedThisStep = 0;
        while (scannedThisStep < budget && !jobs.isEmpty()) {
            Job j = jobs.peek();
            if (!advance(j, budget)) {
                continue; // la tâche continue au prochain appel ; la boucle sort faute de budget
            }
            jobs.remove();
            finish(j);
        }
        maxScannedPerStep = Math.max(maxScannedPerStep, scannedThisStep);
        totalScanned += scannedThisStep;
    }

    /** Fait avancer la tâche dans la limite du budget restant. Renvoie vrai quand elle est terminée. */
    private boolean advance(Job j, int budget) {
        World w = Bukkit.getWorld(j.worldName);
        while (scannedThisStep < budget) {
            if (j.current == null) {
                int[] c = j.chunks.poll();
                if (c == null) {
                    return true;
                }
                if (w == null || !w.isChunkLoaded(c[0], c[1])) {
                    scannedThisStep += EMPTY_CHUNK_COST;
                    continue;
                }
                Chunk chunk = w.getChunkAt(c[0], c[1], false);
                j.current = chunk.getTileEntities(false);
                j.index = 0;
                if (j.current.length == 0) {
                    j.current = null;
                    scannedThisStep += EMPTY_CHUNK_COST;
                    continue;
                }
            }
            while (j.index < j.current.length && scannedThisStep < budget) {
                BlockState st = j.current[j.index++];
                scannedThisStep++;
                if (Tag.BEDS.isTagged(st.getType()) && st.getBlockData() instanceof Bed bed && bed.getPart() == Bed.Part.HEAD
                        && Math.hypot(st.getX() + 0.5 - j.x, st.getZ() + 0.5 - j.z) <= j.radius) {
                    j.beds++;
                }
            }
            if (j.index >= j.current.length) {
                j.current = null;
            }
        }
        return j.current == null && j.chunks.isEmpty();
    }

    private void finish(Job j) {
        World w = Bukkit.getWorld(j.worldName);
        if (j.siteId != null) {
            VillageSite s = registry.get(j.siteId);
            if (s != null) {
                s.state().setBeds(j.beds);
                if (w != null) {
                    warehouse.refresh(s, w);
                }
                registry.markDirty();
            }
            return;
        }
        if (w == null || j.villagers < 2 || j.beds < 1) {
            return;
        }
        // Le groupe a pu être absorbé par un village créé entre-temps.
        if (registry.find(j.worldName, j.x, j.z) != null) {
            return;
        }
        VillageSite s = registry.create(new Location(w, j.x, j.y, j.z), j.radius);
        s.state().setBeds(j.beds);
        registry.mergeNearby();
    }

    /**
     * Passe complète immédiate (hors budget) pour un village : lits et stock à jour. Réservé aux
     * commandes de debug et aux tests ; le fonctionnement normal reste budgété.
     */
    public void refreshNow(VillageSite s) {
        enqueueRefresh(s);
        int guard = 100_000;
        while (!jobs.isEmpty() && guard-- > 0) {
            step();
        }
    }
}
