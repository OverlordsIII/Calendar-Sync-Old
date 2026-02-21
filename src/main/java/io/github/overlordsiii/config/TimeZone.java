package io.github.overlordsiii.config;

public enum TimeZone {
    PACIFIC("America/Los_Angeles"),
    MOUNTAIN("America/Denver"),
    CENTRAL("America/Chicago"),
    EASTERN("America/New_York");

    private final String zone;

    TimeZone(String zone) {
        this.zone = zone;
    }

    public String getZone() {
        return zone;
    }
}
