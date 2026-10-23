package rrp.bukkit;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Commande `/respublica` : sous-commandes de debug et de test. */
public final class Commands implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("help", "selftest", "tick");

    private final TickService ticks;
    private final Selftest selftest;
    private final Lang lang;

    public Commands(TickService ticks, Selftest selftest, Lang lang) {
        this.ticks = ticks;
        this.selftest = selftest;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("respublica.admin")) {
            sender.sendMessage(lang.msg("error.no_permission"));
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(sender);
            case "selftest" -> selftest.runAll(sender);
            case "tick" -> tick(sender, args);
            default -> sender.sendMessage(lang.msg("error.unknown", sub));
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(lang.msg("help.header"));
        for (String s : SUBS) {
            sender.sendMessage(lang.get("help." + s));
        }
    }

    private void tick(CommandSender sender, String[] args) {
        long n;
        try {
            n = args.length < 2 ? -1 : Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            n = -1;
        }
        if (n < 1 || n > 1_000_000) {
            sender.sendMessage(lang.msg("error.usage_tick"));
            return;
        }
        ticks.advance(n);
        sender.sendMessage(lang.msg("tick.done", n));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
