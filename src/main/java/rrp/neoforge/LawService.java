package rrp.neoforge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.VillagerState;
import rrp.core.player.PlayerRole;
import rrp.core.player.Taxation;
import rrp.core.player.VillageLaws;

import java.util.Locale;

/**
 * Laws and taxes of player-ruled villages. The decrees live in {@link PlayerService#laws()} and are read by the
 * services they change (mobilisation share, caravan embargo, border provocation, building ban); the levy is paid
 * into the village warehouse once per period and a heavy burden costs the villagers their loyalty.
 */
@EventBusSubscriber(modid = RrpMod.MOD_ID)
public final class LawService {

    private static double since;

    private LawService() {
    }

    public static VillageLaws lawsOf(String village) {
        return PlayerService.laws().peek(village);
    }

    /** True when the owner of {@code village} has opened its borders to foreign workers. */
    public static boolean openBorders(String village) {
        VillageLaws l = lawsOf(village);
        return l != null && l.openBorders();
    }

    public static boolean embargo(String a, String b) {
        return PlayerService.laws().embargo(a, b);
    }

    /** Share of the population that may be mobilised in {@code village}. */
    public static double conscription(String village, SimConfig cfg) {
        VillageLaws l = lawsOf(village);
        double global = cfg.get(SimConfig.Key.MOBILIZATION_SHARE);
        return l == null ? global : l.conscriptionOr(global, cfg.get(SimConfig.Key.LAW_CONSCRIPTION_MAX));
    }

    public static synchronized void reset() {
        since = 0;
    }

    public static synchronized void onTicks(long ticks, SimConfig cfg, VillageRegistry registry) {
        since += ticks / 20.0;
        double period = cfg.get(SimConfig.Key.TAX_PERIOD_S);
        if (period > 0 && since >= period) {
            since = 0;
            collect(cfg, registry);
        }
    }

    /** Levies the tax of every ruled village; returns the number of items paid into warehouses. */
    public static int collect(SimConfig cfg, VillageRegistry registry) {
        if (RrpMod.server() == null) {
            return 0;
        }
        int total = 0;
        for (VillageSite site : registry.all()) {
            VillageLaws laws = site.owner() == null ? null : lawsOf(site.id());
            if (laws == null || laws.taxes().isEmpty()) {
                continue;
            }
            ServerLevel level = Worlds.level(RrpMod.server(), site.worldName());
            if (level == null || !site.hasWarehouse() || !WarehouseService.loaded(site, level)) {
                continue;
            }
            total += collect(site, level, laws, cfg);
        }
        return total;
    }

    static int collect(VillageSite site, ServerLevel level, VillageLaws laws, SimConfig cfg) {
        int pop = site.state().villagers().size();
        double cap = cfg.get(SimConfig.Key.TAX_MAX_RATE);
        int paid = 0;
        double burden = 0;
        for (Resource r : Resource.values()) {
            double rate = Taxation.clamp(laws.tax(r.name()), cap);
            burden += rate;
            int n = Taxation.yield(pop, rate, cfg.get(SimConfig.Key.TAX_PER_VILLAGER));
            if (n > 0) {
                paid += n - RrpMod.warehouse().deposit(site, level, CaravanService.stackOf(r, n)).getCount();
            }
        }
        double loss = Taxation.unrest(burden, cfg.get(SimConfig.Key.TAX_FREE_BURDEN), cfg.get(SimConfig.Key.TAX_UNREST_PER_POINT));
        if (loss > 0) {
            for (VillagerState s : site.state().villagers()) {
                s.setLoyalty(s.loyalty() - loss);
            }
            RrpMod.crises().tell(site, RrpMod.lang().msg("tax.unrest", site.id(), Math.round(loss)));
        }
        if (paid > 0) {
            RrpMod.crises().tell(site, RrpMod.lang().msg("tax.collected", site.id(), paid));
        }
        return paid;
    }

    /** Foreigners' building ban: a non-observer who is not the ruler cannot place blocks in such a village. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getLevel() instanceof ServerLevel level)
                || RrpMod.registry() == null) {
            return;
        }
        if (bans(p, level, event.getPos())) {
            event.setCanceled(true);
            p.sendSystemMessage(Component.literal(RrpMod.lang().msg("law.build_banned")));
        }
    }

    public static boolean bans(ServerPlayer p, ServerLevel level, BlockPos at) {
        if (PlayerService.roleOf(p.getUUID()) == PlayerRole.OBSERVER) {
            return false;
        }
        VillageSite site = RrpMod.registry().find(Worlds.name(level), at.getX(), at.getZ());
        if (site == null || site.owner() == null || site.owner().equals(p.getUUID().toString())) {
            return false;
        }
        VillageLaws l = lawsOf(site.id());
        return l != null && l.foreignersBuildBan();
    }

    // ----- commands -------------------------------------------------------------------------------------------

    static void install() {
        Commands.register("law", LawService::lawCommand);
        Commands.register("tax", LawService::taxCommand);
    }

    private static VillageSite ruled(CommandSourceStack src) {
        Lang lang = RrpMod.lang();
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            Commands.reply(src, lang.msg("role.players_only"));
            return null;
        }
        var owned = SovereignService.ownedBy(p.getUUID());
        if (owned.isEmpty()) {
            Commands.reply(src, lang.msg("law.not_ruler"));
            return null;
        }
        return owned.get(0);
    }

    private static void lawCommand(CommandSourceStack src, String[] args) {
        VillageSite site = ruled(src);
        if (site == null) {
            return;
        }
        Lang lang = RrpMod.lang();
        SimConfig cfg = RrpMod.simConfig();
        VillageLaws l = PlayerService.laws().of(site.id());
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "show";
        switch (action) {
            case "show" -> Commands.reply(src, lang.msg("law.show", site.id(), l.openBorders(), Math.round(conscription(site.id(), cfg) * 100),
                    l.embargo().isEmpty() ? "-" : String.join(",", l.embargo()), l.foreignersBuildBan()));
            case "borders" -> {
                l.setOpenBorders(args.length > 2 ? flag(args[2]) : !l.openBorders());
                Commands.reply(src, lang.msg("law.borders", l.openBorders()));
            }
            case "ban" -> {
                l.setForeignersBuildBan(args.length > 2 ? flag(args[2]) : !l.foreignersBuildBan());
                Commands.reply(src, lang.msg("law.ban", l.foreignersBuildBan()));
            }
            case "conscript" -> {
                double v = args.length > 2 ? number(args[2]) : Double.NaN;
                if (Double.isNaN(v)) {
                    Commands.reply(src, lang.msg("law.usage"));
                    return;
                }
                l.setConscription(v / 100.0);
                Commands.reply(src, lang.msg("law.conscript", Math.round(conscription(site.id(), cfg) * 100)));
            }
            case "embargo" -> {
                String target = args.length > 2 ? args[2] : null;
                if (target == null || RrpMod.registry().get(target) == null || target.equals(site.id())) {
                    Commands.reply(src, lang.msg("law.usage"));
                    return;
                }
                Commands.reply(src, lang.msg(l.toggleEmbargo(target) ? "law.embargo_on" : "law.embargo_off", target));
            }
            default -> {
                Commands.reply(src, lang.msg("law.usage"));
                return;
            }
        }
        RrpMod.registry().markDirty();
    }

    private static void taxCommand(CommandSourceStack src, String[] args) {
        VillageSite site = ruled(src);
        if (site == null) {
            return;
        }
        Lang lang = RrpMod.lang();
        SimConfig cfg = RrpMod.simConfig();
        VillageLaws l = PlayerService.laws().of(site.id());
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "show";
        if ("set".equals(action)) {
            Resource r = null;
            for (Resource x : Resource.values()) {
                if (args.length > 2 && x.name().equalsIgnoreCase(args[2])) {
                    r = x;
                }
            }
            double pct = args.length > 3 ? number(args[3]) : Double.NaN;
            if (r == null || Double.isNaN(pct)) {
                Commands.reply(src, lang.msg("tax.usage"));
                return;
            }
            double rate = Taxation.clamp(pct / 100.0, cfg.get(SimConfig.Key.TAX_MAX_RATE));
            l.setTax(r.name(), rate);
            RrpMod.registry().markDirty();
            Commands.reply(src, lang.msg("tax.set", r.label(), Math.round(rate * 100)));
        } else if ("show".equals(action)) {
            StringBuilder sb = new StringBuilder();
            for (Resource r : Resource.values()) {
                sb.append(r.label()).append(' ').append(Math.round(l.tax(r.name()) * 100)).append("% ");
            }
            Commands.reply(src, lang.msg("tax.show", site.id(), sb.toString().trim(), Math.round(cfg.get(SimConfig.Key.TAX_MAX_RATE) * 100)));
        } else {
            Commands.reply(src, lang.msg("tax.usage"));
        }
    }

    private static boolean flag(String s) {
        return s.equalsIgnoreCase("on") || s.equalsIgnoreCase("true") || s.equals("1");
    }

    private static double number(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
