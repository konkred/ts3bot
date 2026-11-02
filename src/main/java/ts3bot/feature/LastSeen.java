package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import com.github.theholywaffle.teamspeak3.api.wrapper.DatabaseClientInfo;
import ts3bot.config.BotConfig;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/** Chat-Befehl: Wann war ein Nutzer zuletzt online? */
public final class LastSeen {

    private final TS3Api api;
    private final boolean enabled;
    private final String command;
    private final String usage;
    private final String online;
    private final String offline;
    private final String unknown;

    public LastSeen(TS3Api api, BotConfig config) {
        this.api = api;
        enabled = config.bool("seen.enabled");
        command = config.string("seen.command").toLowerCase(Locale.ROOT);
        usage = config.string("seen.usage");
        online = config.string("seen.message-online");
        offline = config.string("seen.message-offline");
        unknown = config.string("seen.message-unknown");
    }

    /** Gibt {@code true} zurück, wenn die Nachricht dieser Befehl war. */
    public boolean handle(int invokerId, String message) {
        if (!enabled || !message.toLowerCase(Locale.ROOT).startsWith(command)) {
            return false;
        }
        String name = message.substring(command.length()).trim();
        api.sendPrivateMessage(invokerId, name.isEmpty() ? usage : answer(name));
        return true;
    }

    private String answer(String name) {
        for (Client client : api.getClients()) {
            if (!client.isServerQueryClient() && client.getNickname().equalsIgnoreCase(name)) {
                return online.replace("{name}", client.getNickname());
            }
        }
        DatabaseClientInfo match = findInDatabase(name);
        if (match == null) {
            return unknown.replace("{name}", name);
        }
        String date = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(match.getLastConnectedDate());
        return offline.replace("{name}", match.getNickname()).replace("{date}", date);
    }

    private DatabaseClientInfo findInDatabase(String name) {
        List<DatabaseClientInfo> matches;
        try {
            matches = api.getDatabaseClientsByName(name);
        } catch (Exception e) {
            return null;
        }
        if (matches == null) {
            return null;
        }
        // Die Suche findet auch Teilstücke, ein exakter Treffer hat deshalb Vorrang.
        return matches.stream().filter(c -> c.getNickname().equalsIgnoreCase(name)).findFirst()
                .orElse(matches.isEmpty() ? null : matches.get(0));
    }
}
