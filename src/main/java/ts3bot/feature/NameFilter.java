package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.ChannelInfo;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ts3bot.config.BotConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Überwacht Nicknames und Channelnamen auf unerlaubte Begriffe. */
public final class NameFilter {

    private static final Logger LOG = LoggerFactory.getLogger(NameFilter.class);

    private final TS3Api api;
    private final boolean nicknameEnabled;
    private final List<Pattern> nicknamePatterns;
    private final String nicknameAction;
    private final String nicknameMessage;
    private final int nicknameBanMinutes;
    private final Set<Integer> nicknameIgnoreGroups;
    /** Client-ID → zuletzt geahndeter Nickname, damit derselbe Name nicht bei jeder Prüfung erneut bestraft wird. */
    private final Map<Integer, String> punished = new HashMap<>();

    private final boolean channelEnabled;
    private final List<Pattern> channelPatterns;
    private final boolean channelDelete;
    private final String channelAction;
    private final String channelMessage;
    private final int channelBanMinutes;
    private final Set<Integer> channelIgnoreGroups;
    private final Set<Integer> channelIgnoreChannels;

    public NameFilter(TS3Api api, BotConfig config) {
        this.api = api;
        nicknameEnabled = config.bool("nickname.enabled");
        nicknamePatterns = config.patterns("nickname.forbidden");
        nicknameAction = config.choice("nickname.action", "warn", "kick", "ban");
        nicknameMessage = config.string("nickname.message");
        nicknameBanMinutes = config.integer("nickname.ban-minutes");
        nicknameIgnoreGroups = config.intSet("nickname.ignore-groups");
        channelEnabled = config.bool("channel-name.enabled");
        channelPatterns = config.patterns("channel-name.forbidden");
        channelDelete = config.bool("channel-name.delete");
        channelAction = config.choice("channel-name.action", "none", "warn", "kick", "ban");
        channelMessage = config.string("channel-name.message");
        channelBanMinutes = config.integer("channel-name.ban-minutes");
        channelIgnoreGroups = config.intSet("channel-name.ignore-groups");
        channelIgnoreChannels = config.intSet("channel-name.ignore-channels");
    }

    public boolean isNicknameActive() {
        return nicknameEnabled;
    }

    public void checkNicknames(List<Client> clients) {
        for (Client client : clients) {
            if (client.isServerQueryClient() || ClientActions.inAnyGroup(client, nicknameIgnoreGroups)) {
                continue;
            }
            String nickname = client.getNickname();
            if (!isForbidden(nickname, nicknamePatterns)) {
                punished.remove(client.getId());
            } else if (!nickname.equals(punished.put(client.getId(), nickname))) {
                try {
                    LOG.info("Unerlaubter Nickname: {} ({})", nickname, nicknameAction);
                    ClientActions.punish(api, client.getId(), nicknameAction, nicknameMessage, nicknameBanMinutes);
                } catch (Exception e) {
                    LOG.warn("Strafe für {} fehlgeschlagen: {}", nickname, e.getMessage());
                }
            }
        }
        punished.keySet().retainAll(clients.stream().map(Client::getId).collect(Collectors.toSet()));
    }

    /** {@code invokerId} ist der Nutzer, der den Channel angelegt oder umbenannt hat. */
    public void handleChannelChange(int channelId, int invokerId) {
        if (!channelEnabled || channelIgnoreChannels.contains(channelId)) {
            return;
        }
        ChannelInfo channel = api.getChannelInfo(channelId);
        if (!isForbidden(channel.getName(), channelPatterns)) {
            return;
        }
        Client invoker = invokerId > 0 ? api.getClientInfo(invokerId) : null;
        if (invoker != null && (invoker.isServerQueryClient() || ClientActions.inAnyGroup(invoker, channelIgnoreGroups))) {
            return;
        }
        LOG.info("Unerlaubter Channelname: {} (löschen: {}, Strafe: {})", channel.getName(), channelDelete, channelAction);
        // Der Standard-Channel lässt sich nicht löschen, das würde nur einen Fehler auslösen.
        if (channelDelete && !channel.isDefault()) {
            api.deleteChannel(channelId, true);
        }
        if (invoker != null) {
            ClientActions.punish(api, invokerId, channelAction, channelMessage, channelBanMinutes);
        }
    }

    static boolean isForbidden(String name, List<Pattern> patterns) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(name).find());
    }
}
