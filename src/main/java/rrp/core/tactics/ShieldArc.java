package rrp.core.tactics;

/**
 * Front arc of a raised shield: a hit coming from a direction within half of the arc on either side of where the
 * guard faces is blocked. Pure geometry; the Minecraft yaw convention is used (0 faces +z, 90 faces -x).
 */
public final class ShieldArc {

    private ShieldArc() {
    }

    /** Unit vector (x, z) a guard with this yaw (degrees) faces. */
    public static Vec2 facing(double yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vec2(-Math.sin(r), Math.cos(r));
    }

    /**
     * True when the source of the hit, seen from the guard (dx, dz = source minus guard), lies within the front arc
     * of {@code arcDeg} degrees. A source on the very spot of the guard is not blocked (no direction).
     */
    public static boolean blocks(double yawDeg, double dx, double dz, double arcDeg) {
        Vec2 toSource = new Vec2(dx, dz).unit();
        if (toSource.length() < 1e-9) {
            return false;
        }
        double cos = facing(yawDeg).dot(toSource);
        return cos >= Math.cos(Math.toRadians(Math.min(180, Math.max(0, arcDeg)) / 2));
    }

    /** Yaw (degrees) that makes a guard at the origin face the point (dx, dz). */
    public static double yawToward(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }
}
