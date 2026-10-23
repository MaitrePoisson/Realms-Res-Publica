package rrp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void septRolesExistent() {
        assertEquals(7, Role.values().length);
        Set<String> noms = new HashSet<>();
        for (Role r : Role.values()) {
            noms.add(r.name());
        }
        assertEquals(Set.of("BUILDER", "FORESTER", "MINER", "GUARD", "FARMER", "STEWARD", "DIPLOMAT"), noms);
    }

    @Test
    void chaqueRoleAUnLibelle() {
        for (Role r : Role.values()) {
            assertNotNull(r.label());
        }
    }
}
