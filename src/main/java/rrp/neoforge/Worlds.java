package rrp.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Passage entre le nom de dimension stocké dans villages.json et le {@link ServerLevel} correspondant. */
public final class Worlds {

    private Worlds() {
    }

    public static String name(ServerLevel level) {
        return level.dimension().location().toString();
    }

    /** La dimension nommée, ou {@code null} si le serveur est arrêté ou la dimension inconnue. */
    public static ServerLevel level(MinecraftServer server, String name) {
        if (server == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(name);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    public static ServerLevel overworld(MinecraftServer server) {
        return server == null ? null : server.getLevel(Level.OVERWORLD);
    }
}
