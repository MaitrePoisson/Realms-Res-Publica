package rrp.neoforge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.SimConfig;

/** Sous-commandes de débogage qui provoquent vraiment une crise : famine, surpopulation, insécurité. */
final class CrisisCommands {

    private CrisisCommands() {
    }

    static void install() {
        Commands.register("famine", CrisisCommands::famine);
        Commands.register("overpopulate", CrisisCommands::overpopulate);
        Commands.register("insecurity", CrisisCommands::insecurity);
    }

    /** Village nommé en argument, sinon celui où se trouve l'émetteur. */
    private static VillageSite siteArg(CommandSourceStack src, String[] args) {
        if (args.length > 1) {
            return RrpMod.registry().get(args[1]);
        }
        Vec3 pos = src.getPosition();
        return RrpMod.registry().find(Worlds.name(src.getLevel()), pos.x, pos.z);
    }

    /** Vide réellement le stock de nourriture du tonneau-entrepôt : la famine suit par les voies normales. */
    private static void famine(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        VillageSite site = siteArg(src, args);
        if (site == null) {
            Commands.reply(src, lang.msg("error.usage_crisis", "famine"));
            return;
        }
        ServerLevel world = Worlds.level(RrpMod.server(), site.worldName());
        if (!site.hasWarehouse() || world == null) {
            Commands.reply(src, lang.msg("debug.no_warehouse", site.id()));
            return;
        }
        int removed = RrpMod.warehouse().withdraw(site, world, Resource.FOOD, Integer.MAX_VALUE);
        RrpMod.warehouse().refresh(site, world);
        Commands.reply(src, lang.msg("debug.famine", site.id(), removed));
    }

    /** Fait apparaître de vrais villageois adultes jusqu'à dépasser lits × ratio. */
    private static void overpopulate(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        VillageSite site = siteArg(src, args);
        ServerLevel world = site == null ? null : Worlds.level(RrpMod.server(), site.worldName());
        if (world == null) {
            Commands.reply(src, lang.msg("error.usage_crisis", "overpopulate"));
            return;
        }
        double limit = site.state().beds() * RrpMod.simConfig().get(SimConfig.Key.OVERPOPULATION_RATIO);
        int missing = (int) Math.floor(limit) + 1 - site.state().population();
        int n = Math.max(1, Math.min(missing, 40));
        for (int i = 0; i < n; i++) {
            BlockPos at = BlockPos.containing(site.x() + (i % 5) - 2, site.y() + 1, site.z() + (i / 5) % 5 - 2);
            EntityType.VILLAGER.spawn(world, at, MobSpawnType.COMMAND);
        }
        RrpMod.adapter().sync(site, world);
        RrpMod.scanner().refreshNow(site);
        Commands.reply(src, lang.msg("debug.overpopulate", site.id(), n, site.state().population(), site.state().beds()));
    }

    /** Fait apparaître des monstres immobiles et persistants en nombre supérieur à ce que les gardes couvrent. */
    private static void insecurity(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        VillageSite site = siteArg(src, args);
        ServerLevel world = site == null ? null : Worlds.level(RrpMod.server(), site.worldName());
        if (world == null) {
            Commands.reply(src, lang.msg("error.usage_crisis", "insecurity"));
            return;
        }
        double covered = site.state().count(Role.GUARD)
                * RrpMod.simConfig().get(SimConfig.Key.INSECURITY_THREATS_PER_GUARD);
        int n = Math.max(1, Math.min((int) Math.floor(covered) + 1 - site.state().threats(), 20));
        double reach = Math.max(2, site.radius() / 2.0);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            int x = (int) Math.round(site.x() + reach * Math.cos(a));
            int z = (int) Math.round(site.z() + reach * Math.sin(a));
            world.getChunk(x >> 4, z >> 4);
            BlockPos at = new BlockPos(x, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            Zombie zombie = EntityType.ZOMBIE.spawn(world, at, MobSpawnType.COMMAND);
            if (zombie != null) {
                zombie.setNoAi(true); // immobile : ne brûle pas, ne tue personne, les gardes s'en chargent
                zombie.setPersistenceRequired();
            }
        }
        site.state().setThreats(RrpMod.gauges().countThreats(site, world));
        Commands.reply(src, lang.msg("debug.insecurity", site.id(), n, site.state().threats()));
    }
}
