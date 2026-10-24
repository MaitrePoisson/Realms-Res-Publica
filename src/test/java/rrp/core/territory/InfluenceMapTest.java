package rrp.core.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import rrp.core.territory.InfluenceMap.Marker;

class InfluenceMapTest {

    private static InfluenceMap map() {
        return new InfluenceMap(0.2, 0.1);
    }

    @Test
    void chunkOfBlockHandlesNegatives() {
        assertEquals(new ChunkPos(-1, 0), ChunkPos.ofBlock(-1, 15));
        assertEquals(new ChunkPos(1, -2), ChunkPos.ofBlock(16, -17));
    }

    @Test
    void influenceDecaysWithDistanceAndStopsAtRadius() {
        var m = map();
        m.put(new Marker("hall", "A", new ChunkPos(0, 0), 4, 1.0));
        double near = m.influences(new ChunkPos(1, 0)).get("A");
        double far = m.influences(new ChunkPos(3, 0)).get("A");
        assertTrue(near > far);
        assertTrue(m.influences(new ChunkPos(5, 0)).isEmpty());
    }

    @Test
    void ownerNeedsThreshold() {
        var m = map();
        m.put(new Marker("hall", "A", new ChunkPos(0, 0), 4, 1.0));
        assertEquals("A", m.owner(new ChunkPos(0, 0)));
        assertEquals("A", m.owner(new ChunkPos(2, 0)));
        assertNull(m.owner(new ChunkPos(4, 4)));
    }

    @Test
    void strongerFactionWinsAndMarginMakesContest() {
        var m = map();
        m.put(new Marker("a", "A", new ChunkPos(0, 0), 5, 1.0));
        m.put(new Marker("b", "B", new ChunkPos(6, 0), 5, 1.0));
        Claim mid = m.claim(new ChunkPos(3, 0));
        assertTrue(mid.contested());
        assertEquals("B", mid.rival());
        Claim nearA = m.claim(new ChunkPos(1, 0));
        assertEquals("A", nearA.owner());
        assertFalse(nearA.contested());
    }

    @Test
    void markersOfSameFactionAdd() {
        var m = map();
        m.put(new Marker("a", "A", new ChunkPos(0, 0), 3, 0.5));
        double one = m.claim(new ChunkPos(1, 0)).influence();
        m.put(new Marker("a2", "A", new ChunkPos(2, 0), 3, 0.5));
        assertTrue(m.claim(new ChunkPos(1, 0)).influence() > one);
    }

    @Test
    void removingMarkerFreesChunks() {
        var m = map();
        m.put(new Marker("a", "A", new ChunkPos(0, 0), 3, 1.0));
        assertFalse(m.claim(new ChunkPos(0, 0)).free());
        assertTrue(m.remove("a"));
        assertTrue(m.claim(new ChunkPos(0, 0)).free());
        assertFalse(m.remove("a"));
    }

    @Test
    void tieIsDeterministicByFactionId() {
        var m = map();
        m.put(new Marker("b", "B", new ChunkPos(-2, 0), 5, 1.0));
        m.put(new Marker("a", "A", new ChunkPos(2, 0), 5, 1.0));
        Claim c = m.claim(new ChunkPos(0, 0));
        assertEquals("A", c.owner());
        assertTrue(c.contested());
    }

    @Test
    void claimedByListsChunks() {
        var m = map();
        m.put(new Marker("a", "A", new ChunkPos(0, 0), 2, 1.0));
        var l = m.claimedBy("A", -3, -3, 3, 3);
        assertTrue(l.contains(new ChunkPos(0, 0)));
        assertFalse(l.contains(new ChunkPos(3, 3)));
    }
}
