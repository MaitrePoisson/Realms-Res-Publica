package rrp.neoforge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import rrp.core.SimConfig;
import rrp.core.VillagerState;
import rrp.core.diplomacy.Treaty;
import rrp.core.player.Contract;
import rrp.core.player.Contracts;
import rrp.core.player.PlayerRole;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Mercenary contracts. Autonomous villages post contracts (clear a raider camp, take a side in a war) and escrow
 * the pay, in emeralds, out of their warehouse; a MERCENARY player accepts one, the objective is tracked (camp
 * raiders killed, war won) and the pay reaches the player's purse on success. Failure, timeout or an objective
 * that vanishes gives the escrow back to the village.
 */
@EventBusSubscriber(modid = RrpMod.MOD_ID)
public final class ContractService {

    public enum Verdict { OK, UNKNOWN_OBJECTIVE, TOO_MANY, DUPLICATE, NO_FUNDS }

    /** Seconds between two evaluations of the accepted contracts. */
    private static final double CHECK_S = 5;
    private static final int KEEP_SETTLED = 30;

    /** Village a camp came from, seen while the camp still stood (contract id to village id). */
    private static final Map<Integer, String> ORIGIN = new HashMap<>();
    private static double sinceCheck;
    private static double sincePost;

    private ContractService() {
    }

    private static Contracts book() {
        return PlayerService.contracts();
    }

    private static double now() {
        return RrpMod.server().overworld().getGameTime() / 20.0;
    }

    public static synchronized void reset() {
        sinceCheck = 0;
        sincePost = 0;
        ORIGIN.clear();
    }

    public static synchronized void onTicks(long ticks, SimConfig cfg, VillageRegistry registry) {
        if (RrpMod.server() == null) {
            return;
        }
        double s = ticks / 20.0;
        sinceCheck += s;
        sincePost += s;
        if (sinceCheck >= CHECK_S) {
            sinceCheck = 0;
            update(now(), cfg, registry);
        }
        double period = cfg.get(SimConfig.Key.CONTRACT_POST_PERIOD_S);
        if (period > 0 && sincePost >= period) {
            sincePost = 0;
            autoPost(cfg, registry);
        }
    }

    // ----- posting --------------------------------------------------------------------------------------------

    /** Pay of a camp contract: a base plus a supplement per raider. */
    public static long campPay(VillageSite camp, SimConfig cfg) {
        return Math.round(cfg.get(SimConfig.Key.CONTRACT_PAY_BASE)
                + cfg.get(SimConfig.Key.CONTRACT_PAY_PER_RAIDER) * camp.state().villagers().size());
    }

    /** Posts a contract for {@code issuer}, escrowing {@code pay} emeralds out of its warehouse. */
    public static synchronized Verdict post(VillageSite issuer, Contract.Type type, String target, long pay, SimConfig cfg) {
        VillageRegistry registry = RrpMod.registry();
        if (!objectiveValid(issuer, type, target, registry)) {
            return Verdict.UNKNOWN_OBJECTIVE;
        }
        if (book().liveOf(issuer.id()) >= (int) cfg.get(SimConfig.Key.CONTRACT_MAX_PER_VILLAGE)) {
            return Verdict.TOO_MANY;
        }
        if (book().live(issuer.id(), type, target) != null) {
            return Verdict.DUPLICATE;
        }
        ServerLevel level = Worlds.level(RrpMod.server(), issuer.worldName());
        if (pay <= 0 || level == null || !issuer.hasWarehouse() || !WarehouseService.loaded(issuer, level)
                || !RrpMod.warehouse().take(issuer, level, Items.EMERALD, (int) pay)) {
            return Verdict.NO_FUNDS;
        }
        Contract c = book().post(issuer.id(), type, target, pay, now());
        if (c == null) { // cannot happen after the checks above, but never keep the escrow
            RrpMod.warehouse().deposit(issuer, level, new ItemStack(Items.EMERALD, (int) pay));
            return Verdict.DUPLICATE;
        }
        noteOrigin(c, registry);
        RrpMod.registry().markDirty();
        RrpMod.crises().tell(issuer, RrpMod.lang().msg("contract.posted", issuer.id(), c.id(), label(type), target, pay));
        return Verdict.OK;
    }

    private static boolean objectiveValid(VillageSite issuer, Contract.Type type, String target, VillageRegistry registry) {
        VillageSite t = registry.get(target);
        if (t == null || t == issuer) {
            return false;
        }
        if (type == Contract.Type.CLEAR_CAMP) {
            return t.state().parentId() != null;
        }
        return DiplomacyService.treaties().partners(issuer.id(), Treaty.State.WAR).contains(target);
    }

    private static void noteOrigin(Contract c, VillageRegistry registry) {
        if (c.type() == Contract.Type.CLEAR_CAMP) {
            VillageSite camp = registry.get(c.target());
            if (camp != null && camp.state().parentId() != null) {
                ORIGIN.put(c.id(), camp.state().parentId());
            }
        }
    }

    /** Every autonomous village with spare emeralds posts a contract against the camps it spawned and the wars it fights. */
    public static synchronized int autoPost(SimConfig cfg, VillageRegistry registry) {
        int posted = 0;
        for (VillageSite site : registry.all()) {
            if (site.owner() != null || site.state().parentId() != null) {
                continue;
            }
            for (VillageSite camp : registry.all()) {
                if (site.id().equals(camp.state().parentId())
                        && post(site, Contract.Type.CLEAR_CAMP, camp.id(), campPay(camp, cfg), cfg) == Verdict.OK) {
                    posted++;
                }
            }
            for (String enemy : DiplomacyService.treaties().partners(site.id(), Treaty.State.WAR)) {
                if (post(site, warType(site, enemy), enemy, Math.round(cfg.get(SimConfig.Key.CONTRACT_WAR_PAY)), cfg) == Verdict.OK) {
                    posted++;
                }
            }
        }
        return posted;
    }

    /** ATTACK when the issuer fields the stronger regiment, DEFEND otherwise. */
    static Contract.Type warType(VillageSite issuer, String enemy) {
        var mine = MobilizationService.force(issuer.id());
        var theirs = MobilizationService.force(enemy);
        double a = mine == null ? 0 : mine.strength();
        double b = theirs == null ? 0 : theirs.strength();
        return a > b ? Contract.Type.WAR_ATTACK : Contract.Type.WAR_DEFEND;
    }

    // ----- accepting ------------------------------------------------------------------------------------------

    public static synchronized Contracts.Accept accept(ServerPlayer p, int id, SimConfig cfg) {
        Contracts.Accept r = book().accept(id, p.getUUID(), now(), (int) cfg.get(SimConfig.Key.CONTRACT_MAX_PER_MERCENARY));
        if (r == Contracts.Accept.OK) {
            Contract c = book().get(id);
            c.setMark(WarResolution.history().size());
            RrpMod.registry().markDirty();
            VillageSite issuer = RrpMod.registry().get(c.issuer());
            if (issuer != null) {
                RrpMod.crises().tell(issuer, RrpMod.lang().msg("contract.accepted", p.getGameProfile().getName(), c.id(), c.issuer()));
            }
        }
        return r;
    }

    /** A player killed a villager: counts for every contract of theirs whose enemy that villager belongs to. */
    public static synchronized void onKill(UUID killer, UUID victim) {
        VillageRegistry registry = RrpMod.registry();
        for (Contract c : book().accepted()) {
            if (!killer.equals(c.mercenary())) {
                continue;
            }
            VillageSite enemy = registry.get(c.target());
            if (enemy != null && belongs(enemy, victim)) {
                c.addKill();
                registry.markDirty();
            }
        }
    }

    private static boolean belongs(VillageSite site, UUID villager) {
        for (VillagerState s : site.state().villagers()) {
            if (s.id().equals(villager)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Villager v && event.getSource().getEntity() instanceof ServerPlayer p
                && RrpMod.registry() != null && PlayerService.roleOf(p.getUUID()) == PlayerRole.MERCENARY) {
            onKill(p.getUUID(), v.getUUID());
        }
    }

    // ----- tracking -------------------------------------------------------------------------------------------

    /** Ages the book, judges every accepted contract and settles the escrows. */
    public static synchronized void update(double nowSec, SimConfig cfg, VillageRegistry registry) {
        for (Contract c : book().tick(nowSec, cfg.get(SimConfig.Key.CONTRACT_OPEN_TTL_S), cfg.get(SimConfig.Key.CONTRACT_TIMEOUT_S))) {
            tellMerc(c, c.state() == Contract.State.FAILED ? "contract.timeout" : "contract.expired", c.id());
        }
        for (Contract c : book().all()) {
            if (c.state() == Contract.State.OPEN && !objectiveAlive(c, registry)) {
                book().cancel(c.id());
            } else if (c.state() == Contract.State.ACCEPTED) {
                judge(c, cfg, registry);
            }
        }
        settleAll(cfg, registry);
        book().prune(KEEP_SETTLED);
    }

    private static boolean objectiveAlive(Contract c, VillageRegistry registry) {
        if (registry.get(c.issuer()) == null || registry.get(c.target()) == null) {
            return false;
        }
        noteOrigin(c, registry);
        return c.type() == Contract.Type.CLEAR_CAMP
                || DiplomacyService.treaties().partners(c.issuer(), Treaty.State.WAR).contains(c.target());
    }

    private static void judge(Contract c, SimConfig cfg, VillageRegistry registry) {
        if (c.type() == Contract.Type.CLEAR_CAMP) {
            VillageSite camp = registry.get(c.target());
            noteOrigin(c, registry);
            if (camp == null || raidersLeft(camp) == 0) {
                finish(c, c.kills() > 0);
            }
            return;
        }
        List<WarResolution.Result> history = WarResolution.history();
        int from = Math.min(c.mark(), history.size());
        for (int i = history.size() - 1; i >= from; i--) {
            WarResolution.Result r = history.get(i);
            boolean mine = r.winner().equals(c.issuer()) && r.loser().equals(c.target());
            boolean lost = r.winner().equals(c.target()) && r.loser().equals(c.issuer());
            if (mine || lost) {
                if (mine && c.kills() > 0) {
                    finish(c, true);
                } else if (mine) {
                    book().cancel(c.id()); // won without the mercenary lifting a finger: nobody is paid, nobody blamed
                    tellMerc(c, "contract.cancelled", c.id());
                } else {
                    book().fail(c.id());
                    tellMerc(c, "contract.failed", c.id());
                }
                return;
            }
        }
        if (!objectiveAlive(c, registry)) {
            book().cancel(c.id()); // ceasefire or a village that disappeared
            tellMerc(c, "contract.cancelled", c.id());
        }
    }

    private static void finish(Contract c, boolean success) {
        if (success) {
            book().complete(c.id());
        } else {
            book().cancel(c.id());
            tellMerc(c, "contract.cancelled", c.id());
        }
    }

    /** Raiders of the camp still standing: living entities, or unknown ones in unloaded land. */
    static int raidersLeft(VillageSite camp) {
        ServerLevel level = Worlds.level(RrpMod.server(), camp.worldName());
        if (level == null) {
            return camp.state().villagers().size();
        }
        boolean loaded = level.hasChunkAt(BlockPos.containing(camp.x(), camp.y(), camp.z()));
        int left = 0;
        for (VillagerState s : camp.state().villagers()) {
            if (!loaded || (level.getEntity(s.id()) instanceof LivingEntity e && e.isAlive())) {
                left++;
            }
        }
        return left;
    }

    // ----- settling -------------------------------------------------------------------------------------------

    private static void settleAll(SimConfig cfg, VillageRegistry registry) {
        for (Contract c : book().unsettled()) {
            if (c.state() == Contract.State.DONE) {
                PlayerService.ledger().deposit(c.mercenary(), c.pay());
                c.markSettled();
                relationShift(c, cfg, registry);
                tellMerc(c, "contract.done", c.id(), c.pay());
                registry.markDirty();
            } else if (refund(c, registry)) {
                c.markSettled();
                registry.markDirty();
            }
        }
    }

    /** Puts the escrow back in the issuer's warehouse; false while that is impossible (retried later). */
    private static boolean refund(Contract c, VillageRegistry registry) {
        VillageSite issuer = registry.get(c.issuer());
        if (issuer == null) {
            return true; // the village is gone, so is its treasury
        }
        ServerLevel level = Worlds.level(RrpMod.server(), issuer.worldName());
        if (level == null || !issuer.hasWarehouse() || !WarehouseService.loaded(issuer, level)) {
            return false;
        }
        ItemStack left = RrpMod.warehouse().deposit(issuer, level, new ItemStack(Items.EMERALD, (int) c.pay()));
        if (!left.isEmpty()) { // warehouse full: drop the rest by the hall rather than lose it
            int[] at = issuer.hasHall() ? issuer.hall() : issuer.warehouse();
            level.addFreshEntity(new ItemEntity(level, at[0] + 0.5, at[1] + 1, at[2] + 0.5, left));
        }
        return true;
    }

    /** A cleared camp pleases the village it came from; a won war deepens the grudge against the enemy. */
    private static void relationShift(Contract c, SimConfig cfg, VillageRegistry registry) {
        VillageSite issuer = registry.get(c.issuer());
        double shift = cfg.get(SimConfig.Key.CONTRACT_RELATION_SHIFT);
        if (issuer == null || shift <= 0) {
            return;
        }
        if (c.type() == Contract.Type.CLEAR_CAMP) {
            VillageSite origin = registry.get(ORIGIN.getOrDefault(c.id(), ""));
            if (origin != null && origin != issuer) {
                RelationService.change(issuer, origin, shift, cfg);
            }
        } else {
            VillageSite enemy = registry.get(c.target());
            if (enemy != null) {
                RelationService.change(issuer, enemy, -shift, cfg);
            }
        }
        ORIGIN.remove(c.id());
    }

    private static void tellMerc(Contract c, String key, Object... args) {
        if (c.mercenary() == null || RrpMod.server() == null) {
            return;
        }
        ServerPlayer p = RrpMod.server().getPlayerList().getPlayer(c.mercenary());
        if (p != null) {
            p.sendSystemMessage(Component.literal(RrpMod.lang().msg(key, args)));
        }
    }

    private static String label(Contract.Type t) {
        return RrpMod.lang().get("contract.type." + t.name().toLowerCase(Locale.ROOT));
    }

    // ----- commands -------------------------------------------------------------------------------------------

    static void install() {
        Commands.register("contract", ContractService::command);
    }

    private static void command(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            Commands.reply(src, lang.msg("role.players_only"));
            return;
        }
        SimConfig cfg = RrpMod.simConfig();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "list" -> list(src, p);
            case "status", "rep" -> status(src, p);
            case "accept" -> {
                Integer id = args.length > 2 ? number(args[2]) : null;
                if (PlayerService.roleOf(p.getUUID()) != PlayerRole.MERCENARY) {
                    Commands.reply(src, lang.msg("contract.not_mercenary"));
                } else if (id == null) {
                    Commands.reply(src, lang.msg("contract.usage"));
                } else {
                    switch (accept(p, id, cfg)) {
                        case OK -> Commands.reply(src, lang.msg("contract.you_accepted", id, book().get(id).target()));
                        case UNKNOWN -> Commands.reply(src, lang.msg("contract.unknown", id));
                        case NOT_OPEN -> Commands.reply(src, lang.msg("contract.not_open", id));
                        case TOO_MANY -> Commands.reply(src, lang.msg("contract.too_many", (int) cfg.get(SimConfig.Key.CONTRACT_MAX_PER_MERCENARY)));
                    }
                }
            }
            case "abandon" -> {
                Integer id = args.length > 2 ? number(args[2]) : null;
                synchronized (ContractService.class) {
                    Commands.reply(src, lang.msg(id != null && book().abandon(id, p.getUUID()) ? "contract.abandoned" : "contract.unknown", id));
                }
                RrpMod.registry().markDirty();
            }
            case "post" -> postCommand(src, p, args, cfg);
            default -> Commands.reply(src, lang.msg("contract.usage"));
        }
    }

    private static void list(CommandSourceStack src, ServerPlayer p) {
        Lang lang = RrpMod.lang();
        List<Contract> open = book().open();
        if (open.isEmpty()) {
            Commands.reply(src, lang.msg("contract.none"));
        }
        for (Contract c : open) {
            Commands.reply(src, lang.msg("contract.line", c.id(), c.issuer(), label(c.type()), c.target(), c.pay()));
        }
    }

    private static void status(CommandSourceStack src, ServerPlayer p) {
        Lang lang = RrpMod.lang();
        Commands.reply(src, lang.msg("contract.rep", book().reputation(p.getUUID()), PlayerService.ledger().purse(p.getUUID())));
        for (Contract c : book().accepted()) {
            if (p.getUUID().equals(c.mercenary())) {
                Commands.reply(src, lang.msg("contract.mine", c.id(), label(c.type()), c.target(), c.pay(), c.kills(),
                        Math.max(0, Math.round(RrpMod.simConfig().get(SimConfig.Key.CONTRACT_TIMEOUT_S) - (now() - c.acceptedAt())))));
            }
        }
    }

    /** {@code post camp <camp> [pay]} or {@code post war <attack|defend> <enemy> [pay]}, for the ruler of a village. */
    private static void postCommand(CommandSourceStack src, ServerPlayer p, String[] args, SimConfig cfg) {
        Lang lang = RrpMod.lang();
        var owned = SovereignService.ownedBy(p.getUUID());
        if (owned.isEmpty()) {
            Commands.reply(src, lang.msg("law.not_ruler"));
            return;
        }
        VillageSite site = owned.get(0);
        String kind = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
        Contract.Type type;
        String target;
        int payAt;
        if (kind.equals("camp") && args.length > 3) {
            type = Contract.Type.CLEAR_CAMP;
            target = args[3];
            payAt = 4;
        } else if (kind.equals("war") && args.length > 4 && (args[3].equalsIgnoreCase("attack") || args[3].equalsIgnoreCase("defend"))) {
            type = args[3].equalsIgnoreCase("attack") ? Contract.Type.WAR_ATTACK : Contract.Type.WAR_DEFEND;
            target = args[4];
            payAt = 5;
        } else {
            Commands.reply(src, lang.msg("contract.usage"));
            return;
        }
        VillageSite t = RrpMod.registry().get(target);
        Integer asked = args.length > payAt ? number(args[payAt]) : null;
        long pay = asked != null ? asked : type == Contract.Type.CLEAR_CAMP && t != null ? campPay(t, cfg) : Math.round(cfg.get(SimConfig.Key.CONTRACT_WAR_PAY));
        Verdict v = post(site, type, target, pay, cfg);
        String key = switch (v) {
            case OK -> "contract.post_ok";
            case UNKNOWN_OBJECTIVE -> "contract.post_unknown_objective";
            case TOO_MANY -> "contract.post_too_many";
            case DUPLICATE -> "contract.post_duplicate";
            case NO_FUNDS -> "contract.post_no_funds";
        };
        Commands.reply(src, lang.msg(key, pay));
    }

    private static Integer number(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
