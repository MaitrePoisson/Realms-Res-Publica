package rrp.core.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import rrp.core.territory.InfluenceMap.Marker;

class BorderPlanTest {

    @Test
    void postsLieOnTheEdgeOfClaimedChunks() {
        var m = new InfluenceMap(0.2, 0.1);
        m.put(new Marker("a", "A", new ChunkPos(0, 0), 2, 1.0));
        var posts = BorderPlan.posts(m, "A");
        assertFalse(posts.isEmpty());
        for (int[] p : posts) {
            ChunkPos c = ChunkPos.ofBlock(p[0], p[1]);
            assertEquals("A", m.owner(c));
        }
        // the centre chunk is interior: no post in it
        assertTrue(posts.stream().noneMatch(p -> ChunkPos.ofBlock(p[0], p[1]).equals(new ChunkPos(0, 0))));
    }

    @Test
    void noPostBetweenTwoChunksOfTheSameFaction() {
        var m = new InfluenceMap(0.2, 0.1);
        m.put(new Marker("a", "A", new ChunkPos(0, 0), 2, 1.0));
        var posts = BorderPlan.posts(m, "A");
        // chunk (0,0) and (1,0) both owned: the east side of (0,0) (x=15) must have no post
        assertTrue(posts.stream().noneMatch(p -> p[0] == 15 && p[1] == 8));
    }

    @Test
    void deterministicAndUnknownFactionEmpty() {
        var m = new InfluenceMap(0.2, 0.1);
        m.put(new Marker("a", "A", new ChunkPos(3, -2), 3, 1.0));
        var one = BorderPlan.posts(m, "A");
        var two = BorderPlan.posts(m, "A");
        assertEquals(one.size(), two.size());
        for (int i = 0; i < one.size(); i++) {
            assertEquals(one.get(i)[0], two.get(i)[0]);
            assertEquals(one.get(i)[1], two.get(i)[1]);
        }
        assertTrue(BorderPlan.posts(m, "Z").isEmpty());
    }
}
