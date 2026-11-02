package ts3bot.listener;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.TextMessageTargetMode;
import com.github.theholywaffle.teamspeak3.api.event.ClientJoinEvent;
import com.github.theholywaffle.teamspeak3.api.event.ClientMovedEvent;
import com.github.theholywaffle.teamspeak3.api.event.TS3EventAdapter;
import com.github.theholywaffle.teamspeak3.api.event.TextMessageEvent;
import com.github.theholywaffle.teamspeak3.api.wrapper.ClientInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ts3bot.config.BotConfig;
import ts3bot.feature.BotChannel;
import ts3bot.feature.CommandHandler;
import ts3bot.feature.RankManager;
import ts3bot.feature.WelcomeMessage;

/** Verteilt die TeamSpeak-Events an die einzelnen Features. */
public final class BotListener extends TS3EventAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(BotListener.class);

    private final BotConfig config;
    private volatile TS3Api api;
    private volatile int ownClientId = -1;
    private CommandHandler commands;
    private RankManager ranks;
    private WelcomeMessage welcome;
    private BotChannel botChannel;

    public BotListener(BotConfig config) {
        this.config = config;
    }

    public void setApi(TS3Api api, RankManager ranks) {
        this.api = api;
        this.commands = new CommandHandler(api, config);
        this.ranks = ranks;
        this.welcome = new WelcomeMessage(api, config);
        this.botChannel = new BotChannel(api, config);
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
        ClientInfo client;
        try {
            client = api.getClientByUId(e.getUniqueClientIdentifier());
        } catch (Exception ex) {
            LOG.debug("Client {} nicht gefunden: {}", e.getUniqueClientIdentifier(), ex.getMessage());
            return;
        }
        if (client == null || client.isServerQueryClient()) {
            return;
        }
        welcome.handle(client);
        botChannel.handleEnter(client.getId(), e.getClientTargetId());
        ranks.update(client);
    }

    @Override
    public void onClientMoved(ClientMovedEvent e) {
        if (api == null || e.getClientId() == ownClientId) {
            return;
        }
        botChannel.handleEnter(e.getClientId(), e.getTargetChannelId());
    }
}
