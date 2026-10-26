package rrp.core.tactics;

/**
 * What a guard does right now: stand on its post, walk the border, shoot from range or advance behind its shield.
 * Pure decision, fed by what the world adapter sees.
 */
public final class GuardPosture {

    /** Posture of a guard. */
    public enum Posture {
        /** On a tower or gate post, watching. */
        SENTINEL,
        /** Mobile patrol along the border, in pairs. */
        PATROL,
        /** Shooting arrows at a threat in range. */
        SHOOT,
        /** Shield raised, advancing on the threat with the comrades. */
        SHIELD_ADVANCE
    }

    private GuardPosture() {
    }

    /**
     * @param archer      carries a bow and has arrows
     * @param hasPost     a tower or gate post is assigned to this guard
     * @param threatDist  distance (blocks) to the nearest threat, or a negative number when there is none
     * @param rangedRange distance from which archers shoot
     */
    public static Posture choose(boolean archer, boolean hasPost, double threatDist, double rangedRange) {
        if (threatDist >= 0) {
            return archer && threatDist <= rangedRange ? Posture.SHOOT : Posture.SHIELD_ADVANCE;
        }
        return hasPost ? Posture.SENTINEL : Posture.PATROL;
    }
}
