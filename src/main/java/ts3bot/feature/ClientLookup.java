package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import com.github.theholywaffle.teamspeak3.api.wrapper.DatabaseClientInfo;
import ts3bot.config.BotConfig;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/** Findet Nutzer anhand der Eingabe in einem Chat-Befehl. */
final class ClientLookup {

    /** Zieht man einen Namen in den Chat, schickt der TeamSpeak-Client ihn als Link. */
    private static final Pattern NAME_LINK = Pattern.compile("\\[URL=[^]]*](.*?)\\[/URL]", Pattern.CASE_INSENSITIVE);
    /** Limit von TeamSpeak für eine Chat-Nachricht ist 8192, der Rest ist Reserve. */
    private static final int MAX_MESSAGE_BYTES = 8000;

    /** Ein gefundener Nutzer, egal ob online oder aus der Server-Datenbank. */
    record Match(String nickname, String uid, int databaseId, boolean online, Date created, Date lastConnected) {

        static Match of(Client client) {
            return new Match(client.getNickname(), client.getUniqueIdentifier(), client.getDatabaseId(), true,
                    client.getCreatedDate(), client.getLastConnectedDate());
        }

        static Match of(DatabaseClientInfo client) {
            return new Match(client.getNickname(), client.getUniqueIdentifier(), client.getDatabaseId(), false,
                    client.getCreatedDate(), client.getLastConnectedDate());
        }
    }

    private ClientLookup() {
    }

    static String cleanInput(String input) {
        return NAME_LINK.matcher(input).replaceAll("$1").trim();
    }

    /**
     * {@code %} ist in der Datenbanksuche ein Platzhalter. Die API holt für jeden Treffer einzeln die Details,
     * {@code !seen %} würde also die ganze Datenbank abfragen und den Bot lange blockieren.
     */
    static boolean hasWildcard(String input) {
        return input.contains("%");
    }

    /** Liest das Limit im Konstruktor der Befehle, damit ein falscher Wert schon beim Start auffällt. */
    static int maxResults(BotConfig config) {
        int max = config.integer("lookup.max-results");
        if (max < 1) {
            throw new IllegalArgumentException("lookup.max-results: mindestens 1 erwartet, ist aber " + max);
        }
        return max;
    }

    /** Online-Nutzer mit genau diesem Nickname oder dieser Unique-ID. Mehrere Treffer sind möglich. */
    static List<Client> online(TS3Api api, String nameOrUid) {
        return api.getClients().stream()
                .filter(client -> !client.isServerQueryClient())
                .filter(client -> client.getNickname().equalsIgnoreCase(nameOrUid)
                        || client.getUniqueIdentifier().equals(nameOrUid))
                .toList();
    }

    /** Alle Nutzer zu einem Namen oder einer Unique-ID, online und aus der Server-Datenbank. */
    static List<Match> find(TS3Api api, String nameOrUid) {
        List<Match> online = online(api, nameOrUid).stream().map(Match::of).toList();
        // Eine Unique-ID ist eindeutig, die Suche in der Datenbank kann entfallen.
        if (online.stream().anyMatch(match -> match.uid().equals(nameOrUid))) {
            return combine(online, List.of(), nameOrUid);
        }
        List<Match> matches = combine(online, inDatabase(api, nameOrUid), nameOrUid);
        return matches.isEmpty() ? byUid(api, nameOrUid) : matches;
    }

    /** Fasst die Treffer je Nutzer zusammen. Exakte Namen haben Vorrang vor Teilstücken, wer online ist, steht vorn. */
    static List<Match> combine(List<Match> online, List<Match> database, String name) {
        Map<String, Match> byUid = new LinkedHashMap<>();
        online.forEach(match -> byUid.putIfAbsent(match.uid(), match));
        database.forEach(match -> byUid.putIfAbsent(match.uid(), match));
        List<Match> exact = byUid.values().stream().filter(match -> match.nickname().equalsIgnoreCase(name)).toList();
        return (exact.isEmpty() ? byUid.values().stream() : exact.stream())
                .sorted(Comparator.comparing(Match::online).reversed()
                        .thenComparing(Match::lastConnected, Comparator.reverseOrder()))
                .toList();
    }

    /** Nachricht für mehrere Treffer. {@code line} bekommt die laufende Nummer und den Nutzer. */
    static String listMessage(BotConfig config, String name, String command, List<Match> matches, BiFunction<Integer, Match, String> line) {
        List<String> lines = new ArrayList<>();
        int shown = Math.min(matches.size(), maxResults(config));
        for (int i = 0; i < shown; i++) {
            lines.add(line.apply(i + 1, matches.get(i)));
        }
        String header = config.string("lookup.message-header")
                .replace("{count}", String.valueOf(matches.size()))
                .replace("{name}", name);
        String footer = config.string("lookup.message-footer").replace("{command}", command);
        return joinList(header, lines, matches.size(), config.string("lookup.message-more"), footer);
    }

    /** Hängt so viele Zeilen an, wie in eine Nachricht passen. Der Rest wird als Anzahl genannt. */
    static String joinList(String header, List<String> lines, int total, String moreTemplate, String footer) {
        int reserved = bytes(header) + bytes(moreTemplate) + bytes(footer) + 32;
        StringBuilder body = new StringBuilder();
        int shown = 0;
        for (String line : lines) {
            if (reserved + bytes(body.toString()) + bytes(line) + 1 > MAX_MESSAGE_BYTES) {
                break;
            }
            body.append('\n').append(line);
            shown++;
        }
        String more = shown < total ? "\n" + moreTemplate.replace("{count}", String.valueOf(total - shown)) : "";
        return header + body + more + "\n" + footer;
    }

    private static int bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static List<Match> byUid(TS3Api api, String uid) {
        return databaseClientByUid(api, uid).stream().map(Match::of).toList();
    }

    /** Nutzer aus der Server-Datenbank mit genau dieser Unique-ID, sonst eine leere Liste. */
    static List<DatabaseClientInfo> databaseClientByUid(TS3Api api, String uid) {
        try {
            DatabaseClientInfo client = api.getDatabaseClientByUId(uid);
            return client == null ? List.of() : List.of(client);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<Match> inDatabase(TS3Api api, String name) {
        try {
            List<DatabaseClientInfo> matches = api.getDatabaseClientsByName(name);
            return matches == null ? List.of() : matches.stream().map(Match::of).toList();
        } catch (Exception e) {
            // Ein Fehler bei der Namenssuche darf die Suche nach der Unique-ID nicht verhindern.
            return List.of();
        }
    }
}
