package ts3bot.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WelcomeMessageTest {

    @Test
    void onlyOnFirstVisit() {
        assertTrue(WelcomeMessage.isFirstVisit(1));
        assertFalse(WelcomeMessage.isFirstVisit(2));
    }
}
