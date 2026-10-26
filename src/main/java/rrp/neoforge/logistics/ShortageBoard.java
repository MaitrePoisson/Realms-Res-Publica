package rrp.neoforge.logistics;

import rrp.core.Resource;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The shortage flags that waiting workers raise ("the chest is empty") and the steward reads. One set of flagged
 * resources per village; not persisted (a worker raises the flag again at its next empty chest).
 */
public final class ShortageBoard {

    private static final Map<String, Set<Resource>> FLAGS = new HashMap<>();
    private static long raised;
    private static long read;

    private ShortageBoard() {
    }

    /** A worker tells the village that the stock holds no {@code r}. */
    public static void flag(String siteId, Resource r) {
        if (FLAGS.computeIfAbsent(siteId, k -> EnumSet.noneOf(Resource.class)).add(r)) {
            raised++;
        }
    }

    /** True while a shortage of {@code r} is flagged and the steward has not read it yet. */
    public static boolean flagged(String siteId, Resource r) {
        Set<Resource> s = FLAGS.get(siteId);
        return s != null && s.contains(r);
    }

    /** The steward reads and clears the flags of its village. */
    public static Set<Resource> take(String siteId) {
        Set<Resource> s = FLAGS.remove(siteId);
        if (s == null || s.isEmpty()) {
            return Set.of();
        }
        read += s.size();
        return s;
    }

    /** Flags raised / read since the start (tests, diagnostic). */
    public static long raised() { return raised; }
    public static long read() { return read; }

    public static void reset() {
        FLAGS.clear();
        raised = 0;
        read = 0;
    }
}
