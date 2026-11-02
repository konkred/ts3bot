package ts3bot.feature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Prüft in festen Abständen die Ränge aller Online-Nutzer, damit auch Dauer-Online-Nutzer aufsteigen. */
public final class RankChecker {

    private static final Logger LOG = LoggerFactory.getLogger(RankChecker.class);

    private final RankManager ranks;

    public RankChecker(RankManager ranks) {
        this.ranks = ranks;
    }

    /** Startet die Prüfung. Bei einem Intervall von 0 oder weniger bleibt sie aus. */
    public void start(int intervalMinutes) {
        if (intervalMinutes <= 0) {
            return;
        }
        // Daemon-Thread: Ohne Reconnect soll sich der Bot nach einem Verbindungsabbruch weiterhin beenden.
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rank-checker");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::run, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
    }

    private void run() {
        // Eine ungefangene Exception würde alle weiteren Prüfungen stillschweigend abbrechen.
        try {
            ranks.checkOnlineClients();
        } catch (Exception e) {
            LOG.warn("Rang-Prüfung fehlgeschlagen: {}", e.getMessage());
        }
    }
}
