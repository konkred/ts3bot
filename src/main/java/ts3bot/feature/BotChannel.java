package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ts3bot.config.BotConfig;

/**
 * Channel, über den Nutzer den unsichtbaren Bot jederzeit erreichen: Wer ihn betritt, bekommt die
 * Befehlsliste privat und kann im geöffneten Tab antworten. Der Bot sitzt selbst im Channel,
 * damit dort auch der Channel-Chat funktioniert.
 */
public final class BotChannel {

    private static final Logger LOG = LoggerFactory.getLogger(BotChannel.class);

    private final TS3Api api;
    private final BotConfig config;

    public BotChannel(TS3Api api, BotConfig config) {
        this.api = api;
        this.config = config;
    }

    /** Setzt den Bot in den Channel. Muss vor {@code registerAllEvents()} laufen, weil der Channel-Chat dem aktuellen Channel gilt. */
    public static void enter(TS3Api api, BotConfig config) {
        int channelId = config.botChannelId();
        if (channelId <= 0) {
            return;
        }
        // Eine falsche Channel-ID soll den Bot nicht vom Server fernhalten.
        try {
            api.moveQuery(channelId);
        } catch (Exception e) {
            LOG.warn("Bot-Channel {} konnte nicht betreten werden: {}", channelId, e.getMessage());
        }
    }

    public void handleEnter(int clientId, int targetChannelId) {
        if (isBotChannel(config.botChannelId(), targetChannelId)) {
            api.sendPrivateMessage(clientId, config.helpMessage());
        }
    }

    static boolean isBotChannel(int botChannelId, int targetChannelId) {
        return botChannelId > 0 && botChannelId == targetChannelId;
    }
}
