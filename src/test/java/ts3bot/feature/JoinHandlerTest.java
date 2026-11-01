package ts3bot.feature;

import org.junit.jupiter.api.Test;
import ts3bot.config.LoyaltyRank;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinHandlerTest {

    private static final long DAY = 86_400_000L;
    private static final List<LoyaltyRank> RANKS = List.of(
            new LoyaltyRank(180, 315, "6 Monate"),
            new LoyaltyRank(360, 316, "1 Jahr"),
            new LoyaltyRank(2520, 423, "7 Jahre"));

    @Test
    void noRankBeforeFirstThreshold() {
        assertTrue(JoinHandler.highestReached(RANKS, 180 * DAY - 1).isEmpty());
    }

    @Test
    void rankReachedExactlyAtThreshold() {
        // Wie im alten Bot: created + dauer <= jetzt
        assertEquals(315, JoinHandler.highestReached(RANKS, 180 * DAY).orElseThrow().groupId());
    }

    @Test
    void picksHighestReachedRank() {
        assertEquals(316, JoinHandler.highestReached(RANKS, 1000 * DAY).orElseThrow().groupId());
        assertEquals(423, JoinHandler.highestReached(RANKS, 5000 * DAY).orElseThrow().groupId());
    }
}
