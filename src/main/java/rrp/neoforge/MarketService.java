package rrp.neoforge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.player.PlayerRole;
import rrp.core.player.PriceModel;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The free merchant's trade: each village prices every resource from its stock against its need, and a MERCHANT
 * player buys from or sells to the warehouse with real items against the emerald purse. A daily cap per village and
 * resource, and the buy/sell spread, keep the arbitrage from being infinite. A merchant who trades in two
 * different villages in a row improves the relation between them.
 */
public final class MarketService {

    public enum Verdict { OK, NOT_MERCHANT, NO_MARKET, LIMIT, NO_STOCK, NO_ITEMS, NO_FUNDS }

    /** Outcome of a trade: how many units moved and the emeralds that changed hands. */
    public record Receipt(Verdict verdict, int quantity, long emeralds) { }

    private static final PriceModel.Saturation SATURATION = new PriceModel.Saturation();
    private static final Map<UUID, String> LAST_VILLAGE = new HashMap<>();
    private static double since;

    private MarketService() {
    }

    public static synchronized void reset() {
        SATURATION.reset();
        LAST_VILLAGE.clear();
        since = 0;
    }

    public static synchronized void onTicks(long ticks, SimConfig cfg) {
        since += ticks / 20.0;
        if (since >= cfg.get(SimConfig.Key.MARKET_WINDOW_S)) {
            since = 0;
            SATURATION.reset();
        }
    }

    /** Price model of a resource in a village (ore is dearer by a fixed factor). */
    public static PriceModel model(VillageSite site, Resource r, SimConfig cfg) {
        double factor = r == Resource.ORE ? 4 : 1;
        double need = Math.max(1, site.state().villagers().size()) * cfg.get(SimConfig.Key.MARKET_NEED_PER_VILLAGER);
        return new PriceModel(cfg.get(SimConfig.Key.MARKET_BASE_PRICE) * factor, need, cfg.get(SimConfig.Key.MARKET_MIN_MULT),
                cfg.get(SimConfig.Key.MARKET_MAX_MULT), cfg.get(SimConfig.Key.MARKET_SPREAD));
    }

    public static synchronized int remaining(VillageSite site, Resource r, SimConfig cfg) {
        return SATURATION.remaining(site.id(), r.name(), (int) cfg.get(SimConfig.Key.MARKET_DAILY_LIMIT));
    }

    public static int stock(VillageSite site, Resource r, ServerLevel level) {
        return RrpMod.warehouse().read(site, level).getOrDefault(r, 0);
    }

    /** The merchant sells {@code qty} units from their inventory to the village warehouse. */
    public static synchronized Receipt sell(ServerPlayer p, VillageSite site, Resource r, int qty, SimConfig cfg) {
        ServerLevel level = marketLevel(p, site);
        if (level == null) {
            return new Receipt(PlayerRole.MERCHANT == PlayerService.roleOf(p.getUUID()) ? Verdict.NO_MARKET : Verdict.NOT_MERCHANT, 0, 0);
        }
        int allowed = Math.min(qty, remaining(site, r, cfg));
        if (allowed <= 0) {
            return new Receipt(Verdict.LIMIT, 0, 0);
        }
        int have = take(p, r, allowed);
        if (have <= 0) {
            return new Receipt(Verdict.NO_ITEMS, 0, 0);
        }
        int stockBefore = stock(site, r, level);
        int left = deposit(site, level, r, have);
        give(p, r, left); // what the warehouse could not hold goes back to the merchant
        int sold = have - left;
        long money = model(site, r, cfg).sellRevenue(stockBefore, sold);
        PlayerService.ledger().deposit(p.getUUID(), money);
        done(p, site, r, sold, cfg);
        return new Receipt(sold > 0 ? Verdict.OK : Verdict.NO_STOCK, sold, money);
    }

    /** The merchant buys up to {@code qty} units out of the village warehouse with their purse. */
    public static synchronized Receipt buy(ServerPlayer p, VillageSite site, Resource r, int qty, SimConfig cfg) {
        ServerLevel level = marketLevel(p, site);
        if (level == null) {
            return new Receipt(PlayerRole.MERCHANT == PlayerService.roleOf(p.getUUID()) ? Verdict.NO_MARKET : Verdict.NOT_MERCHANT, 0, 0);
        }
        int allowed = Math.min(qty, remaining(site, r, cfg));
        if (allowed <= 0) {
            return new Receipt(Verdict.LIMIT, 0, 0);
        }
        int stock = stock(site, r, level);
        if (stock <= 0) {
            return new Receipt(Verdict.NO_STOCK, 0, 0);
        }
        PriceModel model = model(site, r, cfg);
        long purse = PlayerService.ledger().purse(p.getUUID());
        int n = model.affordable(stock, Math.min(allowed, stock), purse);
        if (n <= 0) {
            return new Receipt(Verdict.NO_FUNDS, 0, 0);
        }
        int taken = RrpMod.warehouse().withdraw(site, level, r, n);
        if (taken <= 0) {
            return new Receipt(Verdict.NO_STOCK, 0, 0);
        }
        long cost = model.buyCost(stock, taken);
        PlayerService.ledger().withdraw(p.getUUID(), cost);
        for (long due = cost; due > 0; due -= 64) { // the payment goes to the village treasury, not into thin air
            RrpMod.warehouse().deposit(site, level, new ItemStack(Items.EMERALD, (int) Math.min(due, 64)));
        }
        give(p, r, taken);
        done(p, site, r, taken, cfg);
        return new Receipt(Verdict.OK, taken, cost);
    }

    private static ServerLevel marketLevel(ServerPlayer p, VillageSite site) {
        if (PlayerService.roleOf(p.getUUID()) != PlayerRole.MERCHANT || RrpMod.server() == null) {
            return null;
        }
        ServerLevel level = Worlds.level(RrpMod.server(), site.worldName());
        return level != null && site.hasWarehouse() && WarehouseService.loaded(site, level) ? level : null;
    }

    private static void done(ServerPlayer p, VillageSite site, Resource r, int units, SimConfig cfg) {
        SATURATION.record(site.id(), r.name(), units);
        String last = LAST_VILLAGE.put(p.getUUID(), site.id());
        VillageSite prev = last == null || last.equals(site.id()) ? null : RrpMod.registry().get(last);
        if (prev != null && units > 0) {
            RelationService.change(prev, site, cfg.get(SimConfig.Key.MARKET_RELATION_GAIN), cfg);
        }
        RrpMod.registry().markDirty();
    }

    /** Removes up to {@code max} units of the resource from the inventory; returns how many were taken. */
    private static int take(ServerPlayer p, Resource r, int max) {
        int taken = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && taken < max; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(CaravanService.stackOf(r, 1).getItem())) { // only the canonical item: no cheap-for-dear swaps
                int t = Math.min(max - taken, s.getCount());
                s.shrink(t);
                taken += t;
            }
        }
        return taken;
    }

    /** Deposits {@code n} units into the warehouse; returns the number that did not fit. */
    private static int deposit(VillageSite site, ServerLevel level, Resource r, int n) {
        int left = 0;
        for (ItemStack s : stacks(r, n)) {
            left += RrpMod.warehouse().deposit(site, level, s).getCount();
        }
        return left;
    }

    private static void give(ServerPlayer p, Resource r, int n) {
        for (ItemStack s : stacks(r, n)) {
            if (!p.getInventory().add(s) && !s.isEmpty()) {
                p.drop(s, false);
            }
        }
    }

    private static java.util.List<ItemStack> stacks(Resource r, int n) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        int rest = n;
        while (rest > 0) {
            ItemStack s = CaravanService.stackOf(r, 1);
            int c = Math.min(rest, s.getMaxStackSize());
            s.setCount(c);
            out.add(s);
            rest -= c;
        }
        return out;
    }

    // ----- commands -------------------------------------------------------------------------------------------

    static void install() {
        Commands.register("market", MarketService::command);
    }

    private static void command(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            Commands.reply(src, lang.msg("role.players_only"));
            return;
        }
        SimConfig cfg = RrpMod.simConfig();
        VillageSite site = RrpMod.registry().find(Worlds.name(p.serverLevel()), p.getX(), p.getZ());
        if (site == null) {
            Commands.reply(src, lang.msg("market.not_in_village"));
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "prices";
        if (action.equals("prices")) {
            prices(src, p, site, cfg);
            return;
        }
        Resource r = null;
        for (Resource x : Resource.values()) {
            if (args.length > 2 && x.name().equalsIgnoreCase(args[2])) {
                r = x;
            }
        }
        int qty = 0;
        try {
            qty = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        } catch (NumberFormatException ignored) {
            // usage reply below
        }
        if (r == null || qty <= 0 || !(action.equals("buy") || action.equals("sell"))) {
            Commands.reply(src, lang.msg("market.usage"));
            return;
        }
        Receipt rc = action.equals("buy") ? buy(p, site, r, qty, cfg) : sell(p, site, r, qty, cfg);
        String key = switch (rc.verdict()) {
            case OK -> action.equals("buy") ? "market.bought" : "market.sold";
            case NOT_MERCHANT -> "market.not_merchant";
            case NO_MARKET -> "market.no_market";
            case LIMIT -> "market.limit";
            case NO_STOCK -> "market.no_stock";
            case NO_ITEMS -> "market.no_items";
            case NO_FUNDS -> "market.no_funds";
        };
        Commands.reply(src, lang.msg(key, rc.quantity(), r.label(), rc.emeralds(), PlayerService.ledger().purse(p.getUUID())));
    }

    private static void prices(CommandSourceStack src, ServerPlayer p, VillageSite site, SimConfig cfg) {
        Lang lang = RrpMod.lang();
        ServerLevel level = p.serverLevel();
        if (!site.hasWarehouse() || !WarehouseService.loaded(site, level)) {
            Commands.reply(src, lang.msg("market.no_market"));
            return;
        }
        Commands.reply(src, lang.msg("market.header", site.id()));
        for (Resource r : Resource.values()) {
            int stock = stock(site, r, level);
            PriceModel m = model(site, r, cfg);
            Commands.reply(src, lang.msg("market.line", r.label(), stock, String.format(Locale.ROOT, "%.2f", m.buyCost(stock, 10) / 10.0),
                    String.format(Locale.ROOT, "%.2f", m.sellRevenue(stock, 10) / 10.0), remaining(site, r, cfg)));
        }
    }
}
