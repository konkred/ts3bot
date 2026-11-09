package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.ChannelProperty;
import ts3bot.config.BotConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Zeigt die Online-Zahl und den Rekord im Namen eines Channels. Den Rekord merkt sich der Bot in einer Datei. */
public final class StatsChannel {

    private static final int FIRST_UPDATE_SECONDS = 5;
    private static final int MAX_NAME = 40;
    private static final String RECORD_KEY = "record";

    private final TS3Api api;
    private final Path file;
    private final boolean enabled;
    private final int channel;
    private final String template;
    private final int intervalSeconds;
    private int record;

    public StatsChannel(TS3Api api, BotConfig config, Path file) throws IOException {
        this.api = api;
        this.file = file;
        enabled = config.bool("stats-channel.enabled");
        channel = config.integer("stats-channel.channel");
        template = config.string("stats-channel.name");
        intervalSeconds = config.integer("stats-channel.interval-seconds");
        if (enabled && channel <= 0) {
            throw new IllegalArgumentException("stats-channel.channel: Bei eingeschalteter Funktion muss eine Channel-ID eingetragen sein");
        }
        if (Files.exists(file)) {
            Properties stored = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                stored.load(reader);
            }
            record = Integer.parseInt(stored.getProperty(RECORD_KEY, "0").trim());
        }
    }

    public void start() {
        if (enabled) {
            Periodic.every("stats-channel", FIRST_UPDATE_SECONDS, intervalSeconds, this::update);
        }
    }

    private void update() {
        int online = (int) api.getClients().stream().filter(client -> !client.isServerQueryClient()).count();
        if (online > record) {
            record = online;
            store();
        }
        String name = channelName(template, online, record);
        // Der Server lehnt eine Änderung auf den bisherigen Namen ab.
        if (!name.equals(api.getChannelInfo(channel).getName())) {
            api.editChannel(channel, ChannelProperty.CHANNEL_NAME, name);
        }
    }

    static String channelName(String template, int online, int record) {
        return ClientActions.shorten(template
                .replace("{online}", String.valueOf(online))
                .replace("{record}", String.valueOf(record)), MAX_NAME);
    }

    private void store() {
        Properties stored = new Properties();
        stored.setProperty(RECORD_KEY, String.valueOf(record));
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            stored.store(writer, "Statistik-Channel: Rekord der gleichzeitig online gewesenen Nutzer");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
