package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ts3bot.config.BotConfig;
import ts3bot.config.LoyaltyRank;

import java.util.List;
import java.util.Optional;

/** Vergibt die Mitgliedsgruppe nach Wartezeit und die Treue-Ränge, beim Join und bei der regelmäßigen Prüfung. */
public final class RankManager {

    private static final Logger LOG = LoggerFactory.getLogger(RankManager.class);

    private final TS3Api api;
    private final BotConfig config;

    public RankManager(TS3Api api, BotConfig config) {
        this.api = api;
        this.config = config;
    }

    public void checkOnlineClients() {
        for (Client client : api.getClients()) {
            // Ein Fehler bei einem Nutzer darf die Prüfung der übrigen nicht verhindern.
            try {
                update(client);
            } catch (Exception ex) {
                LOG.warn("Rang-Prüfung für Client {} fehlgeschlagen: {}", client.getId(), ex.getMessage());
            }
        }
    }

    // synchronized: Join-Events und die regelmäßige Prüfung laufen in verschiedenen Threads.
    public synchronized void update(Client client) {
        // Anti-Bot-Gruppe: bei diesen Nutzern verändert der Bot keine Ränge.
        if (client.isServerQueryClient() || client.isInServerGroup(config.exemptGroup())) {
            return;
        }
        long accountAgeMs = System.currentTimeMillis() - client.getCreatedDate().getTime();

        if (config.memberEnabled()) {
            assignMember(client, accountAgeMs);
        }
        if (config.loyaltyEnabled()) {
            assignLoyaltyRank(client, accountAgeMs);
        }
    }

    private void assignMember(Client client, long accountAgeMs) {
        long delayMs = config.memberDelayMinutes() * 60_000L;
        if (client.isInServerGroup(config.memberGuestGroup())
                && accountAgeMs >= delayMs
                && !client.isInServerGroup(config.memberGroup())) {
            api.addClientToServerGroup(config.memberGroup(), client.getDatabaseId());
        }
    }

    private void assignLoyaltyRank(Client client, long accountAgeMs) {
        List<LoyaltyRank> ranks = config.loyaltyRanks();
        Optional<LoyaltyRank> reached = highestReached(ranks, accountAgeMs);
        if (reached.isEmpty()) {
            return;
        }
        LoyaltyRank rank = reached.get();
        if (!client.isInServerGroup(rank.groupId())) {
            api.addClientToServerGroup(rank.groupId(), client.getDatabaseId());
            api.sendPrivateMessage(client.getId(), config.loyaltyMessage().replace("{label}", rank.label()));
        }
        // Alle anderen Treue-Ränge entfernen, damit immer nur der aktuelle sichtbar ist.
        for (LoyaltyRank other : ranks) {
            if (other.groupId() != rank.groupId() && client.isInServerGroup(other.groupId())) {
                api.removeClientFromServerGroup(other.groupId(), client.getDatabaseId());
            }
        }
    }

    /** Höchster Rang, dessen Dauer erreicht ist. {@code ranks} muss aufsteigend nach Tagen sortiert sein. */
    static Optional<LoyaltyRank> highestReached(List<LoyaltyRank> ranks, long accountAgeMs) {
        LoyaltyRank result = null;
        for (LoyaltyRank rank : ranks) {
            if (accountAgeMs >= rank.durationMs()) {
                result = rank;
            }
        }
        return Optional.ofNullable(result);
    }

    /** Erster Rang, dessen Dauer noch nicht erreicht ist. {@code ranks} muss aufsteigend nach Tagen sortiert sein. */
    static Optional<LoyaltyRank> nextRank(List<LoyaltyRank> ranks, long accountAgeMs) {
        return ranks.stream().filter(rank -> accountAgeMs < rank.durationMs()).findFirst();
    }
}
