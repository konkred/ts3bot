package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;

import java.util.Set;

/** Gemeinsame Aktionen der Features: Nachrichten, Benachrichtigungen und Strafen. */
final class ClientActions {

    /** Längenlimits von TeamSpeak. */
    private static final int MAX_POKE = 100;
    private static final int MAX_REASON = 80;

    private ClientActions() {
    }

    static void send(TS3Api api, int clientId, String type, String message) {
        if ("poke".equals(type)) {
            api.pokeClient(clientId, shorten(message, MAX_POKE));
        } else {
            api.sendPrivateMessage(clientId, message);
        }
    }

    static boolean inAnyGroup(Client client, Set<Integer> groups) {
        return groups.stream().anyMatch(client::isInServerGroup);
    }

    /** Benachrichtigt alle Online-Nutzer der Gruppen, außer dem Nutzer, um den es geht. */
    static void notifyGroups(TS3Api api, Set<Integer> groups, String type, String message, int excludedClientId) {
        for (Client client : api.getClients()) {
            if (!client.isServerQueryClient() && client.getId() != excludedClientId && inAnyGroup(client, groups)) {
                send(api, client.getId(), type, message);
            }
        }
    }

    /** {@code action} ist none, warn, kick oder ban. */
    static void punish(TS3Api api, int clientId, String action, String reason, int banMinutes) {
        switch (action) {
            case "warn" -> api.sendPrivateMessage(clientId, reason);
            case "kick" -> api.kickClientFromServer(shorten(reason, MAX_REASON), clientId);
            case "ban" -> api.banClient(clientId, banMinutes * 60L, shorten(reason, MAX_REASON));
            default -> { }
        }
    }

    static String shorten(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
