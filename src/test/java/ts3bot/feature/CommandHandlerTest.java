package ts3bot.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ts3bot.config.BotConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandHandlerTest {

    private static final long DAY = 86_400_000L;

    @TempDir
    Path dir;

    private BotConfig defaults() throws IOException {
        Path file = dir.resolve("config.properties");
        Files.writeString(file, "");
        return BotConfig.load(file);
    }

    @Test
    void treueShowsNextRank() throws IOException {
        assertEquals("Du bist seit 1 Jahr und 47 Tagen dabei. Nächster Rang: [B]2 Jahre[/B] in 308 Tagen.",
                CommandHandler.treueMessage(defaults(), 412 * DAY));
    }

    @Test
    void treueUsesSingularAndRoundsRemainingUp() throws IOException {
        assertEquals("Du bist seit 1 Tag dabei. Nächster Rang: [B]6 Monate[/B] in 179 Tagen.",
                CommandHandler.treueMessage(defaults(), DAY));
        assertEquals("Du bist seit 179 Tagen dabei. Nächster Rang: [B]6 Monate[/B] in 1 Tag.",
                CommandHandler.treueMessage(defaults(), 179 * DAY + 1));
    }

    @Test
    void treueAfterHighestRank() throws IOException {
        assertEquals("Du bist seit 8 Jahren und 80 Tagen dabei und hast den höchsten Treue-Rang erreicht. Vielen Dank für deine Treue :)",
                CommandHandler.treueMessage(defaults(), 3000 * DAY));
    }

    @Test
    void treueForOtherUser() throws IOException {
        assertEquals("avi ist seit 1 Jahr und 47 Tagen dabei. Nächster Rang: [B]2 Jahre[/B] in 308 Tagen.",
                CommandHandler.treueOtherMessage(defaults(), "avi", 412 * DAY));
        assertEquals("avi ist seit 8 Jahren und 80 Tagen dabei und hat den höchsten Treue-Rang erreicht.",
                CommandHandler.treueOtherMessage(defaults(), "avi", 3000 * DAY));
    }

    @Test
    void durationSwitchesToYearsAt365Days() {
        assertEquals("364 Tagen", CommandHandler.formatDuration(364));
        assertEquals("1 Jahr", CommandHandler.formatDuration(365));
        assertEquals("1 Jahr und 1 Tag", CommandHandler.formatDuration(366));
        assertEquals("2 Jahren", CommandHandler.formatDuration(730));
        assertEquals("11 Jahren und 212 Tagen", CommandHandler.formatDuration(4227));
    }

    private static ClientLookup.Match match(String nickname, String uid, boolean online, long lastConnectedDay) {
        return new ClientLookup.Match(nickname, uid, 1, online, new Date(0), new Date(lastConnectedDay * DAY));
    }

    @Test
    void exactNamesWinAndRecentUsersComeFirst() {
        List<ClientLookup.Match> result = ClientLookup.combine(
                List.of(match("Hans", "online", true, 1)),
                List.of(match("Hans", "alt", false, 2), match("hans", "neu", false, 9), match("Hansi", "teil", false, 50), match("Hans", "online", false, 1)),
                "Hans");
        assertEquals(List.of("online", "neu", "alt"), result.stream().map(ClientLookup.Match::uid).toList());
    }

    @Test
    void partialNamesAreKeptWithoutExactMatch() {
        List<ClientLookup.Match> result = ClientLookup.combine(List.of(), List.of(match("Hansi", "a", false, 1), match("Hansel", "b", false, 2)), "Hans");
        assertEquals(List.of("b", "a"), result.stream().map(ClientLookup.Match::uid).toList());
    }

    @Test
    void sameUserConnectedTwiceCountsOnce() {
        assertEquals(1, ClientLookup.combine(List.of(match("Hans", "a", true, 1), match("Hans", "a", true, 1)), List.of(), "Hans").size());
    }

    @Test
    void listNamesTheHiddenUsers() throws IOException {
        Files.writeString(dir.resolve("config.properties"), "lookup.max-results=2\n");
        BotConfig config = BotConfig.load(dir.resolve("config.properties"));
        List<ClientLookup.Match> matches = List.of(match("Hans", "a", false, 1), match("Hans", "b", false, 1), match("Hans", "c", false, 1));
        assertEquals("""
                Zu "Hans" gibt es 3 Nutzer:
                1. Hans – seit 1 Tag dabei – UID: a
                2. Hans – seit 1 Tag dabei – UID: b
                … und 1 weitere
                Für genau einen Nutzer: !treue <Unique-ID>""",
                ClientLookup.listMessage(config, "Hans", "!treue", matches, (number, m) -> CommandHandler.treueLine(config, number, m, DAY)));
    }

    @Test
    void listStaysBelowTheMessageLimit() {
        String line = "ä".repeat(100);
        String message = ClientLookup.joinList("Kopf", Collections.nCopies(500, line), 500, "… und {count} weitere", "Fuß");
        assertTrue(message.getBytes(StandardCharsets.UTF_8).length <= 8192);
        assertTrue(message.contains("weitere"));
    }

    @Test
    void wildcardIsRecognized() {
        assertTrue(ClientLookup.hasWildcard("%"));
        assertTrue(ClientLookup.hasWildcard("Ha%s"));
        assertFalse(ClientLookup.hasWildcard("Hans_Wurst"));
    }

    @Test
    void nameLinksFromTheClientAreCleaned() {
        assertEquals("K3VK0", ClientLookup.cleanInput(" [URL=client://12/XEP638xkA3770DrBmhXZ0y8L6Ng=~K3VK0]K3VK0[/URL] "));
        assertEquals("K3VK0", ClientLookup.cleanInput("K3VK0"));
    }
}
