package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import com.github.theholywaffle.teamspeak3.api.wrapper.DatabaseClientInfo;
import com.github.theholywaffle.teamspeak3.api.wrapper.ServerGroupClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ts3bot.config.BotConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stellt sicher, dass nur erlaubte Nutzer Mitglied geschützter Gruppen sind. Beim ersten Lauf werden
 * die aktuellen Mitglieder einer Gruppe als erlaubt übernommen, damit niemand versehentlich rausfliegt.
 */
public final class GroupProtection {

    private static final Logger LOG = LoggerFactory.getLogger(GroupProtection.class);
    private static final String ADD = "!schutz-add";
    private static final String REMOVE = "!schutz-del";

    private final TS3Api api;
    private final Path file;
    private final boolean enabled;
    private final int checkSeconds;
    private final Set<Integer> groups;
    private final Set<Integer> adminGroups;
    private final Set<String> adminUsers;
    private final String usage;
    private final String disabled;
    private final String denied;
    private final String notProtected;
    private final String added;
    private final String removed;
    private final String notFound;
    /** Gruppen-ID → erlaubte Unique-IDs, durch Komma getrennt. */
    private final Properties allowed = new Properties();

    public GroupProtection(TS3Api api, BotConfig config, Path file) throws IOException {
        this.api = api;
        this.file = file;
        enabled = config.bool("group-protect.enabled");
        checkSeconds = config.integer("group-protect.check-seconds");
        groups = config.intSet("group-protect.groups");
        adminGroups = config.intSet("group-protect.admin-groups");
        adminUsers = config.stringSet("group-protect.admin-users");
        usage = config.string("group-protect.usage");
        disabled = config.string("group-protect.message-disabled");
        denied = config.string("group-protect.message-denied");
        notProtected = config.string("group-protect.message-not-protected");
        added = config.string("group-protect.message-added");
        removed = config.string("group-protect.message-removed");
        notFound = config.string("group-protect.message-not-found");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                allowed.load(reader);
            }
        }
    }

    public void start() {
        if (enabled) {
            if (adminGroups.isEmpty() && adminUsers.isEmpty()) {
                LOG.warn("Gruppen-Schutz: Weder group-protect.admin-groups noch group-protect.admin-users ist gesetzt, "
                        + "niemand kann die Liste per Chat ändern.");
            }
            Periodic.every("group-protect", checkSeconds, this::check);
        }
    }

    synchronized void check() {
        for (int group : groups) {
            List<ServerGroupClient> members = api.getServerGroupClients(group);
            String key = String.valueOf(group);
            if (!allowed.containsKey(key)) {
                Set<String> current = members.stream().map(ServerGroupClient::getUniqueIdentifier).collect(Collectors.toSet());
                store(group, current);
                LOG.info("Gruppen-Schutz: {} Mitglieder von Gruppe {} als erlaubt übernommen.", current.size(), group);
                continue;
            }
            Set<String> allowedIds = allowedIds(group);
            for (ServerGroupClient member : members) {
                if (!allowedIds.contains(member.getUniqueIdentifier())) {
                    LOG.warn("Gruppen-Schutz: {} aus Gruppe {} entfernt.", member.getNickname(), group);
                    api.removeClientFromServerGroup(group, member.getClientDatabaseId());
                }
            }
        }
    }

    /** Gibt {@code true} zurück, wenn die Nachricht ein Befehl dieses Features war. */
    public synchronized boolean handle(int invokerId, String invokerUid, String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        boolean add = lower.startsWith(ADD);
        if (!add && !lower.startsWith(REMOVE)) {
            return false;
        }
        api.sendPrivateMessage(invokerId, answer(invokerUid, message, add));
        return true;
    }

    private String answer(String invokerUid, String message, boolean add) {
        if (!enabled) {
            return disabled;
        }
        if (!isAdmin(invokerUid)) {
            LOG.info("Gruppen-Schutz: Befehl abgelehnt für {}, steht nicht in admin-groups oder admin-users.", invokerUid);
            return denied;
        }
        String[] args = message.trim().split("\\s+", 3);
        if (args.length < 3 || !args[1].matches("\\d+")) {
            return usage;
        }
        int group = Integer.parseInt(args[1]);
        if (!groups.contains(group)) {
            String list = groups.stream().map(String::valueOf).collect(Collectors.joining(", "));
            return notProtected.replace("{groups}", list).replace("{group}", args[1]);
        }
        String name = ClientLookup.cleanInput(args[2]);
        String uid = resolveUid(name);
        if (uid == null) {
            return notFound.replace("{name}", name);
        }
        Set<String> ids = allowedIds(group);
        if (add) {
            ids.add(uid);
        } else {
            ids.remove(uid);
        }
        store(group, ids);
        return (add ? added : removed).replace("{group}", args[1]).replace("{name}", name);
    }

    private boolean isAdmin(String uid) {
        return adminUsers.contains(uid) || ClientActions.inAnyGroup(api.getClientByUId(uid), adminGroups);
    }

    /** Nur exakte Treffer: Ein Teilstück des Namens darf nie den falschen Nutzer freischalten. */
    private String resolveUid(String nameOrUid) {
        return ClientLookup.online(api, nameOrUid).map(Client::getUniqueIdentifier)
                .or(() -> ClientLookup.byUid(api, nameOrUid).map(DatabaseClientInfo::getUniqueIdentifier))
                .orElse(null);
    }

    private Set<String> allowedIds(int group) {
        String value = allowed.getProperty(String.valueOf(group), "");
        Set<String> ids = new HashSet<>(Arrays.asList(value.split(",")));
        ids.remove("");
        return ids;
    }

    private void store(int group, Set<String> ids) {
        allowed.setProperty(String.valueOf(group), String.join(",", ids));
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            allowed.store(writer, "Gruppen-Schutz: erlaubte Unique-IDs je Gruppe");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
