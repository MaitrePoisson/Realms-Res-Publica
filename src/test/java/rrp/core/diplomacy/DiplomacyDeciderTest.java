package rrp.core.diplomacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import rrp.core.diplomacy.DiplomacyDecider.Action;
import rrp.core.diplomacy.DiplomacyDecider.Rules;
import rrp.core.diplomacy.DiplomacyDecider.Situation;
import rrp.core.diplomacy.Treaty.State;

class DiplomacyDeciderTest {

    private static final Rules R = new Rules(30, 60, -30, 3);

    private static Action decide(State st, double idx, int inc, boolean enemy, boolean shortage) {
        return DiplomacyDecider.decide(new Situation(st, idx, inc, enemy, shortage), R);
    }

    @Test
    void tradeTreatyWhenRelationIsGood() {
        assertEquals(Action.PROPOSE_TRADE, decide(State.PEACE, 35, 0, false, false));
        assertEquals(Action.NONE, decide(State.PEACE, 10, 0, false, false));
        assertEquals(Action.NONE, decide(State.TRADE_TREATY, 35, 0, false, false));
    }

    @Test
    void allianceNeedsHighRelationAndCommonEnemy() {
        assertEquals(Action.PROPOSE_ALLIANCE, decide(State.TRADE_TREATY, 70, 0, true, false));
        assertEquals(Action.PROPOSE_TRADE, decide(State.PEACE, 70, 0, false, false));
        assertEquals(Action.NONE, decide(State.DEFENSIVE_ALLIANCE, 70, 0, true, false));
    }

    @Test
    void borderDisputeDeclaresWar() {
        assertEquals(Action.DECLARE_WAR, decide(State.TRADE_TREATY, 20, 3, false, false));
        assertEquals(Action.NONE, decide(State.PEACE, 20, 2, false, false));
    }

    @Test
    void shortageWithLowRelationDeclaresWar() {
        assertEquals(Action.DECLARE_WAR, decide(State.PEACE, -40, 0, false, true));
        assertEquals(Action.NONE, decide(State.PEACE, 0, 0, false, true));
    }

    @Test
    void hostileRelationBreaksTreaty() {
        assertEquals(Action.BREAK_TREATY, decide(State.TRADE_TREATY, -35, 0, false, false));
    }

    @Test
    void nothingMoreOnceAtWar() {
        assertEquals(Action.NONE, decide(State.WAR, -90, 9, false, true));
    }

    @Test
    void warBreaksTheTreatyAndAlliesJoinTheDefender() {
        Treaty t = new Treaty();
        t.set("A", "B", State.TRADE_TREATY);
        t.set("B", "C", State.DEFENSIVE_ALLIANCE);
        t.set("A", "D", State.DEFENSIVE_ALLIANCE);
        List<String> joined = t.declareWar("A", "B");
        assertEquals(State.WAR, t.get("B", "A"));
        assertEquals(List.of("C"), joined);
        assertTrue(t.atWar("C", "A"));
        assertEquals(State.DEFENSIVE_ALLIANCE, t.get("A", "D"));
    }

    @Test
    void peaceRemovesTheEntry() {
        Treaty t = new Treaty();
        t.set("A", "B", State.WAR);
        t.set("B", "A", State.PEACE);
        assertEquals(0, t.snapshot().size());
    }
}
