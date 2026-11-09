package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import ts3bot.config.BotConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Chat-Befehl: Bestenliste der dienstältesten Nutzer.
 * <p>
 * Die Liste wird im Hintergrund vorbereitet, weil das Lesen der Datenbank bei vielen Nutzern mehrere Sekunden dauert.
 */
public final class Leaderboard {

    private static final long DAY_MS = 86_400_000L;
    /** Bekommt der Server beim Anmelden per Query, sind keine echten Nutzer. */
    private static final List<String> QUERY_UIDS = List.of("serveradmin", "serverquery");
    private static final int FIRST_LOAD_SECONDS = 5;

    record Entry(String nickname, String uid, Date created, Date lastConnected) {
    }

    private final TS3Api api;
    private final boolean enabled;
    private final String command;
    private final int size;
    private final int activeDays;
    private final int refreshMinutes;
    private final String header;
    private final String line;
    private final String loading;
    /** {@code null}, bis die Datenbank das erste Mal gelesen wurde. */
    private volatile List<Entry> top;

    public Leaderboard(TS3Api api, BotConfig config) {
        this.api = api;
        enabled = config.bool("top.enabled");
        command = config.string("top.command");
        size = config.integer("top.size");
        activeDays = config.integer("top.active-days");
        refreshMinutes = config.integer("top.refresh-minutes");
        header = config.string("top.message-header");
        line = config.string("top.line");
        loading = config.string("top.message-loading");
        if (size < 1 || size > 50) {
            throw new IllegalArgumentException("top.size: 1 bis 50 erwartet, ist aber " + size);
        }
    }

    public void start() {
        if (enabled) {
            Periodic.every("top", FIRST_LOAD_SECONDS, refreshMinutes * 60L, this::refresh);
        }
    }

    private void refresh() {
        List<Entry> entries = api.getDatabaseClients().stream()
                .map(client -> new Entry(client.getNickname(), client.getUniqueIdentifier(), client.getCreatedDate(), client.getLastConnectedDate()))
                .toList();
        top = rank(entries, System.currentTimeMillis(), activeDays, size);
    }

    /** Die ältesten Konten zuerst. Bei {@code activeDays} größer 0 nur Nutzer, die in dieser Zeit da waren. */
    static List<Entry> rank(List<Entry> entries, long now, int activeDays, int size) {
        return entries.stream()
                .filter(entry -> !QUERY_UIDS.contains(entry.uid().toLowerCase()))
                .filter(entry -> activeDays <= 0 || now - entry.lastConnected().getTime() <= activeDays * DAY_MS)
                .sorted(Comparator.comparing(Entry::created))
                .limit(size)
                .toList();
    }

    /** Gibt {@code true} zurück, wenn die Nachricht dieser Befehl war. */
    public boolean handle(int invokerId, String message) {
        if (!enabled || ClientActions.arguments(message, command) == null) {
            return false;
        }
        List<Entry> current = top;
        api.sendPrivateMessage(invokerId, current == null ? loading : format(current, System.currentTimeMillis()));
        return true;
    }

    private String format(List<Entry> entries, long now) {
        List<String> lines = new ArrayList<>();
        lines.add(header.replace("{count}", String.valueOf(entries.size())));
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            lines.add(line.replace("{nr}", String.valueOf(i + 1))
                    .replace("{days}", CommandHandler.formatDuration((now - entry.created().getTime()) / DAY_MS))
                    .replace("{name}", entry.nickname()));
        }
        return String.join("\n", lines);
    }
}
