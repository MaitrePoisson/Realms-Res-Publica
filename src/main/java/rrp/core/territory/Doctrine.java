package rrp.core.territory;

import java.util.EnumMap;
import java.util.Map;
import rrp.core.Role;

/**
 * Doctrine émergente d'une faction, fixée à la fondation de l'Hôtel de Ville d'après les ressources
 * dominantes de ses alentours. Chaque doctrine porte des modificateurs d'équilibrage (multiplicateurs
 * neutres à 1.0) utilisés par les planificateurs urbains, la répartition des rôles et la diplomatie.
 */
public enum Doctrine {
    /** Plaines et côtes : routes, marchés, neutralité armée. */
    MERCHANT_CITY(Map.of(Role.DIPLOMAT, 2.0, Role.STEWARD, 1.3, Role.GUARD, 0.8, Role.MINER, 0.8),
            1.0, 0.7, 0.3, 0.6),
    /** Badlands et montagnes : mines, armées nombreuses, expansion agressive. */
    WARLIKE_EMPIRE(Map.of(Role.GUARD, 1.8, Role.MINER, 1.7, Role.DIPLOMAT, 0.5, Role.FARMER, 0.9),
            1.5, 1.8, 0.5, 1.4),
    /** Forêts denses et marais : méfiance des étrangers, monuments, alchimie. */
    ISOLATIONIST_THEOCRACY(Map.of(Role.FORESTER, 1.3, Role.BUILDER, 1.3, Role.DIPLOMAT, 0.4, Role.GUARD, 1.1),
            0.8, 0.5, 1.0, 1.0);

    private final Map<Role, Double> roleWeights = new EnumMap<>(Role.class);
    private final double claimRadiusFactor;
    private final double expansionAggressiveness;
    private final double foreignerDistrust;
    private final double wallPriority;

    Doctrine(Map<Role, Double> weights, double claimRadiusFactor, double expansionAggressiveness,
             double foreignerDistrust, double wallPriority) {
        this.roleWeights.putAll(weights);
        this.claimRadiusFactor = claimRadiusFactor;
        this.expansionAggressiveness = expansionAggressiveness;
        this.foreignerDistrust = foreignerDistrust;
        this.wallPriority = wallPriority;
    }

    /** Multiplicateur du poids d'un rôle dans la répartition (1.0 si la doctrine est indifférente). */
    public double roleWeight(Role role) {
        return roleWeights.getOrDefault(role, 1.0);
    }

    /** Multiplicateur du rayon de revendication du bourg. */
    public double claimRadiusFactor() { return claimRadiusFactor; }

    /** 0 = jamais d'expansion agressive ; au-delà de 1 le bourg convoite les frontières voisines. */
    public double expansionAggressiveness() { return expansionAggressiveness; }

    /** 0 = accueille les étrangers ; 1 = les expulse de ses chunks. */
    public double foreignerDistrust() { return foreignerDistrust; }

    /** Multiplicateur de priorité des défenses (enceinte, tours). */
    public double wallPriority() { return wallPriority; }
}
