package ts3bot.feature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Führt Aufgaben in festen Abständen aus. */
public final class Periodic {

    private static final Logger LOG = LoggerFactory.getLogger(Periodic.class);

    private Periodic() {
    }

    /** Startet die Aufgabe. Bei einem Abstand von 0 oder weniger bleibt sie aus. */
    public static void every(String name, long seconds, Runnable task) {
        every(name, seconds, seconds, task);
    }

    /** Wie {@link #every(String, long, Runnable)}, der erste Lauf kommt aber schon nach {@code firstDelaySeconds}. */
    public static void every(String name, long firstDelaySeconds, long seconds, Runnable task) {
        if (seconds <= 0) {
            return;
        }
        // Daemon-Thread: Ohne Reconnect soll sich der Bot nach einem Verbindungsabbruch weiterhin beenden.
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        }).scheduleWithFixedDelay(() -> {
            // Eine ungefangene Exception würde alle weiteren Läufe stillschweigend abbrechen.
            try {
                task.run();
            } catch (Exception e) {
                LOG.warn("{} fehlgeschlagen: {}", name, e.getMessage());
            }
        }, firstDelaySeconds, seconds, TimeUnit.SECONDS);
    }
}
