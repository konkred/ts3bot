package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import ts3bot.config.BotConfig;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Chat-Befehl: Leitet eine Nachricht an die Admins weiter, die gerade online sind. */
public final class AdminCall {

    private final TS3Api api;
    private final boolean enabled;
    private final String command;
    private final Set<Integer> groups;
    private final String type;
    private final int cooldownSeconds;
    private final String usage;
    private final String notification;
    private final String sent;
    private final String nobody;
    private final String cooldown;
    /** Unique-ID → Zeitpunkt der letzten zugestellten Nachricht. */
    private final Map<String, Long> lastUsed = new ConcurrentHashMap<>();

    public AdminCall(TS3Api api, BotConfig config) {
        this.api = api;
        enabled = config.bool("admin-call.enabled");
        command = config.string("admin-call.command");
        groups = config.intSet("admin-call.groups");
        type = config.choice("admin-call.type", "message", "poke");
        cooldownSeconds = config.integer("admin-call.cooldown-seconds");
        usage = config.string("admin-call.usage");
        notification = config.string("admin-call.notification");
        sent = config.string("admin-call.message-sent");
        nobody = config.string("admin-call.message-nobody");
        cooldown = config.string("admin-call.message-cooldown");
        if (enabled && groups.isEmpty()) {
            throw new IllegalArgumentException("admin-call.groups: Bei eingeschalteter Funktion muss mindestens eine Gruppe eingetragen sein");
        }
    }

    /** Gibt {@code true} zurück, wenn die Nachricht dieser Befehl war. */
    public boolean handle(int invokerId, String invokerName, String invokerUid, String message) {
        String text = ClientActions.arguments(message, command);
        if (!enabled || text == null) {
            return false;
        }
        api.sendPrivateMessage(invokerId, answer(invokerId, invokerName, invokerUid, text));
        return true;
    }

    private String answer(int invokerId, String invokerName, String uid, String text) {
        if (text.isEmpty()) {
            return usage.replace("{command}", command);
        }
        long now = System.currentTimeMillis();
        Long last = lastUsed.get(uid);
        if (last != null && now - last <= cooldownSeconds * 1000L) {
            return cooldown.replace("{seconds}", String.valueOf(cooldownSeconds));
        }
        String forwarded = notification.replace("{client}", invokerName).replace("{text}", text);
        int reached = ClientActions.notifyGroups(api, groups, type, forwarded, invokerId);
        if (reached == 0) {
            return nobody;
        }
        lastUsed.put(uid, now);
        return sent.replace("{count}", String.valueOf(reached));
    }
}
