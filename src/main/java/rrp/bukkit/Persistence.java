package rrp.bukkit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import rrp.core.CrisisLevel;
import rrp.core.Faction;
import rrp.core.Resource;
import rrp.core.VillageState;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * Lecture et écriture de `villages.json`. Les changements sont signalés au registre (tampon) et
 * écrits en groupe : périodiquement (`persistence_flush_s`) et à l'arrêt du plugin.
 */
public final class Persistence {

    public static final String FILE = "villages.json";

    private final VillageRegistry registry;
    private final File file;
    private final Logger log;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private long ticksSinceFlush;
    private int writes;

    public Persistence(VillageRegistry registry, File dataFolder, Logger log) {
        this.registry = registry;
        this.file = new File(dataFolder, FILE);
        this.log = log;
    }

    public File file() {
        return file;
    }

    /** Nombre d'écritures effectuées (utile aux tests : les écritures sont groupées). */
    public int writes() {
        return writes;
    }

    /** Appelé à chaque avance du temps du plugin : écrit si le registre a changé et que l'intervalle est écoulé. */
    public void onTicks(long ticks, double flushSeconds) {
        ticksSinceFlush += ticks;
        if (ticksSinceFlush >= flushSeconds * 20 && registry.isDirty()) {
            flush();
        }
    }

    /** Écrit immédiatement (arrêt du plugin, tests). Renvoie faux en cas d'erreur d'entrée-sortie. */
    public boolean flush() {
        ticksSinceFlush = 0;
        registry.consumeDirty();
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray arr = new JsonArray();
        for (VillageSite s : registry.all()) {
            arr.add(toJson(s));
        }
        root.add("villages", arr);
        try {
            file.getParentFile().mkdirs();
            File tmp = new File(file.getParentFile(), FILE + ".tmp");
            Files.writeString(tmp.toPath(), gson.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            writes++;
            return true;
        } catch (IOException e) {
            log.severe("écriture de " + file + " impossible : " + e.getMessage());
            registry.markDirty();
            return false;
        }
    }

    /** Recharge le fichier dans le registre (le remplace). Renvoie le nombre de villages lus. */
    public int load() {
        registry.clear();
        registry.consumeDirty();
        if (!file.isFile()) {
            return 0;
        }
        int n = 0;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("villages")) {
                registry.restore(fromJson(e.getAsJsonObject()));
                n++;
            }
        } catch (IOException | RuntimeException e) {
            log.severe("lecture de " + file + " impossible : " + e);
        }
        registry.consumeDirty();
        return n;
    }

    private static JsonObject toJson(VillageSite s) {
        VillageState st = s.state();
        JsonObject o = new JsonObject();
        o.addProperty("id", st.id());
        o.addProperty("faction", st.faction().name());
        if (st.parentId() != null) {
            o.addProperty("parentId", st.parentId());
        }
        o.addProperty("world", s.worldName());
        o.addProperty("x", s.x());
        o.addProperty("y", s.y());
        o.addProperty("z", s.z());
        o.addProperty("radius", s.radius());
        if (s.hasWarehouse()) {
            int[] w = s.warehouse();
            JsonArray a = new JsonArray();
            a.add(w[0]);
            a.add(w[1]);
            a.add(w[2]);
            o.add("warehouse", a);
        }
        o.addProperty("beds", st.beds());
        o.addProperty("level", st.level().name());
        o.addProperty("relations", st.relations());
        o.addProperty("contestTimer", st.contestTimer());
        o.addProperty("exileTimer", st.exileTimer());
        o.addProperty("warTimer", st.warTimer());
        o.addProperty("birthTimer", st.birthTimer());
        JsonObject stock = new JsonObject();
        for (Resource r : Resource.values()) {
            stock.addProperty(r.name(), st.stock(r));
        }
        o.add("stock", stock);
        return o;
    }

    private static VillageSite fromJson(JsonObject o) {
        VillageState st = new VillageState(o.get("id").getAsString(), Faction.valueOf(o.get("faction").getAsString()));
        if (o.has("parentId")) {
            st.setParentId(o.get("parentId").getAsString());
        }
        st.setBeds(o.get("beds").getAsInt());
        st.setLevel(CrisisLevel.valueOf(o.get("level").getAsString()));
        st.setRelations(o.get("relations").getAsDouble());
        st.setContestTimer(o.get("contestTimer").getAsDouble());
        st.setExileTimer(o.get("exileTimer").getAsDouble());
        st.setWarTimer(o.get("warTimer").getAsDouble());
        st.setBirthTimer(o.get("birthTimer").getAsDouble());
        JsonObject stock = o.getAsJsonObject("stock");
        for (Resource r : Resource.values()) {
            if (stock.has(r.name())) {
                st.setStock(r, stock.get(r.name()).getAsInt());
            }
        }
        VillageSite s = new VillageSite(st, o.get("world").getAsString(), o.get("x").getAsDouble(),
                o.get("y").getAsDouble(), o.get("z").getAsDouble(), o.get("radius").getAsInt());
        if (o.has("warehouse")) {
            JsonArray a = o.getAsJsonArray("warehouse");
            s.setWarehouse(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
        }
        return s;
    }
}
