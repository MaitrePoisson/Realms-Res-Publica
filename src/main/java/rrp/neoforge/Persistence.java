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
        JsonArray rel = new JsonArray();
        RelationService.matrix().snapshot().forEach((k, v) -> {
            JsonObject r = new JsonObject();
            r.addProperty("pair", k);
            r.addProperty("value", v[0]);
            r.addProperty("baseline", v[1]);
            rel.add(r);
        });
        root.add("relations", rel);
        JsonArray tre = new JsonArray();
        DiplomacyService.treaties().snapshot().forEach((k, v) -> {
            JsonObject r = new JsonObject();
            r.addProperty("pair", k);
            r.addProperty("state", v.name());
            tre.add(r);
        });
        root.add("treaties", tre);
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
        RelationService.matrix().clear();
        DiplomacyService.treaties().clear();
        registry.consumeDirty();
        if (!Files.isRegularFile(file)) {
            return 0;
        }
        int n = 0;
        boolean problem = false;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("relations")) {
                for (JsonElement e : root.getAsJsonArray("relations")) {
                    JsonObject r = e.getAsJsonObject();
                    RelationService.matrix().restore(r.get("pair").getAsString(), r.get("value").getAsDouble(),
                            r.get("baseline").getAsDouble());
                }
            }
            if (root.has("treaties")) {
                for (JsonElement e : root.getAsJsonArray("treaties")) {
                    JsonObject r = e.getAsJsonObject();
                    DiplomacyService.treaties().restore(r.get("pair").getAsString(),
                            rrp.core.diplomacy.Treaty.State.valueOf(r.get("state").getAsString()));
                }
            }
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
        if (s.doctrine() != null) {
            o.addProperty("doctrine", s.doctrine().name());
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
        if (s.construction() != null) {
            JsonObject co = new JsonObject();
            co.addProperty("type", s.construction().type().name());
            co.addProperty("x", s.construction().origin().getX());
            co.addProperty("y", s.construction().origin().getY());
            co.addProperty("z", s.construction().origin().getZ());
            co.addProperty("step", s.construction().step());
            o.add("construction", co);
        }
        if (!s.roads().isEmpty()) {
            JsonArray ra = new JsonArray();
            for (var r : s.roads()) {
                JsonObject ro = new JsonObject();
                ro.addProperty("fx", r.fromX());
                ro.addProperty("fz", r.fromZ());
                ro.addProperty("tx", r.toX());
                ro.addProperty("tz", r.toZ());
                ro.addProperty("wide", r.wide());
                ro.addProperty("laid", r.laid());
                ro.addProperty("paved", r.paved());
                if (r.hasPath()) {
                    JsonArray pa = new JsonArray();
                    for (int v : r.path()) {
                        pa.add(v);
                    }
                    ro.add("path", pa);
                }
                ra.add(ro);
            }
            o.add("roads", ra);
        }
        if (s.defense().started() || !s.defense().towers().isEmpty()) {
            var d = s.defense();
            JsonObject dj = new JsonObject();
            dj.addProperty("stage", d.stage());
            dj.addProperty("radius", d.radius());
            dj.addProperty("laid", d.laid());
            dj.addProperty("upgraded", d.upgraded());
            dj.addProperty("issued", d.towersIssued());
            JsonArray ta = new JsonArray();
            for (int[] t : d.towers()) {
                ta.add(t[0]);
                ta.add(t[1]);
                ta.add(t[2]);
            }
            dj.add("towers", ta);
            o.add("defense", dj);
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
        if (o.has("doctrine")) {
            try {
                s.setDoctrine(rrp.core.territory.Doctrine.valueOf(o.get("doctrine").getAsString()));
            } catch (IllegalArgumentException ignored) {
                // doctrine inconnue : sera recalculée
            }
        }
        if (o.has("orders")) {
            for (JsonElement e : o.getAsJsonArray("orders")) {
                JsonObject bo = e.getAsJsonObject();
                s.orders().add(new BuildOrder(BuildType.valueOf(bo.get("type").getAsString()), bo.get("x").getAsInt(),
                        bo.get("y").getAsInt(), bo.get("z").getAsInt(), bo.get("wood").getAsInt(), bo.get("stone").getAsInt()));
            }
        }
        if (o.has("construction")) {
            JsonObject co = o.getAsJsonObject("construction");
            s.setConstruction(new rrp.neoforge.urban.ConstructionSite(BuildType.valueOf(co.get("type").getAsString()),
                    new net.minecraft.core.BlockPos(co.get("x").getAsInt(), co.get("y").getAsInt(), co.get("z").getAsInt()),
                    co.get("step").getAsInt()));
        }
        if (o.has("roads")) {
            for (JsonElement e : o.getAsJsonArray("roads")) {
                JsonObject ro = e.getAsJsonObject();
                int[] path = null;
                if (ro.has("path")) {
                    JsonArray pa = ro.getAsJsonArray("path");
                    path = new int[pa.size()];
                    for (int i = 0; i < path.length; i++) {
                        path[i] = pa.get(i).getAsInt();
                    }
                }
                s.roads().add(new rrp.neoforge.urban.RoadNetwork.Road(ro.get("fx").getAsInt(), ro.get("fz").getAsInt(),
                        ro.get("tx").getAsInt(), ro.get("tz").getAsInt(), ro.get("wide").getAsBoolean(), path,
                        ro.get("laid").getAsInt(), ro.get("paved").getAsInt()));
            }
        }
        if (o.has("defense")) {
            JsonObject dj = o.getAsJsonObject("defense");
            var d = s.defense();
            d.restore(dj.get("stage").getAsInt(), dj.get("radius").getAsInt(), dj.get("laid").getAsInt(),
                    dj.get("upgraded").getAsInt(), dj.get("issued").getAsInt());
            JsonArray ta = dj.getAsJsonArray("towers");
            for (int i = 0; i + 2 < ta.size(); i += 3) {
                d.towers().add(new int[] {ta.get(i).getAsInt(), ta.get(i + 1).getAsInt(), ta.get(i + 2).getAsInt()});
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
