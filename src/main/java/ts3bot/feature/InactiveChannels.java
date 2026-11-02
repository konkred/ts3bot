package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ts3bot.config.BotConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Löscht Channels, die lange leer waren. Ohne {@code dry-run=false} werden sie nur im Log genannt. */
public final class InactiveChannels {

    private static final Logger LOG = LoggerFactory.getLogger(InactiveChannels.class);

    private final TS3Api api;
    private final boolean enabled;
    private final int checkMinutes;
    private final long maxEmptySeconds;
    private final boolean keepPermanent;
    private final boolean dryRun;
    private final Set<Integer> ignoreChannels;

    public InactiveChannels(TS3Api api, BotConfig config) {
        this.api = api;
        enabled = config.bool("inactive-channel.enabled");
        checkMinutes = config.integer("inactive-channel.check-minutes");
        maxEmptySeconds = config.integer("inactive-channel.max-empty-days") * 86_400L;
        keepPermanent = config.bool("inactive-channel.keep-permanent");
        dryRun = config.bool("inactive-channel.dry-run");
        // Channels, die der Bot selbst als Ziel braucht, dürfen nie verschwinden.
        ignoreChannels = new HashSet<>(config.intSet("inactive-channel.ignore-channels"));
        ignoreChannels.add(config.botChannelId());
        for (String key : List.of("away.channel", "mute.channel", "idle.channel", "recording.channel", "auto-move.channel")) {
            ignoreChannels.add(config.integer(key));
        }
        ignoreChannels.addAll(config.intSet("channel-watch.channels"));
    }

    public void start() {
        if (enabled) {
            Periodic.every("inactive-channel", checkMinutes * 60L, this::check);
        }
    }

    void check() {
        List<Channel> channels = api.getChannels();
        Set<Integer> parents = new HashSet<>();
        channels.forEach(channel -> parents.add(channel.getParentChannelId()));

        for (Channel channel : channels) {
            if (!isCandidate(channel.getId(), channel.isDefault(), channel.isEmpty(), channel.isPermanent(),
                    parents.contains(channel.getId()), keepPermanent, ignoreChannels)
                    || channel.getSecondsEmpty() < maxEmptySeconds) {
                continue;
            }
            if (dryRun) {
                LOG.info("Inaktiver Channel würde gelöscht (dry-run): {} (ID {})", channel.getName(), channel.getId());
            } else {
                LOG.info("Lösche inaktiven Channel: {} (ID {})", channel.getName(), channel.getId());
                api.deleteChannel(channel.getId(), false);
            }
        }
    }

    static boolean isCandidate(int id, boolean isDefault, boolean isEmpty, boolean isPermanent, boolean hasChildren,
                               boolean keepPermanent, Set<Integer> ignoreChannels) {
        return !isDefault && isEmpty && !hasChildren && !(keepPermanent && isPermanent) && !ignoreChannels.contains(id);
    }
}
