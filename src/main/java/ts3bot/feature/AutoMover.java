package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ts3bot.config.BotConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Verschiebt oder kickt Nutzer bei Aufnahme, Abwesenheit, Stummschaltung und Untätigkeit.
 * Endet der Zustand, geht der Nutzer zurück in seinen alten Channel. Wer sich inzwischen selbst
 * woanders hinbewegt hat, bleibt dort und wird auch nicht erneut verschoben.
 */
public final class AutoMover {

    private static final Logger LOG = LoggerFactory.getLogger(AutoMover.class);

    private record Rule(int channel, boolean kick, String message, Set<Integer> ignoreGroups,
                        Set<Integer> ignoreChannels, Predicate<Client> active) {
    }

    private record Moved(int origin, int target) {
    }

    private final TS3Api api;
    /** In dieser Reihenfolge geprüft, die erste zutreffende Regel gewinnt. */
    private final List<Rule> rules = new ArrayList<>();
    private final Map<Integer, Moved> moved = new HashMap<>();
    private final Set<Integer> kicked = new HashSet<>();
    private final Map<Integer, Long> mutedSince = new HashMap<>();
    private final Set<Integer> idleWarned = new HashSet<>();

    private final boolean idleEnabled;
    private final long idleMs;
    private final long idleWarnMs;
    private final String idleWarnMessage;
    private final Set<Integer> idleIgnoreGroups;
    private final String muteType;
    private final long muteDelayMs;

    public AutoMover(TS3Api api, BotConfig config) {
        this.api = api;
        idleEnabled = config.bool("idle.enabled");
        idleMs = config.integer("idle.minutes") * 60_000L;
        idleWarnMs = config.integer("idle.warn-minutes") * 60_000L;
        idleWarnMessage = config.string("idle.warn-message");
        idleIgnoreGroups = config.intSet("idle.ignore-groups");
        muteType = config.choice("mute.type", "any", "all", "input", "output");
        muteDelayMs = config.integer("mute.delay-seconds") * 1000L;

        addRule(config, "recording", config.choice("recording.action", "move", "kick"), Client::isRecording);
        addRule(config, "away", "move", Client::isAway);
        addRule(config, "mute", "move", this::mutedLongEnough);
        addRule(config, "idle", config.choice("idle.action", "move", "kick"), client -> client.getIdleTime() >= idleMs);
    }

    private void addRule(BotConfig config, String prefix, String action, Predicate<Client> active) {
        if (!config.bool(prefix + ".enabled")) {
            return;
        }
        int channel = config.integer(prefix + ".channel");
        boolean kick = "kick".equals(action);
        if (!kick && channel <= 0) {
            throw new IllegalArgumentException(prefix + ".channel: Ziel-Channel fehlt");
        }
        rules.add(new Rule(channel, kick, config.string(prefix + ".message"),
                config.intSet(prefix + ".ignore-groups"), config.intSet(prefix + ".ignore-channels"), active));
    }

    public boolean isActive() {
        return !rules.isEmpty();
    }

    public void check(List<Client> clients) {
        for (Client client : clients) {
            if (client.isServerQueryClient()) {
                continue;
            }
            // Ein Fehler bei einem Nutzer darf die Prüfung der übrigen nicht verhindern.
            try {
                updateMuteTimer(client);
                warnIdle(client);
                apply(client);
            } catch (Exception e) {
                LOG.warn("Verschieben von {} fehlgeschlagen: {}", client.getNickname(), e.getMessage());
            }
        }
        // Client-IDs werden neu vergeben, Einträge von Nutzern, die offline sind, müssen also weg.
        Set<Integer> online = clients.stream().map(Client::getId).collect(Collectors.toSet());
        moved.keySet().retainAll(online);
        kicked.retainAll(online);
        mutedSince.keySet().retainAll(online);
        idleWarned.retainAll(online);
    }

    private void apply(Client client) {
        int id = client.getId();
        Moved previous = moved.get(id);
        Rule rule = rules.stream().filter(r -> applies(r, client, previous != null)).findFirst().orElse(null);

        if (rule == null) {
            kicked.remove(id);
            if (previous != null) {
                moved.remove(id);
                if (client.getChannelId() == previous.target()) {
                    api.moveClient(id, previous.origin());
                }
            }
            return;
        }
        // Vor der Aktion eintragen: Schlägt sie fehl, wird es nicht alle paar Sekunden neu versucht.
        if (rule.kick()) {
            if (kicked.add(id)) {
                api.kickClientFromServer(ClientActions.shorten(rule.message(), 80), id);
            }
        } else if (previous == null && client.getChannelId() != rule.channel()) {
            moved.put(id, new Moved(client.getChannelId(), rule.channel()));
            api.moveClient(id, rule.channel());
            if (!rule.message().isBlank()) {
                api.sendPrivateMessage(id, rule.message());
            }
        }
    }

    private static boolean applies(Rule rule, Client client, boolean alreadyMoved) {
        // Wer schon verschoben wurde, darf nicht wegen seines neuen Channels sofort zurückwandern.
        return rule.active().test(client)
                && !ClientActions.inAnyGroup(client, rule.ignoreGroups())
                && (alreadyMoved || !rule.ignoreChannels().contains(client.getChannelId()));
    }

    private void updateMuteTimer(Client client) {
        if (isMuted(muteType, client.isInputMuted(), client.isOutputMuted())) {
            mutedSince.putIfAbsent(client.getId(), System.currentTimeMillis());
        } else {
            mutedSince.remove(client.getId());
        }
    }

    private boolean mutedLongEnough(Client client) {
        Long since = mutedSince.get(client.getId());
        return since != null && System.currentTimeMillis() - since >= muteDelayMs;
    }

    private void warnIdle(Client client) {
        if (!idleEnabled || idleWarnMs <= 0 || ClientActions.inAnyGroup(client, idleIgnoreGroups)) {
            return;
        }
        long idle = client.getIdleTime();
        if (idle < idleWarnMs) {
            idleWarned.remove(client.getId());
        } else if (idle < idleMs && idleWarned.add(client.getId())) {
            api.sendPrivateMessage(client.getId(), idleWarnMessage);
        }
    }

    static boolean isMuted(String type, boolean input, boolean output) {
        return switch (type) {
            case "input" -> input;
            case "output" -> output;
            case "all" -> input && output;
            default -> input || output;
        };
    }
}
