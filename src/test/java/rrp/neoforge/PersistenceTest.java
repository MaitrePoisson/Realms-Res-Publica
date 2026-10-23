package rrp.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import rrp.core.CrisisLevel;
import rrp.core.Faction;
import rrp.core.Resource;

import java.nio.file.Files;
import java.nio.file.Path;

class PersistenceTest {

    @TempDir
    Path dir;

    private Persistence persistence(VillageRegistry reg) {
        return new Persistence(reg, dir, NOPLogger.NOP_LOGGER);
    }

    @Test
    void villageSurvivesWriteAndReload() {
        VillageRegistry reg = new VillageRegistry();
        VillageSite s = reg.create("minecraft:overworld", 10.5, 64, -20.25, 48);
        s.state().setBeds(5);
        s.state().setLevel(CrisisLevel.CONTESTATION);
        s.state().setStock(Resource.FOOD, 42);
        s.state().setContestTimer(12.5);
        s.state().setRelations(30);
        s.setWarehouse(3, 64, -7);
        s.setHall(1, 65, -2);
        VillageSite camp = reg.create("minecraft:overworld", 100, 70, 100, 16, Faction.DISSIDENT);
        camp.state().setParentId(s.id());
        assertTrue(persistence(reg).flush());

        VillageRegistry back = new VillageRegistry();
        assertEquals(2, persistence(back).load());
        VillageSite r = back.get(s.id());
        assertNotNull(r);
        assertEquals(5, r.state().beds());
        assertEquals(CrisisLevel.CONTESTATION, r.state().level());
        assertEquals(42, r.state().stock(Resource.FOOD));
        assertEquals(12.5, r.state().contestTimer());
        assertEquals(30, r.state().relations());
        assertEquals(-20.25, r.z());
        assertEquals(3, r.warehouse()[0]);
        assertEquals(-7, r.warehouse()[2]);
        assertEquals(65, r.hall()[1]);
        assertTrue(r.inCore("minecraft:overworld", 2, -1, 12));
        assertFalse(r.inCore("minecraft:overworld", 40, -1, 12));
        assertFalse(back.get(camp.id()).hasHall());
        assertEquals(s.id(), back.get(camp.id()).state().parentId());
        assertEquals(Faction.DISSIDENT, back.get(camp.id()).state().faction());
        // the id counter continues after the reloaded ones
        assertEquals("village-3", back.create("minecraft:overworld", 0, 0, 0, 8).id());
    }

    @Test
    void groupedWritesOnlyWhenDirtyAndIntervalElapsed() {
        VillageRegistry reg = new VillageRegistry();
        Persistence p = persistence(reg);
        reg.create("minecraft:overworld", 0, 0, 0, 8);
        p.onTicks(100, 60, () -> { });
        assertEquals(0, p.writes());
        p.onTicks(1100, 60, () -> { });
        assertEquals(1, p.writes());
        p.onTicks(2400, 60, () -> { });
        assertEquals(1, p.writes());
    }

    @Test
    void corruptFileIsBackedUpAndIgnored() throws Exception {
        Files.writeString(dir.resolve(Persistence.FILE), "{ not json");
        VillageRegistry reg = new VillageRegistry();
        assertEquals(0, persistence(reg).load());
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(f -> f.getFileName().toString().contains(".bak-")));
        }
    }

    @Test
    void findPicksNearestContainingVillageAndMergeKeepsOldest() {
        VillageRegistry reg = new VillageRegistry();
        VillageSite a = reg.create("w", 0, 0, 0, 30);
        VillageSite b = reg.create("w", 20, 0, 0, 30);
        assertEquals(a, reg.find("w", 5, 0));
        assertEquals(b, reg.find("w", 18, 0));
        assertNull(reg.find("other", 5, 0));
        assertEquals(1, reg.mergeNearby());
        assertEquals(1, reg.size());
        assertFalse(reg.all().contains(b));
    }
}
