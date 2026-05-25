package dev.videooven.processor;

import dev.videooven.subtitle.SrtWriter;
import dev.videooven.subtitle.SubtitleCue;
import dev.videooven.subtitle.SubtitleParser;
import dev.videooven.translation.Translator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SubtitleProcessor {
    private final SubtitleParser parser;
    private final Translator translator;
    private final OutputMode outputMode;
    private final SrtWriter writer;

    public SubtitleProcessor(SubtitleParser parser, Translator translator, OutputMode outputMode) {
        this(parser, translator, outputMode, new SrtWriter());
    }

    SubtitleProcessor(SubtitleParser parser, Translator translator, OutputMode outputMode, SrtWriter writer) {
        this.parser = parser;
        this.translator = translator;
        this.outputMode = outputMode;
        this.writer = writer;
    }

    public void process(Path input, Path output, String sourceLanguage, String targetLanguage)
            throws IOException, InterruptedException {
        List<SubtitleCue> cues = parser.parse(input);
        List<String> originals = cues.stream().map(SubtitleCue::text).toList();
        List<String> translations = translator.translate(originals, sourceLanguage, targetLanguage);

        // 每条字幕对应一条翻译，这样可以保持原来的时间轴不变。
        if (translations.size() != cues.size()) {
            throw new IOException("Translator returned " + translations.size() + " items for " + cues.size() + " cues");
        }

        List<SubtitleCue> rendered = new ArrayList<>();
        for (int i = 0; i < cues.size(); i++) {
            SubtitleCue cue = cues.get(i);
            List<String> translatedLines = splitLines(translations.get(i));
            List<String> outputLines = switch (outputMode) {
                case CHINESE -> translatedLines;
                case BILINGUAL -> {
                    List<String> lines = new ArrayList<>(translatedLines);
                    // 双语字幕中文放前面，比较符合 B 站常见阅读习惯。
                    lines.addAll(cue.lines());
                    yield lines;
                }
            };
            rendered.add(new SubtitleCue(cue.start(), cue.end(), outputLines));
        }
        writer.write(output, rendered);
    }

    private static List<String> splitLines(String text) {
        return text.lines().toList();
    }
}
