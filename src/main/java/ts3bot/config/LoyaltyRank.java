package ts3bot.config;

/**
 * Treue-Rang: wer seit mindestens {@code days} Tagen dabei ist (gemessen ab dem
 * ersten Besuch auf dem Server), bekommt die Servergruppe {@code groupId}.
 */
public record LoyaltyRank(int days, int groupId, String label) {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    public long durationMs() {
        return days * DAY_MS;
    }

    /** Parst einen Config-Wert im Format {@code <tage>;<gruppen-id>;<bezeichnung>}. */
    static LoyaltyRank parse(String key, String value) {
        String[] parts = value.split(";", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException(key + ": erwartet '<tage>;<gruppen-id>;<bezeichnung>', ist aber '" + value + "'");
        }
        try {
            return new LoyaltyRank(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), parts[2].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + ": Tage und Gruppen-ID müssen Zahlen sein, ist aber '" + value + "'", e);
        }
    }
}
