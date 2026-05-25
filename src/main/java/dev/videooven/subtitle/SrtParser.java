package dev.videooven.subtitle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SrtParser implements SubtitleParser {
    private static final Pattern TIMING = Pattern.compile(
            "(\\d{2}:\\d{2}:\\d{2},\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2},\\d{3}).*"
    );

    @Override
    public List<SubtitleCue> parse(Path input) throws IOException {
        List<String> lines = Files.readAllLines(input);
        List<SubtitleCue> cues = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            while (index < lines.size() && lines.get(index).isBlank()) {
                index++;
            }
            if (index >= lines.size()) {
                break;
            }

            // SRT 序号只是给人看的，内部模型不需要保存。
            if (lines.get(index).trim().matches("\\d+")) {
                index++;
            }
            if (index >= lines.size()) {
                break;
            }

            Matcher matcher = TIMING.matcher(lines.get(index).trim());
            if (!matcher.matches()) {
                throw new IOException("Invalid SRT timing line: " + lines.get(index));
            }
            index++;

            List<String> textLines = new ArrayList<>();
            while (index < lines.size() && !lines.get(index).isBlank()) {
                textLines.add(lines.get(index));
                index++;
            }
            cues.add(new SubtitleCue(matcher.group(1), matcher.group(2), textLines));
        }
        return cues;
    }
}
