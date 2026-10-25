package rrp.core.diplomacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import rrp.core.Resource;

class TradePlannerTest {

    @Test
    void ironIsSwappedForMoreWheat() {
        var a = Map.of(Resource.ORE, 120, Resource.FOOD, 10);
        var b = Map.of(Resource.FOOD, 200, Resource.ORE, 5);
        var offer = TradePlanner.plan(a, b, 100, 40, 32).orElseThrow();
        assertEquals(Resource.ORE, offer.give());
        assertEquals(Resource.FOOD, offer.get());
        assertTrue(offer.giveAmount() * 3 >= offer.getAmount() && (offer.giveAmount() - 1) * 3 < offer.getAmount(), "ore is worth three food");
        assertTrue(offer.getAmount() <= 32 && offer.giveAmount() <= 80);
    }

    @Test
    void noTradeWithoutMutualNeed() {
        var a = Map.of(Resource.ORE, 120, Resource.FOOD, 80);
        var b = Map.of(Resource.FOOD, 200, Resource.ORE, 5);
        assertTrue(TradePlanner.plan(a, b, 100, 40, 32).isEmpty(), "A has no deficit");
        assertTrue(TradePlanner.plan(Map.of(), Map.of(), 100, 40, 32).isEmpty());
    }

    @Test
    void aVillageNeverGivesBelowItsReserve() {
        var a = Map.of(Resource.WOOD, 105, Resource.FOOD, 0);
        var b = Map.of(Resource.FOOD, 500);
        var offer = TradePlanner.plan(a, b, 100, 40, 32).orElseThrow();
        assertTrue(a.get(Resource.WOOD) - offer.giveAmount() >= 40 || offer.giveAmount() <= 65);
        assertEquals(Resource.WOOD, offer.give());
    }

    @Test
    void escortsHalveTheAmbushChance() {
        assertEquals(0.2, TradePlanner.ambushChance(0.2, 0), 1e-9);
        assertEquals(0.05, TradePlanner.ambushChance(0.2, 2), 1e-9);
    }
}
