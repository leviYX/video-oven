package dev.videooven.subtitle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface SubtitleParser {
    List<SubtitleCue> parse(Path input) throws IOException;
}
