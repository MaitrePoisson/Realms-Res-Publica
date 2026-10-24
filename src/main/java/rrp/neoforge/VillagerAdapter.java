package rrp.neoforge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.VillagerState;
import rrp.neoforge.RrpAttachments.VillagerData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Pont entre les villageois du monde et le {@link VillagerState} du cœur : adoption (rôle + jauges
 * écrits dans les données attachées {@code respublica:villager}), relecture après redémarrage, nom affiché.
 * Les naissances vanilla sont annulées par {@link RrpMod} : seul le mod fait naître des villageois.
 */
public final class VillagerAdapter {

    private final Supplier<SimConfig> config;
    private final Supplier<MinecraftServer> server;
    private final VillageRegistry registry;

    private long writes;

    public VillagerAdapter(Supplier<SimConfig> config, Supplier<MinecraftServer> server, VillageRegistry registry) {
        this.config = config;
        this.server = server;
        this.registry = registry;
    }

    /** État relu dans les données attachées, ou {@code null} si le villageois n'a pas (encore) de rôle. */
    public VillagerState read(Villager v) {
        if (!v.hasData(RrpAttachments.VILLAGER)) {
            return null;
        }
        VillagerData d = v.getData(RrpAttachments.VILLAGER);
        Role role;
        try {
            role = Role.valueOf(d.role());
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new VillagerState(v.getUUID(), role, d.faction(), d.morale(), d.satiety(), d.loyalty());
    }

    /** Nombre d'écritures complètes dans des données attachées depuis le démarrage (tests, diagnostic). */
    public long writes() {
        return writes;
    }

    public void write(Villager v, VillagerState s) {
        writes++;
        boolean named = v.hasData(RrpAttachments.VILLAGER) && v.getData(RrpAttachments.VILLAGER).named();
        named = applyName(v, s.role(), named);
        v.setData(RrpAttachments.VILLAGER,
                new VillagerData(s.role().name(), s.morale(), s.satiety(), s.loyalty(), s.factionId(), named));
    }

    /** Nom affiché = libellé du rôle, sans jamais écraser un nom donné par un joueur. Renvoie le nouvel indicateur « nom à nous ». */
    private boolean applyName(Villager v, Role role, boolean ours) {
        boolean show = config.get().getInt(SimConfig.Key.SHOW_ROLE_NAMES) == 1;
        if (show && (ours || !v.hasCustomName())) {
            v.setCustomName(Component.literal(role.label()));
            v.setCustomNameVisible(true);
            return true;
        } else if (!show && ours) {
            v.setCustomName(null);
            v.setCustomNameVisible(false);
            return false;
        }
        return ours;
    }

    /** Villageois adultes et vivants dont le village le plus proche est {@code site}. */
    public List<Villager> present(VillageSite site, ServerLevel level) {
        int r = site.reach();
        AABB box = new AABB(site.x() - r, level.getMinBuildHeight(), site.z() - r,
                site.x() + r, level.getMaxBuildHeight(), site.z() + r);
        String world = Worlds.name(level);
        List<Villager> out = new ArrayList<>();
        for (Villager v : level.getEntitiesOfClass(Villager.class, box)) {
            if (v.isAlive() && !v.isBaby() && registry.find(world, v.getX(), v.getZ()) == site) {
                out.add(v);
            }
        }
        return out;
    }

    /**
     * Réconcilie l'état du village avec les villageois réellement présents : retire les absents,
     * garde l'état en mémoire des présents, relit les données des revenants et adopte les nouveaux
     * (rôle attribué, jauges initiales, données écrites). Renvoie le nombre de villageois adoptés.
     */
    public int sync(VillageSite site, ServerLevel level) {
        List<Villager> present = present(site, level);
        present.sort((a, b) -> a.getUUID().compareTo(b.getUUID()));
        Set<UUID> presentIds = new HashSet<>();
        for (Villager v : present) {
            presentIds.add(v.getUUID());
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
            if (known.contains(v.getUUID())) {
                continue;
            }
            VillagerState s = read(v);
            if (s != null) {
                restored.add(s);
                returning.add(v);
            } else {
                fresh.add(new VillagerState(v.getUUID(), Role.FARMER, site.id()));
                hints.add(RoleAssigner.hint(v.getVillagerData().getProfession().name()));
                newcomers.add(v);
            }
        }
        next.addAll(restored);
        RoleAssigner.assign(next, fresh, hints, site.doctrine());
        next.addAll(fresh);
        for (int i = 0; i < newcomers.size(); i++) {
            write(newcomers.get(i), fresh.get(i));
        }
        for (int i = 0; i < returning.size(); i++) {
            write(returning.get(i), restored.get(i));
        }
        if (next.size() != site.state().villagers().size() || !fresh.isEmpty() || !restored.isEmpty()) {
            site.state().villagers().clear();
            site.state().villagers().addAll(next);
            registry.markDirty();
        }
        return fresh.size();
    }

    /** Écrit dans les données attachées les jauges en mémoire des villageois chargés du village. */
    public int save(VillageSite site) {
        ServerLevel level = Worlds.level(server.get(), site.worldName());
        if (level == null) {
            return 0;
        }
        int n = 0;
        for (VillagerState s : site.state().villagers()) {
            if (level.getEntity(s.id()) instanceof Villager v) {
                if (!sameAsStored(v, s)) {
                    write(v, s);
                }
                n++;
            }
        }
        return n;
    }

    /** Écrit les jauges en mémoire de tous les villages (flush groupé, arrêt du serveur). */
    public void saveAll() {
        for (VillageSite site : registry.all()) {
            save(site);
        }
    }

    /** Vrai si les données contiennent déjà exactement ces valeurs (évite une écriture inutile au flush groupé). */
    private boolean sameAsStored(Villager v, VillagerState s) {
        VillagerState old = read(v);
        return old != null && old.role() == s.role() && old.factionId().equals(s.factionId())
                && old.morale() == s.morale() && old.satiety() == s.satiety() && old.loyalty() == s.loyalty();
    }

    private VillageSite siteOf(Villager v) {
        return registry.find(Worlds.name((ServerLevel) v.level()), v.getX(), v.getZ());
    }

    /** État vivant du villageois s'il est connu d'un village, sinon relu dans les données attachées. */
    public VillagerState stateOf(Villager v) {
        VillageSite site = siteOf(v);
        if (site != null) {
            for (VillagerState k : site.state().villagers()) {
                if (k.id().equals(v.getUUID())) {
                    return k;
                }
            }
        }
        return read(v);
    }

    /** Change le rôle d'un villageois (état en mémoire s'il est connu, données toujours). */
    public void setRole(Villager v, Role role) {
        VillagerState s = stateOf(v);
        if (s == null) {
            VillageSite site = siteOf(v);
            s = new VillagerState(v.getUUID(), role, site == null ? "" : site.id());
        }
        s.setRole(role);
        write(v, s);
    }

    /** Un villageois qui quitte le monde garde ses jauges : on les écrit avant qu'elles quittent la mémoire. */
    public void onLeave(Entity entity) {
        if (entity instanceof Villager v && !v.level().isClientSide()) {
            VillagerState s = stateOf(v);
            if (s != null) {
                write(v, s);
            }
        }
    }
}
