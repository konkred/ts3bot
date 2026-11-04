package ts3bot.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ts3bot.config.BotConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void nameLinksFromTheClientAreCleaned() {
        assertEquals("K3VK0", ClientLookup.cleanInput(" [URL=client://12/XEP638xkA3770DrBmhXZ0y8L6Ng=~K3VK0]K3VK0[/URL] "));
        assertEquals("K3VK0", ClientLookup.cleanInput("K3VK0"));
    }
}
