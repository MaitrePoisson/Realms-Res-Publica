package rrp.neoforge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import rrp.core.Resource;
import rrp.core.SimConfig;
import rrp.core.diplomacy.Treaty.State;
import rrp.core.player.Proposals;
import rrp.core.player.Proposals.Answer;
import rrp.core.player.Proposals.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The diplomatic register ({@code /rrp register}): relations, treaties and wars of the villages around, with
 * clickable proposals for a ruler, who sends an emissary from one of their villages; the target village answers
 * through the same decider the autonomous villages use. Everyone else reads it only.
 */
public final class DiplomaticRegister {

    private static final int MAX_LINES = 14;

    private DiplomaticRegister() {
    }

    static void install() {
        Commands.register("register", DiplomaticRegister::command);
    }

    private static void command(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            Commands.reply(src, lang.msg("role.players_only"));
            return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("propose")) {
            Kind kind = args.length > 4 ? Kind.parse(args[2]) : null;
            VillageSite from = kind == null ? null : RrpMod.registry().get(args[3]);
            VillageSite to = kind == null ? null : RrpMod.registry().get(args[4]);
            if (kind == null || from == null || to == null) {
                Commands.reply(src, lang.msg("register.usage"));
                return;
            }
            Commands.reply(src, lang.msg("register.answer." + propose(p, from, to, kind, RrpMod.simConfig()).name().toLowerCase(Locale.ROOT),
                    from.id(), to.id(), lang.get("register.kind." + kind.name().toLowerCase(Locale.ROOT))));
            return;
        }
        show(src, p, RrpMod.simConfig());
    }

    /** Outcome of an emissary, for the command and the tests. */
    public enum Result { ACCEPTED, REFUSED, INVALID, NOT_RULER, UNKNOWN_VILLAGE }

    /** The ruler's emissary goes from {@code from} to {@code to}; the target answers and the answer is applied. */
    public static Result propose(ServerPlayer p, VillageSite from, VillageSite to, Kind kind, SimConfig cfg) {
        if (from.owner() == null || !from.owner().equals(p.getUUID().toString())) {
            return Result.NOT_RULER;
        }
        if (from == to || !from.worldName().equals(to.worldName())) {
            return Result.UNKNOWN_VILLAGE;
        }
        State state = DiplomacyService.state(from.id(), to.id());
        double index = RelationService.index(from, to, cfg);
        boolean shortage = to.state().stock(Resource.FOOD) < cfg.get(SimConfig.Key.DIPLOMACY_CRITICAL_FOOD);
        Answer answer = Proposals.answer(kind, state, index, shortage, DiplomacyService.rules(cfg));
        if (answer != Answer.ACCEPTED) {
            return answer == Answer.REFUSED ? Result.REFUSED : Result.INVALID;
        }
        switch (kind) {
            case TRADE -> DiplomacyService.conclude(from, to, State.TRADE_TREATY);
            case ALLIANCE -> DiplomacyService.conclude(from, to, State.DEFENSIVE_ALLIANCE);
            case CEASEFIRE -> {
                DiplomacyService.conclude(from, to, State.PEACE);
                RelationService.change(from, to, cfg.get(SimConfig.Key.WAR_PEACE_GAIN), cfg);
            }
            default -> {
                if (!DiplomacyService.ultimatum(from, to, cfg)) {
                    return Result.INVALID;
                }
            }
        }
        RrpMod.registry().markDirty();
        return Result.ACCEPTED;
    }

    /** Lines of the register for a player, buttons included when they rule a village. */
    public static List<Component> lines(ServerPlayer p, SimConfig cfg) {
        Lang lang = RrpMod.lang();
        String world = Worlds.name(p.serverLevel());
        List<VillageSite> sites = new ArrayList<>(RrpMod.registry().all());
        sites.removeIf(s -> !s.worldName().equals(world));
        sites.sort((a, b) -> a.id().compareTo(b.id()));
        List<VillageSite> own = new ArrayList<>(SovereignService.ownedBy(p.getUUID()));
        own.removeIf(s -> !s.worldName().equals(world));
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(lang.msg(own.isEmpty() ? "register.header.read" : "register.header.ruler", sites.size())));
        if (own.isEmpty()) {
            for (int i = 0; i < sites.size(); i++) {
                for (int j = i + 1; j < sites.size(); j++) {
                    VillageSite a = sites.get(i);
                    VillageSite b = sites.get(j);
                    State st = DiplomacyService.state(a.id(), b.id());
                    if (st != State.PEACE) {
                        out.add(Component.literal(lang.get("register.pair", a.id(), b.id(), stateName(st), fmt(RelationService.index(a, b, cfg)))));
                    }
                }
            }
        } else {
            for (VillageSite mine : own) {
                out.add(Component.literal(lang.get("register.own", mine.id())));
                for (VillageSite o : sites) {
                    if (o != mine) {
                        out.add(row(mine, o, cfg));
                    }
                }
            }
        }
        for (DiplomacyService.Pending pe : DiplomacyService.pending()) {
            out.add(Component.literal(lang.get("register.pending", pe.aggressor(), pe.defender(), Math.round(pe.remaining()))));
        }
        if (out.size() > MAX_LINES) {
            int hidden = out.size() - MAX_LINES;
            out = new ArrayList<>(out.subList(0, MAX_LINES));
            out.add(Component.literal(lang.get("register.more", hidden)));
        }
        return out;
    }

    private static Component row(VillageSite mine, VillageSite o, SimConfig cfg) {
        Lang lang = RrpMod.lang();
        State st = DiplomacyService.state(mine.id(), o.id());
        MutableComponent c = Component.literal(lang.get("register.row", o.id(), fmt(RelationService.index(mine, o, cfg)), stateName(st)));
        for (Kind k : Kind.values()) {
            boolean useful = switch (k) {
                case TRADE -> st == State.PEACE;
                case ALLIANCE -> st == State.PEACE || st == State.TRADE_TREATY;
                case CEASEFIRE -> st == State.WAR;
                case ULTIMATUM -> st != State.WAR;
            };
            if (useful) {
                String cmd = "/rrp register propose " + k.name().toLowerCase(Locale.ROOT) + " " + mine.id() + " " + o.id();
                c.append(Component.literal(" [" + lang.get("register.kind." + k.name().toLowerCase(Locale.ROOT)) + "]")
                        .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                                .withColor(k == Kind.ULTIMATUM ? 0xFF5555 : 0x55FF55)));
            }
        }
        return c;
    }

    private static String stateName(State s) {
        return RrpMod.lang().get("register.state." + s.name().toLowerCase(Locale.ROOT));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%+.0f", v);
    }

    private static void show(CommandSourceStack src, ServerPlayer p, SimConfig cfg) {
        for (Component c : lines(p, cfg)) {
            src.sendSuccess(() -> c, false);
        }
    }
}
