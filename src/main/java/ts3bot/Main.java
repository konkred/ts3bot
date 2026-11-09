package ts3bot;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.TS3Config;
import com.github.theholywaffle.teamspeak3.TS3Query;
import com.github.theholywaffle.teamspeak3.api.reconnect.ConnectionHandler;
import com.github.theholywaffle.teamspeak3.api.reconnect.ReconnectStrategy;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ts3bot.config.BotConfig;
import ts3bot.feature.Announcements;
import ts3bot.feature.AutoMover;
import ts3bot.feature.BotChannel;
import ts3bot.feature.GroupCheck;
import ts3bot.feature.GroupProtection;
import ts3bot.feature.InactiveChannels;
import ts3bot.feature.JoinToCreate;
import ts3bot.feature.Leaderboard;
import ts3bot.feature.NameFilter;
import ts3bot.feature.Periodic;
import ts3bot.feature.RankManager;
import ts3bot.feature.StatsChannel;
import ts3bot.listener.BotListener;

import java.nio.file.Path;
import java.util.List;

public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final Path CONFIG_FILE = Path.of("config.properties");
    private static final Path PROTECTION_FILE = Path.of("group-protection.properties");
    private static final Path STATS_FILE = Path.of("stats-channel.properties");

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (BotConfig.createDefaultIfMissing(CONFIG_FILE)) {
            LOG.warn("{} wurde neu angelegt. Bitte Zugangsdaten eintragen und den Bot neu starten.", CONFIG_FILE);
            return;
        }
        BotConfig config = BotConfig.load(CONFIG_FILE);
        BotListener listener = new BotListener(config);

        TS3Config tsConfig = new TS3Config()
                .setHost(config.host())
                .setQueryPort(config.queryPort())
                .setFloodRate(config.floodRate())
                .setReconnectStrategy(config.reconnect()
                        ? ReconnectStrategy.exponentialBackoff()
                        : ReconnectStrategy.disconnect())
                .setConnectionHandler(new ConnectionHandler() {
                    @Override
                    public void onConnect(TS3Api api) {
                        // Läuft beim ersten Verbinden und nach jedem Reconnect.
                        api.login(config.user(), config.password());
                        api.selectVirtualServerByPort(config.virtualServerPort());
                        api.setNickname(config.nickname());
                        BotChannel.enter(api, config);
                        api.registerAllEvents();
                        listener.setOwnClientId(api.whoAmI().getId());
                        LOG.info("Mit dem TeamSpeak-Server verbunden.");
                    }

                    @Override
                    public void onDisconnect(TS3Query query) {
                        LOG.warn("Verbindung zum TeamSpeak-Server verloren.");
                    }
                });

        TS3Query query = new TS3Query(tsConfig);
        TS3Api api = query.getApi();

        // Vor dem Verbinden anlegen: Ein Fehler in der Config beendet den Bot so mit einer klaren Meldung.
        RankManager ranks = new RankManager(api, config);
        AutoMover mover = new AutoMover(api, config);
        NameFilter names = new NameFilter(api, config);
        InactiveChannels inactiveChannels = new InactiveChannels(api, config);
        GroupProtection protection = new GroupProtection(api, config, PROTECTION_FILE);
        Announcements announcements = new Announcements(api, config);
        Leaderboard leaderboard = new Leaderboard(api, config);
        JoinToCreate joinToCreate = new JoinToCreate(api, config);
        StatsChannel statsChannel = new StatsChannel(api, config, STATS_FILE);
        listener.setApi(api, ranks, names, protection, leaderboard, joinToCreate);

        query.connect();
        api.addTS3Listeners(listener);

        GroupCheck.run(api, config);
        Periodic.every("rank-check", config.rankCheckIntervalMinutes() * 60L, ranks::checkOnlineClients);
        if (mover.isActive() || names.isNicknameActive()) {
            Periodic.every("client-monitor", config.integer("monitor.interval-seconds"), () -> {
                List<Client> clients = api.getClients();
                mover.check(clients);
                if (names.isNicknameActive()) {
                    names.checkNicknames(clients);
                }
            });
        }
        announcements.start();
        inactiveChannels.start();
        protection.start();
        leaderboard.start();
        joinToCreate.start();
        statsChannel.start();

        if (!config.startupMessage().isBlank()) {
            api.sendServerMessage(config.startupMessage());
        }
        LOG.info("Der Bot wurde erfolgreich gestartet!");
    }
}
