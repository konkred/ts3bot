package ts3bot.feature;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.api.wrapper.ServerGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ts3bot.config.BotConfig;
import ts3bot.config.LoyaltyRank;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Warnt beim Start im Log, wenn eine eingetragene Gruppen-ID auf dem Server nicht existiert. */
public final class GroupCheck {

    private static final Logger LOG = LoggerFactory.getLogger(GroupCheck.class);

    private GroupCheck() {
    }

    public static void run(TS3Api api, BotConfig config) {
        // Die Prüfung ist nur ein Hinweis und darf den Start des Bots nicht verhindern.
        try {
            Set<Integer> existing = api.getServerGroups().stream().map(ServerGroup::getId).collect(Collectors.toSet());
            List<String> missing = missingGroups(configuredGroups(config), existing);
            missing.forEach(group -> LOG.warn("Servergruppe aus der Config existiert nicht auf dem Server: {}", group));
            if (missing.isEmpty()) {
                LOG.info("Alle Servergruppen aus der Config sind auf dem Server vorhanden.");
            }
        } catch (Exception e) {
            LOG.warn("Servergruppen konnten nicht geprüft werden: {}", e.getMessage());
        }
    }

    /** Gruppen-ID je Verwendungszweck. Abgeschaltete Funktionen werden nicht geprüft. */
    static Map<String, Integer> configuredGroups(BotConfig config) {
        Map<String, Integer> groups = new LinkedHashMap<>();
        groups.put("Anti-Bot", config.exemptGroup());
        groups.put(config.ruheName(), config.ruheGroup());
        if (config.memberEnabled()) {
            groups.put("Gast", config.memberGuestGroup());
            groups.put("Member", config.memberGroup());
        }
        if (config.loyaltyEnabled()) {
            for (LoyaltyRank rank : config.loyaltyRanks()) {
                groups.put("Treue-Rang " + rank.label(), rank.groupId());
            }
        }
        config.intSet("command.treue.other-groups").forEach(id -> groups.put("command.treue.other-groups " + id, id));
        if (config.bool("self-role.enabled")) {
            SelfRoles.parseRoles(config.string("self-role.roles")).values()
                    .forEach(role -> groups.put("Rolle " + role.name(), role.group()));
        }
        addGroups(groups, config, "admin-call", "groups");
        addGroups(groups, config, "auto-move", "groups");
        addGroups(groups, config, "channel-watch", "notify-groups");
        addGroups(groups, config, "group-notify", "groups", "notify-groups");
        addGroups(groups, config, "group-protect", "groups", "admin-groups");
        return groups;
    }

    private static void addGroups(Map<String, Integer> groups, BotConfig config, String feature, String... keys) {
        if (config.bool(feature + ".enabled")) {
            for (String key : keys) {
                config.intSet(feature + "." + key).forEach(id -> groups.put(feature + "." + key + " " + id, id));
            }
        }
    }

    static List<String> missingGroups(Map<String, Integer> configured, Set<Integer> existing) {
        return configured.entrySet().stream()
                .filter(entry -> !existing.contains(entry.getValue()))
                .map(entry -> entry.getKey() + " (ID " + entry.getValue() + ")")
                .toList();
    }
}
