package rrp.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import rrp.core.diplomacy.DiplomacyDecider;
import rrp.core.diplomacy.Treaty.State;
import rrp.core.player.Proposals.Answer;
import rrp.core.player.Proposals.Kind;

class PoliticalMapTest {

    private static final DiplomacyDecider.Rules RULES = new DiplomacyDecider.Rules(30, 60, -30, 3);

    @Test
    void bordersSeparateFactionsAndWarHatchesTheBelligerents() {
        // west of block 0 belongs to faction 1, east to faction 2
        int[] px = MapRaster.render(32, 8, 0, 0, (cx, cz) -> cx < 0 ? 1 : 2, Set.of(2), List.of(), List.of());
        int mid = 16;
        int borders = 0;
        int war = 0;
        int ones = 0;
        for (int y = 0; y < 32; y++) {
            if (px[y * 32 + mid - 1] == MapRaster.BORDER) {
                borders++;
            }
            for (int x = 0; x < 32; x++) {
                war += px[y * 32 + x] == MapRaster.WAR ? 1 : 0;
                ones += px[y * 32 + x] == 1 ? 1 : 0;
                assertTrue(x >= mid || px[y * 32 + x] != 2 && px[y * 32 + x] != MapRaster.WAR);
            }
        }
        assertEquals(32, borders, "one border pixel per row between the two");
        assertTrue(war > 0, "faction 2 is hatched");
        assertTrue(ones > 400, "faction 1 fills its half");
    }

    @Test
    void routesAndMarkersAreDrawn() {
        int[] px = MapRaster.render(64, 4, 0, 0, (cx, cz) -> 0, Set.of(), List.of(new int[] {-80, 0, 80, 0}), List.of(new int[] {0, 60}));
        long route = java.util.Arrays.stream(px).filter(c -> c == MapRaster.ROUTE).count();
        assertTrue(route > 20, "dotted route: " + route);
        assertEquals(MapRaster.MARKER, px[(32 + 15) * 64 + 32]);
        assertEquals(MapRaster.EMPTY, px[0]);
    }

    @Test
    void emissariesAreAnsweredByTheDecider() {
        assertEquals(Answer.ACCEPTED, Proposals.answer(Kind.TRADE, State.PEACE, 40, false, RULES));
        assertEquals(Answer.REFUSED, Proposals.answer(Kind.TRADE, State.PEACE, 10, false, RULES));
        assertEquals(Answer.INVALID, Proposals.answer(Kind.TRADE, State.WAR, 40, false, RULES));
        assertEquals(Answer.ACCEPTED, Proposals.answer(Kind.ALLIANCE, State.TRADE_TREATY, 70, false, RULES));
        assertEquals(Answer.REFUSED, Proposals.answer(Kind.ALLIANCE, State.TRADE_TREATY, 40, false, RULES));
        assertEquals(Answer.INVALID, Proposals.answer(Kind.CEASEFIRE, State.PEACE, 0, false, RULES));
        assertEquals(Answer.ACCEPTED, Proposals.answer(Kind.CEASEFIRE, State.WAR, -40, false, RULES));
        assertEquals(Answer.REFUSED, Proposals.answer(Kind.CEASEFIRE, State.WAR, -90, false, RULES));
        assertEquals(Answer.ACCEPTED, Proposals.answer(Kind.CEASEFIRE, State.WAR, -90, true, RULES));
        assertEquals(Answer.ACCEPTED, Proposals.answer(Kind.ULTIMATUM, State.PEACE, -5, false, RULES));
        assertEquals(Answer.REFUSED, Proposals.answer(Kind.ULTIMATUM, State.PEACE, 50, false, RULES));
    }
}
