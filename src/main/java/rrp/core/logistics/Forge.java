package rrp.core.logistics;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Pure model of the village forge: the pieces of a guard's kit, what each costs in ore and wood, and which piece the
 * local smith forges next from what the guards still lack and what the armoury already holds.
 */
public final class Forge {

    /** A piece of a guard's kit, in forging priority order (the body armour first, then the weapon). */
    public enum Piece {
        CHESTPLATE,
        SWORD,
        BOW,
        HELMET,
        LEGGINGS,
        BOOTS
    }

    /** Ore and wood a piece costs. */
    public record Cost(int ore, int wood) {
        public Cost {
            ore = Math.max(0, ore);
            wood = Math.max(0, wood);
        }
    }

    /** Arrows that come with a freshly forged bow. */
    public static final int ARROWS_PER_BOW = 16;

    private Forge() {
    }

    /**
     * Cost of a piece. The chestplate costs {@code armorOre}; leggings three quarters of it, helmet and boots half
     * of it (at least 1 each); a sword costs {@code swordOre} and a bow {@code bowWood}.
     */
    public static Cost cost(Piece piece, int swordOre, int armorOre, int bowWood) {
        return switch (piece) {
            case CHESTPLATE -> new Cost(armorOre, 0);
            case LEGGINGS -> new Cost(Math.max(1, armorOre * 3 / 4), 0);
            case HELMET, BOOTS -> new Cost(Math.max(1, armorOre / 2), 0);
            case SWORD -> new Cost(swordOre, 0);
            case BOW -> new Cost(0, bowWood);
        };
    }

    /** Pieces still to produce: what the guards lack minus what the armoury holds (only positive entries). */
    public static Map<Piece, Integer> shortfall(Map<Piece, Integer> lacking, Map<Piece, Integer> stocked) {
        Map<Piece, Integer> out = new EnumMap<>(Piece.class);
        for (Piece p : Piece.values()) {
            int n = lacking.getOrDefault(p, 0) - stocked.getOrDefault(p, 0);
            if (n > 0) {
                out.put(p, n);
            }
        }
        return out;
    }

    /** The piece to forge next: the first one, in priority order, that is short. Empty when nothing is needed. */
    public static Optional<Piece> next(Map<Piece, Integer> shortfall) {
        for (Piece p : Piece.values()) {
            if (shortfall.getOrDefault(p, 0) > 0) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** True if {@code ore} and {@code wood} pay for the cost. */
    public static boolean affordable(Cost cost, int ore, int wood) {
        return ore >= cost.ore() && wood >= cost.wood();
    }
}
