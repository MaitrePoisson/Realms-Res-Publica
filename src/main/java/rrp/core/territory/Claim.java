package rrp.core.territory;

/** Résultat pour un chunk : propriétaire (null si libre), influence, et éventuel rival. */
public record Claim(String owner, double influence, String rival, double rivalInfluence) {

    public static final Claim FREE = new Claim(null, 0, null, 0);

    public boolean free() {
        return owner == null;
    }

    public boolean contested() {
        return owner != null && rival != null;
    }
}
