package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.ClientInfo;
import ts3bot.config.BotConfig;

/** Schreibt neue Nutzer beim ersten Besuch an. Dadurch öffnet sich bei ihnen ein Chat-Tab mit dem unsichtbaren Bot. */
public final class WelcomeMessage {

    private final TS3Api api;
    private final BotConfig config;

    public WelcomeMessage(TS3Api api, BotConfig config) {
        this.api = api;
        this.config = config;
    }

    public void handle(ClientInfo client) {
        String message = config.welcomeMessage();
        if (!message.isBlank() && isFirstVisit(client.getTotalConnections())) {
            api.sendPrivateMessage(client.getId(), message);
        }
    }

    static boolean isFirstVisit(int totalConnections) {
        return totalConnections <= 1;
    }
}
