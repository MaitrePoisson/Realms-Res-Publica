package rrp.core.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import rrp.core.Role;

class DoctrineTest {

    @Test
    void biomesMapToDoctrines() {
        assertEquals(Doctrine.MERCHANT_CITY, DoctrineSelector.ofBiome("plains"));
        assertEquals(Doctrine.MERCHANT_CITY, DoctrineSelector.ofBiome("beach"));
        assertEquals(Doctrine.WARLIKE_EMPIRE, DoctrineSelector.ofBiome("eroded_badlands"));
        assertEquals(Doctrine.WARLIKE_EMPIRE, DoctrineSelector.ofBiome("jagged_peaks"));
        assertEquals(Doctrine.ISOLATIONIST_THEOCRACY, DoctrineSelector.ofBiome("dark_forest"));
        assertEquals(Doctrine.ISOLATIONIST_THEOCRACY, DoctrineSelector.ofBiome("swamp"));
        assertNull(DoctrineSelector.ofBiome("the_void"));
        assertNull(DoctrineSelector.ofBiome(null));
    }

    @Test
    void dominantBiomeDecides() {
        assertEquals(Doctrine.WARLIKE_EMPIRE, DoctrineSelector.select(List.of("badlands", "badlands", "plains")));
        assertEquals(Doctrine.ISOLATIONIST_THEOCRACY, DoctrineSelector.select(List.of("swamp", "mangrove_swamp", "plains")));
    }

    @Test
    void tieOrEmptyFallsBackToMerchantCity() {
        assertEquals(Doctrine.MERCHANT_CITY, DoctrineSelector.select(List.of()));
        assertEquals(Doctrine.MERCHANT_CITY, DoctrineSelector.select(null));
        assertEquals(Doctrine.MERCHANT_CITY, DoctrineSelector.select(List.of("badlands", "plains")));
        assertEquals(Doctrine.MERCHANT_CITY, DoctrineSelector.select(List.of("the_void")));
    }

    @Test
    void modifiersDifferByDoctrine() {
        assertTrue(Doctrine.MERCHANT_CITY.roleWeight(Role.DIPLOMAT) > Doctrine.WARLIKE_EMPIRE.roleWeight(Role.DIPLOMAT));
        assertTrue(Doctrine.WARLIKE_EMPIRE.roleWeight(Role.GUARD) > 1.0);
        assertTrue(Doctrine.WARLIKE_EMPIRE.roleWeight(Role.MINER) > Doctrine.MERCHANT_CITY.roleWeight(Role.MINER));
        assertEquals(1.0, Doctrine.MERCHANT_CITY.roleWeight(Role.FARMER));
        assertTrue(Doctrine.WARLIKE_EMPIRE.expansionAggressiveness() > Doctrine.MERCHANT_CITY.expansionAggressiveness());
        assertTrue(Doctrine.ISOLATIONIST_THEOCRACY.foreignerDistrust() > Doctrine.WARLIKE_EMPIRE.foreignerDistrust());
        assertTrue(Doctrine.WARLIKE_EMPIRE.claimRadiusFactor() > Doctrine.ISOLATIONIST_THEOCRACY.claimRadiusFactor());
        assertTrue(Doctrine.WARLIKE_EMPIRE.wallPriority() > Doctrine.MERCHANT_CITY.wallPriority());
    }
}
