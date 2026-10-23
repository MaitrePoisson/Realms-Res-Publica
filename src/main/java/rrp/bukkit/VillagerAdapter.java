package rrp.bukkit;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.VillagerState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Pont entre les villageois du monde et le {@link VillagerState} du cœur : adoption (rôle + jauges
 * écrits dans le PDC sous {@code rrp:*}), relecture après redémarrage, nom affiché. Les naissances
 * vanilla sont annulées : seul le plugin fait naître des villageois.
 */
public final class VillagerAdapter implements Listener {

    @SuppressWarnings("deprecation")
    private static NamespacedKey key(String name) {
        return new NamespacedKey("rrp", name);
    }

    private final NamespacedKey roleKey = key("role");
    private final NamespacedKey moraleKey = key("morale");
    private final NamespacedKey satietyKey = key("satiety");
    private final NamespacedKey loyaltyKey = key("loyalty");
    private final NamespacedKey factionKey = key("faction");
    private final NamespacedKey namedKey = key("named");

    private final Supplier<SimConfig> config;
    private final VillageRegistry registry;

    public VillagerAdapter(Supplier<SimConfig> config, VillageRegistry registry) {
        this.config = config;
        this.registry = registry;
    }

    /** État relu dans le PDC, ou {@code null} si le villageois n'a pas (encore) de rôle. */
    public VillagerState read(Villager v) {
        PersistentDataContainer pdc = v.getPersistentDataContainer();
        String roleName = pdc.get(roleKey, PersistentDataType.STRING);
        if (roleName == null) {
            return null;
        }
        Role role;
        try {
            role = Role.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new VillagerState(v.getUniqueId(), role, pdc.getOrDefault(factionKey, PersistentDataType.STRING, ""),
                pdc.getOrDefault(moraleKey, PersistentDataType.DOUBLE, 70.0),
                pdc.getOrDefault(satietyKey, PersistentDataType.DOUBLE, 80.0),
                pdc.getOrDefault(loyaltyKey, PersistentDataType.DOUBLE, 70.0));
    }

    public void write(Villager v, VillagerState s) {
        PersistentDataContainer pdc = v.getPersistentDataContainer();
        pdc.set(roleKey, PersistentDataType.STRING, s.role().name());
        pdc.set(moraleKey, PersistentDataType.DOUBLE, s.morale());
        pdc.set(satietyKey, PersistentDataType.DOUBLE, s.satiety());
        pdc.set(loyaltyKey, PersistentDataType.DOUBLE, s.loyalty());
        pdc.set(factionKey, PersistentDataType.STRING, s.factionId());
        applyName(v, s.role());
    }

    /** Nom affiché = libellé du rôle, sans jamais écraser un nom donné par un joueur. */
    private void applyName(Villager v, Role role) {
        PersistentDataContainer pdc = v.getPersistentDataContainer();
        boolean ours = pdc.has(namedKey, PersistentDataType.BYTE);
        boolean show = config.get().getInt(SimConfig.Key.SHOW_ROLE_NAMES) == 1;
        if (show && (ours || v.customName() == null)) {
            v.customName(Component.text(role.label()));
            v.setCustomNameVisible(true);
            pdc.set(namedKey, PersistentDataType.BYTE, (byte) 1);
        } else if (!show && ours) {
            v.customName(null);
            v.setCustomNameVisible(false);
            pdc.remove(namedKey);
        }
    }

    /** Villageois adultes et vivants dont le village le plus proche est {@code site}. */
    public List<Villager> present(VillageSite site, World world) {
        List<Villager> out = new ArrayList<>();
        int r = site.reach();
        for (Entity e : world.getNearbyEntities(site.center(world), r, 128, r, x -> x instanceof Villager)) {
            Villager v = (Villager) e;
            Location l = v.getLocation();
            if (v.isValid() && !v.isDead() && v.isAdult() && registry.find(world.getName(), l.getX(), l.getZ()) == site) {
                out.add(v);
            }
        }
        return out;
    }

    /**
     * Réconcilie l'état du village avec les villageois réellement présents : retire les absents,
     * garde l'état en mémoire des présents, relit le PDC des revenants et adopte les nouveaux
     * (rôle attribué, jauges initiales, PDC écrit). Renvoie le nombre de villageois adoptés.
     */
    public int sync(VillageSite site, World world) {
        List<Villager> present = present(site, world);
        present.sort((a, b) -> a.getUniqueId().compareTo(b.getUniqueId()));
        Set<UUID> presentIds = new HashSet<>();
        for (Villager v : present) {
            presentIds.add(v.getUniqueId());
        }
        Set<UUID> known = new HashSet<>();
        List<VillagerState> next = new ArrayList<>();
        for (VillagerState s : site.state().villagers()) {
            known.add(s.id());
            if (presentIds.contains(s.id())) {
                next.add(s);
            }
        }
        List<Villager> returning = new ArrayList<>();
        List<Villager> newcomers = new ArrayList<>();
        List<VillagerState> restored = new ArrayList<>();
        List<VillagerState> fresh = new ArrayList<>();
        List<Role> hints = new ArrayList<>();
        for (Villager v : present) {
            if (known.contains(v.getUniqueId())) {
                continue;
            }
            VillagerState s = read(v);
            if (s != null) {
                restored.add(s);
                returning.add(v);
            } else {
                fresh.add(new VillagerState(v.getUniqueId(), Role.FARMER, site.id()));
                hints.add(RoleAssigner.hint(v.getProfession()));
                newcomers.add(v);
            }
        }
        next.addAll(restored);
        RoleAssigner.assign(next, fresh, hints);
        next.addAll(fresh);
        for (int i = 0; i < newcomers.size(); i++) {
            write(newcomers.get(i), fresh.get(i));
        }
        for (int i = 0; i < returning.size(); i++) {
            applyName(returning.get(i), restored.get(i).role());
        }
        if (next.size() != site.state().villagers().size() || !fresh.isEmpty() || !restored.isEmpty()) {
            site.state().villagers().clear();
            site.state().villagers().addAll(next);
            registry.markDirty();
        }
        return fresh.size();
    }

    /** Écrit dans le PDC les jauges en mémoire des villageois chargés du village. */
    public int save(VillageSite site) {
        int n = 0;
        for (VillagerState s : site.state().villagers()) {
            Entity e = Bukkit.getEntity(s.id());
            if (e instanceof Villager v) {
                write(v, s);
                n++;
            }
        }
        return n;
    }

    private VillageSite siteOf(Villager v) {
        Location l = v.getLocation();
        return registry.find(l.getWorld().getName(), l.getX(), l.getZ());
    }

    /** État vivant du villageois s'il est connu d'un village, sinon relu dans le PDC. */
    public VillagerState stateOf(Villager v) {
        VillageSite site = siteOf(v);
        if (site != null) {
            for (VillagerState k : site.state().villagers()) {
                if (k.id().equals(v.getUniqueId())) {
                    return k;
                }
            }
        }
        return read(v);
    }

    /** Change le rôle d'un villageois (état en mémoire s'il est connu, PDC toujours). */
    public void setRole(Villager v, Role role) {
        VillagerState s = stateOf(v);
        if (s == null) {
            VillageSite site = siteOf(v);
            s = new VillagerState(v.getUniqueId(), role, site == null ? "" : site.id());
        }
        s.setRole(role);
        write(v, s);
    }

    /** Les naissances vanilla sont remplacées par celles du plugin (conditions réelles). */
    @EventHandler
    public void onBreed(EntityBreedEvent e) {
        if (e.getEntity() instanceof Villager) {
            e.setCancelled(true);
        }
    }

    /** Un villageois qui se décharge garde ses jauges : on les écrit avant qu'elles quittent la mémoire. */
    @EventHandler
    public void onUnload(EntitiesUnloadEvent e) {
        for (Entity en : e.getEntities()) {
            if (en instanceof Villager v) {
                VillagerState s = stateOf(v);
                if (s != null) {
                    write(v, s);
                }
            }
        }
    }
}
