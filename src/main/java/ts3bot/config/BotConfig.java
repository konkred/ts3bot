package ts3bot.config;

import com.github.theholywaffle.teamspeak3.TS3Query.FloodRate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/**
 * Bot-Konfiguration aus {@code config.properties}.
 * <p>
 * Fehlende Schlüssel werden aus {@code config.default.properties} (im Jar) ergänzt,
 * damit auch eine alte Config mit nur host/port/user/password/nickname weiter funktioniert.
 */
public final class BotConfig {

    private static final String DEFAULTS_RESOURCE = "/config.default.properties";
    private static final String RANK_PREFIX = "loyalty.rank.";

    private final Properties props;
    private final List<LoyaltyRank> loyaltyRanks;

    BotConfig(Properties props) {
        this.props = props;
        this.loyaltyRanks = props.stringPropertyNames().stream()
                .filter(key -> key.startsWith(RANK_PREFIX))
                .map(key -> LoyaltyRank.parse(key, props.getProperty(key)))
                .sorted(Comparator.comparingInt(LoyaltyRank::days))
                .toList();
    }

    /** Legt die Config aus der Vorlage an, falls sie fehlt. Gibt {@code true} zurück, wenn sie neu angelegt wurde. */
    public static boolean createDefaultIfMissing(Path file) throws IOException {
        if (Files.exists(file)) {
            return false;
        }
        try (InputStream in = BotConfig.class.getResourceAsStream(DEFAULTS_RESOURCE)) {
            Files.copy(in, file);
        }
        return true;
    }

    public static BotConfig load(Path file) throws IOException {
        Properties user = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            user.load(reader);
        }
        return new BotConfig(withDefaults(user));
    }

    static Properties withDefaults(Properties user) throws IOException {
        Properties merged = new Properties();
        try (Reader reader = new InputStreamReader(BotConfig.class.getResourceAsStream(DEFAULTS_RESOURCE), StandardCharsets.UTF_8)) {
            merged.load(reader);
        }
        // Eigene Treue-Ränge ersetzen die Standard-Ränge komplett, statt sich mit ihnen zu mischen.
        if (user.stringPropertyNames().stream().anyMatch(key -> key.startsWith(RANK_PREFIX))) {
            merged.stringPropertyNames().stream()
                    .filter(key -> key.startsWith(RANK_PREFIX))
                    .forEach(merged::remove);
        }
        merged.putAll(user);
        return merged;
    }

    // --- Verbindung ---

    public String host() {
        return string("host");
    }

    public int queryPort() {
        return integer("query.port");
    }

    /** Port des virtuellen Servers (Schlüssel {@code port}, wie in der alten Config). */
    public int virtualServerPort() {
        return integer("port");
    }

    public String user() {
        return string("user");
    }

    public String password() {
        return string("password");
    }

    public String nickname() {
        return string("nickname");
    }

    public FloodRate floodRate() {
        String value = string("query.flood-rate");
        return switch (value.toLowerCase()) {
            case "default" -> FloodRate.DEFAULT;
            case "unlimited" -> FloodRate.UNLIMITED;
            default -> throw new IllegalArgumentException("query.flood-rate: 'default' oder 'unlimited' erwartet, ist aber '" + value + "'");
        };
    }

    public boolean reconnect() {
        return bool("reconnect");
    }

    public String startupMessage() {
        return string("startup.message");
    }

    /** Anti-Bot-Gruppe: bei Mitgliedern dieser Gruppe verändert der Bot keine Ränge automatisch. */
    public int exemptGroup() {
        return integer("exempt-group");
    }

    // --- Befehle ---

    public int ruheGroup() {
        return integer("command.ruhe.group");
    }

    public String ruheName() {
        return string("command.ruhe.name");
    }

    public int ruheCooldownSeconds() {
        return integer("command.ruhe.cooldown-seconds");
    }

    public String botinfoMessage() {
        return string("command.botinfo.message");
    }

    // --- Mitglied nach Wartezeit ---

    public boolean memberEnabled() {
        return bool("member.enabled");
    }

    public int memberGuestGroup() {
        return integer("member.guest-group");
    }

    public int memberGroup() {
        return integer("member.member-group");
    }

    public int memberDelayMinutes() {
        return integer("member.delay-minutes");
    }

    // --- Treue-Ränge ---

    public boolean loyaltyEnabled() {
        return bool("loyalty.enabled");
    }

    public String loyaltyMessage() {
        return string("loyalty.message");
    }

    /** Alle Treue-Ränge, aufsteigend nach Tagen sortiert. */
    public List<LoyaltyRank> loyaltyRanks() {
        return loyaltyRanks;
    }

    private String string(String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Config-Schlüssel fehlt: " + key);
        }
        return value.trim();
    }

    private int integer(String key) {
        String value = string(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + ": Zahl erwartet, ist aber '" + value + "'", e);
        }
    }

    private boolean bool(String key) {
        return Boolean.parseBoolean(string(key));
    }
}
