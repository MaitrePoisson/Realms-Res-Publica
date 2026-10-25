package rrp.neoforge;

import rrp.core.SimConfig;
import rrp.core.abstractsim.AbstractMarch;
import rrp.neoforge.urban.RoadNetwork;

/** Builds the abstract routes (point marches) of armies and caravans between villages. */
public final class AbstractRoutes {

    /** A road end counts as "at" a village when it is within this many blocks of it. */
    private static final double ROAD_END = 40;

    private AbstractRoutes() { }

    /** March from a village to another one, faster when a fully laid road links them. */
    public static AbstractMarch between(VillageSite from, VillageSite to, SimConfig cfg) {
        return from(from.x(), from.z(), from, to, cfg);
    }

    /** March from an arbitrary point (a party that has just left an active area) to a village. */
    public static AbstractMarch from(double x, double z, VillageSite origin, VillageSite to, SimConfig cfg) {
        double factor = linked(origin, to) ? cfg.get(SimConfig.Key.ABSTRACT_ROAD_FACTOR) : 1;
        return new AbstractMarch(x, z, to.x(), to.z(), cfg.get(SimConfig.Key.ABSTRACT_MARCH_SPEED), factor);
    }

    static boolean linked(VillageSite a, VillageSite b) {
        for (VillageSite s : new VillageSite[] {a, b}) {
            for (RoadNetwork.Road r : s.roads()) {
                if (r.length() > 0 && r.laid() >= r.length()
                        && ((near(r.fromX(), r.fromZ(), a) && near(r.toX(), r.toZ(), b))
                        || (near(r.fromX(), r.fromZ(), b) && near(r.toX(), r.toZ(), a)))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean near(double x, double z, VillageSite s) {
        return Math.hypot(x - s.x(), z - s.z()) <= ROAD_END;
    }
}
