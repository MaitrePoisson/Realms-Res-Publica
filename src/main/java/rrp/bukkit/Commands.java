package rrp.bukkit;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import rrp.core.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Commande `/respublica` : sous-commandes de debug et de test. */
public final class Commands implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("help", "selftest", "tick", "village", "warehouse", "status");

    private final RrpPlugin plugin;
    private final TickService ticks;
    private final Selftest selftest;
    private final Lang lang;

    public Commands(RrpPlugin plugin) {
        this.plugin = plugin;
        this.ticks = plugin.ticks();
        this.selftest = plugin.selftest();
        this.lang = plugin.lang();
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
            case "village" -> village(sender, args);
            case "warehouse" -> warehouse(sender, args);
            case "status" -> status(sender, args);
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

    private void village(CommandSender sender, String[] args) {
        String action = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                if (plugin.registry().size() == 0) {
                    sender.sendMessage(lang.msg("village.none"));
                }
                for (VillageSite s : plugin.registry().all()) {
                    sender.sendMessage(lang.msg("village.line", s.id(), s.worldName(), Math.round(s.x()),
                            Math.round(s.z()), s.radius(), s.state().population(), s.state().beds()));
                }
            }
            case "scan" -> {
                plugin.scanner().enqueueAll();
                plugin.ticks().advance(1);
                sender.sendMessage(lang.msg("village.scan"));
            }
            case "flush" -> sender.sendMessage(lang.msg(plugin.persistence().flush() ? "village.flushed" : "village.flush_failed"));
            default -> sender.sendMessage(lang.msg("error.usage_village"));
        }
    }

    private void warehouse(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("set")) {
            sender.sendMessage(lang.msg("error.usage_warehouse"));
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(lang.msg("error.player_only"));
            return;
        }
        Block target = p.getTargetBlockExact(6);
        if (target == null || target.getType() != org.bukkit.Material.BARREL) {
            sender.sendMessage(lang.msg("warehouse.not_barrel"));
            return;
        }
        VillageSite site = plugin.registry().find(p.getLocation());
        if (site == null) {
            sender.sendMessage(lang.msg("warehouse.no_village"));
            return;
        }
        plugin.warehouse().set(site, target);
        plugin.warehouse().refresh(site, p.getWorld());
        sender.sendMessage(lang.msg("warehouse.set", site.id(), target.getX(), target.getY(), target.getZ()));
    }

    private void status(CommandSender sender, String[] args) {
        VillageSite site = null;
        if (args.length >= 2) {
            site = plugin.registry().get(args[1]);
        } else if (sender instanceof Player p) {
            site = plugin.registry().find(p.getLocation());
        }
        if (site == null) {
            sender.sendMessage(lang.msg("status.none"));
            return;
        }
        var st = site.state();
        sender.sendMessage(lang.msg("status.header", site.id(), st.faction(), st.level()));
        sender.sendMessage(lang.get("status.people", st.population(), st.beds(), site.radius()));
        StringBuilder stock = new StringBuilder();
        for (Resource r : Resource.values()) {
            stock.append(r.label()).append(' ').append(st.stock(r)).append("  ");
        }
        sender.sendMessage(lang.get("status.stock", site.hasWarehouse() ? stock.toString().trim() : lang.get("status.no_warehouse")));
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
        } else if (args.length == 2 && args[0].equalsIgnoreCase("village")) {
            out.addAll(List.of("list", "scan", "flush"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("warehouse")) {
            out.add("set");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("status")) {
            plugin.registry().all().forEach(v -> out.add(v.id()));
        }
        return out;
    }
}
