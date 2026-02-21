package io.github.overlordsiii.config;

public enum EventColor {
    PALE_BLUE("1"),
    PALE_GREEN("2"),
    MAUVE("3"),
    PALE_RED("4"),
    YELLOW("5"),
    ORANGE("6"),
    CYAN("7"),
    GRAY("8"),
    BLUE("9"),
    GREEN("10"),
    RED("11");

    private final String id;

    EventColor(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
