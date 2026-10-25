package rrp.core.logistics;

import java.util.EnumMap;
import rrp.core.Resource;

/**
 * What a villager physically carries: a bounded number of units (default half a stack, {@code carry_capacity}),
 * possibly of several resources. Nothing reaches a warehouse or a construction site without passing through here.
 */
public final class CarryLoad {

    private final int capacity;
    private final EnumMap<Resource, Integer> held = new EnumMap<>(Resource.class);

    public CarryLoad(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity < 1: " + capacity);
        }
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public int total() {
        int t = 0;
        for (int n : held.values()) {
            t += n;
        }
        return t;
    }

    public int free() {
        return capacity - total();
    }

    public boolean isEmpty() {
        return held.isEmpty();
    }

    public boolean isFull() {
        return free() <= 0;
    }

    public int count(Resource r) {
        return held.getOrDefault(r, 0);
    }

    /** Puts up to {@code n} units in the load and returns how many were really accepted (0 when full). */
    public int load(Resource r, int n) {
        if (n <= 0) {
            return 0;
        }
        int taken = Math.min(n, free());
        if (taken > 0) {
            held.merge(r, taken, Integer::sum);
        }
        return taken;
    }

    /** Removes up to {@code n} units of {@code r} and returns how many were really removed. */
    public int take(Resource r, int n) {
        int have = count(r);
        int out = Math.min(Math.max(n, 0), have);
        if (out > 0) {
            if (out == have) {
                held.remove(r);
            } else {
                held.put(r, have - out);
            }
        }
        return out;
    }

    /** Empties the load of {@code r} and returns the units that were carried. */
    public int unload(Resource r) {
        return take(r, count(r));
    }
}
