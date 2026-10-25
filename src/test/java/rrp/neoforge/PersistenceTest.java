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
        s.setOwner("abc");
        s.orders().add(rrp.neoforge.urban.BuildOrder.of(rrp.neoforge.urban.BuildType.TOWER, 9, 64, -8));
        s.built().put(rrp.neoforge.urban.BuildType.FARM, 2);
        VillageSite camp = reg.create("minecraft:overworld", 100, 70, 100, 16, Faction.DISSIDENT);
        camp.state().setParentId(s.id());
        assertTrue(persistence(reg).flush());

        VillageRegistry back = new VillageRegistry();
        assertEquals(2, persistence(back).load());
        VillageSite r = back.get(s.id());
        assertNotNull(r);
        assertEquals("abc", r.owner());
        assertEquals(5, r.state().beds());
        assertEquals(CrisisLevel.CONTESTATION, r.state().level());
        assertEquals(42, r.state().stock(Resource.FOOD));
        assertEquals(12.5, r.state().contestTimer());
        assertEquals(30, r.state().relations());
        assertEquals(-20.25, r.z());
        assertEquals(3, r.warehouse()[0]);
        assertEquals(-7, r.warehouse()[2]);
        assertEquals(65, r.hall()[1]);
        assertEquals(1, r.orders().size());
        assertEquals(rrp.neoforge.urban.BuildType.TOWER, r.orders().get(0).type());
        assertEquals(40, r.orders().get(0).stone());
        assertEquals(2, r.built().get(rrp.neoforge.urban.BuildType.FARM));
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

    @Test
    void playerRolesAndPurseSurviveReload() {
        java.util.UUID id = java.util.UUID.randomUUID();
        PlayerService.ledger().clear();
        PlayerService.ledger().switchRole(id, rrp.core.player.PlayerRole.MERCHANT, 5, 0);
        PlayerService.ledger().deposit(id, 12);
        VillageRegistry reg = new VillageRegistry();
        assertTrue(persistence(reg).flush());
        PlayerService.ledger().clear();
        persistence(new VillageRegistry()).load();
        assertEquals(rrp.core.player.PlayerRole.MERCHANT, PlayerService.roleOf(id));
        assertEquals(12, PlayerService.ledger().purse(id));
        PlayerService.ledger().clear();
    }

    @Test
    void lawsAndTaxesSurviveReload() {
        PlayerService.laws().clear();
        var l = PlayerService.laws().of("v1");
        l.setOpenBorders(true);
        l.setConscription(0.4);
        l.toggleEmbargo("v2");
        l.setTax("FOOD", 0.25);
        assertTrue(persistence(new VillageRegistry()).flush());
        PlayerService.laws().clear();
        persistence(new VillageRegistry()).load();
        var r = PlayerService.laws().peek("v1");
        assertTrue(r != null && r.openBorders() && r.embargoes("v2"));
        assertEquals(0.4, r.conscription(), 1e-9);
        assertEquals(0.25, r.tax("FOOD"), 1e-9);
        PlayerService.laws().clear();
    }

    @Test
    void zonesSurviveReload() {
        PlayerService.zones().clear();
        PlayerService.zones().add("v1", rrp.core.player.Zones.Zone.of(rrp.core.player.ZoneType.MARKET, 5, 6, -3, 20));
        assertTrue(persistence(new VillageRegistry()).flush());
        PlayerService.zones().clear();
        persistence(new VillageRegistry()).load();
        assertEquals(rrp.core.player.ZoneType.MARKET, PlayerService.zones().typeAt("v1", 0, 10));
        assertEquals(rrp.core.player.ZoneType.MARKET, PlayerService.zones().typeAt("v1", -3, 6));
        PlayerService.zones().clear();
    }

    @Test
    void contractsAndReputationSurviveReload() {
        java.util.UUID merc = java.util.UUID.randomUUID();
        PlayerService.contracts().clear();
        var open = PlayerService.contracts().post("v1", rrp.core.player.Contract.Type.CLEAR_CAMP, "camp1", 7, 10);
        var taken = PlayerService.contracts().post("v1", rrp.core.player.Contract.Type.WAR_ATTACK, "v2", 9, 11);
        PlayerService.contracts().accept(taken.id(), merc, 20, 2);
        taken.addKill();
        taken.setMark(3);
        PlayerService.contracts().restoreReputation(merc, 4);
        assertTrue(persistence(new VillageRegistry()).flush());
        PlayerService.contracts().clear();
        persistence(new VillageRegistry()).load();
        var a = PlayerService.contracts().get(open.id());
        var b = PlayerService.contracts().get(taken.id());
        assertTrue(a != null && b != null);
        assertEquals(rrp.core.player.Contract.State.OPEN, a.state());
        assertEquals(7, a.pay());
        assertEquals(rrp.core.player.Contract.State.ACCEPTED, b.state());
        assertEquals(merc, b.mercenary());
        assertEquals(1, b.kills());
        assertEquals(3, b.mark());
        assertEquals(4, PlayerService.contracts().reputation(merc));
        assertTrue(PlayerService.contracts().post("v3", rrp.core.player.Contract.Type.CLEAR_CAMP, "c", 1, 0).id() > b.id(),
                "ids keep growing after a reload");
        PlayerService.contracts().clear();
    }
}
