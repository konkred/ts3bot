package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import ts3bot.config.BotConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Chat-Befehl: Wann war ein Nutzer zuletzt online? */
public final class LastSeen {

    private final TS3Api api;
    private final BotConfig config;
    private final boolean enabled;
    private final String command;
    private final String usage;
    private final String online;
    private final String offline;
    private final String unknown;
    private final String lineOnline;
    private final String lineOffline;

    public LastSeen(TS3Api api, BotConfig config) {
        this.api = api;
        this.config = config;
        ClientLookup.maxResults(config);
        enabled = config.bool("seen.enabled");
        command = config.string("seen.command").toLowerCase(Locale.ROOT);
        usage = config.string("seen.usage");
        online = config.string("seen.message-online");
        offline = config.string("seen.message-offline");
        unknown = config.string("seen.message-unknown");
        lineOnline = config.string("seen.line-online");
        lineOffline = config.string("seen.line-offline");
    }

    /** Gibt {@code true} zurück, wenn die Nachricht dieser Befehl war. */
    public boolean handle(int invokerId, String message) {
        if (!enabled || !message.toLowerCase(Locale.ROOT).startsWith(command)) {
            return false;
        }
        String name = ClientLookup.cleanInput(message.substring(command.length()));
        api.sendPrivateMessage(invokerId, name.isEmpty() ? usage : answer(name));
        return true;
    }

    private String answer(String name) {
        if (ClientLookup.hasWildcard(name)) {
            return config.string("lookup.message-wildcard");
        }
        List<ClientLookup.Match> matches = ClientLookup.find(api, name);
        if (matches.isEmpty()) {
            return unknown.replace("{name}", name);
        }
        if (matches.size() > 1) {
            return ClientLookup.listMessage(config, name, command, matches, this::line);
        }
        ClientLookup.Match match = matches.get(0);
        return match.online()
                ? online.replace("{name}", match.nickname())
                : offline.replace("{date}", formatDate(match.lastConnected())).replace("{name}", match.nickname());
    }

    private String line(int number, ClientLookup.Match match) {
        return (match.online() ? lineOnline : lineOffline)
                .replace("{nr}", String.valueOf(number))
                .replace("{date}", formatDate(match.lastConnected()))
                .replace("{uid}", match.uid())
                .replace("{name}", match.nickname());
    }

    private static String formatDate(Date date) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(date);
    }
}
