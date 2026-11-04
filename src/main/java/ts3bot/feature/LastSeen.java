package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import ts3bot.config.BotConfig;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Optional;

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
        String name = ClientLookup.cleanInput(message.substring(command.length()));
        api.sendPrivateMessage(invokerId, name.isEmpty() ? usage : answer(name));
        return true;
    }

    private String answer(String name) {
        Optional<Client> client = ClientLookup.online(api, name);
        if (client.isPresent()) {
            return online.replace("{name}", client.get().getNickname());
        }
        return ClientLookup.inDatabase(api, name)
                .map(match -> {
                    String date = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(match.getLastConnectedDate());
                    return offline.replace("{date}", date).replace("{name}", match.getNickname());
                })
                .orElseGet(() -> unknown.replace("{name}", name));
    }
}
