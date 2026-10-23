package rrp.neoforge;


import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Horloge des minuteurs du mod. Chaque tick serveur avance d'un tick ; `/respublica tick n`
 * (et les tests) l'avancent de n ticks d'un coup sans attendre.
 */
public final class TickService {

    private final List<LongConsumer> listeners = new ArrayList<>();
    private long total;
    private long nanos;
    private long measuredTicks;

    /** Enregistre un minuteur appelé avec le nombre de ticks écoulés. */
    public void register(LongConsumer listener) {
        listeners.add(listener);
    }

    public void unregister(LongConsumer listener) {
        listeners.remove(listener);
    }

    public void advance(long ticks) {
        if (ticks <= 0) {
            return;
        }
        total += ticks;
        long t0 = System.nanoTime();
        for (LongConsumer l : new ArrayList<>(listeners)) {
            l.accept(ticks);
        }
        nanos += System.nanoTime() - t0;
        measuredTicks += ticks;
    }

    /** Remet à zéro le chronomètre du coût des minuteurs (tests de performance). */
    public void resetCost() {
        nanos = 0;
        measuredTicks = 0;
    }

    /** Coût moyen des minuteurs du mod, en millisecondes par tick avancé, depuis {@link #resetCost()}. */
    public double averageMsPerTick() {
        return measuredTicks == 0 ? 0 : nanos / 1_000_000.0 / measuredTicks;
    }

    /** Ticks écoulés depuis le démarrage (réels + accélérés). */
    public long total() {
        return total;
    }
}
