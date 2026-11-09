package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.event.TextMessageEvent;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ts3bot.config.BotConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Chat-Befehle: {@code !ruhe}, {@code !botinfo}, {@code !treue} und {@code !help}. */
public final class CommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CommandHandler.class);
    private static final long DAY_MS = 86_400_000L;

    private final TS3Api api;
    private final BotConfig config;
    /** Client-ID → Zeitpunkt der letzten erfolgreichen {@code !ruhe}-Nutzung. */
    private final Map<Integer, Long> ruheLastUsed = new ConcurrentHashMap<>();

    private final LastSeen lastSeen;
    private final GroupProtection protection;
    /** Wer {@code !treue <Name>} nutzen darf. Leer bedeutet: alle. */
    private final Set<Integer> treueOtherGroups;

    private final SelfRoles selfRoles;
    private final AdminCall adminCall;
    private final OnlineCount onlineCount;
    private final Leaderboard leaderboard;

    public CommandHandler(TS3Api api, BotConfig config, GroupProtection protection, Leaderboard leaderboard) {
        this.api = api;
        this.config = config;
        this.lastSeen = new LastSeen(api, config);
        this.protection = protection;
        this.selfRoles = new SelfRoles(api, config);
        this.adminCall = new AdminCall(api, config);
        this.onlineCount = new OnlineCount(api, config);
        this.leaderboard = leaderboard;
        this.treueOtherGroups = config.intSet("command.treue.other-groups");
    }

    public void handle(TextMessageEvent e) {
        // Ohne das bliebe ein fehlgeschlagener Befehl für den Nutzer ohne jede Antwort.
        try {
            dispatch(e);
        } catch (Exception ex) {
            LOG.warn("Befehl '{}' von {} fehlgeschlagen: {}", e.getMessage(), e.getInvokerName(), ex.getMessage());
            api.sendPrivateMessage(e.getInvokerId(), config.string("command.error-message"));
        }
    }

    private void dispatch(TextMessageEvent e) {
        String message = e.getMessage().toLowerCase();
        int invokerId = e.getInvokerId();

        // Diese Befehle brauchen die Originalschreibweise, weil Unique-IDs groß/klein unterscheiden.
        if (lastSeen.handle(invokerId, e.getMessage())
                || protection.handle(invokerId, e.getInvokerUniqueId(), e.getMessage())
                || selfRoles.handle(invokerId, e.getInvokerUniqueId(), e.getMessage())
                || adminCall.handle(invokerId, e.getInvokerName(), e.getInvokerUniqueId(), e.getMessage())
                || onlineCount.handle(invokerId, e.getMessage())
                || leaderboard.handle(invokerId, e.getMessage())) {
            return;
        }
        if (message.startsWith("!ruhe")) {
            ruhe(invokerId, e.getInvokerUniqueId());
        } else if (message.startsWith("!botinfo")) {
            api.sendPrivateMessage(invokerId, config.botinfoMessage());
        } else if (message.startsWith("!treue")) {
            String name = ClientLookup.cleanInput(e.getMessage().substring("!treue".length()));
            String uniqueId = e.getInvokerUniqueId();
            api.sendPrivateMessage(invokerId, name.isEmpty() ? ownTreue(uniqueId) : otherTreueIfAllowed(uniqueId, name));
        } else if (message.startsWith("!help")) {
            api.sendPrivateMessage(invokerId, config.helpMessage());
        } else if (message.startsWith("!") && !config.string("command.unknown-message").isBlank()) {
            // Nur mit "!": Normale Nachrichten im Channel-Chat sind keine Befehle.
            api.sendPrivateMessage(invokerId, config.string("command.unknown-message"));
        }
    }

    private String ownTreue(String uniqueId) {
        Client client = api.getClientByUId(uniqueId);
        return client.isInServerGroup(config.exemptGroup()) || !config.loyaltyEnabled()
                ? config.treueNoRanksMessage()
                : treueMessage(config, System.currentTimeMillis() - client.getCreatedDate().getTime());
    }

    private String otherTreueIfAllowed(String invokerUid, String name) {
        return treueOtherGroups.isEmpty() || ClientActions.inAnyGroup(api.getClientByUId(invokerUid), treueOtherGroups)
                ? otherTreue(name)
                : config.string("command.treue.other-denied");
    }

    private String otherTreue(String name) {
        if (ClientLookup.hasWildcard(name)) {
            return config.string("lookup.message-wildcard");
        }
        List<ClientLookup.Match> matches = ClientLookup.find(api, name);
        if (matches.isEmpty()) {
            return config.string("command.treue.other-unknown").replace("{name}", name);
        }
        if (matches.size() > 1) {
            long now = System.currentTimeMillis();
            return ClientLookup.listMessage(config, name, "!treue", matches, (number, match) -> treueLine(config, number, match, now));
        }
        ClientLookup.Match match = matches.get(0);
        return isExempt(match.databaseId()) || !config.loyaltyEnabled()
                ? config.string("command.treue.other-no-ranks").replace("{name}", match.nickname())
                : treueOtherMessage(config, match.nickname(), System.currentTimeMillis() - match.created().getTime());
    }

    static String treueLine(BotConfig config, int number, ClientLookup.Match match, long now) {
        return config.string("command.treue.line")
                .replace("{nr}", String.valueOf(number))
                .replace("{days}", formatDuration((now - match.created().getTime()) / DAY_MS))
                .replace("{uid}", match.uid())
                .replace("{name}", match.nickname());
    }

    private boolean isExempt(int databaseId) {
        return api.getServerGroupsByClientId(databaseId).stream().anyMatch(group -> group.getId() == config.exemptGroup());
    }

    static String treueMessage(BotConfig config, long accountAgeMs) {
        return formatTreue(config, accountAgeMs, config.treueNextMessage(), config.treueMaxMessage());
    }

    static String treueOtherMessage(BotConfig config, String nickname, long accountAgeMs) {
        return formatTreue(config, accountAgeMs, config.string("command.treue.other-next"), config.string("command.treue.other-max"))
                .replace("{name}", nickname);
    }

    private static String formatTreue(BotConfig config, long accountAgeMs, String nextTemplate, String maxTemplate) {
        String days = formatDuration(accountAgeMs / DAY_MS);
        return RankManager.nextRank(config.loyaltyRanks(), accountAgeMs)
                .map(next -> {
                    // Aufrunden: Ein angefangener Tag zählt noch als Wartezeit.
                    long remaining = (next.durationMs() - accountAgeMs + DAY_MS - 1) / DAY_MS;
                    return nextTemplate
                            .replace("{days}", days)
                            .replace("{next}", next.label())
                            .replace("{remaining}", formatDuration(remaining));
                })
                .orElseGet(() -> maxTemplate.replace("{days}", days));
    }

    /** Dativ, passend zu "seit 11 Jahren und 212 Tagen" und "in 1 Tag". Ein Jahr zählt 365 Tage. */
    static String formatDuration(long days) {
        long years = days / 365;
        long rest = days % 365;
        if (years == 0) {
            return unit(rest, "Tag", "Tagen");
        }
        String yearText = unit(years, "Jahr", "Jahren");
        return rest == 0 ? yearText : yearText + " und " + unit(rest, "Tag", "Tagen");
    }

    private static String unit(long count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
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
