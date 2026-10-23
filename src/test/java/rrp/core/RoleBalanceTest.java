package rrp.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleBalanceTest {

    private static List<VillagerState> assignAll(int n, Role hint) {
        List<VillagerState> all = new ArrayList<>();
        List<VillagerState> fresh = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            VillagerState v = new VillagerState(UUID.randomUUID(), Role.FARMER, "village-1");
            v.setRole(RoleBalance.choose(RoleBalance.count(all), n, hint));
            all.add(v);
            fresh.add(v);
        }
        RoleBalance.ensureEssentials(all, fresh);
        return all;
    }

    @Test
    void hintIsFollowedWhenNotOverrepresented() {
        assertEquals(Role.MINER, RoleBalance.choose(RoleBalance.count(List.of()), 6, Role.MINER));
    }

    @Test
    void overrepresentedHintFallsBackToLeastRepresentedRole() {
        List<VillagerState> all = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            all.add(new VillagerState(UUID.randomUUID(), Role.MINER, "v"));
        }
        // population 6 : plafond 2 par rôle d'indice, le troisième mineur devient autre chose
        Role r = RoleBalance.choose(RoleBalance.count(all), 6, Role.MINER);
        assertTrue(r != Role.MINER);
    }

    @Test
    void noHintGivesLeastRepresentedRoleInPriorityOrder() {
        assertEquals(Role.FARMER, RoleBalance.choose(RoleBalance.count(List.of()), 4, null));
        List<VillagerState> all = List.of(new VillagerState(UUID.randomUUID(), Role.FARMER, "v"));
        assertEquals(Role.GUARD, RoleBalance.choose(RoleBalance.count(all), 4, null));
    }

    @Test
    void sufficientPopulationHasGuardStewardAndDiplomatEvenIfEveryoneHintsFarmer() {
        for (int n = 5; n <= 12; n++) {
            var counts = RoleBalance.count(assignAll(n, Role.FARMER));
            assertTrue(counts.get(Role.GUARD) >= 1, "garde absent pour " + n);
            assertTrue(counts.get(Role.STEWARD) >= 1, "intendant absent pour " + n);
            assertTrue(counts.get(Role.DIPLOMAT) >= 1, "diplomate absent pour " + n);
        }
    }

    @Test
    void smallPopulationDoesNotRequireEveryEssentialRole() {
        var counts = RoleBalance.count(assignAll(3, Role.FARMER));
        assertTrue(counts.get(Role.GUARD) >= 1);
        assertEquals(0, (int) counts.get(Role.DIPLOMAT));
    }

    @Test
    void existingVillagersAreNeverReassigned() {
        List<VillagerState> all = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            all.add(new VillagerState(UUID.randomUUID(), Role.FARMER, "v"));
        }
        RoleBalance.ensureEssentials(all, List.of());
        assertEquals(6, (int) RoleBalance.count(all).get(Role.FARMER));
    }

    @Test
    void isDeterministic() {
        List<Role> a = new ArrayList<>();
        List<Role> b = new ArrayList<>();
        assignAll(9, Role.FORESTER).forEach(v -> a.add(v.role()));
        assignAll(9, Role.FORESTER).forEach(v -> b.add(v.role()));
        assertEquals(a, b);
    }
}
