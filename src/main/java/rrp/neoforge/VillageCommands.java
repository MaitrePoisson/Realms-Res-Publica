package rrp.neoforge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.VillageState;
import rrp.core.VillagerState;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

/** Sous-commandes de /respublica liées aux villages et aux villageois : tick, village, warehouse, status, inspect, setrole. */
final class VillageCommands {

    private VillageCommands() {
    }

    static void install() {
        Commands.register("tick", VillageCommands::tick);
        Commands.register("village", VillageCommands::village);
        Commands.register("warehouse", VillageCommands::warehouse);
        Commands.register("townhall", VillageCommands::townHall);
        Commands.register("status", VillageCommands::status);
        Commands.register("inspect", VillageCommands::inspect);
        Commands.register("setrole", VillageCommands::setRole);
    }

    private static void tick(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        long n;
        try {
            n = args.length < 2 ? -1 : Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            n = -1;
        }
        if (n < 1 || n > 1_000_000) {
            Commands.reply(src, lang.msg("error.usage_tick"));
            return;
        }
        RrpMod.ticks().advance(n);
        Commands.reply(src, lang.msg("tick.done", n));
    }

    private static void village(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        VillageRegistry registry = RrpMod.registry();
        String action = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                if (registry.size() == 0) {
                    Commands.reply(src, lang.msg("village.none"));
                }
                for (VillageSite s : registry.all()) {
                    Commands.reply(src, lang.msg("village.line", s.id(), s.worldName(), Math.round(s.x()),
                            Math.round(s.z()), s.radius(), s.state().population(), s.state().beds()));
                }
            }
            case "scan" -> {
                RrpMod.scanner().scanNow();
                Commands.reply(src, lang.msg("village.scan"));
            }
            case "flush" -> {
                RrpMod.adapter().saveAll();
                Commands.reply(src, lang.msg(RrpMod.persistence().flush() ? "village.flushed" : "village.flush_failed"));
            }
            case "act" -> {
                ServerLevel level = src.getLevel();
                Vec3 pos = src.getPosition();
                VillageSite site = RrpMod.registry().find(Worlds.name(level), pos.x, pos.z);
                if (site == null) {
                    Commands.reply(src, lang.msg("warehouse.no_village"));
                    return;
                }
                int done = 0;
                for (Villager v : RrpMod.adapter().present(site, level)) {
                    if (RrpMod.behaviors().runOnce(site, level, v)) {
                        done++;
                    }
                }
                Commands.reply(src, lang.msg("village.acted", site.id(), done));
            }
            default -> Commands.reply(src, lang.msg("error.usage_village"));
        }
    }

    /** {@code warehouse set} vise un tonneau (joueur) ; {@code warehouse set x y z} le désigne par coordonnées ; la console prend le tonneau le plus proche. */
    private static void warehouse(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        if (args.length < 2 || !args[1].equalsIgnoreCase("set")) {
            Commands.reply(src, lang.msg("error.usage_warehouse"));
            return;
        }
        ServerLevel level = src.getLevel();
        BlockPos target;
        if (args.length >= 5) {
            try {
                target = new BlockPos(Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
            } catch (NumberFormatException e) {
                Commands.reply(src, lang.msg("error.usage_warehouse"));
                return;
            }
        } else {
            ServerPlayer p = src.getPlayer();
            if (p == null) {
                target = nearestBarrel(level, src.getPosition());
            } else {
                HitResult hit = p.pick(6, 0, false);
                target = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK ? b.getBlockPos() : null;
            }
        }
        if (target == null || !level.getBlockState(target).is(net.minecraft.world.level.block.Blocks.BARREL)) {
            Commands.reply(src, lang.msg("warehouse.not_barrel"));
            return;
        }
        Vec3 pos = src.getPosition();
        VillageSite site = RrpMod.registry().find(Worlds.name(level), pos.x, pos.z);
        if (site == null) {
            site = RrpMod.registry().find(Worlds.name(level), target.getX(), target.getZ());
        }
        if (site == null) {
            Commands.reply(src, lang.msg("warehouse.no_village"));
            return;
        }
        RrpMod.warehouse().set(site, level, target);
        RrpMod.warehouse().refresh(site, level);
        Commands.reply(src, lang.msg("warehouse.set", site.id(), target.getX(), target.getY(), target.getZ()));
    }

    /** {@code townhall info|set [x y z]|build} : consulte, désigne (bloc d'or) ou fait poser l'Hôtel de Ville du village courant. */
    private static void townHall(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        String action = args.length < 2 ? "info" : args[1].toLowerCase(Locale.ROOT);
        ServerLevel level = src.getLevel();
        Vec3 pos = src.getPosition();
        VillageSite site = RrpMod.registry().find(Worlds.name(level), pos.x, pos.z);
        if (site == null) {
            Commands.reply(src, lang.msg("warehouse.no_village"));
            return;
        }
        int core = (int) RrpMod.simConfig().get(rrp.core.SimConfig.Key.TOWN_HALL_RADIUS);
        switch (action) {
            case "info" -> {
                if (!site.hasHall()) {
                    Commands.reply(src, lang.msg("townhall.none", site.id()));
                } else {
                    int[] h = site.hall();
                    Commands.reply(src, lang.msg("townhall.info", site.id(), h[0], h[1], h[2], core));
                }
            }
            case "set" -> {
                BlockPos base;
                try {
                    base = args.length >= 5
                            ? new BlockPos(Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]))
                            : BlockPos.containing(pos).below();
                } catch (NumberFormatException e) {
                    Commands.reply(src, lang.msg("error.usage_townhall"));
                    return;
                }
                if (!TownHall.isHall(level, base)) {
                    Commands.reply(src, lang.msg("townhall.invalid"));
                    return;
                }
                site.setHall(base.getX(), base.getY(), base.getZ());
                RrpMod.warehouse().ensureForHall(site, level);
                RrpMod.registry().markDirty();
                Commands.reply(src, lang.msg("townhall.set", site.id(), base.getX(), base.getY(), base.getZ()));
            }
            case "build" -> {
                BlockPos base = TownHall.findSpot(level, site);
                if (base == null) {
                    Commands.reply(src, lang.msg("townhall.no_spot"));
                    return;
                }
                TownHall.build(level, base);
                site.setHall(base.getX(), base.getY(), base.getZ());
                RrpMod.warehouse().ensureForHall(site, level);
                RrpMod.registry().markDirty();
                Commands.reply(src, lang.msg("townhall.built", site.id(), base.getX(), base.getY(), base.getZ()));
            }
            default -> Commands.reply(src, lang.msg("error.usage_townhall"));
        }
    }

    /** Pour la console : le tonneau le plus proche (6 blocs) de la position de la commande, ou {@code null}. */
    private static BlockPos nearestBarrel(ServerLevel level, Vec3 from) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos origin = BlockPos.containing(from);
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-6, -6, -6), origin.offset(6, 6, 6))) {
            if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.BARREL) && p.distToCenterSqr(from) < bestD) {
                best = p.immutable();
                bestD = p.distToCenterSqr(from);
            }
        }
        return best;
    }

    private static void status(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        VillageSite site;
        if (args.length >= 2) {
            site = RrpMod.registry().get(args[1]);
        } else {
            Vec3 pos = src.getPosition();
            site = RrpMod.registry().find(Worlds.name(src.getLevel()), pos.x, pos.z);
        }
        if (site == null) {
            Commands.reply(src, lang.msg("status.none"));
            return;
        }
        VillageState st = site.state();
        Commands.reply(src, lang.msg("status.header", site.id(), st.faction().label(), st.level().label()));
        Commands.reply(src, lang.get("status.people", st.population(), st.beds(), site.radius()));
        double satiety = st.villagers().stream().mapToDouble(VillagerState::satiety).average().orElse(0);
        Commands.reply(src, lang.get("status.gauges", Math.round(st.averageMorale()), Math.round(satiety),
                Math.round(st.averageLoyalty()), st.threats(), Math.round(st.relations())));
        Commands.reply(src, lang.get("status.timers", Math.round(st.contestTimer()), Math.round(st.exileTimer()),
                Math.round(st.warTimer())));
        StringBuilder stock = new StringBuilder();
        for (Resource r : Resource.values()) {
            stock.append(r.label()).append(' ').append(st.stock(r)).append("  ");
        }
        Commands.reply(src, lang.get("status.stock", site.hasWarehouse() ? stock.toString().trim() : lang.get("status.no_warehouse")));
        if (site.hasHall()) {
            Commands.reply(src, lang.msg("status.orders", site.orders().isEmpty() ? "aucun" : site.orders().stream()
                    .map(b -> b.type() + "@" + b.x() + "," + b.z()).collect(java.util.stream.Collectors.joining(" "))));
            var def = site.defense();
            Commands.reply(src, lang.msg("status.defense", def.stage(), def.laid(), def.upgraded(), def.length(), def.towers().size()));
            Commands.reply(src, lang.msg("status.chests", RrpMod.warehouse().communityChests(site, Worlds.level(RrpMod.server(), site.worldName()))));
        }
    }

    /** Villageois désigné : UUID donné en argument, sinon celui que vise le joueur, sinon le plus proche (6 blocs). */
    private static Villager targetVillager(CommandSourceStack src, String uuidArg) {
        ServerLevel level = src.getLevel();
        if (uuidArg != null) {
            try {
                return level.getEntity(UUID.fromString(uuidArg)) instanceof Villager v ? v : null;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        Vec3 from = src.getPosition();
        Villager best = null;
        double bestD = 36;
        for (Villager v : level.getEntitiesOfClass(Villager.class,
                new net.minecraft.world.phys.AABB(from.x - 6, from.y - 6, from.z - 6, from.x + 6, from.y + 6, from.z + 6))) {
            double d = v.position().distanceToSqr(from);
            if (d < bestD) {
                best = v;
                bestD = d;
            }
        }
        return best;
    }

    /** Minuscules sans accents, pour comparer « Bâtisseur » et « batisseur ». */
    private static String plain(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static void inspect(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        Villager v = targetVillager(src, args.length >= 2 ? args[1] : null);
        if (v == null) {
            Commands.reply(src, lang.msg("inspect.none"));
            return;
        }
        VillagerState s = RrpMod.adapter().stateOf(v);
        if (s == null) {
            Commands.reply(src, lang.msg("inspect.unadopted", v.getUUID()));
            return;
        }
        Commands.reply(src, lang.msg("inspect.line", s.role().label(), Math.round(s.satiety()), Math.round(s.morale()),
                Math.round(s.loyalty())));
    }

    private static void setRole(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        Role role = null;
        if (args.length >= 2) {
            for (Role r : Role.values()) {
                if (r.name().equalsIgnoreCase(args[1]) || plain(r.label()).equals(plain(args[1]))) {
                    role = r;
                }
            }
        }
        if (role == null) {
            Commands.reply(src, lang.msg("error.usage_setrole"));
            return;
        }
        Villager v = targetVillager(src, args.length >= 3 ? args[2] : null);
        if (v == null) {
            Commands.reply(src, lang.msg("inspect.none"));
            return;
        }
        RrpMod.adapter().setRole(v, role);
        Commands.reply(src, lang.msg("setrole.done", role.label()));
    }
}
