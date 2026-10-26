package rrp.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import rrp.core.CrisisEngine;
import rrp.core.CrisisLevel;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.SimEvent;
import rrp.core.VillageState;
import rrp.core.VillagerState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fait avancer les crises des villages réels : le cœur ({@link CrisisEngine}) décide d'après l'état lu dans
 * le monde (stock du tonneau, lits, menaces), ce service applique les événements au monde. Un exil déplace
 * vraiment les mécontents (mémoires HOME/JOB_SITE effacées), pose un campement ({@link CampBuilder}) et
 * transfère la nourriture du vrai tonneau du village vers celui du camp.
 */
public final class CrisisService {

    /** Un pas de crise toutes les 5 s de jeu. */
    static final long CRISIS_TICKS = 100;
    /** Un pas du cœur dure au plus 5 s, pour que le temps accéléré franchisse les paliers dans l'ordre. */
    private static final double MAX_SUBSTEP_S = 5.0;
    private static final int MAX_SUBSTEPS = 240;

    private final Supplier<SimConfig> config;
    private final Supplier<MinecraftServer> server;
    private final VillageRegistry registry;
    private final WarehouseService warehouse;
    private final VillagerAdapter adapter;
    private final Supplier<Lang> lang;
    private final Logger log;
    private final CampBuilder camps = new CampBuilder();
    private final List<Consumer<SimEvent>> listeners = new ArrayList<>();
    private long accumulated;
    private long exiles;

    public CrisisService(Supplier<SimConfig> config, Supplier<MinecraftServer> server, VillageRegistry registry,
                         WarehouseService warehouse, VillagerAdapter adapter, Supplier<Lang> lang, Logger log) {
        this.config = config;
        this.server = server;
        this.registry = registry;
        this.warehouse = warehouse;
        this.adapter = adapter;
        this.lang = lang;
        this.log = log;
    }

    /** Abonne un service (guerre civile) aux événements, appelé après leur application au monde. */
    public void addListener(Consumer<SimEvent> listener) {
        listeners.add(listener);
    }

    public CampBuilder camps() {
        return camps;
    }

    /** Nombre d'exils appliqués au monde depuis le démarrage (tests, diagnostic). */
    public long exiles() {
        return exiles;
    }

    /** Appelé à chaque avance du temps (ticks réels ou accélérés). */
    public void onTicks(long ticks) {
        accumulated += ticks;
        if (accumulated < CRISIS_TICKS) {
            return;
        }
        long elapsed = accumulated;
        accumulated = 0;
        advance(elapsed);
    }

    /** Fait avancer toutes les crises de {@code ticks} ticks ; renvoie les événements émis, dans l'ordre. */
    public List<SimEvent> advance(long ticks) {
        List<SimEvent> out = new ArrayList<>();
        if (ticks <= 0) {
            return out;
        }
        double dt = ticks / 20.0;
        int sub = (int) Math.min(MAX_SUBSTEPS, Math.max(1, Math.ceil(dt / MAX_SUBSTEP_S)));
        for (int i = 0; i < sub; i++) {
            out.addAll(stepOnce(dt / sub));
        }
        return out;
    }

    private List<SimEvent> stepOnce(double dt) {
        List<VillageSite> sites = new ArrayList<>();
        List<VillageState> states = new ArrayList<>();
        for (VillageSite site : registry.all()) {
            ServerLevel w = Worlds.level(server.get(), site.worldName());
            if (w != null) {
                boolean fresh = warehouse.refresh(site, w);
                site.state().setFoodManaged(site.hasWarehouse() && fresh);
                site.state().setActive(WarehouseService.loaded(site, w));
                sites.add(site);
                states.add(site.state());
            }
        }
        List<SimEvent> events = CrisisEngine.step(states, config.get(), dt);
        for (VillageSite site : sites) {
            if (!states.contains(site.state())) { // campement vidé par la fin d'une guerre
                registry.remove(site.id());
            }
        }
        for (SimEvent e : events) {
            apply(e, states);
        }
        return events;
    }

    private void apply(SimEvent e, List<VillageState> states) {
        if (e instanceof SimEvent.CrisisChanged c) {
            announceChange(c);
        } else if (e instanceof SimEvent.Exile x) {
            applyExile(x, states);
        }
        for (Consumer<SimEvent> l : listeners) {
            l.accept(e);
        }
    }

    private void announceChange(SimEvent.CrisisChanged c) {
        VillageSite site = registry.get(c.villageId());
        if (site == null) {
            return;
        }
        if (c.to() == CrisisLevel.CONTESTATION && c.from() == CrisisLevel.STABLE) {
            tell(site, lang.get().msg("crisis.contestation", site.id(), cause(site.state())));
        } else if (c.to() == CrisisLevel.STABLE) {
            tell(site, lang.get().msg("crisis.calm", site.id()));
        }
    }

    private String cause(VillageState v) {
        CrisisEngine.Indicators i = CrisisEngine.indicators(v, config.get());
        String key = i.famine() ? "famine" : i.overpopulation() ? "overpopulation" : i.insecurity() ? "insecurity" : "unknown";
        return lang.get().get("crisis.cause." + key);
    }

    /** Message aux joueurs proches du village (rayon + marge) et dans la console. */
    public void tell(VillageSite site, String message) {
        log.info(message.replaceAll("§.", ""));
        ServerLevel w = Worlds.level(server.get(), site.worldName());
        if (w == null) {
            return;
        }
        String name = Worlds.name(w);
        for (ServerPlayer p : w.players()) {
            if (site.distance(name, p.getX(), p.getZ()) <= site.radius() + 48) {
                p.sendSystemMessage(Component.literal(message));
            }
        }
    }

    /** Construit (ou agrandit) le campement, y envoie les exilés et transfère la nourriture. */
    private void applyExile(SimEvent.Exile x, List<VillageState> states) {
        VillageSite village = registry.get(x.villageId());
        VillageState campState = null;
        for (VillageState s : states) {
            if (s.id().equals(x.campId())) {
                campState = s;
            }
        }
        ServerLevel world = village == null ? null : Worlds.level(server.get(), village.worldName());
        if (village == null || campState == null || world == null) {
            return;
        }
        VillageSite camp = registry.get(x.campId());
        boolean fresh = camp == null;
        BlockPos fire = fresh ? camps.findSite(world, village, (int) config.get().get(SimConfig.Key.CAMP_DISTANCE))
                : BlockPos.containing(camp.x(), camp.y(), camp.z());
        int tents = Math.min(x.villagerIds().size(), CampBuilder.maxTents());
        CampBuilder.Result built = camps.build(world, fire, tents, fresh);
        int wool = 0;
        for (int dx = -CampBuilder.CAMP_RADIUS; dx <= CampBuilder.CAMP_RADIUS; dx++)
            for (int dz = -CampBuilder.CAMP_RADIUS; dz <= CampBuilder.CAMP_RADIUS; dz++)
                for (int dy = -2; dy <= 4; dy++)
                    if (world.getBlockState(built.fire().offset(dx, dy, dz)).is(net.minecraft.tags.BlockTags.WOOL)) wool++;
        log.info("[Res Publica] Campement vérifié : {} bloc(s) de laine, tonneau {}, {} lit(s).", wool,
                world.getBlockState(built.barrel()).is(net.minecraft.world.level.block.Blocks.BARREL) ? "présent" : "ABSENT", built.beds());
        if (fresh) {
            camp = registry.adopt(campState, village.worldName(), built.fire().getX() + 0.5, built.fire().getY(),
                    built.fire().getZ() + 0.5, CampBuilder.CAMP_RADIUS);
            warehouse.set(camp, world, built.barrel());
        }
        int priorBeds = Math.max(0, campState.beds() - x.villagerIds().size());
        campState.setBeds(priorBeds + built.beds());
        if (x.foodMoved() > 0) {
            int took = warehouse.withdraw(village, world, Resource.FOOD, x.foodMoved());
            if (took > 0) {
                warehouse.deposit(camp, world, new ItemStack(Items.BREAD, took));
            }
        }
        moveExiles(world, x.villagerIds(), campState, built, new Vec3(camp.x(), camp.y(), camp.z()));
        exiles += x.villagerIds().size();
        registry.markDirty();
        tell(village, lang.get().msg("crisis.exile", village.id(), x.villagerIds().size()));
    }

    private void moveExiles(ServerLevel world, List<UUID> ids, VillageState campState, CampBuilder.Result built, Vec3 fire) {
        int i = 0;
        for (UUID id : ids) {
            if (!(world.getEntity(id) instanceof Villager v)) {
                log.warn("Exilé {} non chargé : il rejoindra le campement à son retour.", id);
                continue;
            }
            clearMemories(v);
            Vec3 spot = built.spots().isEmpty() ? fire : built.spots().get(i++ % built.spots().size());
            v.teleportTo(spot.x, spot.y, spot.z);
            for (VillagerState s : campState.villagers()) {
                if (s.id().equals(id)) {
                    adapter.write(v, s);
                }
            }
        }
    }

    /** Efface lit, poste de travail et point de rendez-vous : l'IA vanilla ne les ramène pas au village. */
    static void clearMemories(Villager v) {
        v.getBrain().eraseMemory(MemoryModuleType.HOME);
        v.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        v.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        v.getBrain().eraseMemory(MemoryModuleType.MEETING_POINT);
    }
}
