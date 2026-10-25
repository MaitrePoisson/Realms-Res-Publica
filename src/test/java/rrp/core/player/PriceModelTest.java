package rrp.core.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PriceModelTest {

    private final PriceModel m = new PriceModel(0.5, 100, 0.25, 3, 0.1);

    @Test
    void priceFallsWithStockWithinBounds() {
        assertEquals(1.5, m.unitPrice(0), 1e-9, "empty store is capped at 3x");
        assertEquals(0.5, m.unitPrice(100), 1e-9, "stock = need is the base price");
        assertEquals(0.125, m.unitPrice(10_000), 1e-9, "a glut is floored at 0.25x");
        assertTrue(m.unitPrice(40) > m.unitPrice(80));
    }

    @Test
    void buyingIsDearerThanSellingAtTheSameStock() {
        assertTrue(m.buyCost(100, 10) > m.sellRevenue(100, 10));
        assertEquals(0, m.sellRevenue(100, 1), "a single unit rounds down to nothing");
    }

    @Test
    void buyingPushesThePriceUp() {
        long first = m.buyCost(100, 10);
        long next = m.buyCost(50, 10);
        assertTrue(next > first, first + " then " + next);
    }

    @Test
    void arbitrageBetweenSurplusAndScarcityIsProfitable() {
        long paid = m.buyCost(400, 30);   // surplus village
        long earned = m.sellRevenue(10, 30); // starving village
        assertTrue(earned > paid, "bought for " + paid + ", sold for " + earned);
    }

    @Test
    void roundTripInTheSameVillageLosesMoney() {
        long paid = m.buyCost(100, 20);
        long back = m.sellRevenue(80, 20);
        assertTrue(back < paid, paid + " then " + back);
    }

    @Test
    void affordableRespectsPurse() {
        int n = m.affordable(100, 50, 5);
        assertTrue(n > 0 && n < 50);
        assertTrue(m.buyCost(100, n) <= 5 && m.buyCost(100, n + 1) > 5);
        assertEquals(0, m.affordable(100, 50, 0));
    }

    @Test
    void saturationCapsAndResets() {
        PriceModel.Saturation s = new PriceModel.Saturation();
        assertEquals(64, s.remaining("v", "FOOD", 64));
        s.record("v", "FOOD", 60);
        assertEquals(4, s.remaining("v", "FOOD", 64));
        assertEquals(64, s.remaining("v", "WOOD", 64));
        s.record("v", "FOOD", 10);
        assertEquals(0, s.remaining("v", "FOOD", 64));
        s.reset();
        assertEquals(64, s.remaining("v", "FOOD", 64));
    }
}
