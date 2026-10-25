package rrp.core.abstractsim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LodPolicyTest {

    @Test
    void liveVillageIsAlwaysActive() {
        assertEquals(Lod.ACTIVE, LodPolicy.next(Lod.ABSTRACT, true, 0, 600));
        assertEquals(Lod.ACTIVE, LodPolicy.next(Lod.ACTIVE, true, 0, 600));
    }

    @Test
    void turnsAbstractOnlyAfterTheGracePeriod() {
        assertEquals(Lod.ACTIVE, LodPolicy.next(Lod.ACTIVE, false, 599, 600));
        assertEquals(Lod.ABSTRACT, LodPolicy.next(Lod.ACTIVE, false, 600, 600));
    }

    @Test
    void abstractStaysAbstractWhileNotLive() {
        assertEquals(Lod.ABSTRACT, LodPolicy.next(Lod.ABSTRACT, false, 0, 600));
    }

    @Test
    void zeroGraceSwitchesImmediately() {
        assertEquals(Lod.ABSTRACT, LodPolicy.next(Lod.ACTIVE, false, 0, 0));
    }
}
