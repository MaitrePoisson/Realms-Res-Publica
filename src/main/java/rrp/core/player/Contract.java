package rrp.core.player;

import java.util.UUID;

/** A mercenary contract posted by a village: an objective, an escrowed pay and a lifecycle. Pure data. */
public final class Contract {

    /** What has to be done: wipe a raider camp, or take a side in a war against the target. */
    public enum Type { CLEAR_CAMP, WAR_ATTACK, WAR_DEFEND }

    public enum State {
        OPEN, ACCEPTED, DONE,
        /** Terminal states below: the pay goes back to the issuer. */
        FAILED, EXPIRED, CANCELLED;

        public boolean terminal() {
            return this != OPEN && this != ACCEPTED;
        }

        /** True when the escrow returns to the issuer instead of the mercenary. */
        public boolean refundable() {
            return this == FAILED || this == EXPIRED || this == CANCELLED;
        }
    }

    private final int id;
    private final String issuer;
    private final Type type;
    private final String target;
    private final long pay;
    private final double postedAt;
    private State state = State.OPEN;
    private UUID mercenary;
    private double acceptedAt;
    private int kills;
    private int mark;
    private boolean settled;

    public Contract(int id, String issuer, Type type, String target, long pay, double postedAt) {
        this.id = id;
        this.issuer = issuer;
        this.type = type;
        this.target = target;
        this.pay = pay;
        this.postedAt = postedAt;
    }

    public int id() { return id; }
    public String issuer() { return issuer; }
    public Type type() { return type; }
    public String target() { return target; }
    public long pay() { return pay; }
    public double postedAt() { return postedAt; }
    public State state() { return state; }
    public UUID mercenary() { return mercenary; }
    public double acceptedAt() { return acceptedAt; }
    public int kills() { return kills; }
    /** Size of the war history when the contract was accepted, to spot a verdict that comes after. */
    public int mark() { return mark; }
    /** True once the escrow has gone where it belongs (mercenary purse or issuer warehouse). */
    public boolean settled() { return settled; }

    void setState(State s) { this.state = s; }
    void accept(UUID merc, double now) { this.mercenary = merc; this.acceptedAt = now; this.state = State.ACCEPTED; }
    void reopen() { this.mercenary = null; this.kills = 0; this.state = State.OPEN; }
    public void addKill() { kills++; }
    public void setMark(int mark) { this.mark = mark; }
    public void markSettled() { this.settled = true; }

    static Contract restored(int id, String issuer, Type type, String target, long pay, double postedAt, State state,
                             UUID merc, double acceptedAt, int kills, int mark, boolean settled) {
        Contract c = new Contract(id, issuer, type, target, pay, postedAt);
        c.state = state;
        c.mercenary = merc;
        c.acceptedAt = acceptedAt;
        c.kills = kills;
        c.mark = mark;
        c.settled = settled;
        return c;
    }
}
