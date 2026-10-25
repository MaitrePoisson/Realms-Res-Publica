package rrp.core.diplomacy;

/**
 * Régiment d'un village en guerre : effectif plafonné à une part de la population, équipement forgé localement
 * (épées et armures de fer, arcs de bois) dans la limite des stocks. Logique pure, sans Minecraft.
 */
public final class Regiment {

    /** Coûts d'équipement en unités de stock. */
    public record Costs(int swordOre, int armorOre, int bowWood) { }

    /** Équipement distribué : un objet par soldat au plus, et ressources dépensées. */
    public record Loadout(int swords, int armors, int bows, int oreSpent, int woodSpent) { }

    public static final double BASE = 1.0;
    public static final double SWORD = 1.0;
    public static final double ARMOR = 0.75;
    public static final double BOW = 0.75;

    private Regiment() { }

    /** Effectif : les gardes disponibles, dans la limite de {@code share} de la population (au moins 1 s'il y a un garde). */
    public static int size(int guards, int population, double share) {
        if (guards <= 0 || population <= 0) {
            return 0;
        }
        int cap = Math.max(1, (int) Math.floor(population * Math.max(0, Math.min(1, share))));
        return Math.min(guards, cap);
    }

    /** Équipe d'abord les épées, puis les armures, puis les arcs, tant que le fer et le bois le permettent. */
    public static Loadout equip(int members, int ore, int wood, Costs costs) {
        int swords = affordable(members, ore, costs.swordOre());
        int oreLeft = ore - swords * costs.swordOre();
        int armors = affordable(members, oreLeft, costs.armorOre());
        int bows = affordable(members, wood, costs.bowWood());
        return new Loadout(swords, armors, bows, swords * costs.swordOre() + armors * costs.armorOre(),
                bows * costs.bowWood());
    }

    private static int affordable(int members, int stock, int cost) {
        return cost <= 0 ? members : Math.max(0, Math.min(members, stock / cost));
    }

    /** Force du régiment : base par soldat plus les bonus d'équipement ; un régiment vide vaut 0. */
    public static double strength(int members, Loadout l) {
        if (members <= 0) {
            return 0;
        }
        return members * BASE + Math.min(members, l.swords()) * SWORD + Math.min(members, l.armors()) * ARMOR
                + Math.min(members, l.bows()) * BOW;
    }
}
