package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import ts3bot.config.BotConfig;

/** Sendet in festen Abständen eine Nachricht in den Server-Chat oder den Chat des Channels, in dem der Bot sitzt. */
public final class Announcements {

    private Announcements() {
    }

    public static void start(TS3Api api, BotConfig config) {
        String target = config.choice("announcement.target", "server", "channel");
        String message = config.string("announcement.message");
        if (!config.bool("announcement.enabled")) {
            return;
        }
        Periodic.every("announcement", config.integer("announcement.interval-minutes") * 60L, () -> {
            if ("channel".equals(target)) {
                api.sendChannelMessage(message);
            } else {
                api.sendServerMessage(message);
            }
        });
    }
}
