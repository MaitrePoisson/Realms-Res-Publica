package rrp.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import rrp.core.abstractsim.AbstractMarch;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;
import rrp.core.diplomacy.Regiment;
import rrp.core.diplomacy.Treaty.State;

/**
 * Mobilisation : quand un village est en guerre, ses gardes forment un régiment (part plafonnée de la population),
 * équipé depuis l'entrepôt avec le fer et le bois forgés localement ; un équipement manquant affaiblit le
 * régiment. Le régiment marche vers l'hôtel de ville ennemi et se démobilise à la paix.
 */
public final class MobilizationService {

    /** Régiment levé par un village. */
    public static final class Force {
        final String village;
        final String enemy;
        final List<UUID> members = new ArrayList<>();
        Regiment.Loadout loadout;
        double strength;
        /** Set for a regiment of an ABSTRACT village: a point on the map instead of entities. */
        AbstractMarch march;
        int abstractSize;

        Force(String village, String enemy) {
            this.village = village;
            this.enemy = enemy;
        }

        public String village() { return village; }
        public String enemy() { return enemy; }
        public int size() { return march != null ? abstractSize : members.size(); }
        public boolean isAbstract() { return march != null; }
        public AbstractMarch march() { return march; }
        /** Physical regiments are always "there"; an abstract one has to finish its march. */
        public boolean arrived() { return march == null || march.arrived(); }
        public Regiment.Loadout loadout() { return loadout; }
        public double strength() { return strength; }
    }

    private static final Map<String, Force> FORCES = new HashMap<>();
    private static double since;

    private MobilizationService() { }

    public static synchronized void reset() {
        FORCES.clear();
        since = 0;
    }

    public static synchronized Force force(String village) { return FORCES.get(village); }

    public static synchronized List<Force> forces() { return new ArrayList<>(FORCES.values()); }

    /** Soldats du régiment encore vivants. */
    public static int alive(Force f, ServerLevel level) {
        int n = 0;
        for (UUID id : f.members) {
            if (level.getEntity(id) instanceof Villager v && v.isAlive()) {
                n++;
            }
        }
        return n;
    }

    public static Regiment.Costs costs(SimConfig cfg) {
        return new Regiment.Costs((int) cfg.get(SimConfig.Key.MOBILIZATION_SWORD_ORE),
                (int) cfg.get(SimConfig.Key.MOBILIZATION_ARMOR_ORE), (int) cfg.get(SimConfig.Key.MOBILIZATION_BOW_WOOD));
    }

    public static synchronized void onTicks(long ticks, SimConfig cfg, VillageRegistry registry) {
        since += ticks / 20.0;
        double interval = cfg.get(SimConfig.Key.MOBILIZATION_INTERVAL_S);
        if (interval > 0 && since >= interval) {
            double elapsed = since;
            since = 0;
            update(cfg, registry, elapsed);
        }
    }

    public static synchronized void update(SimConfig cfg, VillageRegistry registry) {
        update(cfg, registry, cfg.get(SimConfig.Key.MOBILIZATION_INTERVAL_S));
    }

    /** Lève, équipe, fait marcher ou démobilise les régiments de tous les villages. */
    public static synchronized void update(SimConfig cfg, VillageRegistry registry, double elapsed) {
        if (RrpMod.server() == null) {
            return;
        }
        for (VillageSite site : new ArrayList<>(registry.all())) {
            VillageSite enemy = enemyOf(site, registry);
            Force f = FORCES.get(site.id());
            ServerLevel level = Worlds.level(RrpMod.server(), site.worldName());
            if (level == null) {
                continue;
            }
            if (enemy == null) {
                if (f != null) {
                    demobilize(f, site, level);
                }
                continue;
            }
            if (f != null && f.isAbstract() != site.isAbstract()) { // the village changed level of detail: re-form
                demobilize(f, site, level);
                f = null;
            }
            if (f == null || !f.enemy.equals(enemy.id())) {
                if (f != null) {
                    demobilize(f, site, level);
                }
                f = site.isAbstract() ? formAbstract(site, enemy, cfg) : form(site, enemy, level, cfg);
                if (f == null) {
                    continue;
                }
            }
            if (f.isAbstract()) {
                f.march.advance(elapsed);
            } else {
                march(f, enemy, level);
            }
        }
    }

    private static VillageSite enemyOf(VillageSite site, VillageRegistry registry) {
        List<String> ids = new ArrayList<>(DiplomacyService.treaties().partners(site.id(), State.WAR));
        ids.sort(String::compareTo);
        for (String id : ids) {
            VillageSite e = registry.get(id);
            if (e != null && e.worldName().equals(site.worldName())) {
                return e;
            }
        }
        return null;
    }

    private static Force form(VillageSite site, VillageSite enemy, ServerLevel level, SimConfig cfg) {
        List<Villager> present = RrpMod.adapter().present(site, level);
        List<Villager> guards = new ArrayList<>();
        for (Villager v : present) {
            var st = RrpMod.adapter().stateOf(v);
            if (st != null && st.role() == Role.GUARD) {
                guards.add(v);
            }
        }
        int n = Regiment.size(guards.size(), present.size(), LawService.conscription(site.id(), cfg));
        if (n <= 0) {
            return null;
        }
        var stock = RrpMod.warehouse().read(site, level);
        var loadout = Regiment.equip(n, stock.getOrDefault(Resource.ORE, 0), stock.getOrDefault(Resource.WOOD, 0), costs(cfg));
        int ore = RrpMod.warehouse().withdraw(site, level, Resource.ORE, loadout.oreSpent());
        int wood = RrpMod.warehouse().withdraw(site, level, Resource.WOOD, loadout.woodSpent());
        if (ore < loadout.oreSpent() || wood < loadout.woodSpent()) {
            loadout = Regiment.equip(n, ore, wood, new Regiment.Costs(1, 0, 1));
        }
        Force f = new Force(site.id(), enemy.id());
        f.loadout = loadout;
        for (int i = 0; i < n; i++) {
            Villager v = guards.get(i);
            f.members.add(v.getUUID());
            if (i < loadout.swords()) {
                v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            }
            if (i < loadout.bows()) {
                v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
            }
            if (i < loadout.armors()) {
                v.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            }
        }
        f.strength = Regiment.strength(n, loadout);
        FORCES.put(site.id(), f);
        RrpMod.crises().tell(site, RrpMod.lang().msg("mobilization.formed", site.id(), n, enemy.id(),
                Math.round(f.strength * 10) / 10.0));
        return f;
    }

    /** Regiment of a sleeping village: sized and equipped from the in-memory state, then sent as a point on the map. */
    private static Force formAbstract(VillageSite site, VillageSite enemy, SimConfig cfg) {
        var st = site.state();
        int n = Regiment.size(st.count(Role.GUARD), st.population(), LawService.conscription(site.id(), cfg));
        if (n <= 0) {
            return null;
        }
        var loadout = Regiment.equip(n, st.stock(Resource.ORE), st.stock(Resource.WOOD), costs(cfg));
        st.addStock(Resource.ORE, -loadout.oreSpent());
        st.addStock(Resource.WOOD, -loadout.woodSpent());
        Force f = new Force(site.id(), enemy.id());
        f.loadout = loadout;
        f.abstractSize = n;
        f.strength = Regiment.strength(n, loadout);
        f.march = AbstractRoutes.between(site, enemy, cfg);
        FORCES.put(site.id(), f);
        RrpMod.crises().tell(site, RrpMod.lang().msg("mobilization.formed", site.id(), n, enemy.id(),
                Math.round(f.strength * 10) / 10.0));
        return f;
    }

    private static void march(Force f, VillageSite enemy, ServerLevel level) {
        int[] hall = enemy.hall();
        double x = hall == null ? enemy.x() : hall[0] + 0.5;
        double y = hall == null ? enemy.y() : hall[1];
        double z = hall == null ? enemy.z() : hall[2] + 0.5;
        for (UUID id : f.members) {
            if (level.getEntity(id) instanceof Villager v && v.isAlive() && !v.isNoAi()) {
                v.getNavigation().moveTo(x, y, z, 0.5);
            }
        }
    }

    private static void demobilize(Force f, VillageSite site, ServerLevel level) {
        for (UUID id : f.members) {
            if (level.getEntity(id) instanceof Villager v) {
                v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                v.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                v.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            }
        }
        FORCES.remove(f.village);
        RrpMod.crises().tell(site, RrpMod.lang().msg("mobilization.demobilized", site.id()));
    }
}
