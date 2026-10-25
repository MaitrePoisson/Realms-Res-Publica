package rrp.core.diplomacy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import rrp.core.diplomacy.Regiment.Costs;

class RegimentTest {

    private static final Costs COSTS = new Costs(2, 4, 2);

    @Test
    void sizeIsCappedByPopulationShare() {
        assertEquals(2, Regiment.size(5, 4, 0.5));
        assertEquals(3, Regiment.size(3, 20, 0.5));
        assertEquals(1, Regiment.size(3, 1, 0.1));
        assertEquals(0, Regiment.size(0, 10, 0.5));
    }

    @Test
    void swordsComeFirstThenArmorThenBows() {
        var l = Regiment.equip(4, 10, 100, COSTS);
        assertEquals(4, l.swords());
        assertEquals(0, l.armors());
        assertEquals(4, l.bows());
        assertEquals(8, l.oreSpent());
        assertEquals(8, l.woodSpent());
    }

    @Test
    void plentyOfIronEquipsEveryone() {
        var l = Regiment.equip(3, 100, 100, COSTS);
        assertEquals(3, l.swords());
        assertEquals(3, l.armors());
        assertEquals(3 * 2 + 3 * 4, l.oreSpent());
    }

    @Test
    void missingGearLowersStrength() {
        double full = Regiment.strength(4, Regiment.equip(4, 100, 100, COSTS));
        double bare = Regiment.strength(4, Regiment.equip(4, 0, 0, COSTS));
        double half = Regiment.strength(4, Regiment.equip(4, 4, 0, COSTS));
        assertEquals(4 * 3.5, full, 1e-9);
        assertEquals(4.0, bare, 1e-9);
        assertEquals(6.0, half, 1e-9);
        assertEquals(0, Regiment.strength(0, Regiment.equip(0, 100, 100, COSTS)), 1e-9);
    }
}
