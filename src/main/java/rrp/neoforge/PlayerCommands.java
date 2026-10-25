package rrp.neoforge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import rrp.core.SimConfig;
import rrp.core.player.PlayerLedger;
import rrp.core.player.PlayerRole;

import java.util.Locale;
import java.util.UUID;

/**
 * Player roles and purse ({@code /rrp role}). Every player is an OBSERVER by default: the mod never targets
 * them. Roles are switched freely (cooldown from config) and persisted in villages.json.
 */
public final class PlayerCommands {

    private static final PlayerLedger LEDGER = PlayerService.ledger();

    private PlayerCommands() {
    }

    static void install() {
        Commands.register("role", PlayerCommands::command);
    }

    private static double nowSec() {
        return RrpMod.server().overworld().getGameTime() / 20.0;
    }

    private static void command(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            Commands.reply(src, lang.msg("role.players_only"));
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "show";
        double cooldown = RrpMod.simConfig().get(SimConfig.Key.ROLE_SWITCH_COOLDOWN_S);
        switch (action) {
            case "show" -> Commands.reply(src, lang.msg("role.show", lang.get("role.name." + LEDGER.role(p.getUUID()).name().toLowerCase(Locale.ROOT)),
                    LEDGER.purse(p.getUUID())));
            case "set" -> {
                PlayerRole to = args.length > 2 ? PlayerRole.parse(args[2]) : null;
                if (to == null) {
                    Commands.reply(src, lang.msg("role.usage"));
                    return;
                }
                switch (LEDGER.switchRole(p.getUUID(), to, nowSec(), cooldown)) {
                    case OK -> Commands.reply(src, lang.msg("role.switched", lang.get("role.name." + to.name().toLowerCase(Locale.ROOT))));
                    case SAME -> Commands.reply(src, lang.msg("role.same"));
                    case COOLDOWN -> Commands.reply(src, lang.msg("role.cooldown", Math.round(LEDGER.cooldownLeft(p.getUUID(), nowSec(), cooldown))));
                }
                RrpMod.registry().markDirty();
            }
            case "purse" -> purse(src, p, args);
            default -> Commands.reply(src, lang.msg("role.usage"));
        }
    }

    /** {@code purse [deposit|withdraw <n>]}: emeralds from/to the inventory. */
    private static void purse(CommandSourceStack src, ServerPlayer p, String[] args) {
        Lang lang = RrpMod.lang();
        if (args.length < 4) {
            Commands.reply(src, lang.msg("role.show", lang.get("role.name." + LEDGER.role(p.getUUID()).name().toLowerCase(Locale.ROOT)),
                    LEDGER.purse(p.getUUID())));
            return;
        }
        long n;
        try {
            n = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            n = 0;
        }
        if (n <= 0 || n > 100000) {
            Commands.reply(src, lang.msg("role.usage"));
            return;
        }
        if ("deposit".equalsIgnoreCase(args[2])) {
            if (countEmeralds(p) < n) {
                Commands.reply(src, lang.msg("role.no_emeralds", n));
                return;
            }
            takeEmeralds(p, (int) n);
            LEDGER.deposit(p.getUUID(), n);
        } else if ("withdraw".equalsIgnoreCase(args[2])) {
            if (!LEDGER.withdraw(p.getUUID(), n)) {
                Commands.reply(src, lang.msg("role.no_funds", LEDGER.purse(p.getUUID())));
                return;
            }
            giveEmeralds(p, (int) n);
        } else {
            Commands.reply(src, lang.msg("role.usage"));
            return;
        }
        RrpMod.registry().markDirty();
        Commands.reply(src, lang.msg("role.purse", LEDGER.purse(p.getUUID())));
    }

    static int countEmeralds(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(Items.EMERALD)) {
                n += s.getCount();
            }
        }
        return n;
    }

    static void takeEmeralds(Player p, int n) {
        int left = n;
        for (ItemStack s : p.getInventory().items) {
            if (left > 0 && s.is(Items.EMERALD)) {
                int t = Math.min(left, s.getCount());
                s.shrink(t);
                left -= t;
            }
        }
    }

    static void giveEmeralds(Player p, int n) {
        int left = n;
        while (left > 0) {
            int t = Math.min(left, 64);
            ItemStack stack = new ItemStack(Items.EMERALD, t);
            if (!p.getInventory().add(stack)) {
                p.drop(stack, false);
            }
            left -= t;
        }
    }
}
