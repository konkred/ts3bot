package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.ClientInfo;
import ts3bot.config.BotConfig;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Chat-Befehl: Nutzer schalten freigegebene Servergruppen selbst an und aus. */
public final class SelfRoles {

    record Role(String name, int group) {
    }

    private final TS3Api api;
    private final boolean enabled;
    private final String command;
    private final Map<String, Role> roles;
    private final int cooldownSeconds;
    private final String usage;
    private final String added;
    private final String removed;
    private final String unknown;
    private final String cooldown;
    /** Unique-ID → Zeitpunkt der letzten Änderung. */
    private final Map<String, Long> lastUsed = new ConcurrentHashMap<>();

    public SelfRoles(TS3Api api, BotConfig config) {
        this.api = api;
        enabled = config.bool("self-role.enabled");
        command = config.string("self-role.command");
        roles = parseRoles(config.string("self-role.roles"));
        cooldownSeconds = config.integer("self-role.cooldown-seconds");
        usage = config.string("self-role.usage");
        added = config.string("self-role.message-added");
        removed = config.string("self-role.message-removed");
        unknown = config.string("self-role.message-unknown");
        cooldown = config.string("self-role.message-cooldown");
        if (enabled && roles.isEmpty()) {
            throw new IllegalArgumentException("self-role.roles: Bei eingeschalteter Funktion muss mindestens eine Rolle eingetragen sein");
        }
    }

    /** Format {@code Name:Gruppe;Name:Gruppe}. Die Schlüssel der Map sind kleingeschrieben. */
    static Map<String, Role> parseRoles(String value) {
        Map<String, Role> roles = new LinkedHashMap<>();
        for (String entry : value.split(";")) {
            if (entry.isBlank()) {
                continue;
            }
            int separator = entry.lastIndexOf(':');
            if (separator <= 0 || !entry.substring(separator + 1).trim().matches("\\d+") || entry.substring(0, separator).isBlank()) {
                throw new IllegalArgumentException("self-role.roles: 'Name:Gruppen-ID' erwartet, ist aber '" + entry.trim() + "'");
            }
            String name = entry.substring(0, separator).trim();
            roles.put(name.toLowerCase(Locale.ROOT), new Role(name, Integer.parseInt(entry.substring(separator + 1).trim())));
        }
        return roles;
    }

    /** Gibt {@code true} zurück, wenn die Nachricht dieser Befehl war. */
    public boolean handle(int invokerId, String invokerUid, String message) {
        String argument = ClientActions.arguments(message, command);
        if (!enabled || argument == null) {
            return false;
        }
        api.sendPrivateMessage(invokerId, answer(invokerUid, argument));
        return true;
    }

    private String answer(String uid, String argument) {
        String list = roles.values().stream().map(Role::name).collect(Collectors.joining(", "));
        if (argument.isEmpty()) {
            return usage.replace("{command}", command).replace("{roles}", list);
        }
        Role role = roles.get(argument.toLowerCase(Locale.ROOT));
        if (role == null) {
            return unknown.replace("{roles}", list).replace("{role}", argument);
        }
        long now = System.currentTimeMillis();
        Long last = lastUsed.get(uid);
        if (last != null && now - last <= cooldownSeconds * 1000L) {
            return cooldown.replace("{seconds}", String.valueOf(cooldownSeconds));
        }
        lastUsed.put(uid, now);
        ClientInfo client = api.getClientByUId(uid);
        if (client.isInServerGroup(role.group())) {
            api.removeClientFromServerGroup(role.group(), client.getDatabaseId());
            return removed.replace("{role}", role.name());
        }
        api.addClientToServerGroup(role.group(), client.getDatabaseId());
        return added.replace("{role}", role.name());
    }
}
