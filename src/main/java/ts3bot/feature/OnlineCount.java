package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import ts3bot.config.BotConfig;

import java.util.List;

/** Chat-Befehl: Wie viele Nutzer sind gerade online? */
public final class OnlineCount {

    private final TS3Api api;
    private final boolean enabled;
    private final String command;
    private final String message;

    public OnlineCount(TS3Api api, BotConfig config) {
        this.api = api;
        enabled = config.bool("online.enabled");
        command = config.string("online.command");
        message = config.string("online.message");
    }

    /** Gibt {@code true} zurück, wenn die Nachricht dieser Befehl war. */
    public boolean handle(int invokerId, String text) {
        if (!enabled || ClientActions.arguments(text, command) == null) {
            return false;
        }
        List<Client> clients = api.getClients().stream().filter(client -> !client.isServerQueryClient()).toList();
        long channels = clients.stream().map(Client::getChannelId).distinct().count();
        api.sendPrivateMessage(invokerId, message
                .replace("{clients}", String.valueOf(clients.size()))
                .replace("{channels}", String.valueOf(channels)));
        return true;
    }
}
