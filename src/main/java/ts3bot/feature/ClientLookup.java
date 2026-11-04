package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import com.github.theholywaffle.teamspeak3.api.wrapper.DatabaseClientInfo;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Findet Nutzer anhand der Eingabe in einem Chat-Befehl. */
final class ClientLookup {

    /** Zieht man einen Namen in den Chat, schickt der TeamSpeak-Client ihn als Link. */
    private static final Pattern NAME_LINK = Pattern.compile("\\[URL=[^]]*](.*?)\\[/URL]", Pattern.CASE_INSENSITIVE);

    private ClientLookup() {
    }

    static String cleanInput(String input) {
        return NAME_LINK.matcher(input).replaceAll("$1").trim();
    }

    /** Online-Nutzer mit genau diesem Nickname oder dieser Unique-ID. */
    static Optional<Client> online(TS3Api api, String nameOrUid) {
        return api.getClients().stream()
                .filter(client -> !client.isServerQueryClient())
                .filter(client -> client.getNickname().equalsIgnoreCase(nameOrUid)
                        || client.getUniqueIdentifier().equals(nameOrUid))
                .findFirst();
    }

    /** Nutzer aus der Server-Datenbank mit genau dieser Unique-ID. */
    static Optional<DatabaseClientInfo> byUid(TS3Api api, String uid) {
        try {
            return Optional.ofNullable(api.getDatabaseClientByUId(uid));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Sucht in der Server-Datenbank nach dem Namen, auch nach Teilstücken. Ein exakter Treffer hat Vorrang. */
    static Optional<DatabaseClientInfo> inDatabase(TS3Api api, String name) {
        List<DatabaseClientInfo> matches;
        try {
            matches = api.getDatabaseClientsByName(name);
        } catch (Exception e) {
            return byUid(api, name);
        }
        if (matches == null || matches.isEmpty()) {
            return byUid(api, name);
        }
        return Optional.of(matches.stream().filter(c -> c.getNickname().equalsIgnoreCase(name)).findFirst()
                .orElse(matches.get(0)));
    }
}
