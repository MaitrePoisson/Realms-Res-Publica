package rrp.core.logistics;

import org.junit.jupiter.api.Test;
import rrp.core.logistics.Forge.Piece;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeTest {

    private static Map<Piece, Integer> of(Object... kv) {
        Map<Piece, Integer> m = new EnumMap<>(Piece.class);
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Piece) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    @Test
    void costsFollowTheConfiguredOreAndWood() {
        assertEquals(new Forge.Cost(4, 0), Forge.cost(Piece.CHESTPLATE, 2, 4, 2));
        assertEquals(new Forge.Cost(3, 0), Forge.cost(Piece.LEGGINGS, 2, 4, 2));
        assertEquals(new Forge.Cost(2, 0), Forge.cost(Piece.HELMET, 2, 4, 2));
        assertEquals(new Forge.Cost(2, 0), Forge.cost(Piece.BOOTS, 2, 4, 2));
        assertEquals(new Forge.Cost(2, 0), Forge.cost(Piece.SWORD, 2, 4, 2));
        assertEquals(new Forge.Cost(0, 2), Forge.cost(Piece.BOW, 2, 4, 2));
    }

    @Test
    void everyPieceCostsAtLeastOneWhenPricedAtOne() {
        for (Piece p : Piece.values()) {
            Forge.Cost c = Forge.cost(p, 1, 1, 1);
            assertTrue(c.ore() + c.wood() >= 1, p + " must not be free");
        }
    }

    @Test
    void shortfallIsWhatIsLackingMinusWhatTheArmouryHolds() {
        var lacking = of(Piece.CHESTPLATE, 3, Piece.SWORD, 2, Piece.BOW, 1);
        var stocked = of(Piece.CHESTPLATE, 1, Piece.SWORD, 5);
        assertEquals(of(Piece.CHESTPLATE, 2, Piece.BOW, 1), Forge.shortfall(lacking, stocked));
    }

    @Test
    void nextPicksTheBodyArmourBeforeTheWeaponAndTheRest() {
        assertEquals(Piece.CHESTPLATE, Forge.next(of(Piece.BOOTS, 1, Piece.SWORD, 1, Piece.CHESTPLATE, 1)).orElseThrow());
        assertEquals(Piece.SWORD, Forge.next(of(Piece.BOOTS, 1, Piece.SWORD, 1)).orElseThrow());
        assertEquals(Piece.BOOTS, Forge.next(of(Piece.BOOTS, 2)).orElseThrow());
    }

    @Test
    void nothingToForgeWhenNothingIsShort() {
        assertTrue(Forge.next(Map.of()).isEmpty());
        assertTrue(Forge.shortfall(of(Piece.SWORD, 1), of(Piece.SWORD, 1)).isEmpty());
    }

    @Test
    void affordabilityNeedsBothOreAndWood() {
        assertTrue(Forge.affordable(new Forge.Cost(4, 0), 4, 0));
        assertFalse(Forge.affordable(new Forge.Cost(4, 0), 3, 99));
        assertFalse(Forge.affordable(new Forge.Cost(0, 2), 99, 1));
    }
}
