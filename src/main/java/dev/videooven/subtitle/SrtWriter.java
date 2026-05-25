package dev.videooven.subtitle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SrtWriter {
    public void write(Path output, List<SubtitleCue> cues) throws IOException {
        Files.writeString(output, writeToString(cues));
    }

    public String writeToString(List<SubtitleCue> cues) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cues.size(); i++) {
            SubtitleCue cue = cues.get(i);
            builder.append(i + 1).append('\n');
            builder.append(cue.start()).append(" --> ").append(cue.end()).append('\n');
            for (String line : cue.lines()) {
                builder.append(line).append('\n');
            }
            if (i < cues.size() - 1) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }
}
