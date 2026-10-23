package rrp.core;

import java.util.UUID;

/** État d'un villageois vu par le cœur : rôle et trois jauges bornées 0–100. */
public final class VillagerState {
    public static final double MAX = 100.0;

    private final UUID id;
    private Role role;
    private double morale;
    private double satiety;
    private double loyalty;
    private String factionId;

    public VillagerState(UUID id, Role role, String factionId) {
        this(id, role, factionId, 70, 80, 70);
    }

    public VillagerState(UUID id, Role role, String factionId, double morale, double satiety, double loyalty) {
        this.id = id;
        this.role = role;
        this.factionId = factionId;
        this.morale = clamp(morale);
        this.satiety = clamp(satiety);
        this.loyalty = clamp(loyalty);
    }

    public static double clamp(double v) {
        if (Double.isNaN(v)) {
            return 0;
        }
        return Math.max(0, Math.min(MAX, v));
    }

    public UUID id() { return id; }
    public Role role() { return role; }
    public void setRole(Role role) { this.role = role; }
    public double morale() { return morale; }
    public void setMorale(double v) { this.morale = clamp(v); }
    public double satiety() { return satiety; }
    public void setSatiety(double v) { this.satiety = clamp(v); }
    public double loyalty() { return loyalty; }
    public void setLoyalty(double v) { this.loyalty = clamp(v); }
    public String factionId() { return factionId; }
    public void setFactionId(String factionId) { this.factionId = factionId; }
}
