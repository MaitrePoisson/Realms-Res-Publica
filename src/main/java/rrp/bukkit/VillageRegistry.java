package rrp.bukkit;

import org.bukkit.Location;
import rrp.core.Faction;
import rrp.core.VillageState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ensemble des villages connus : création, recherche, fusion. Persisté par {@link Persistence}. */
public final class VillageRegistry {

    private final Map<String, VillageSite> sites = new LinkedHashMap<>();
    private boolean dirty;
    private int counter;

    public Collection<VillageSite> all() {
        return Collections.unmodifiableCollection(sites.values());
    }

    public VillageSite get(String id) {
        return sites.get(id);
    }

    public int size() {
        return sites.size();
    }

    public void clear() {
        sites.clear();
        counter = 0;
        dirty = true;
    }

    /** Supprime un village (fin de test, dissolution). Renvoie vrai s'il existait. */
    public boolean remove(String id) {
        boolean existed = sites.remove(id) != null;
        dirty |= existed;
        return existed;
    }

    public void markDirty() {
        dirty = true;
    }

    /** Renvoie vrai (une seule fois) si le registre a changé depuis la dernière écriture. */
    public boolean consumeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Crée un village loyaliste centré sur `center`. */
    public VillageSite create(Location center, int radius) {
        return create(center, radius, Faction.LOYALIST);
    }

    public VillageSite create(Location center, int radius, Faction faction) {
        String id = (faction == Faction.LOYALIST ? "village-" : "camp-") + (++counter);
        VillageSite s = new VillageSite(new VillageState(id, faction), center.getWorld().getName(),
                center.getX(), center.getY(), center.getZ(), radius);
        sites.put(id, s);
        dirty = true;
        return s;
    }

    /** Enregistre un état déjà créé par le cœur (campement dissident issu d'un exil) à l'emplacement donné. */
    public VillageSite adopt(VillageState state, Location center, int radius) {
        VillageSite s = new VillageSite(state, center.getWorld().getName(), center.getX(), center.getY(), center.getZ(), radius);
        restore(s);
        dirty = true;
        return s;
    }

    /** Ajoute un village déjà construit (rechargement) ; met à jour le compteur d'identifiants. */
    void restore(VillageSite s) {
        sites.put(s.id(), s);
        String id = s.id();
        int dash = id.lastIndexOf('-');
        if (dash >= 0) {
            try {
                counter = Math.max(counter, Integer.parseInt(id.substring(dash + 1)));
            } catch (NumberFormatException ignored) {
                // identifiant non numérique : le compteur reste inchangé
            }
        }
    }

    /** Village (le plus proche) dont le rayon contient le point, ou {@code null}. */
    public VillageSite find(String world, double x, double z) {
        VillageSite best = null;
        double bestD = Double.MAX_VALUE;
        for (VillageSite s : sites.values()) {
            double d = s.distance(world, x, z);
            if (d <= s.reach() && d < bestD) {
                best = s;
                bestD = d;
            }
        }
        return best;
    }

    public VillageSite find(Location l) {
        return find(l.getWorld().getName(), l.getX(), l.getZ());
    }

    /**
     * Fusionne les villages loyalistes dont les centres sont à moins d'un rayon l'un de l'autre :
     * le plus ancien absorbe le plus récent. Renvoie le nombre de fusions.
     */
    public int mergeNearby() {
        int merges = 0;
        boolean again = true;
        while (again) {
            again = false;
            for (VillageSite a : new ArrayList<>(sites.values())) {
                for (VillageSite b : new ArrayList<>(sites.values())) {
                    if (a == b || a.state().faction() != Faction.LOYALIST || b.state().faction() != Faction.LOYALIST) {
                        continue;
                    }
                    if (a.distance(b.worldName(), b.x(), b.z()) <= Math.max(a.radius(), b.radius())
                            && sites.containsKey(a.id()) && sites.containsKey(b.id()) && order(a) < order(b)) {
                        merge(a, b);
                        merges++;
                        again = true;
                    }
                }
            }
        }
        return merges;
    }

    private int order(VillageSite s) {
        int i = 0;
        for (String id : sites.keySet()) {
            if (id.equals(s.id())) {
                return i;
            }
            i++;
        }
        return Integer.MAX_VALUE;
    }

    /** `keep` absorbe `gone` : entrepôt (si `keep` n'en a pas), relations, villageois ; `gone` disparaît. */
    public void merge(VillageSite keep, VillageSite gone) {
        if (!keep.hasWarehouse() && gone.hasWarehouse()) {
            int[] w = gone.warehouse();
            keep.setWarehouse(w[0], w[1], w[2]);
        }
        keep.state().setRelations(Math.max(keep.state().relations(), gone.state().relations()));
        keep.state().villagers().addAll(gone.state().villagers());
        sites.remove(gone.id());
        dirty = true;
    }
}
