package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.ClientInfo;
import ts3bot.config.BotConfig;

import java.util.Set;

/** Verschiebt Nutzer gewählter Gruppen beim Betreten des Servers. Wer einen eigenen Start-Channel hat, bleibt dort. */
public final class AutoMoveOnJoin {

    private final TS3Api api;
    private final boolean enabled;
    private final Set<Integer> groups;
    private final int channel;

    public AutoMoveOnJoin(TS3Api api, BotConfig config) {
        this.api = api;
        enabled = config.bool("auto-move.enabled");
        groups = config.intSet("auto-move.groups");
        channel = config.integer("auto-move.channel");
        if (enabled && channel <= 0) {
            throw new IllegalArgumentException("auto-move.channel: Ziel-Channel fehlt");
        }
    }

    public void handle(ClientInfo client, int joinedChannel) {
        if (enabled && joinedChannel != channel && ClientActions.inAnyGroup(client, groups) && isDefaultChannel(joinedChannel)) {
            api.moveClient(client.getId(), channel);
        }
    }

    private boolean isDefaultChannel(int channelId) {
        return api.getChannels().stream().anyMatch(c -> c.getId() == channelId && c.isDefault());
    }
}
