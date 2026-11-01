package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.event.ClientJoinEvent;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ts3bot.config.BotConfig;
import ts3bot.config.LoyaltyRank;

import java.util.List;
import java.util.Optional;

/** Beim Betreten des Servers: Mitgliedsgruppe nach Wartezeit und Treue-Ränge vergeben. */
public final class JoinHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JoinHandler.class);

    private final TS3Api api;
    private final BotConfig config;

    public JoinHandler(TS3Api api, BotConfig config) {
        this.api = api;
        this.config = config;
    }

    public void handle(ClientJoinEvent e) {
        Client client;
        try {
            client = api.getClientByUId(e.getUniqueClientIdentifier());
        } catch (Exception ex) {
            LOG.debug("Client {} nicht gefunden: {}", e.getUniqueClientIdentifier(), ex.getMessage());
            return;
        }
        if (client == null || client.isServerQueryClient()) {
            return;
        }
        long accountAgeMs = System.currentTimeMillis() - client.getCreatedDate().getTime();

        if (config.memberEnabled()) {
            assignMember(client, accountAgeMs);
        }
        if (config.loyaltyEnabled() && !client.isInServerGroup(config.loyaltyExemptGroup())) {
            assignLoyaltyRank(client, e.getClientId(), accountAgeMs);
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

    private void assignLoyaltyRank(Client client, int clientId, long accountAgeMs) {
        List<LoyaltyRank> ranks = config.loyaltyRanks();
        Optional<LoyaltyRank> reached = highestReached(ranks, accountAgeMs);
        if (reached.isEmpty()) {
            return;
        }
        LoyaltyRank rank = reached.get();
        if (!client.isInServerGroup(rank.groupId())) {
            api.addClientToServerGroup(rank.groupId(), client.getDatabaseId());
            api.sendPrivateMessage(clientId, config.loyaltyMessage().replace("{label}", rank.label()));
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
}
