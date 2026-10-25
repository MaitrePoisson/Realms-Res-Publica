package rrp.core.player;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Player profiles: role (with a delay between switches) and emerald purse. Pure logic. */
public final class PlayerLedger {

    /** Result of a role switch. */
    public enum Switch { OK, SAME, COOLDOWN }

    private static final class Profile {
        PlayerRole role = PlayerRole.OBSERVER;
        double lastSwitch = -1e18;
        long purse;
    }

    private final Map<UUID, Profile> profiles = new TreeMap<>();

    private Profile of(UUID id) {
        return profiles.computeIfAbsent(id, k -> new Profile());
    }

    public PlayerRole role(UUID id) {
        Profile p = profiles.get(id);
        return p == null ? PlayerRole.OBSERVER : p.role;
    }

    public long purse(UUID id) {
        Profile p = profiles.get(id);
        return p == null ? 0 : p.purse;
    }

    /** Switches role; going back to OBSERVER is never subject to the cooldown. */
    public Switch switchRole(UUID id, PlayerRole to, double nowSec, double cooldownSec) {
        if (to == null) {
            return Switch.SAME;
        }
        Profile p = of(id);
        if (p.role == to) {
            return Switch.SAME;
        }
        if (to != PlayerRole.OBSERVER && nowSec - p.lastSwitch < cooldownSec) {
            return Switch.COOLDOWN;
        }
        p.role = to;
        p.lastSwitch = nowSec;
        return Switch.OK;
    }

    /** Seconds left before the role may be switched again. */
    public double cooldownLeft(UUID id, double nowSec, double cooldownSec) {
        Profile p = profiles.get(id);
        return p == null ? 0 : Math.max(0, cooldownSec - (nowSec - p.lastSwitch));
    }

    public void deposit(UUID id, long amount) {
        if (amount > 0) {
            of(id).purse += amount;
        }
    }

    /** Takes from the purse; false when the balance is too low. */
    public boolean withdraw(UUID id, long amount) {
        Profile p = of(id);
        if (amount <= 0 || p.purse < amount) {
            return false;
        }
        p.purse -= amount;
        return true;
    }

    /** uuid -> {role, last switch, purse}. */
    public Map<UUID, Object[]> snapshot() {
        Map<UUID, Object[]> out = new TreeMap<>();
        profiles.forEach((k, p) -> out.put(k, new Object[] {p.role, p.lastSwitch, p.purse}));
        return out;
    }

    public void restore(UUID id, PlayerRole role, double lastSwitch, long purse) {
        Profile p = of(id);
        p.role = role;
        p.lastSwitch = lastSwitch;
        p.purse = Math.max(0, purse);
    }

    public void clear() {
        profiles.clear();
    }
}
