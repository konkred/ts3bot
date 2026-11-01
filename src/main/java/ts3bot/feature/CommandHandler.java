package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.event.TextMessageEvent;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import ts3bot.config.BotConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Chat-Befehle: {@code !ruhe} und {@code !botinfo}. */
public final class CommandHandler {

    private final TS3Api api;
    private final BotConfig config;
    /** Client-ID → Zeitpunkt der letzten erfolgreichen {@code !ruhe}-Nutzung. */
    private final Map<Integer, Long> ruheLastUsed = new ConcurrentHashMap<>();

    public CommandHandler(TS3Api api, BotConfig config) {
        this.api = api;
        this.config = config;
    }

    public void handle(TextMessageEvent e) {
        String message = e.getMessage().toLowerCase();
        int invokerId = e.getInvokerId();

        if (message.startsWith("!ruhe")) {
            ruhe(invokerId, e.getInvokerUniqueId());
        } else if (message.startsWith("!botinfo")) {
            api.sendPrivateMessage(invokerId, config.botinfoMessage());
        }
    }

    private void ruhe(int invokerId, String uniqueId) {
        long now = System.currentTimeMillis();
        int cooldownSeconds = config.ruheCooldownSeconds();
        Long lastUsed = ruheLastUsed.get(invokerId);
        if (lastUsed != null && now - lastUsed <= cooldownSeconds * 1000L) {
            api.sendPrivateMessage(invokerId,
                    "Bitte warte " + cooldownSeconds + " Sekunden bevor du diese Funktion wieder nutzen kannst!");
            return;
        }
        ruheLastUsed.put(invokerId, now);
        toggleGroup(api.getClientByUId(uniqueId), config.ruheGroup(), config.ruheName());
    }

    /** Fügt die Servergruppe hinzu bzw. entfernt sie, und informiert den Nutzer darüber. */
    private void toggleGroup(Client client, int groupId, String name) {
        if (client.isInServerGroup(groupId)) {
            api.removeClientFromServerGroup(groupId, client.getDatabaseId());
            api.sendPrivateMessage(client.getId(), "Du hast nun nicht mehr den " + name + "-Rang.");
        } else {
            api.addClientToServerGroup(groupId, client.getDatabaseId());
            api.sendPrivateMessage(client.getId(), "Du hast nun den " + name + "-Rang.");
        }
    }
}
