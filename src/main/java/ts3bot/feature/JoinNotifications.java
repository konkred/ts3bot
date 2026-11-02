package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.ClientInfo;
import ts3bot.config.BotConfig;

import java.util.Set;

/** Informiert gewählte Gruppen, wenn jemand einen überwachten Channel betritt oder ein Mitglied bestimmter Gruppen online kommt. */
public final class JoinNotifications {

    private final TS3Api api;
    private final boolean watchEnabled;
    private final Set<Integer> watchChannels;
    private final Set<Integer> watchNotifyGroups;
    private final String watchType;
    private final String watchMessage;
    private final boolean groupEnabled;
    private final Set<Integer> groupGroups;
    private final Set<Integer> groupNotifyGroups;
    private final String groupType;
    private final String groupMessage;

    public JoinNotifications(TS3Api api, BotConfig config) {
        this.api = api;
        watchEnabled = config.bool("channel-watch.enabled");
        watchChannels = config.intSet("channel-watch.channels");
        watchNotifyGroups = config.intSet("channel-watch.notify-groups");
        watchType = config.choice("channel-watch.type", "message", "poke");
        watchMessage = config.string("channel-watch.message");
        groupEnabled = config.bool("group-notify.enabled");
        groupGroups = config.intSet("group-notify.groups");
        groupNotifyGroups = config.intSet("group-notify.notify-groups");
        groupType = config.choice("group-notify.type", "message", "poke");
        groupMessage = config.string("group-notify.message");
    }

    public void handleEnter(int clientId, int targetChannel) {
        if (!watchEnabled || !watchChannels.contains(targetChannel)) {
            return;
        }
        ClientInfo client = api.getClientInfo(clientId);
        if (client.isServerQueryClient()) {
            return;
        }
        String message = watchMessage
                .replace("{client}", client.getNickname())
                .replace("{channel}", api.getChannelInfo(targetChannel).getName());
        ClientActions.notifyGroups(api, watchNotifyGroups, watchType, message, clientId);
    }

    public void handleJoin(ClientInfo client) {
        if (groupEnabled && ClientActions.inAnyGroup(client, groupGroups)) {
            String message = groupMessage.replace("{client}", client.getNickname());
            ClientActions.notifyGroups(api, groupNotifyGroups, groupType, message, client.getId());
        }
    }
}
