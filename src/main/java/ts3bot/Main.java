package ts3bot;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.TS3Config;
import com.github.theholywaffle.teamspeak3.TS3Query;
import com.github.theholywaffle.teamspeak3.api.reconnect.ConnectionHandler;
import com.github.theholywaffle.teamspeak3.api.reconnect.ReconnectStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ts3bot.config.BotConfig;
import ts3bot.feature.GroupCheck;
import ts3bot.feature.RankChecker;
import ts3bot.feature.RankManager;
import ts3bot.listener.BotListener;

import java.nio.file.Path;

public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final Path CONFIG_FILE = Path.of("config.properties");

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
        query.connect();

        TS3Api api = query.getApi();
        RankManager ranks = new RankManager(api, config);
        listener.setApi(api, ranks);
        api.addTS3Listeners(listener);

        GroupCheck.run(api, config);
        new RankChecker(ranks).start(config.rankCheckIntervalMinutes());

        if (!config.startupMessage().isBlank()) {
            api.sendServerMessage(config.startupMessage());
        }
        LOG.info("Der Bot wurde erfolgreich gestartet!");
    }
}
