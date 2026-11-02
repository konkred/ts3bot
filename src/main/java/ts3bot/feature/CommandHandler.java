package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.event.TextMessageEvent;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import ts3bot.config.BotConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Chat-Befehle: {@code !ruhe}, {@code !botinfo}, {@code !treue} und {@code !help}. */
public final class CommandHandler {

    private static final long DAY_MS = 86_400_000L;

    private final TS3Api api;
    private final BotConfig config;
    /** Client-ID → Zeitpunkt der letzten erfolgreichen {@code !ruhe}-Nutzung. */
    private final Map<Integer, Long> ruheLastUsed = new ConcurrentHashMap<>();

    private final LastSeen lastSeen;
    private final GroupProtection protection;

    public CommandHandler(TS3Api api, BotConfig config, GroupProtection protection) {
        this.api = api;
        this.config = config;
        this.lastSeen = new LastSeen(api, config);
        this.protection = protection;
    }

    public void handle(TextMessageEvent e) {
        String message = e.getMessage().toLowerCase();
        int invokerId = e.getInvokerId();

        // Diese Befehle brauchen die Originalschreibweise, weil Unique-IDs groß/klein unterscheiden.
        if (lastSeen.handle(invokerId, e.getMessage())
                || protection.handle(invokerId, e.getInvokerUniqueId(), e.getMessage())) {
            return;
        }
        if (message.startsWith("!ruhe")) {
            ruhe(invokerId, e.getInvokerUniqueId());
        } else if (message.startsWith("!botinfo")) {
            api.sendPrivateMessage(invokerId, config.botinfoMessage());
        } else if (message.startsWith("!treue")) {
            treue(invokerId, e.getInvokerUniqueId());
        } else if (message.startsWith("!help")) {
            api.sendPrivateMessage(invokerId, config.helpMessage());
        }
    }

    private void treue(int invokerId, String uniqueId) {
        Client client = api.getClientByUId(uniqueId);
        String answer = client.isInServerGroup(config.exemptGroup()) || !config.loyaltyEnabled()
                ? config.treueNoRanksMessage()
                : treueMessage(config, System.currentTimeMillis() - client.getCreatedDate().getTime());
        api.sendPrivateMessage(invokerId, answer);
    }

    static String treueMessage(BotConfig config, long accountAgeMs) {
        String days = formatDays(accountAgeMs / DAY_MS);
        return RankManager.nextRank(config.loyaltyRanks(), accountAgeMs)
                .map(next -> {
                    // Aufrunden: Ein angefangener Tag zählt noch als Wartezeit.
                    long remaining = (next.durationMs() - accountAgeMs + DAY_MS - 1) / DAY_MS;
                    return config.treueNextMessage()
                            .replace("{days}", days)
                            .replace("{next}", next.label())
                            .replace("{remaining}", formatDays(remaining));
                })
                .orElseGet(() -> config.treueMaxMessage().replace("{days}", days));
    }

    /** Dativ, passend zu "seit 1 Tag" und "in 308 Tagen". */
    private static String formatDays(long days) {
        return days == 1 ? "1 Tag" : days + " Tagen";
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
