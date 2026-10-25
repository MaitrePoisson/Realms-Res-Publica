package rrp.core.tactics;

/** Horizontal position or vector (x, z), enough for the tactical geometry. */
public record Vec2(double x, double z) {

    public Vec2 plus(Vec2 o) {
        return new Vec2(x + o.x, z + o.z);
    }

    public Vec2 minus(Vec2 o) {
        return new Vec2(x - o.x, z - o.z);
    }

    public Vec2 scale(double k) {
        return new Vec2(x * k, z * k);
    }

    public double length() {
        return Math.sqrt(x * x + z * z);
    }

    public double dist(Vec2 o) {
        return minus(o).length();
    }

    public double dot(Vec2 o) {
        return x * o.x + z * o.z;
    }

    /** Unit vector, or (0, 0) for a null vector. */
    public Vec2 unit() {
        double l = length();
        return l < 1e-9 ? new Vec2(0, 0) : new Vec2(x / l, z / l);
    }
}
