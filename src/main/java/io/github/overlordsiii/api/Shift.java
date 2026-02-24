package io.github.overlordsiii.api;

import java.time.LocalDateTime;

public class Shift {
    private LocalDateTime start;
    private LocalDateTime end;

    public Shift(LocalDateTime start, LocalDateTime end) {
        this.start = start;
        this.end = end;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public void setStart(LocalDateTime start) {
        this.start = start;
    }

    public void setEnd(LocalDateTime end) {
        this.end = end;
    }
}
