package rrp.neoforge.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnitProgressTest {

    @Test
    void unitsAreCountedWhenTheirLastBlockIsDown() {
        List<Integer> seen = new ArrayList<>();
        UnitProgress p = new UnitProgress(seen::add);
        p.unitEnds(3);
        p.unitEnds(6);
        p.unitEnds(9);
        p.accept(1);
        p.accept(2);
        assertEquals(List.of(), seen);
        p.accept(3);
        assertEquals(List.of(1), seen);
        p.accept(7);
        assertEquals(List.of(1, 1), seen);
        p.accept(9);
        assertEquals(List.of(1, 1, 1), seen);
        p.accept(9);
        assertEquals(3, seen.size());
    }

    @Test
    void aUnitWithNoBlockCountsWithTheOnesBefore() {
        List<Integer> seen = new ArrayList<>();
        UnitProgress p = new UnitProgress(seen::add);
        p.unitEnds(2);
        p.unitEnds(2); // nothing to lay in this one
        p.unitEnds(2);
        p.unitEnds(4);
        p.accept(1);
        assertEquals(List.of(), seen);
        p.accept(2);
        assertEquals(List.of(3), seen);
        p.accept(4);
        assertEquals(List.of(3, 1), seen);
    }

    @Test
    void anEmptyJobIsFinishedAtOnce() {
        int[] total = {0};
        UnitProgress p = new UnitProgress(n -> total[0] += n);
        p.unitEnds(0);
        p.unitEnds(0);
        p.finish();
        assertEquals(2, total[0]);
        assertEquals(2, p.units());
    }

    @Test
    void finishCountsWhatIsLeft() {
        int[] total = {0};
        UnitProgress p = new UnitProgress(n -> total[0] += n);
        p.unitEnds(3);
        p.unitEnds(6);
        p.accept(3);
        p.finish();
        assertEquals(2, total[0]);
    }
}
