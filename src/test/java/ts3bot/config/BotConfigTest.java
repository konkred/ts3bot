package ts3bot.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BotConfigTest {

    @Test
    void oldConfigGetsDefaultsMatchingTheOldBot() throws IOException {
        Properties old = new Properties();
        old.setProperty("host", "ts.example.org");
        old.setProperty("port", "9988");
        old.setProperty("user", "serveradmin");
        old.setProperty("password", "geheim");
        old.setProperty("nickname", "Bot");

        BotConfig config = new BotConfig(BotConfig.withDefaults(old));

        assertEquals("ts.example.org", config.host());
        assertEquals(9988, config.virtualServerPort());
        assertEquals(10011, config.queryPort());
        assertEquals(416, config.ruheGroup());
        assertEquals(410, config.exemptGroup());
        assertEquals(0, config.botChannelId());
        assertEquals(List.of(315, 316, 317, 318, 409, 417, 422, 423),
                config.loyaltyRanks().stream().map(LoyaltyRank::groupId).toList());
        assertEquals(List.of(180, 360, 720, 1080, 1440, 1800, 2160, 2520),
                config.loyaltyRanks().stream().map(LoyaltyRank::days).toList());
    }

    @Test
    void ownRanksReplaceDefaultRanks() throws IOException {
        Properties user = new Properties();
        user.setProperty("loyalty.rank.b", "365;501;1 Jahr");
        user.setProperty("loyalty.rank.a", "30;500;1 Monat");

        BotConfig config = new BotConfig(BotConfig.withDefaults(user));

        assertEquals(List.of(new LoyaltyRank(30, 500, "1 Monat"), new LoyaltyRank(365, 501, "1 Jahr")),
                config.loyaltyRanks());
    }
}
