package rrp.sim;

import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import rrp.core.CrisisLevel;
import rrp.core.SimEvent;
import rrp.sim.ScenarioRunner.Result;
import rrp.sim.ScenarioRunner.Sample;
import rrp.sim.ScenarioRunner.TimedEvent;

/** Sorties d'un scénario : CSV et rapport SVG (population, nourriture, moral moyen, niveau de crise). */
public final class Report {
    private static final String[] COLORS = {"#2a6fdb", "#d9622b", "#2f9e6b", "#8a5cc2"};
    private static final String[] LEVELS = {"Stable", "Contestation", "Exil", "Guerre civ."};

    private Report() { }

    public static String csv(Result r) {
        StringBuilder sb = new StringBuilder(
                "t_s,village,population,food,wood,beds,threats,morale,satiety,loyalty,crisis_level\n");
        for (Sample s : r.samples()) {
            sb.append(String.format(Locale.ROOT, "%.0f,%s,%d,%d,%d,%d,%d,%.1f,%.1f,%.1f,%s%n", s.t(), s.villageId(),
                    s.population(), s.food(), s.wood(), s.beds(), s.threats(), s.morale(), s.satiety(), s.loyalty(),
                    s.level()));
        }
        return sb.toString();
    }

    public static String svg(Result r) {
        int w = 960;
        int h = 700;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" "
                + "viewBox=\"0 0 %d %d\" font-family=\"sans-serif\">%n", w, h, w, h));
        sb.append(String.format(Locale.ROOT, "<rect width=\"%d\" height=\"%d\" fill=\"#ffffff\"/>%n", w, h));
        sb.append("<text x=\"20\" y=\"28\" font-size=\"18\" font-weight=\"bold\" fill=\"#222\">Scénario « ")
                .append(esc(r.scenario().title())).append(" » — simulateur à économie virtuelle</text>\n");
        sb.append(legend(r, 20, 48));
        panel(sb, r, "Population (villageois)", 20, 70, 450, 280, 0, Double.NaN, Sample::population, false);
        panel(sb, r, "Nourriture en stock (unités)", 490, 70, 450, 280, 0, Double.NaN, Sample::food, false);
        panel(sb, r, "Moral moyen (0–100)", 20, 380, 450, 280, 0, 100, Sample::morale, false);
        panel(sb, r, "Niveau de crise", 490, 380, 450, 280, 0, 3, s -> s.level().ordinal(), true);
        sb.append(String.format(Locale.ROOT, "<text x=\"20\" y=\"%d\" font-size=\"11\" fill=\"#666\">Axe horizontal : "
                + "minutes de simulation. Économie virtuelle : sert à l'équilibrage, pas de preuve du comportement "
                + "du serveur.</text>%n", h - 12));
        return sb.append("</svg>\n").toString();
    }

    private static String legend(Result r, int x, int y) {
        StringBuilder sb = new StringBuilder();
        int cx = x;
        List<String> ids = r.villageIds();
        for (int i = 0; i < ids.size(); i++) {
            sb.append(String.format(Locale.ROOT, "<rect x=\"%d\" y=\"%d\" width=\"14\" height=\"4\" fill=\"%s\"/>"
                    + "<text x=\"%d\" y=\"%d\" font-size=\"12\" fill=\"#333\">%s</text>%n", cx, y - 5, COLORS[i % COLORS.length],
                    cx + 20, y, esc(ids.get(i))));
            cx += 30 + 8 * ids.get(i).length();
        }
        return sb.toString();
    }

    private static void panel(StringBuilder sb, Result r, String title, int x, int y, int w, int h, double yMin,
                              double yMaxFixed, ToDoubleFunction<Sample> f, boolean levels) {
        int left = 52;
        int right = 12;
        int top = 28;
        int bottom = 30;
        double px = x + left;
        double py = y + top;
        double pw = w - left - right;
        double ph = h - top - bottom;
        double tMax = r.scenario().durationS();
        double yMax = yMaxFixed;
        if (Double.isNaN(yMax)) {
            yMax = 1;
            for (Sample s : r.samples()) {
                yMax = Math.max(yMax, f.applyAsDouble(s));
            }
            yMax = niceCeil(yMax);
        }
        sb.append(String.format(Locale.ROOT, "<text x=\"%.0f\" y=\"%.0f\" font-size=\"14\" font-weight=\"bold\" "
                + "fill=\"#222\">%s</text>%n", px, py - 10, esc(title)));
        sb.append(String.format(Locale.ROOT, "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" fill=\"#fafafa\" "
                + "stroke=\"#ccc\"/>%n", px, py, pw, ph));
        int ticks = levels ? 3 : 4;
        for (int i = 0; i <= ticks; i++) {
            double v = yMin + (yMax - yMin) * i / ticks;
            double yy = py + ph - ph * i / ticks;
            sb.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"#e4e4e4\"/>%n",
                    px, yy, px + pw, yy));
            String label = levels ? LEVELS[i] : String.format(Locale.ROOT, "%.0f", v);
            sb.append(String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%.1f\" font-size=\"10\" text-anchor=\"end\" "
                    + "fill=\"#555\">%s</text>%n", px - 5, yy + 3, esc(label)));
        }
        int xTicks = 6;
        for (int i = 0; i <= xTicks; i++) {
            double xx = px + pw * i / xTicks;
            sb.append(String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%.1f\" font-size=\"10\" text-anchor=\"middle\" "
                    + "fill=\"#555\">%.0f</text>%n", xx, py + ph + 14, tMax / 60.0 * i / xTicks));
        }
        List<String> ids = r.villageIds();
        for (int k = 0; k < ids.size(); k++) {
            StringBuilder pts = new StringBuilder();
            Sample prev = null;
            for (Sample s : r.of(ids.get(k))) {
                double xx = px + pw * s.t() / tMax;
                double yy = py + ph - ph * (f.applyAsDouble(s) - yMin) / (yMax - yMin);
                if (levels && prev != null) {
                    double py0 = py + ph - ph * (f.applyAsDouble(prev) - yMin) / (yMax - yMin);
                    pts.append(String.format(Locale.ROOT, "%.1f,%.1f ", xx, py0));
                }
                pts.append(String.format(Locale.ROOT, "%.1f,%.1f ", xx, yy));
                prev = s;
            }
            sb.append(String.format(Locale.ROOT, "<polyline fill=\"none\" stroke=\"%s\" stroke-width=\"2\" "
                    + "points=\"%s\"/>%n", COLORS[k % COLORS.length], pts.toString().trim()));
        }
        if (levels) {
            markEvents(sb, r, px, py, pw, ph, tMax);
        }
    }

    /** Repères verticaux pour exil, début et fin de guerre. */
    private static void markEvents(StringBuilder sb, Result r, double px, double py, double pw, double ph, double tMax) {
        for (TimedEvent te : r.events()) {
            String label;
            if (te.event() instanceof SimEvent.Exile) {
                label = "exil";
            } else if (te.event() instanceof SimEvent.WarStarted) {
                label = "guerre";
            } else if (te.event() instanceof SimEvent.WarEnded) {
                label = "fin guerre";
            } else {
                continue;
            }
            double xx = px + pw * te.t() / tMax;
            sb.append(String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"#999\" "
                    + "stroke-dasharray=\"3,3\"/><text x=\"%.1f\" y=\"%.1f\" font-size=\"10\" fill=\"#555\">%s</text>%n",
                    xx, py, xx, py + ph, xx + 3, py + 11, label));
        }
    }

    private static double niceCeil(double v) {
        double mag = Math.pow(10, Math.floor(Math.log10(v)));
        for (double m : new double[] {1, 2, 4, 5, 8, 10}) {
            if (m * mag >= v) {
                return m * mag;
            }
        }
        return 10 * mag;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Résumé court d'un scénario pour la console. */
    public static String summary(Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%-15s", r.scenario().name()));
        Sample first = r.of(Scenario.VILLAGE_ID).get(0);
        Sample last = r.last(Scenario.VILLAGE_ID);
        sb.append(String.format(Locale.ROOT, " pop %d→%d  nourriture %d→%d  moral %.0f→%.0f  crise finale %s",
                first.population(), last.population(), first.food(), last.food(), first.morale(), last.morale(),
                last.level()));
        for (CrisisLevel l : new CrisisLevel[] {CrisisLevel.CONTESTATION, CrisisLevel.EXIL, CrisisLevel.GUERRE_CIVILE}) {
            double t = r.firstTimeAt(Scenario.VILLAGE_ID, l);
            if (t >= 0) {
                sb.append(String.format(Locale.ROOT, "  %s à %.0f s", l, t));
            }
        }
        return sb.toString();
    }
}
