package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import ts3bot.config.BotConfig;

/** Sendet in festen Abständen eine Nachricht in den Server-Chat oder den Chat des Channels, in dem der Bot sitzt. */
public final class Announcements {

    private final TS3Api api;
    private final boolean enabled;
    private final int intervalMinutes;
    private final String target;
    private final String message;

    public Announcements(TS3Api api, BotConfig config) {
        this.api = api;
        this.enabled = config.bool("announcement.enabled");
        this.intervalMinutes = config.integer("announcement.interval-minutes");
        this.target = config.choice("announcement.target", "server", "channel");
        this.message = config.string("announcement.message");
    }

    public void start() {
        if (!enabled) {
            return;
        }
        Periodic.every("announcement", intervalMinutes * 60L, () -> {
            if ("channel".equals(target)) {
                api.sendChannelMessage(message);
            } else {
                api.sendServerMessage(message);
            }
        });
    }
}
