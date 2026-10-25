package rrp.core.tactics;

import java.util.List;

/** Tactical retreat of a badly wounded guard: an orderly withdrawal behind the line of comrades, never a flight. */
public final class Retreat {

    /** A withdrawn guard resumes duty once healed back above this share of its maximum health. */
    public static final double RESUME_FRACTION = 0.6;

    private Retreat() {
    }

    /** True when the guard must withdraw: health at or below {@code thresholdPct} percent of the maximum. */
    public static boolean shouldWithdraw(double health, double maxHealth, double thresholdPct) {
        if (maxHealth <= 0) {
            return false;
        }
        return health / maxHealth * 100.0 <= thresholdPct;
    }

    /** Hysteresis: once withdrawn, stay out until health is back above {@link #RESUME_FRACTION}. */
    public static boolean stillWithdrawing(boolean withdrawing, double health, double maxHealth, double thresholdPct) {
        if (shouldWithdraw(health, maxHealth, thresholdPct)) {
            return true;
        }
        return withdrawing && maxHealth > 0 && health / maxHealth < RESUME_FRACTION;
    }

    /**
     * Point behind the line: on the ray from the enemy through the wounded guard, farther from the enemy than the
     * guard and than every comrade, by {@code margin} blocks. The wounded guard never runs towards the enemy.
     */
    public static Vec2 pointBehindLine(Vec2 self, Vec2 enemy, List<Vec2> comrades, double margin) {
        Vec2 away = self.minus(enemy).unit();
        if (away.length() == 0) {
            away = new Vec2(1, 0);
        }
        double depth = self.minus(enemy).dot(away);
        for (Vec2 c : comrades) {
            depth = Math.max(depth, c.minus(enemy).dot(away));
        }
        return enemy.plus(away.scale(depth + margin));
    }
}
