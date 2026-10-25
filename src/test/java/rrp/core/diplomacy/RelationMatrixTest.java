package rrp.core.diplomacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import rrp.core.diplomacy.RelationMatrix.Thresholds;
import rrp.core.territory.Doctrine;
import rrp.core.territory.FactionRelations.Stance;

class RelationMatrixTest {

    private static final Thresholds T = new Thresholds(30, -30);

    @Test
    void baselineFollowsTheDoctrinePair() {
        assertEquals(40, RelationMatrix.baseline(Doctrine.MERCHANT_CITY, Doctrine.MERCHANT_CITY, 40, -40));
        assertEquals(0, RelationMatrix.baseline(Doctrine.MERCHANT_CITY, Doctrine.ISOLATIONIST_THEOCRACY, 40, -40));
        assertEquals(-40, RelationMatrix.baseline(Doctrine.WARLIKE_EMPIRE, Doctrine.WARLIKE_EMPIRE, 40, -40));
    }

    @Test
    void pairIsUnorderedAndStartsAtItsBaseline() {
        RelationMatrix m = new RelationMatrix();
        assertFalse(m.known("a", "b"));
        m.ensure("b", "a", 40);
        assertTrue(m.known("a", "b"));
        assertEquals(40, m.get("a", "b"));
        m.ensure("a", "b", -10);
        assertEquals(40, m.get("b", "a"), "ensure never overwrites");
        assertEquals(1, m.size());
    }

    @Test
    void changesAreClamped() {
        RelationMatrix m = new RelationMatrix();
        m.ensure("a", "b", 90);
        assertEquals(100, m.add("a", "b", 50));
        assertEquals(-100, m.add("a", "b", -500));
        assertEquals(0, m.get("a", "zzz"));
    }

    @Test
    void indexDriftsBackToItsBaseline() {
        RelationMatrix m = new RelationMatrix();
        m.ensure("a", "b", 0);
        m.add("a", "b", -20);
        m.tick(10, 0.5);
        assertEquals(-15, m.get("a", "b"), 1e-9);
        m.tick(1000, 0.5);
        assertEquals(0, m.get("a", "b"), 1e-9);
        m.add("a", "b", 20);
        m.tick(1000, 0.5);
        assertEquals(0, m.get("a", "b"), 1e-9);
    }

    @Test
    void stanceReadsTheThresholds() {
        assertEquals(Stance.TRADE, RelationMatrix.stance(30, T));
        assertEquals(Stance.NEUTRAL, RelationMatrix.stance(0, T));
        assertEquals(Stance.HOSTILE, RelationMatrix.stance(-30, T));
    }

    @Test
    void snapshotRoundTripsThroughRestore() {
        RelationMatrix m = new RelationMatrix();
        m.ensure("a", "b", 40);
        m.add("a", "b", -25);
        RelationMatrix copy = new RelationMatrix();
        m.snapshot().forEach((k, v) -> copy.restore(k, v[0], v[1]));
        assertEquals(15, copy.get("b", "a"));
        copy.tick(1000, 1);
        assertEquals(40, copy.get("a", "b"), "baseline survives persistence");
    }
}
