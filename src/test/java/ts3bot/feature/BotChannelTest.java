package ts3bot.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotChannelTest {

    @Test
    void matchesOnlyTheBotChannel() {
        assertTrue(BotChannel.isBotChannel(77, 77));
        assertFalse(BotChannel.isBotChannel(77, 78));
    }

    @Test
    void offWithoutChannelId() {
        assertFalse(BotChannel.isBotChannel(0, 0));
    }
}
