package ts3bot.listener;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.TextMessageTargetMode;
import com.github.theholywaffle.teamspeak3.api.event.ClientJoinEvent;
import com.github.theholywaffle.teamspeak3.api.event.TS3EventAdapter;
import com.github.theholywaffle.teamspeak3.api.event.TextMessageEvent;
import ts3bot.config.BotConfig;
import ts3bot.feature.CommandHandler;
import ts3bot.feature.JoinHandler;

/** Verteilt die TeamSpeak-Events an die einzelnen Features. */
public final class BotListener extends TS3EventAdapter {

    private final BotConfig config;
    private volatile TS3Api api;
    private volatile int ownClientId = -1;
    private CommandHandler commands;
    private JoinHandler joins;

    public BotListener(BotConfig config) {
        this.config = config;
    }

    public void setApi(TS3Api api) {
        this.api = api;
        this.commands = new CommandHandler(api, config);
        this.joins = new JoinHandler(api, config);
    }

    /** Eigene Client-ID des Bots, damit er nicht auf seine eigenen Nachrichten reagiert. */
    public void setOwnClientId(int ownClientId) {
        this.ownClientId = ownClientId;
    }

    @Override
    public void onTextMessage(TextMessageEvent e) {
        if (api == null || e.getInvokerId() == ownClientId) {
            return;
        }
        TextMessageTargetMode mode = e.getTargetMode();
        if (mode == TextMessageTargetMode.CLIENT || mode == TextMessageTargetMode.CHANNEL) {
            commands.handle(e);
        }
    }

    @Override
    public void onClientJoin(ClientJoinEvent e) {
        if (api == null) {
            return;
        }
        joins.handle(e);
    }
}
