package rrp.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Commande {@code /respublica} (alias {@code /rrp}) : sous-commandes de debug et de test.
 * Les services (villages, comportements, crises…) enregistrent leur sous-commande avec
 * {@link #register(String, Handler)} ; tant qu'elle n'est pas enregistrée, elle répond « pas encore disponible ».
 */
@EventBusSubscriber(modid = RrpMod.MOD_ID)
public final class Commands {

    /** Niveau de permission vanilla requis (opérateur). */
    public static final int PERMISSION_LEVEL = 2;

    static final List<String> SUBS = List.of("help", "tick", "village", "warehouse", "townhall", "status", "territory", "caravan", "inspect", "setrole",
            "famine", "overpopulate", "insecurity", "reload");

    /** Corps d'une sous-commande : {@code args[0]} est le nom de la sous-commande. */
    @FunctionalInterface
    public interface Handler {
        void run(CommandSourceStack source, String[] args);
    }

    private static final Map<String, Handler> HANDLERS = new ConcurrentHashMap<>();

    private Commands() {
    }

    public static void register(String sub, Handler handler) {
        HANDLERS.put(sub, handler);
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        registerOn(event.getDispatcher());
    }

    /** Enregistre {@code /respublica} et {@code /rrp} (utilisable aussi par les tests). */
    public static void registerOn(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String root : new String[] {"respublica", "rrp"}) {
            LiteralArgumentBuilder<CommandSourceStack> node = net.minecraft.commands.Commands.literal(root)
                    .requires(s -> s.hasPermission(PERMISSION_LEVEL))
                    .executes(c -> run(c.getSource(), new String[0]))
                    .then(net.minecraft.commands.Commands.argument("args", StringArgumentType.greedyString())
                            .suggests((c, b) -> {
                                String typed = b.getRemaining().toLowerCase(Locale.ROOT);
                                if (typed.indexOf(' ') < 0) {
                                    SUBS.stream().filter(s -> s.startsWith(typed)).forEach(b::suggest);
                                }
                                return b.buildFuture();
                            })
                            .executes(c -> run(c.getSource(), StringArgumentType.getString(c, "args").trim().split("\\s+"))));
            dispatcher.register(node);
        }
    }

    static int run(CommandSourceStack source, String[] args) {
        onCommand(source, args);
        return 1;
    }

    static void onCommand(CommandSourceStack source, String[] args) {
        Lang lang = RrpMod.lang();
        String sub = args.length == 0 || args[0].isEmpty() ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(source);
            case "tick", "village", "warehouse", "townhall", "status", "territory", "caravan", "inspect", "setrole", "famine", "overpopulate", "insecurity" -> {
                Handler h = HANDLERS.get(sub);
                if (h == null) {
                    reply(source, lang.msg("error.not_ready", sub));
                } else {
                    String[] a = args.clone();
                    a[0] = sub;
                    h.run(source, a);
                }
            }
            case "reload" -> reload(source);
            default -> reply(source, lang.msg("error.unknown", sub));
        }
    }

    private static void help(CommandSourceStack source) {
        Lang lang = RrpMod.lang();
        reply(source, lang.msg("help.header"));
        for (String s : SUBS) {
            reply(source, lang.get("help." + s));
        }
    }

    private static void reload(CommandSourceStack source) {
        Lang lang = RrpMod.lang();
        RrpMod.reloadConfig();
        reply(source, lang.msg("reload.done"));
    }

    /** Envoie un texte à l'émetteur (console ou joueur). */
    public static void reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}
