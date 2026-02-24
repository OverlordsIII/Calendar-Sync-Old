package io.github.overlordsiii.config;

public enum TimeZone {
    PACIFIC("America/Los_Angeles", -7),
    MOUNTAIN("America/Denver", -6),
    CENTRAL("America/Chicago", -5),
    EASTERN("America/New_York", -4);

    private final String zone;
    private final int utcCode;

    TimeZone(String zone, int utcCode) {
        this.zone = zone;
        this.utcCode = utcCode;
    }

    public String getZone() {
        return zone;
    }

    public int getUtcCode() {
        return utcCode;
    }

    public static TimeZone fromZone(String zone) {
        return switch (zone) {
            case "America/Los_Angeles" -> PACIFIC;
            case "America/Denver" -> MOUNTAIN;
            case "America/Chicago"-> CENTRAL;
            case "America/New_York" -> EASTERN;
            default -> throw new IllegalStateException("Unexpected value: " + zone);
        };
    }
}
