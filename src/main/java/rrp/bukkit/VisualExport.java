package rrp.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import rrp.core.CrisisLevel;
import rrp.core.Faction;
import rrp.core.Resource;
import rrp.core.Role;
import rrp.core.VillageState;
import rrp.core.VillagerState;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Vue de dessus des villages du monde de test : à la fin de chaque cas de selftest (avant le nettoyage),
 * photographie les villages créés (lits, tonneau, huttes, tentes, feu de camp, villageois par rôle) et
 * en fait une page HTML autonome (un SVG par cas). Aucun client Minecraft n'est nécessaire.
 */
public final class VisualExport {

    public static final String FILE = "selftest-visuels.html";
    private static final int MAX_SHOTS = 60;
    private static final int MAX_CELLS_PER_SITE = 6000;

    private record Cell(int x, int z, String kind) {
    }

    private record Dot(double x, double z, Role role, String tip) {
    }

    private record SiteView(String id, Faction faction, CrisisLevel level, int radius, int cx, int cz, int pop,
                            int beds, int food, int morale, int loyalty, List<Cell> cells, List<Dot> dots) {
    }

    private record Shot(String title, List<SiteView> sites) {
    }

    private static final Map<String, String> CELL_COLORS = Map.of(
            "bed", "#d33a3a", "barrel", "#8a5a2b", "fire", "#ff9a1f", "wool", "#f2f2f2",
            "wood", "#c9a15a", "water", "#4a86d6");
    private static final Map<String, String> CELL_LABELS = Map.of(
            "bed", "Lit", "barrel", "Tonneau (entrepôt)", "fire", "Feu de camp", "wool", "Tente (laine)",
            "wood", "Hutte (planches)", "water", "Eau");
    private static final Map<Role, String> ROLE_COLORS = new EnumMap<>(Map.of(
            Role.BUILDER, "#f5c542", Role.FORESTER, "#2e8b3d", Role.MINER, "#6b6b7a", Role.GUARD, "#c0392b",
            Role.FARMER, "#8bc34a", Role.STEWARD, "#8e44ad", Role.DIPLOMAT, "#1abc9c"));

    private final List<Shot> shots = new ArrayList<>();

    public synchronized void reset() {
        shots.clear();
    }

    public synchronized int count() {
        return shots.size();
    }

    /** Photographie les villages donnés (ceux que le cas vient de créer). Sans village : rien n'est gardé. */
    public synchronized void capture(String title, Collection<VillageSite> sites) {
        if (sites.isEmpty() || shots.size() >= MAX_SHOTS) {
            return;
        }
        List<SiteView> views = new ArrayList<>();
        for (VillageSite s : sites) {
            World world = Bukkit.getWorld(s.worldName());
            if (world != null) {
                views.add(view(world, s));
            }
        }
        boolean empty = views.stream().allMatch(v -> v.dots().isEmpty()
                && v.cells().stream().allMatch(c -> c.kind().equals("water")));
        if (!views.isEmpty() && !empty) {
            shots.add(new Shot(title, views));
        }
    }

    private SiteView view(World world, VillageSite s) {
        int cx = (int) Math.floor(s.x());
        int cz = (int) Math.floor(s.z());
        int cy = (int) Math.floor(s.y());
        int r = s.radius();
        List<Cell> cells = new ArrayList<>();
        for (int x = cx - r; x <= cx + r && cells.size() < MAX_CELLS_PER_SITE; x++) {
            for (int z = cz - r; z <= cz + r && cells.size() < MAX_CELLS_PER_SITE; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                String kind = null;
                for (int y = cy + 6; y >= cy - 2 && kind == null; y--) {
                    kind = kind(world.getBlockAt(x, y, z).getType());
                }
                if (kind != null) {
                    cells.add(new Cell(x, z, kind));
                }
            }
        }
        VillageState st = s.state();
        List<Dot> dots = new ArrayList<>();
        for (VillagerState v : st.villagers()) {
            Entity e = Bukkit.getEntity(v.id());
            if (e != null && e.getWorld().equals(world)) {
                String tip = v.role().label() + " — moral " + Math.round(v.morale()) + ", satiété "
                        + Math.round(v.satiety()) + ", loyauté " + Math.round(v.loyalty());
                dots.add(new Dot(e.getLocation().getX(), e.getLocation().getZ(), v.role(), tip));
            }
        }
        return new SiteView(st.id(), st.faction(), st.level(), r, cx, cz, st.population(), st.beds(),
                st.stock(Resource.FOOD), (int) Math.round(st.averageMorale()), (int) Math.round(st.averageLoyalty()),
                cells, dots);
    }

    private static String kind(Material m) {
        String n = m.name();
        if (m == Material.BARREL) {
            return "barrel";
        }
        if (n.endsWith("_BED")) {
            return "bed";
        }
        if (m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE) {
            return "fire";
        }
        if (n.endsWith("_WOOL")) {
            return "wool";
        }
        if (n.endsWith("_PLANKS")) {
            return "wood";
        }
        return m == Material.WATER ? "water" : null;
    }

    /** Page HTML complète : légende + un SVG par cas photographié. */
    public synchronized String html(int passed, int failed) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"fr\"><head><meta charset=\"utf-8\"><title>Realms &amp; Res Publica — villages du selftest</title>\n")
                .append("<style>body{font-family:Segoe UI,Arial,sans-serif;background:#1d2127;color:#e8e8e8;margin:20px}")
                .append("h1{font-size:22px}h2{font-size:15px;margin:22px 0 4px}.legend span{display:inline-block;margin-right:14px}")
                .append(".legend i{display:inline-block;width:12px;height:12px;margin-right:4px;vertical-align:middle;border:1px solid #000}")
                .append(".dot{border-radius:50%!important}svg{background:#3f6b3a;border:1px solid #000;max-width:100%}")
                .append(".meta{font-size:12px;color:#b8c0c8}</style></head><body>\n");
        sb.append("<h1>Realms &amp; Res Publica — villages du monde de test</h1>\n<p class=\"meta\">Généré le ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                .append(" — selftest : ").append(passed).append(" réussi(s), ").append(failed).append(" échoué(s) — ")
                .append(shots.size()).append(" scène(s) photographiée(s). Vue de dessus, 1 case = 1 bloc.</p>\n");
        sb.append("<p class=\"legend\">");
        for (String k : List.of("bed", "barrel", "fire", "wool", "wood", "water")) {
            sb.append("<span><i style=\"background:").append(CELL_COLORS.get(k)).append("\"></i>")
                    .append(CELL_LABELS.get(k)).append("</span>");
        }
        for (Role role : Role.values()) {
            sb.append("<span><i class=\"dot\" style=\"background:").append(ROLE_COLORS.get(role)).append("\"></i>")
                    .append(role.label()).append("</span>");
        }
        sb.append("</p>\n");
        if (shots.isEmpty()) {
            sb.append("<p>Aucune scène : aucun cas n'a créé de village.</p>\n");
        }
        // Les scènes les plus parlantes d'abord : celles qui montrent plusieurs villages (village + campement dissident).
        List<Shot> ordered = new ArrayList<>(shots);
        ordered.sort((a, b) -> Integer.compare(b.sites().size(), a.sites().size()));
        for (Shot shot : ordered) {
            sb.append(svg(shot));
        }
        return sb.append("</body></html>\n").toString();
    }

    private static String svg(Shot shot) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        // Cadrage sur le contenu (blocs et villageois), pas sur le rayon du village qui peut atteindre des dizaines de blocs.
        for (SiteView v : shot.sites()) {
            minX = Math.min(minX, v.cx() - 3);
            minZ = Math.min(minZ, v.cz() - 3);
            maxX = Math.max(maxX, v.cx() + 4);
            maxZ = Math.max(maxZ, v.cz() + 4);
            for (Cell c : v.cells()) {
                if (!c.kind().equals("water")) {
                    minX = Math.min(minX, c.x() - 2);
                    minZ = Math.min(minZ, c.z() - 2);
                    maxX = Math.max(maxX, c.x() + 3);
                    maxZ = Math.max(maxZ, c.z() + 3);
                }
            }
            for (Dot d : v.dots()) {
                minX = Math.min(minX, (int) Math.floor(d.x()) - 2);
                minZ = Math.min(minZ, (int) Math.floor(d.z()) - 2);
                maxX = Math.max(maxX, (int) Math.ceil(d.x()) + 3);
                maxZ = Math.max(maxZ, (int) Math.ceil(d.z()) + 3);
            }
        }
        int w = maxX - minX;
        int h = maxZ - minZ;
        double scale = Math.max(2, Math.min(14, 640.0 / Math.max(w, h)));
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(esc(shot.title())).append("</h2>\n");
        for (SiteView v : shot.sites()) {
            sb.append("<div class=\"meta\">").append(esc(v.id())).append(" (").append(v.faction().label())
                    .append(", ").append(v.level().label()).append(") — population ").append(v.pop())
                    .append(", lits ").append(v.beds()).append(", nourriture ").append(v.food())
                    .append(", moral moyen ").append(v.morale()).append(", loyauté moyenne ").append(v.loyalty())
                    .append("</div>\n");
        }
        sb.append(String.format(java.util.Locale.ROOT, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">\n",
                (int) (w * scale), (int) (h * scale), (int) (w * scale), (int) (h * scale)));
        for (SiteView v : shot.sites()) {
            String edge = v.faction() == Faction.LOYALIST ? "#4aa3ff" : "#ff5a4a";
            sb.append(String.format(java.util.Locale.ROOT,
                    "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" fill=\"none\" stroke=\"%s\" stroke-width=\"2\" stroke-dasharray=\"6 4\"/>\n",
                    (v.cx() + 0.5 - minX) * scale, (v.cz() + 0.5 - minZ) * scale, v.radius() * scale, edge));
            for (Cell c : v.cells()) {
                sb.append(String.format(java.util.Locale.ROOT, "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" fill=\"%s\"/>",
                        (c.x() - minX) * scale, (c.z() - minZ) * scale, scale, scale, CELL_COLORS.get(c.kind())));
            }
            sb.append('\n');
            for (Dot d : v.dots()) {
                sb.append(String.format(java.util.Locale.ROOT,
                        "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" fill=\"%s\" stroke=\"#fff\" stroke-width=\"1.5\"><title>%s</title></circle>",
                        (d.x() - minX) * scale, (d.z() - minZ) * scale, Math.max(3.5, scale * 0.45),
                        ROLE_COLORS.get(d.role()), esc(d.tip())));
            }
            sb.append(String.format(java.util.Locale.ROOT,
                    "\n<text x=\"%.1f\" y=\"%.1f\" fill=\"#fff\" font-size=\"13\" stroke=\"#000\" stroke-width=\"0.4\">%s</text>\n",
                    (v.cx() - v.radius() - minX) * scale + 4, (v.cz() - v.radius() - minZ) * scale + 14, esc(v.id())));
        }
        return sb.append("</svg>\n").toString();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
