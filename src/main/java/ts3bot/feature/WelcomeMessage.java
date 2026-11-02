package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.ClientInfo;
import ts3bot.config.BotConfig;

/** Schreibt Nutzer beim Betreten des Servers an, standardmäßig nur beim ersten Besuch. Dadurch öffnet sich bei ihnen ein Chat-Tab mit dem unsichtbaren Bot. */
public final class WelcomeMessage {

    private final TS3Api api;
    private final BotConfig config;
    private final String type;
    private final boolean firstVisitOnly;

    public WelcomeMessage(TS3Api api, BotConfig config) {
        this.api = api;
        this.config = config;
        type = config.choice("welcome.type", "message", "poke");
        firstVisitOnly = config.bool("welcome.first-visit-only");
    }

    public void handle(ClientInfo client) {
        String message = config.welcomeMessage();
        if (!message.isBlank() && (!firstVisitOnly || isFirstVisit(client.getTotalConnections()))) {
            ClientActions.send(api, client.getId(), type, message);
        }
    }

    static boolean isFirstVisit(int totalConnections) {
        return totalConnections <= 1;
    }
}
