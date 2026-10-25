package rrp.core.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import rrp.core.territory.FactionRelations.Stance;

class FactionRelationsTest {

    @Test
    void doctrinePairsGiveTheBaseStance() {
        assertEquals(Stance.TRADE, FactionRelations.base(Doctrine.MERCHANT_CITY, Doctrine.MERCHANT_CITY));
        assertEquals(Stance.NEUTRAL, FactionRelations.base(Doctrine.MERCHANT_CITY, Doctrine.WARLIKE_EMPIRE));
        assertEquals(Stance.HOSTILE, FactionRelations.base(Doctrine.WARLIKE_EMPIRE, Doctrine.ISOLATIONIST_THEOCRACY));
        assertEquals(Stance.HOSTILE, FactionRelations.base(Doctrine.WARLIKE_EMPIRE, Doctrine.WARLIKE_EMPIRE));
        assertEquals(Stance.NEUTRAL, FactionRelations.base(null, Doctrine.WARLIKE_EMPIRE));
    }

    @Test
    void incidentsDegradeTheStance() {
        assertEquals(Stance.NEUTRAL, FactionRelations.stance(Doctrine.MERCHANT_CITY, Doctrine.MERCHANT_CITY, 1));
        assertEquals(Stance.HOSTILE, FactionRelations.stance(Doctrine.MERCHANT_CITY, Doctrine.MERCHANT_CITY, 3));
        assertEquals(Stance.HOSTILE, FactionRelations.stance(Doctrine.ISOLATIONIST_THEOCRACY, Doctrine.ISOLATIONIST_THEOCRACY, 1));
        assertEquals(Stance.HOSTILE, FactionRelations.stance(Doctrine.WARLIKE_EMPIRE, Doctrine.WARLIKE_EMPIRE, 0));
    }
}
