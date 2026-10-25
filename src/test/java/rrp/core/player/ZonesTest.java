package rrp.core.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZonesTest {

    @Test
    void lookupAndPriority() {
        Zones zones = new Zones();
        zones.add("a", Zones.Zone.of(ZoneType.RESIDENTIAL, 10, 10, 0, 0));
        assertEquals(ZoneType.RESIDENTIAL, zones.typeAt("a", 5, 5));
        assertNull(zones.typeAt("a", 11, 5));
        assertNull(zones.typeAt("b", 5, 5));
        zones.add("a", Zones.Zone.of(ZoneType.FARM, 0, 0, 20, 20));
        assertEquals(ZoneType.FARM, zones.typeAt("a", 5, 5), "latest plot wins");
        zones.add("a", Zones.Zone.of(ZoneType.FORBIDDEN, 4, 4, 6, 6));
        assertEquals(ZoneType.FORBIDDEN, zones.typeAt("a", 5, 5), "forbidden beats everything");
        assertEquals(ZoneType.FARM, zones.typeAt("a", 8, 8));
        zones.clear("a");
        assertNull(zones.typeAt("a", 8, 8));
    }

    @Test
    void spotsStayInsideTheirPlotAndWrap() {
        var list = new java.util.ArrayList<Zones.Zone>();
        list.add(Zones.Zone.of(ZoneType.MARKET, 100, 100, 115, 107));
        assertNull(Zones.spot(list, ZoneType.FARM, 0, 8));
        int[] first = Zones.spot(list, ZoneType.MARKET, 0, 8);
        assertArrayEquals(new int[] {104, 104}, first);
        for (int i = 0; i < 6; i++) {
            int[] p = Zones.spot(list, ZoneType.MARKET, i, 8);
            assertTrue(list.get(0).contains(p[0], p[1]));
        }
        assertArrayEquals(first, Zones.spot(list, ZoneType.MARKET, 2, 8), "2 cells, wraps");
        assertEquals(ZoneType.BARRACKS, ZoneType.parse("barracks"));
        assertNull(ZoneType.parse("castle"));
    }
}
