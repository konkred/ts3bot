package ts3bot.config;

import com.github.theholywaffle.teamspeak3.TS3Query.FloodRate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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

    public String helpMessage() {
        String help = string("command.help.message");
        return bool("seen.enabled") ? help + "\n" + string("seen.help-line") : help;
    }

    public String treueNextMessage() {
        return string("command.treue.message-next");
    }

    public String treueMaxMessage() {
        return string("command.treue.message-max");
    }

    public String treueNoRanksMessage() {
        return string("command.treue.message-no-ranks");
    }

    // --- Erreichbarkeit des Bots ---

    public String welcomeMessage() {
        return string("welcome.message");
    }

    /** ID des Bot-Channels, 0 wenn keiner eingetragen ist. */
    public int botChannelId() {
        return integer("bot-channel.id");
    }

    // --- Regelmäßige Rang-Prüfung ---

    public int rankCheckIntervalMinutes() {
        return integer("rank-check.interval-minutes");
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

    // --- Allgemeine Zugriffe, die Features lesen ihre eigenen Schlüssel damit im Konstruktor ---

    public String string(String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Config-Schlüssel fehlt: " + key);
        }
        return value.trim();
    }

    public int integer(String key) {
        String value = string(key);
        if (value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + ": Zahl erwartet, ist aber '" + value + "'", e);
        }
    }

    public boolean bool(String key) {
        return Boolean.parseBoolean(string(key));
    }

    /** Durch Komma getrennte Zahlen, z. B. Gruppen- oder Channel-IDs. */
    public Set<Integer> intSet(String key) {
        Set<Integer> values = new LinkedHashSet<>();
        for (String part : string(key).split(",")) {
            if (!part.isBlank()) {
                try {
                    values.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(key + ": Zahlen erwartet, ist aber '" + part.trim() + "'", e);
                }
            }
        }
        return values;
    }

    /** Durch Komma getrennte Texte, z. B. Unique-IDs. */
    public Set<String> stringSet(String key) {
        Set<String> values = new LinkedHashSet<>();
        for (String part : string(key).split(",")) {
            if (!part.isBlank()) {
                values.add(part.trim());
            }
        }
        return values;
    }

    /** Durch Semikolon getrennte reguläre Ausdrücke, ohne Unterscheidung von Groß- und Kleinschreibung. */
    public List<Pattern> patterns(String key) {
        List<Pattern> patterns = new ArrayList<>();
        for (String part : string(key).split(";")) {
            if (!part.isBlank()) {
                try {
                    patterns.add(Pattern.compile(part.trim(), Pattern.CASE_INSENSITIVE));
                } catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException(key + ": ungültiger Ausdruck '" + part.trim() + "'", e);
                }
            }
        }
        return patterns;
    }

    /** Wert in Kleinbuchstaben, muss einer der erlaubten sein. */
    public String choice(String key, String... allowed) {
        String value = string(key).toLowerCase(Locale.ROOT);
        if (!List.of(allowed).contains(value)) {
            throw new IllegalArgumentException(key + ": erlaubt sind " + List.of(allowed) + ", ist aber '" + value + "'");
        }
        return value;
    }
}
