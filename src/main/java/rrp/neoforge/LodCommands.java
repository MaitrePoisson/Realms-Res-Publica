package rrp.neoforge;

import net.minecraft.commands.CommandSourceStack;
import rrp.core.abstractsim.Lod;

import java.util.Locale;

/** {@code rrp lod}: lists the level of detail of every village; {@code rrp lod force <village> active|abstract|auto}. */
final class LodCommands {

    private LodCommands() {
    }

    static void install() {
        Commands.register("lod", LodCommands::command);
    }

    private static void command(CommandSourceStack src, String[] args) {
        Lang lang = RrpMod.lang();
        if (args.length >= 4 && "force".equalsIgnoreCase(args[1])) {
            VillageSite site = RrpMod.registry().get(args[2]);
            if (site == null) {
                Commands.reply(src, lang.msg("lod.unknown", args[2]));
                return;
            }
            String mode = args[3].toLowerCase(Locale.ROOT);
            switch (mode) {
                case "active" -> site.setLodForced(Lod.ACTIVE);
                case "abstract" -> site.setLodForced(Lod.ABSTRACT);
                case "auto" -> site.setLodForced(null);
                default -> {
                    Commands.reply(src, lang.msg("lod.usage"));
                    return;
                }
            }
            RrpMod.activity().evaluate(site, 0);
            Commands.reply(src, lang.msg("lod.forced", site.id(), mode, site.lod().name()));
            return;
        }
        int abstractCount = 0;
        for (VillageSite s : RrpMod.registry().all()) {
            abstractCount += s.isAbstract() ? 1 : 0;
            Commands.reply(src, lang.msg("lod.line", s.id(), s.lod().name(), s.lodForced() == null ? "auto" : "forced"));
        }
        Commands.reply(src, lang.msg("lod.summary", RrpMod.registry().size(), abstractCount));
    }
}
