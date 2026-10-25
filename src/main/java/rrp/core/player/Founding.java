package rrp.core.player;

/** Pure rules for a Sovereign founding a village. */
public final class Founding {

    public enum Verdict { OK, NOT_SOVEREIGN, TOO_CLOSE, TOO_POOR, TOO_MANY }

    private Founding() {
    }

    /**
     * @param nearest distance to the nearest existing village (Double.MAX_VALUE when none)
     * @param owned villages already ruled by the player
     */
    public static Verdict check(PlayerRole role, long purse, long cost, double nearest, double minDistance, int owned, int maxOwned) {
        if (role != PlayerRole.SOVEREIGN) {
            return Verdict.NOT_SOVEREIGN;
        }
        if (nearest < minDistance) {
            return Verdict.TOO_CLOSE;
        }
        if (owned >= maxOwned) {
            return Verdict.TOO_MANY;
        }
        if (purse < cost) {
            return Verdict.TOO_POOR;
        }
        return Verdict.OK;
    }
}
