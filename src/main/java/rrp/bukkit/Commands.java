package rrp.bukkit;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.VillagerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Commande `/respublica` : sous-commandes de debug et de test. */
public final class Commands implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("help", "selftest", "tick", "village", "warehouse", "status", "inspect", "setrole",
            "famine", "overpopulate", "insecurity", "reload");

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
            case "inspect" -> inspect(sender, args);
            case "setrole" -> setRole(sender, args);
            case "famine" -> famine(sender, args);
            case "overpopulate" -> overpopulate(sender, args);
            case "insecurity" -> insecurity(sender, args);
            case "reload" -> reload(sender);
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

    /** Villageois désigné : UUID donné en argument, sinon celui que vise le joueur, sinon le plus proche. */
    private Villager targetVillager(CommandSender sender, String uuidArg) {
        if (uuidArg != null) {
            try {
                Entity e = org.bukkit.Bukkit.getEntity(UUID.fromString(uuidArg));
                return e instanceof Villager v ? v : null;
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        if (!(sender instanceof Player p)) {
            return null;
        }
        var hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 8, 0.5,
                e -> e instanceof Villager);
        if (hit != null && hit.getHitEntity() instanceof Villager v) {
            return v;
        }
        Villager best = null;
        double bestD = 36;
        for (Entity e : p.getNearbyEntities(6, 6, 6)) {
            if (e instanceof Villager v && v.getLocation().distanceSquared(p.getLocation()) < bestD) {
                best = v;
                bestD = v.getLocation().distanceSquared(p.getLocation());
            }
        }
        return best;
    }

    private void inspect(CommandSender sender, String[] args) {
        Villager v = targetVillager(sender, args.length >= 2 ? args[1] : null);
        if (v == null) {
            sender.sendMessage(lang.msg("inspect.none"));
            return;
        }
        VillagerState s = plugin.adapter().stateOf(v);
        if (s == null) {
            sender.sendMessage(lang.msg("inspect.unadopted", v.getUniqueId()));
            return;
        }
        sender.sendMessage(lang.msg("inspect.line", s.role().label(), Math.round(s.satiety()), Math.round(s.morale()),
                Math.round(s.loyalty())));
    }

    /** Minuscules sans accents, pour comparer « Bâtisseur » et « batisseur ». */
    private static String plain(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private void setRole(CommandSender sender, String[] args) {
        Role role = null;
        if (args.length >= 2) {
            for (Role r : Role.values()) {
                if (r.name().equalsIgnoreCase(args[1]) || plain(r.label()).equals(plain(args[1]))) {
                    role = r;
                }
            }
        }
        if (role == null) {
            sender.sendMessage(lang.msg("error.usage_setrole"));
            return;
        }
        Villager v = targetVillager(sender, args.length >= 3 ? args[2] : null);
        if (v == null) {
            sender.sendMessage(lang.msg("inspect.none"));
            return;
        }
        plugin.adapter().setRole(v, role);
        sender.sendMessage(lang.msg("setrole.done", role.label()));
    }

    /** Village désigné par l'argument {@code index} (identifiant) ou, à défaut, celui où se trouve le joueur. */
    private VillageSite siteArg(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            return plugin.registry().get(args[index]);
        }
        return sender instanceof Player p ? plugin.registry().find(p.getLocation()) : null;
    }

    /** Vide réellement le stock de nourriture du tonneau-entrepôt : la famine suit par les voies normales. */
    private void famine(CommandSender sender, String[] args) {
        VillageSite site = siteArg(sender, args, 1);
        if (site == null) {
            sender.sendMessage(lang.msg("error.usage_crisis", "famine"));
            return;
        }
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(site.worldName());
        if (!site.hasWarehouse() || world == null) {
            sender.sendMessage(lang.msg("debug.no_warehouse", site.id()));
            return;
        }
        int removed = plugin.warehouse().withdraw(site, world, Resource.FOOD, Integer.MAX_VALUE);
        plugin.warehouse().refresh(site, world);
        sender.sendMessage(lang.msg("debug.famine", site.id(), removed));
    }

    /** Fait apparaître de vrais villageois adultes jusqu'à dépasser lits × ratio. */
    private void overpopulate(CommandSender sender, String[] args) {
        VillageSite site = siteArg(sender, args, 1);
        org.bukkit.World world = site == null ? null : org.bukkit.Bukkit.getWorld(site.worldName());
        if (world == null) {
            sender.sendMessage(lang.msg("error.usage_crisis", "overpopulate"));
            return;
        }
        double limit = site.state().beds() * plugin.simConfig().get(rrp.core.SimConfig.Key.OVERPOPULATION_RATIO);
        int missing = (int) Math.floor(limit) + 1 - site.state().population();
        int n = Math.max(1, Math.min(missing, 40));
        org.bukkit.Location at = site.center(world);
        for (int i = 0; i < n; i++) {
            world.spawnEntity(at.clone().add((i % 5) - 2, 1, (i / 5) % 5 - 2), org.bukkit.entity.EntityType.VILLAGER);
        }
        plugin.adapter().sync(site, world);
        plugin.scanner().refreshNow(site);
        sender.sendMessage(lang.msg("debug.overpopulate", site.id(), n, site.state().population(), site.state().beds()));
    }

    /** Fait apparaître des monstres persistants (qui ne brûlent pas) en nombre supérieur à ce que les gardes couvrent. */
    private void insecurity(CommandSender sender, String[] args) {
        VillageSite site = siteArg(sender, args, 1);
        org.bukkit.World world = site == null ? null : org.bukkit.Bukkit.getWorld(site.worldName());
        if (world == null) {
            sender.sendMessage(lang.msg("error.usage_crisis", "insecurity"));
            return;
        }
        double covered = site.state().count(Role.GUARD)
                * plugin.simConfig().get(rrp.core.SimConfig.Key.INSECURITY_THREATS_PER_GUARD);
        int n = Math.max(1, Math.min((int) Math.floor(covered) + 1 - site.state().threats(), 20));
        org.bukkit.Location at = site.center(world);
        double reach = Math.max(2, site.radius() / 2.0);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            int x = (int) Math.round(at.getX() + reach * Math.cos(a));
            int z = (int) Math.round(at.getZ() + reach * Math.sin(a));
            org.bukkit.Location l = new org.bukkit.Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
            org.bukkit.entity.Zombie z0 = world.spawn(l, org.bukkit.entity.Zombie.class);
            z0.setShouldBurnInDay(false);
            z0.setPersistent(true);
            z0.setRemoveWhenFarAway(false);
        }
        site.state().setThreats(plugin.gauges().countThreats(site, world));
        sender.sendMessage(lang.msg("debug.insecurity", site.id(), n, site.state().threats()));
    }

    private void reload(CommandSender sender) {
        plugin.loadSimConfig();
        sender.sendMessage(lang.msg("reload.done"));
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
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setrole")) {
            for (Role r : Role.values()) {
                out.add(r.name().toLowerCase(Locale.ROOT));
            }
        } else if (args.length == 2 && List.of("status", "famine", "overpopulate", "insecurity").contains(args[0].toLowerCase(Locale.ROOT))) {
            plugin.registry().all().forEach(v -> out.add(v.id()));
        }
        return out;
    }
}
