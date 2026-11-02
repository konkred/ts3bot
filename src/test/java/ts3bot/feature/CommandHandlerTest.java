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
        assertEquals("Du bist seit 412 Tagen dabei. Nächster Rang: [B]2 Jahre[/B] in 308 Tagen.",
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
        assertEquals("Du bist seit 3000 Tagen dabei und hast den höchsten Treue-Rang erreicht. Vielen Dank für deine Treue :)",
                CommandHandler.treueMessage(defaults(), 3000 * DAY));
    }
}
