package rrp.bukkit;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Horloge des minuteurs du plugin. Chaque tick serveur avance d'un tick ; `/respublica tick n`
 * (et les tests) l'avancent de n ticks d'un coup sans attendre.
 */
public final class TickService {

    private final List<LongConsumer> listeners = new ArrayList<>();
    private BukkitTask task;
    private long total;

    public void start(JavaPlugin plugin) {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> advance(1), 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

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
        for (LongConsumer l : new ArrayList<>(listeners)) {
            l.accept(ticks);
        }
    }

    /** Ticks écoulés depuis le démarrage (réels + accélérés). */
    public long total() {
        return total;
    }
}
