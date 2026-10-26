package rrp.core.building;

import java.util.LinkedHashMap;
import java.util.Map;

/** What a builder has left once a job is paid: the surplus it puts down (chest or ground) instead of carrying it around. */
public final class Surplus {

    private Surplus() {
    }

    /** Units to put down: what is carried, minus what the rest of the work still needs, minus what the builder may keep. */
    public static int leftover(int carried, int stillNeeded, int keep) {
        return Math.max(0, carried - Math.max(0, stillNeeded) - Math.max(0, keep));
    }

    /** The same per kind of item: only the kinds with something to put down are in the result. */
    public static <K> Map<K, Integer> toDeposit(Map<K, Integer> carried, Map<K, Integer> stillNeeded, int keep) {
        Map<K, Integer> out = new LinkedHashMap<>();
        carried.forEach((kind, n) -> {
            int left = leftover(n, stillNeeded.getOrDefault(kind, 0), keep);
            if (left > 0) {
                out.put(kind, left);
            }
        });
        return out;
    }
}
