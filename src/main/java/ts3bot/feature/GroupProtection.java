package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
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
    private final String usage;
    private final String done;
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
        usage = config.string("group-protect.usage");
        done = config.string("group-protect.message-done");
        notFound = config.string("group-protect.message-not-found");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                allowed.load(reader);
            }
        }
    }

    public void start() {
        if (enabled) {
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
        if (!enabled || !(add || lower.startsWith(REMOVE))) {
            return false;
        }
        // Keine Antwort an Nutzer ohne Rechte: Die Befehle sollen nicht auffallen.
        if (!ClientActions.inAnyGroup(api.getClientByUId(invokerUid), adminGroups)) {
            return true;
        }
        String[] args = message.trim().split("\\s+", 3);
        Integer group = args.length == 3 ? parseProtectedGroup(args[1]) : null;
        if (group == null) {
            api.sendPrivateMessage(invokerId, usage);
            return true;
        }
        String uid = resolveUid(args[2]);
        if (uid == null) {
            api.sendPrivateMessage(invokerId, notFound.replace("{name}", args[2]));
            return true;
        }
        Set<String> ids = allowedIds(group);
        if (add) {
            ids.add(uid);
        } else {
            ids.remove(uid);
        }
        store(group, ids);
        api.sendPrivateMessage(invokerId, done.replace("{name}", args[2]).replace("{group}", args[1]));
        return true;
    }

    private Integer parseProtectedGroup(String value) {
        try {
            int group = Integer.parseInt(value);
            return groups.contains(group) ? group : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Sucht zuerst nach dem Nickname eines Online-Nutzers, sonst wird der Text als Unique-ID geprüft. */
    private String resolveUid(String nameOrUid) {
        for (Client client : api.getClients()) {
            if (client.getNickname().equalsIgnoreCase(nameOrUid)) {
                return client.getUniqueIdentifier();
            }
        }
        try {
            return api.getDatabaseClientByUId(nameOrUid).getUniqueIdentifier();
        } catch (Exception e) {
            return null;
        }
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
