package rrp.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Book of mercenary contracts and of the reputation they earn. Pure logic; the world side is ContractService. */
public final class Contracts {

    public enum Accept { OK, UNKNOWN, NOT_OPEN, TOO_MANY }

    private final List<Contract> all = new ArrayList<>();
    private final Map<UUID, Integer> reputation = new TreeMap<>();
    private int next = 1;

    /** Posts a contract; null when the same objective is already live for that issuer or the pay is not positive. */
    public Contract post(String issuer, Contract.Type type, String target, long pay, double now) {
        if (pay <= 0 || issuer == null || target == null || live(issuer, type, target) != null) {
            return null;
        }
        Contract c = new Contract(next++, issuer, type, target, pay, now);
        all.add(c);
        return c;
    }

    /** The open or accepted contract for that exact objective, if any. */
    public Contract live(String issuer, Contract.Type type, String target) {
        for (Contract c : all) {
            if (!c.state().terminal() && c.issuer().equals(issuer) && c.type() == type && c.target().equals(target)) {
                return c;
            }
        }
        return null;
    }

    public Contract get(int id) {
        for (Contract c : all) {
            if (c.id() == id) {
                return c;
            }
        }
        return null;
    }

    public List<Contract> all() {
        return new ArrayList<>(all);
    }

    public List<Contract> open() {
        return byState(Contract.State.OPEN);
    }

    public List<Contract> accepted() {
        return byState(Contract.State.ACCEPTED);
    }

    private List<Contract> byState(Contract.State s) {
        List<Contract> out = new ArrayList<>();
        for (Contract c : all) {
            if (c.state() == s) {
                out.add(c);
            }
        }
        return out;
    }

    /** Number of live contracts a village has posted (open or accepted). */
    public int liveOf(String issuer) {
        int n = 0;
        for (Contract c : all) {
            if (!c.state().terminal() && c.issuer().equals(issuer)) {
                n++;
            }
        }
        return n;
    }

    public int activeOf(UUID merc) {
        int n = 0;
        for (Contract c : accepted()) {
            if (merc.equals(c.mercenary())) {
                n++;
            }
        }
        return n;
    }

    public Accept accept(int id, UUID merc, double now, int maxActive) {
        Contract c = get(id);
        if (c == null) {
            return Accept.UNKNOWN;
        }
        if (c.state() != Contract.State.OPEN) {
            return Accept.NOT_OPEN;
        }
        if (activeOf(merc) >= maxActive) {
            return Accept.TOO_MANY;
        }
        c.accept(merc, now);
        return Accept.OK;
    }

    /** The mercenary walks away: the contract is offered again and the reputation drops. */
    public boolean abandon(int id, UUID merc) {
        Contract c = get(id);
        if (c == null || c.state() != Contract.State.ACCEPTED || !merc.equals(c.mercenary())) {
            return false;
        }
        c.reopen();
        addReputation(merc, -1);
        return true;
    }

    /** Objective reached: DONE, the mercenary earns reputation. */
    public boolean complete(int id) {
        Contract c = get(id);
        if (c == null || c.state() != Contract.State.ACCEPTED) {
            return false;
        }
        c.setState(Contract.State.DONE);
        addReputation(c.mercenary(), 1);
        return true;
    }

    /** Objective lost or timed out: FAILED, the mercenary loses reputation. */
    public boolean fail(int id) {
        Contract c = get(id);
        if (c == null || c.state() != Contract.State.ACCEPTED) {
            return false;
        }
        c.setState(Contract.State.FAILED);
        addReputation(c.mercenary(), -1);
        return true;
    }

    /** The objective disappeared on its own (peace, camp gone): the escrow returns, nobody is blamed. */
    public boolean cancel(int id) {
        Contract c = get(id);
        if (c == null || c.state().terminal()) {
            return false;
        }
        c.setState(Contract.State.CANCELLED);
        return true;
    }

    /** Ages the book: unaccepted contracts expire after {@code openTtl}, accepted ones fail after {@code timeout}. */
    public List<Contract> tick(double now, double openTtl, double timeout) {
        List<Contract> changed = new ArrayList<>();
        for (Contract c : all()) {
            if (c.state() == Contract.State.OPEN && openTtl > 0 && now - c.postedAt() >= openTtl) {
                c.setState(Contract.State.EXPIRED);
                changed.add(c);
            } else if (c.state() == Contract.State.ACCEPTED && timeout > 0 && now - c.acceptedAt() >= timeout) {
                fail(c.id());
                changed.add(c);
            }
        }
        return changed;
    }

    /** Terminal contracts whose escrow has not been settled yet. */
    public List<Contract> unsettled() {
        List<Contract> out = new ArrayList<>();
        for (Contract c : all) {
            if (c.state().terminal() && !c.settled()) {
                out.add(c);
            }
        }
        return out;
    }

    /** Drops settled terminal contracts beyond the {@code keep} most recent ones. */
    public void prune(int keep) {
        List<Contract> done = new ArrayList<>();
        for (Contract c : all) {
            if (c.state().terminal() && c.settled()) {
                done.add(c);
            }
        }
        for (int i = 0; i < done.size() - keep; i++) {
            all.remove(done.get(i));
        }
    }

    public int reputation(UUID id) {
        return reputation.getOrDefault(id, 0);
    }

    private void addReputation(UUID id, int delta) {
        if (id != null) {
            reputation.merge(id, delta, Integer::sum);
        }
    }

    public Map<UUID, Integer> reputations() {
        return new TreeMap<>(reputation);
    }

    public void restoreReputation(UUID id, int value) {
        reputation.put(id, value);
    }

    public void restore(Contract c) {
        all.removeIf(x -> x.id() == c.id());
        all.add(c);
        next = Math.max(next, c.id() + 1);
    }

    public static Contract restored(int id, String issuer, Contract.Type type, String target, long pay, double postedAt,
                                    Contract.State state, UUID merc, double acceptedAt, int kills, int mark, boolean settled) {
        return Contract.restored(id, issuer, type, target, pay, postedAt, state, merc, acceptedAt, kills, mark, settled);
    }

    public void clear() {
        all.clear();
        reputation.clear();
        next = 1;
    }
}
