package rrp.core.abstractsim;

/**
 * A party (regiment or caravan) travelling between two points as a mere point on the map, with no entity and no
 * chunk. The route is a straight line, sped up by {@code roadFactor} when it follows a laid road. Pure logic.
 */
public final class AbstractMarch {

    private final double fromX;
    private final double fromZ;
    private final double toX;
    private final double toZ;
    private final double speed;
    private double travelled;

    /**
     * @param speed      blocks per second on open ground
     * @param roadFactor speed multiplier when the route follows a road (1 = no road)
     */
    public AbstractMarch(double fromX, double fromZ, double toX, double toZ, double speed, double roadFactor) {
        this.fromX = fromX;
        this.fromZ = fromZ;
        this.toX = toX;
        this.toZ = toZ;
        this.speed = Math.max(1e-6, speed * Math.max(1e-6, roadFactor));
    }

    public double distance() { return Math.hypot(toX - fromX, toZ - fromZ); }

    /** Effective speed in blocks per second (road factor included). */
    public double speed() { return speed; }

    /** Seconds needed for the whole route from the start. */
    public double etaSeconds() { return distance() / speed; }

    /** Seconds still needed to arrive. */
    public double remainingSeconds() { return Math.max(0, distance() - travelled) / speed; }

    public boolean arrived() { return travelled >= distance(); }

    /** Fraction of the route done, in [0 ; 1]. */
    public double progress() {
        double d = distance();
        return d <= 0 ? 1 : Math.min(1, travelled / d);
    }

    public AbstractMarch advance(double seconds) {
        travelled = Math.min(distance(), travelled + Math.max(0, seconds) * speed);
        return this;
    }

    public double x() { return fromX + (toX - fromX) * progress(); }

    public double z() { return fromZ + (toZ - fromZ) * progress(); }
}
