package dev.videooven.subtitle;

import java.util.List;

public record SubtitleCue(String start, String end, List<String> lines) {
    public SubtitleCue {
        if (start == null || start.isBlank()) {
            throw new IllegalArgumentException("start timestamp is required");
        }
        if (end == null || end.isBlank()) {
            throw new IllegalArgumentException("end timestamp is required");
        }
        lines = List.copyOf(lines);
    }

    public String text() {
        return String.join("\n", lines);
    }
}
