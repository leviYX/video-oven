package dev.videooven.asr;

import dev.videooven.util.ExternalCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WhisperCliAudioTranscriber implements AudioTranscriber {
    private final String command;
    private final String model;
    private final Duration timeout;

    public WhisperCliAudioTranscriber(String command, String model, Duration timeout) {
        this.command = command;
        this.model = model;
        this.timeout = timeout;
    }

    @Override
    public Path transcribe(Path mediaInput, String sourceLanguage) throws IOException, InterruptedException {
        Path outputDir = Files.createTempDirectory("video-oven-whisper-");
        List<String> commandLine = new ArrayList<>(List.of(
                command,
                mediaInput.toString(),
                "--language", sourceLanguage,
                "--task", "transcribe",
                "--model", model,
                "--output_format", "srt",
                "--output_dir", outputDir.toString()
        ));

        ExternalCommand.Result result = ExternalCommand.run(commandLine, timeout);
        result.requireSuccess();

        try (var files = Files.list(outputDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".srt"))
                    .max(Comparator.comparingLong(WhisperCliAudioTranscriber::fileSize))
                    .orElseThrow(() -> new IOException("Whisper did not produce a .srt file"));
        }
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1;
        }
    }
}
