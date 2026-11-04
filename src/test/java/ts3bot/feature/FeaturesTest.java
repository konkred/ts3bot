package ts3bot.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ts3bot.config.BotConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeaturesTest {

    @TempDir
    Path dir;

    private BotConfig config(String content) throws IOException {
        Path file = dir.resolve("config.properties");
        Files.writeString(file, content);
        return BotConfig.load(file);
    }

    @Test
    void allFeaturesLoadTheirKeysFromTheDefaults() throws IOException {
        BotConfig config = config("");
        assertDoesNotThrow(() -> {
            new AutoMoveOnJoin(null, config);
            new JoinNotifications(null, config);
            new NameFilter(null, config);
            new InactiveChannels(null, config);
            new LastSeen(null, config);
            new GroupProtection(null, config, dir.resolve("schutz.properties"));
            new Announcements(null, config);
            new CommandHandler(null, config, null);
        });
        assertFalse(new AutoMover(null, config).isActive());
    }

    @Test
    void invalidAnnouncementTargetFailsAtStartup() throws IOException {
        // Auch bei abgeschalteter Funktion, damit der Fehler nicht erst beim Einschalten auffällt.
        BotConfig config = config("announcement.target=irgendwo\n");
        assertThrows(IllegalArgumentException.class, () -> new Announcements(null, config));
    }

    @Test
    void invalidMaxResultsFailsAtStartup() throws IOException {
        BotConfig config = config("lookup.max-results=0\n");
        assertThrows(IllegalArgumentException.class, () -> new CommandHandler(null, config, null));
    }

    @Test
    void invalidTreueGroupsFailAtStartup() throws IOException {
        BotConfig config = config("command.treue.other-groups=admin\n");
        assertThrows(IllegalArgumentException.class, () -> new CommandHandler(null, config, null));
    }

    @Test
    void enabledMoveWithoutChannelFailsAtStartup() throws IOException {
        BotConfig config = config("away.enabled=true\n");
        assertThrows(IllegalArgumentException.class, () -> new AutoMover(null, config));
    }

    @Test
    void kickNeedsNoChannel() throws IOException {
        assertTrue(new AutoMover(null, config("recording.enabled=true\nrecording.action=kick\n")).isActive());
    }

    @Test
    void muteTypes() {
        assertTrue(AutoMover.isMuted("any", true, false));
        assertFalse(AutoMover.isMuted("all", true, false));
        assertTrue(AutoMover.isMuted("all", true, true));
        assertFalse(AutoMover.isMuted("input", false, true));
        assertTrue(AutoMover.isMuted("output", false, true));
    }

    @Test
    void forbiddenNamesIgnoreCaseAndMatchParts() throws IOException {
        List<Pattern> patterns = config("nickname.forbidden=admin;^bot\\\\d+$\n").patterns("nickname.forbidden");
        assertTrue(NameFilter.isForbidden("Der ADMIN", patterns));
        assertTrue(NameFilter.isForbidden("bot12", patterns));
        assertFalse(NameFilter.isForbidden("bot12x", patterns));
    }

    @Test
    void invalidPatternFailsAtStartup() throws IOException {
        BotConfig config = config("nickname.forbidden=[\n");
        assertThrows(IllegalArgumentException.class, () -> new NameFilter(null, config));
    }

    @Test
    void inactiveChannelsKeepProtectedOnes() {
        Set<Integer> ignored = Set.of(9);
        assertTrue(InactiveChannels.isCandidate(1, false, true, false, false, true, ignored));
        assertFalse(InactiveChannels.isCandidate(1, true, true, false, false, true, ignored), "Standard-Channel");
        assertFalse(InactiveChannels.isCandidate(1, false, false, false, false, true, ignored), "nicht leer");
        assertFalse(InactiveChannels.isCandidate(1, false, true, true, false, true, ignored), "permanent");
        assertTrue(InactiveChannels.isCandidate(1, false, true, true, false, false, ignored), "permanent erlaubt");
        assertFalse(InactiveChannels.isCandidate(1, false, true, false, true, true, ignored), "Unterchannel");
        assertFalse(InactiveChannels.isCandidate(9, false, true, false, false, true, ignored), "ausgenommen");
    }

    @Test
    void seenAppearsInHelpOnlyWhenEnabled() throws IOException {
        assertFalse(config("").helpMessage().contains("!seen"));
        assertTrue(config("seen.enabled=true\n").helpMessage().contains("!seen"));
    }

    @Test
    void uniqueIdListsKeepTheirSpecialCharacters() throws IOException {
        assertEquals(Set.of("XEP638xkA3770DrBmhXZ0y8L6Ng=", "a+b/c="),
                config("group-protect.admin-users=XEP638xkA3770DrBmhXZ0y8L6Ng=, a+b/c=\n").stringSet("group-protect.admin-users"));
    }

    @Test
    void groupListsAreParsed() throws IOException {
        assertEquals(Set.of(1, 2), config("auto-move.groups=1, 2\n").intSet("auto-move.groups"));
        assertTrue(config("").intSet("auto-move.groups").isEmpty());
        assertEquals(0, config("").integer("auto-move.channel"));
    }
}
