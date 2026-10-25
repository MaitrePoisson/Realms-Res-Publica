package rrp.neoforge;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rrp.core.player.PlayerRole;

import java.util.Locale;
import java.util.UUID;

/**
 * Operator-only test driver ({@code /rrp probe ...}): a headless server has no client to log in, so a fake player
 * runs the player-facing commands and its messages are written to the server log as {@code [probe] ...}.
 * Subcommands: {@code role <role>}, {@code purse <n>}, {@code give <item> <n>}, {@code found}, {@code map},
 * anything else is run as a normal {@code rrp} command by the probe, at the operator's position.
 */
final class ProbeDriver {

    private static final Logger LOG = LoggerFactory.getLogger("rrp-probe");
    private static final UUID ID = UUID.nameUUIDFromBytes("rrp-probe".getBytes());
    private static Probe probe;

    private ProbeDriver() {
    }

    static void install() {
        Commands.register("probe", ProbeDriver::command);
    }

    private static final class Probe extends FakePlayer {
        Probe(ServerLevel level) {
            super(level, new GameProfile(ID, "RrpProbe"));
        }

        @Override
        public void sendSystemMessage(Component message) {
            LOG.info("[probe] {}", message.getString());
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay) {
            LOG.info("[probe] {}", message.getString());
        }
    }

    private static void command(CommandSourceStack src, String[] args) {
        if (!src.hasPermission(Commands.PERMISSION_LEVEL)) {
            Commands.reply(src, RrpMod.lang().msg("error.op_only"));
            return;
        }
        ServerLevel level = src.getLevel();
        if (probe == null || probe.serverLevel() != level) {
            probe = new Probe(level);
        }
        probe.setPos(src.getPosition());
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "role" -> {
                PlayerRole to = args.length > 2 ? PlayerRole.parse(args[2]) : null;
                if (to != null) {
                    PlayerService.ledger().switchRole(ID, to, 0, 0);
                } else {
                    runAsProbe(args);
                }
            }
            case "purse" -> PlayerService.ledger().deposit(ID, Long.parseLong(args[2]));
            case "give" -> {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(args[2]));
                probe.getInventory().add(new ItemStack(item, Integer.parseInt(args[3])));
            }
            case "found" -> {
                BlockPos base = BlockPos.containing(src.getPosition());
                TownHall.build(level, base);
                SovereignService.found(probe, level, base);
            }
            case "map" -> PoliticalMap.use(probe, level);
            default -> runAsProbe(args);
        }
        RrpMod.registry().markDirty();
        LOG.info("[probe] ran: {}", String.join(" ", args));
    }

    private static void runAsProbe(String[] args) {
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        Commands.onCommand(probe.createCommandSourceStack().withSource(RrpMod.server()), rest);
    }
}
