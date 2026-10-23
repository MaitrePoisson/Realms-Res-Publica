package rrp.core;

import java.util.List;
import java.util.UUID;

/** Événements émis par le cœur ; la couche Bukkit les applique au monde. */
public sealed interface SimEvent {
    String villageId();

    /** Une naissance est décidée (la nourriture est déjà consommée dans l'état). */
    record Birth(String villageId, int foodCost) implements SimEvent { }

    /** Le niveau de crise du village a changé. */
    record CrisisChanged(String villageId, CrisisLevel from, CrisisLevel to) implements SimEvent { }

    /**
     * Des mécontents quittent le village pour fonder (ou rejoindre) le campement {@code campId}. L'état du
     * campement existe déjà dans le cœur avec ses exilés et {@code foodMoved} unités de nourriture ; la
     * couche Bukkit déplace les entités et construit le campement.
     */
    record Exile(String villageId, List<UUID> villagerIds, String campId, int foodMoved) implements SimEvent { }

    /** La guerre civile éclate entre le village et son campement dissident. */
    record WarStarted(String villageId, String otherVillageId) implements SimEvent { }

    /** La guerre prend fin (trêve ou élimination d'un camp). */
    record WarEnded(String villageId, String otherVillageId, boolean truce) implements SimEvent { }
}
