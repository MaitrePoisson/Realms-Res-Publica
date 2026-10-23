package rrp.neoforge.urban;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoadNetworkTest {

    private static final int B = RoadNetwork.BLOCKED;

    @Test
    void straightPathOnFlatGround() {
        int[] p = RoadNetwork.findPath((x, z) -> 64, 0, 0, 10, 0);
        assertNotNull(p);
        assertEquals(11, p.length / 2);
        assertEquals(0, p[0]);
        assertEquals(10, p[p.length - 2]);
    }

    @Test
    void detoursAroundAWallThroughItsGap() {
        // wall at x=5 from z=-8..8 (water), gap at z=9
        int[] p = RoadNetwork.findPath((x, z) -> x == 5 && z >= -8 && z <= 8 ? B : 64, 0, 0, 10, 0);
        assertNotNull(p);
        boolean crossesGap = false;
        for (int i = 0; i < p.length / 2; i++) {
            int x = p[2 * i];
            int z = p[2 * i + 1];
            assertTrue(!(x == 5 && z >= -8 && z <= 8), "path walks through the wall");
            if (x == 5 && z == 9) {
                crossesGap = true;
            }
            if (i > 0) {
                assertEquals(1, Math.abs(x - p[2 * i - 2]) + Math.abs(z - p[2 * i - 1]), "tiles not adjacent");
            }
        }
        assertTrue(crossesGap);
    }

    @Test
    void cliffsAreNotCrossedAndUnreachableGivesNull() {
        // a 2-high step all along x=5 cannot be climbed
        assertNull(RoadNetwork.findPath((x, z) -> x >= 5 ? 66 : 64, 0, 0, 10, 0));
        // a single stair step of 1 is fine
        assertNotNull(RoadNetwork.findPath((x, z) -> x >= 5 ? 65 : 64, 0, 0, 10, 0));
    }
}
