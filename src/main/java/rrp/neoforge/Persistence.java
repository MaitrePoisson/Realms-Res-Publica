package rrp.neoforge;

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
import rrp.neoforge.urban.BuildOrder;
import rrp.neoforge.urban.BuildType;

import java.nio.file.Path;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;

/**
 * Lecture et écriture de `villages.json`. Les changements sont signalés au registre (tampon) et
 * écrits en groupe : périodiquement (`persistence_flush_s`) et à l'arrêt du plugin.
 */
public final class Persistence {

    public static final String FILE = "villages.json";

    private final VillageRegistry registry;
    private final Path file;
    private final Logger log;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private long ticksSinceFlush;
    private int writes;

    public Persistence(VillageRegistry registry, Path dataFolder, Logger log) {
        this.registry = registry;
        this.file = dataFolder.resolve(FILE);
        this.log = log;
    }

    public Path file() {
        return file;
    }

    /** Nombre d'écritures effectuées (utile aux tests : les écritures sont groupées). */
    public int writes() {
        return writes;
    }

    /** Appelé à chaque avance du temps du plugin : écrit si le registre a changé et que l'intervalle est écoulé. */
    public void onTicks(long ticks, double flushSeconds, Runnable beforeFlush) {
        ticksSinceFlush += ticks;
        if (ticksSinceFlush >= flushSeconds * 20) {
            beforeFlush.run();
            if (registry.isDirty()) {
                flush();
            }
            ticksSinceFlush = 0;
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
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(FILE + ".tmp");
            Files.writeString(tmp, gson.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            writes++;
            return true;
        } catch (IOException e) {
            log.error("écriture de {} impossible : {}", file, e.getMessage());
            registry.markDirty();
            return false;
        }
    }

    /** Recharge le fichier dans le registre (le remplace). Renvoie le nombre de villages lus. */
    public int load() {
        registry.clear();
        registry.consumeDirty();
        if (!Files.isRegularFile(file)) {
            return 0;
        }
        int n = 0;
        boolean problem = false;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("villages")) {
                try {
                    registry.restore(fromJson(e.getAsJsonObject()));
                    n++;
                } catch (RuntimeException ex) {
                    problem = true;
                    log.error("village illisible ignoré dans {} : {}", file, ex.toString());
                }
            }
        } catch (IOException | RuntimeException e) {
            problem = true;
            log.error("lecture de {} impossible : {}", file, e.toString());
        }
        if (problem) {
            backup();
        }
        registry.consumeDirty();
        return n;
    }

    /** Garde une copie du fichier fautif avant que l'arrêt du plugin ne le réécrive. */
    private void backup() {
        Path bak = file.resolveSibling(FILE + ".bak-" + System.currentTimeMillis());
        try {
            Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING);
            log.error("copie de sauvegarde du fichier fautif : {}", bak);
        } catch (IOException ex) {
            log.error("copie de sauvegarde impossible : {}", ex.getMessage());
        }
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
        if (s.hasHall()) {
            int[] h = s.hall();
            JsonArray a = new JsonArray();
            a.add(h[0]);
            a.add(h[1]);
            a.add(h[2]);
            o.add("hall", a);
        }
        if (!s.orders().isEmpty()) {
            JsonArray oa = new JsonArray();
            for (BuildOrder b : s.orders()) {
                JsonObject bo = new JsonObject();
                bo.addProperty("type", b.type().name());
                bo.addProperty("x", b.x());
                bo.addProperty("y", b.y());
                bo.addProperty("z", b.z());
                bo.addProperty("wood", b.wood());
                bo.addProperty("stone", b.stone());
                oa.add(bo);
            }
            o.add("orders", oa);
        }
        if (!s.built().isEmpty()) {
            JsonObject bt = new JsonObject();
            s.built().forEach((t, n) -> bt.addProperty(t.name(), n));
            o.add("built", bt);
        }
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

    private static double num(JsonObject o, String key, double def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : def;
    }

    private static VillageSite fromJson(JsonObject o) {
        VillageState st = new VillageState(o.get("id").getAsString(), Faction.valueOf(o.get("faction").getAsString()));
        if (o.has("parentId")) {
            st.setParentId(o.get("parentId").getAsString());
        }
        st.setBeds((int) num(o, "beds", 0));
        if (o.has("level")) {
            try {
                st.setLevel(CrisisLevel.valueOf(o.get("level").getAsString()));
            } catch (IllegalArgumentException ignored) {
                // niveau inconnu : le village reste stable
            }
        }
        st.setRelations(num(o, "relations", 0));
        st.setContestTimer(num(o, "contestTimer", 0));
        st.setExileTimer(num(o, "exileTimer", 0));
        st.setWarTimer(num(o, "warTimer", 0));
        st.setBirthTimer(num(o, "birthTimer", 0));
        JsonObject stock = o.has("stock") ? o.getAsJsonObject("stock") : new JsonObject();
        for (Resource r : Resource.values()) {
            if (stock.has(r.name())) {
                st.setStock(r, stock.get(r.name()).getAsInt());
            }
        }
        VillageSite s = new VillageSite(st, o.get("world").getAsString(), o.get("x").getAsDouble(),
                o.get("y").getAsDouble(), o.get("z").getAsDouble(), (int) num(o, "radius", 24));
        if (o.has("hall")) {
            JsonArray a = o.getAsJsonArray("hall");
            s.setHall(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
        }
        if (o.has("orders")) {
            for (JsonElement e : o.getAsJsonArray("orders")) {
                JsonObject bo = e.getAsJsonObject();
                s.orders().add(new BuildOrder(BuildType.valueOf(bo.get("type").getAsString()), bo.get("x").getAsInt(),
                        bo.get("y").getAsInt(), bo.get("z").getAsInt(), bo.get("wood").getAsInt(), bo.get("stone").getAsInt()));
            }
        }
        if (o.has("built")) {
            for (var e : o.getAsJsonObject("built").entrySet()) {
                s.built().put(BuildType.valueOf(e.getKey()), e.getValue().getAsInt());
            }
        }
        if (o.has("warehouse")) {
            JsonArray a = o.getAsJsonArray("warehouse");
            s.setWarehouse(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
        }
        return s;
    }
}
