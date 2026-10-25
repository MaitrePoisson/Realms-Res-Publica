package rrp.core.tactics;

/** Geometry of the encirclement: where the third guard goes to hit the enemy from behind. */
public final class Flank {

    private Flank() {
    }

    private static Vec2 front(Vec2 enemy, Vec2 fixerA, Vec2 fixerB) {
        Vec2 mid = new Vec2((fixerA.x() + fixerB.x()) / 2, (fixerA.z() + fixerB.z()) / 2);
        Vec2 front = enemy.minus(mid).unit();
        return front.length() == 0 ? new Vec2(1, 0) : front;
    }

    /**
     * Point {@code distance} blocks behind the enemy, on the line from the two fixing guards through the enemy.
     * With both fixers on the enemy the direction falls back to +x.
     */
    public static Vec2 point(Vec2 enemy, Vec2 fixerA, Vec2 fixerB, double distance) {
        return enemy.plus(front(enemy, fixerA, fixerB).scale(distance));
    }

    /**
     * Intermediate waypoint that makes the flanker go around the enemy instead of through its front: on the side of
     * the enemy (perpendicular to the front line) where the flanker already is, at {@code distance} from the enemy.
     */
    public static Vec2 waypoint(Vec2 flanker, Vec2 enemy, Vec2 fixerA, Vec2 fixerB, double distance) {
        Vec2 front = front(enemy, fixerA, fixerB);
        Vec2 side = new Vec2(-front.z(), front.x());
        double sign = flanker.minus(enemy).dot(side) >= 0 ? 1 : -1;
        return enemy.plus(side.scale(sign * distance));
    }
}
