package ts3bot.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ts3bot.config.BotConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupCheckTest {

    @TempDir
    Path dir;

    @Test
    void reportsOnlyMissingGroups() {
        Map<String, Integer> configured = Map.of("Member", 10);
        assertEquals(List.of("Member (ID 10)"), GroupCheck.missingGroups(configured, Set.of(11)));
        assertTrue(GroupCheck.missingGroups(configured, Set.of(10, 11)).isEmpty());
    }

    @Test
    void disabledFeaturesAreNotChecked() throws IOException {
        Path file = dir.resolve("config.properties");
        Files.writeString(file, "member.enabled=false\nloyalty.enabled=false\n");

        Map<String, Integer> groups = GroupCheck.configuredGroups(BotConfig.load(file));

        assertEquals(Set.of(410, 416), Set.copyOf(groups.values()));
        assertFalse(groups.containsKey("Member"));
    }
}
