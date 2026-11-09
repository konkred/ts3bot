package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.ChannelProperty;
import com.github.theholywaffle.teamspeak3.api.wrapper.Channel;
import com.github.theholywaffle.teamspeak3.api.wrapper.ClientInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ts3bot.config.BotConfig;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wer den Auslöser-Channel betritt, bekommt einen eigenen Channel. Leere Unterchannels des Eltern-Channels löscht der Bot.
 * <p>
 * Alles unter dem Eltern-Channel gilt als temporär. So überstehen die Channels auch einen Neustart des Bots.
 */
public final class JoinToCreate {

    private static final Logger LOG = LoggerFactory.getLogger(JoinToCreate.class);
    /** Längenlimit von TeamSpeak für Channelnamen. */
    private static final int MAX_NAME = 40;

    private final TS3Api api;
    private final boolean enabled;
    private final int trigger;
    private final int parent;
    private final String nameTemplate;
    private final int maxClients;
    private final int channelGroup;
    private final int checkSeconds;
    private final String failed;

    public JoinToCreate(TS3Api api, BotConfig config) {
        this.api = api;
        enabled = config.bool("join-create.enabled");
        trigger = config.integer("join-create.channel");
        parent = config.integer("join-create.parent");
        nameTemplate = config.string("join-create.name");
        maxClients = config.integer("join-create.max-clients");
        channelGroup = config.integer("join-create.channel-group");
        checkSeconds = config.integer("join-create.check-seconds");
        failed = config.string("join-create.message-failed");
        if (enabled && (trigger <= 0 || parent <= 0 || trigger == parent)) {
            throw new IllegalArgumentException("join-create: channel und parent müssen zwei verschiedene Channel-IDs sein");
        }
    }

    public void start() {
        if (enabled) {
            Periodic.every("join-create", checkSeconds, this::cleanup);
        }
    }

    public void handleEnter(int clientId, int targetChannel) {
        if (!enabled || targetChannel != trigger) {
            return;
        }
        try {
            create(clientId);
        } catch (Exception e) {
            LOG.warn("Join-to-Create: Channel für Client {} konnte nicht angelegt werden: {}", clientId, e.getMessage());
            api.sendPrivateMessage(clientId, failed);
        }
    }

    /** {@code synchronized}, damit {@link #cleanup()} den neuen Channel nicht löscht, bevor der Nutzer drin ist. */
    private synchronized void create(int clientId) {
        ClientInfo client = api.getClientInfo(clientId);
        if (client.isServerQueryClient()) {
            return;
        }
        Set<String> taken = api.getChannels().stream()
                .filter(channel -> channel.getParentChannelId() == parent)
                .map(channel -> channel.getName().toLowerCase())
                .collect(Collectors.toSet());

        Map<ChannelProperty, String> properties = new EnumMap<>(ChannelProperty.class);
        properties.put(ChannelProperty.CPID, String.valueOf(parent));
        properties.put(ChannelProperty.CHANNEL_FLAG_SEMI_PERMANENT, "1");
        if (maxClients > 0) {
            properties.put(ChannelProperty.CHANNEL_FLAG_MAXCLIENTS_UNLIMITED, "0");
            properties.put(ChannelProperty.CHANNEL_MAXCLIENTS, String.valueOf(maxClients));
        }
        int channelId = api.createChannel(channelName(nameTemplate, client.getNickname(), taken), properties);
        api.moveClient(clientId, channelId);
        if (channelGroup > 0) {
            api.setClientChannelGroup(channelGroup, channelId, client.getDatabaseId());
        }
    }

    synchronized void cleanup() {
        List<Channel> channels = api.getChannels();
        for (Channel channel : channels) {
            if (channel.getParentChannelId() == parent && channel.getTotalClientsFamily() == 0) {
                api.deleteChannel(channel.getId(), false);
            }
        }
    }

    /** Freier Name mit dem Nickname. {@code taken} enthält die vergebenen Namen in Kleinbuchstaben. */
    static String channelName(String template, String nickname, Set<String> taken) {
        String base = ClientActions.shorten(template.replace("{name}", nickname), MAX_NAME);
        String name = base;
        for (int number = 2; taken.contains(name.toLowerCase()); number++) {
            String suffix = " " + number;
            name = ClientActions.shorten(base, MAX_NAME - suffix.length()) + suffix;
        }
        return name;
    }
}
