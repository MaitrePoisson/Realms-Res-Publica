package rrp.core.diplomacy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import rrp.core.diplomacy.WarOutcome.Kind;
import rrp.core.diplomacy.WarOutcome.Side;

class WarOutcomeTest {

    private static final WarOutcome.Rules RULES = new WarOutcome.Rules(1.2, 2.0, 3.0);

    @Test
    void closeFightIsRepelled() {
        var v = WarOutcome.decide(new Side(10, 1, 0, 12), new Side(10, 1, 0, 12), RULES);
        assertEquals(Kind.REPELLED, v.kind());
    }

    @Test
    void moderateAdvantageIsPillage() {
        var v = WarOutcome.decide(new Side(15, 1, 0, 12), new Side(10, 1, 0, 12), RULES);
        assertEquals(0, v.winner());
        assertEquals(Kind.PILLAGE, v.kind());
    }

    @Test
    void strongAdvantageIsAnnexation() {
        var v = WarOutcome.decide(new Side(10, 1, 0, 12), new Side(25, 1, 0, 12), RULES);
        assertEquals(1, v.winner());
        assertEquals(Kind.ANNEXATION, v.kind());
    }

    @Test
    void crushingAdvantageNeedsFullBreachToDestroy() {
        assertEquals(Kind.ANNEXATION, WarOutcome.decide(new Side(40, 1, 5, 12), new Side(10, 1, 0, 12), RULES).kind());
        assertEquals(Kind.DESTRUCTION, WarOutcome.decide(new Side(40, 1, 12, 12), new Side(10, 1, 0, 12), RULES).kind());
    }

    @Test
    void attritionAndBreachesChangeThePower() {
        assertEquals(5.0, WarOutcome.power(new Side(10, 0.5, 0, 12)), 1e-9);
        assertEquals(20.0, WarOutcome.power(new Side(10, 1, 12, 12)), 1e-9);
    }

    @Test
    void lootAndTributeAreCapped() {
        assertEquals(20, WarOutcome.loot(100, 0.2, 50));
        assertEquals(50, WarOutcome.loot(1000, 0.5, 50));
        assertEquals(0, WarOutcome.tribute(-5, 0.5, 50));
    }
}
