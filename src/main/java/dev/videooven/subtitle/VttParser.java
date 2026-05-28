package dev.videooven.subtitle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VttParser implements SubtitleParser {

    private static final int ZERO_ZERO_LEN = "00:00,000".length();

    private static final Pattern TIMING = Pattern.compile(
            "((?:\\d{2}:)?\\d{2}:\\d{2}\\.\\d{3})\\s*-->\\s*((?:\\d{2}:)?\\d{2}:\\d{2}\\.\\d{3})(?:\\s+.*)?"
    );

    @Override
    public List<SubtitleCue> parse(Path input) throws IOException {
        List<String> lines = Files.readAllLines(input);
        List<SubtitleCue> cues = new ArrayList<>();
        List<String> previousLines = List.of();
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index).trim();
            if (line.isBlank() || line.equals("WEBVTT") || line.startsWith("NOTE")) {
                index++;
                continue;
            }

            Matcher matcher = TIMING.matcher(line);
            if (!matcher.matches() && index + 1 < lines.size()) {
                // WebVTT 的时间轴前面可能会有一行 cue id。
                Matcher nextLineMatcher = TIMING.matcher(lines.get(index + 1).trim());
                if (nextLineMatcher.matches()) {
                    matcher = nextLineMatcher;
                    index++;
                }
            }

            if (!matcher.matches()) {
                index++;
                continue;
            }
            index++;

            List<String> textLines = new ArrayList<>();
            while (index < lines.size() && !lines.get(index).isBlank()) {
                textLines.add(SubtitleTextCleaner.clean(lines.get(index)));
                index++;
            }
            textLines = removeRollingPrefix(previousLines, textLines);
            cues.add(new SubtitleCue(toSrtTimestamp(matcher.group(1)), toSrtTimestamp(matcher.group(2)), textLines));
            previousLines = textLines;
        }
        return cues;
    }

    private static List<String> removeRollingPrefix(List<String> previousLines, List<String> currentLines) {
        if (previousLines.isEmpty() || currentLines.size() <= previousLines.size()) {
            return currentLines;
        }

        for (int i = 0; i < previousLines.size(); i++) {
            if (!previousLines.get(i).equals(currentLines.get(i))) {
                return currentLines;
            }
        }
        return new ArrayList<>(currentLines.subList(previousLines.size(), currentLines.size()));
    }

    private static String toSrtTimestamp(String timestamp) {
        // SRT 使用逗号表示毫秒，并且固定带小时字段。
        String normalized = timestamp.replace('.', ',');
        if (normalized.length() == ZERO_ZERO_LEN) {
            return "00:" + normalized;
        }
        return normalized;
    }
}
