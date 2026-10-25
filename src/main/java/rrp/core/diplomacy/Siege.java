package rrp.core.diplomacy;

/**
 * Règles pures d'un siège : machines (bélier, catapulte) avec points de vie, budget de blocs détruits par siège
 * et dégâts infligés par les défenseurs. Logique sans Minecraft.
 */
public final class Siege {

    /** Type de machine de siège. */
    public enum Kind { RAM, CATAPULT }

    /** Machine de siège : points de vie, détruite à 0. */
    public static final class Engine {
        private final Kind kind;
        private double hp;

        public Engine(Kind kind, double hp) {
            this.kind = kind;
            this.hp = Math.max(0, hp);
        }

        public Kind kind() { return kind; }
        public double hp() { return hp; }
        public boolean destroyed() { return hp <= 0; }

        public void hit(double damage) {
            hp = Math.max(0, hp - Math.max(0, damage));
        }
    }

    /** Budget de brèches d'un siège : jamais plus de {@code cap} blocs détruits au total. */
    public static final class Budget {
        private final int cap;
        private int used;

        public Budget(int cap) {
            this.cap = Math.max(0, cap);
        }

        public int cap() { return cap; }
        public int used() { return used; }
        public int remaining() { return cap - used; }

        /** Accorde au plus {@code wanted} blocs, dans la limite du reste ; consomme ce qui est accordé. */
        public int take(int wanted) {
            int granted = Math.max(0, Math.min(wanted, remaining()));
            used += granted;
            return granted;
        }
    }

    private Siege() { }

    /** Prochaine machine à assembler : le bélier d'abord (portes), puis la catapulte (remparts) ; null si les deux existent. */
    public static Kind next(boolean hasRam, boolean hasCatapult) {
        if (!hasRam) {
            return Kind.RAM;
        }
        return hasCatapult ? null : Kind.CATAPULT;
    }

    /** Dégâts reçus par une machine en une mise à jour : chaque défenseur à portée frappe. */
    public static double defenderDamage(int defendersInRange, double perDefender) {
        return Math.max(0, defendersInRange) * Math.max(0, perDefender);
    }
}
