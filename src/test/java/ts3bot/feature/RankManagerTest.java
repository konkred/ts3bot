package ts3bot.feature;

import org.junit.jupiter.api.Test;
import ts3bot.config.LoyaltyRank;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankManagerTest {

    private static final long DAY = 86_400_000L;
    private static final List<LoyaltyRank> RANKS = List.of(
            new LoyaltyRank(180, 315, "6 Monate"),
            new LoyaltyRank(360, 316, "1 Jahr"),
            new LoyaltyRank(2520, 423, "7 Jahre"));

    @Test
    void noRankBeforeFirstThreshold() {
        assertTrue(RankManager.highestReached(RANKS, 180 * DAY - 1).isEmpty());
    }

    @Test
    void rankReachedExactlyAtThreshold() {
        // Wie im alten Bot: created + dauer <= jetzt
        assertEquals(315, RankManager.highestReached(RANKS, 180 * DAY).orElseThrow().groupId());
    }

    @Test
    void picksHighestReachedRank() {
        assertEquals(316, RankManager.highestReached(RANKS, 1000 * DAY).orElseThrow().groupId());
        assertEquals(423, RankManager.highestReached(RANKS, 5000 * DAY).orElseThrow().groupId());
    }

    @Test
    void nextRankIsFirstNotYetReached() {
        assertEquals(315, RankManager.nextRank(RANKS, 0).orElseThrow().groupId());
        assertEquals(316, RankManager.nextRank(RANKS, 180 * DAY).orElseThrow().groupId());
        assertEquals(423, RankManager.nextRank(RANKS, 360 * DAY).orElseThrow().groupId());
    }

    @Test
    void noNextRankAfterHighestReached() {
        assertTrue(RankManager.nextRank(RANKS, 2520 * DAY).isEmpty());
    }
}
