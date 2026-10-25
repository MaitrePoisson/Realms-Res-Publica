package rrp.core.player;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Laws and tax rates decreed by the ruler of one village. Pure data, persisted by the mod. */
public final class VillageLaws {

    private boolean openBorders;
    private boolean foreignersBuildBan;
    private double conscription = -1;
    private final Set<String> embargo = new TreeSet<>();
    private final Map<String, Double> taxes = new TreeMap<>();

    public boolean openBorders() { return openBorders; }
    public void setOpenBorders(boolean v) { openBorders = v; }
    public boolean foreignersBuildBan() { return foreignersBuildBan; }
    public void setForeignersBuildBan(boolean v) { foreignersBuildBan = v; }

    /** Share of the population the ruler wants mobilised, or a negative value to follow the global setting. */
    public double conscription() { return conscription; }
    public void setConscription(double v) { conscription = v < 0 ? -1 : v; }

    /** Share used in war: the decree (capped) or the global setting. */
    public double conscriptionOr(double global, double cap) {
        return conscription < 0 ? global : Math.min(conscription, cap);
    }

    public Set<String> embargo() { return embargo; }
    public boolean embargoes(String village) { return embargo.contains(village); }

    /** Toggles the embargo on a village; returns true when it is now in force. */
    public boolean toggleEmbargo(String village) {
        if (!embargo.remove(village)) {
            embargo.add(village);
            return true;
        }
        return false;
    }

    public Map<String, Double> taxes() { return taxes; }
    public double tax(String resource) { return taxes.getOrDefault(resource, 0.0); }

    public void setTax(String resource, double rate) {
        if (rate <= 0) {
            taxes.remove(resource);
        } else {
            taxes.put(resource, rate);
        }
    }

    public double totalTax() {
        return taxes.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public boolean isDefault() {
        return !openBorders && !foreignersBuildBan && conscription < 0 && embargo.isEmpty() && taxes.isEmpty();
    }
}
