package dev.videooven.platform;

import dev.videooven.util.ExternalCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class YtDlpMediaDownloader implements MediaDownloader {
    private final YtDlpOptions options;

    public YtDlpMediaDownloader() {
        this(YtDlpOptions.none());
    }

    public YtDlpMediaDownloader(YtDlpOptions options) {
        this.options = options;
    }

    @Override
    public Path downloadAudio(String url) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("video-oven-yt-dlp-audio-");
        List<String> command = new ArrayList<>(List.of(
                "yt-dlp",
                "--extract-audio",
                "--audio-format", "mp3",
                "-o", workDir.resolve("audio.%(ext)s").toString()
        ));
        options.appendTo(command);
        command.add(url);

        ExternalCommand.Result result = ExternalCommand.run(command, Duration.ofHours(1));
        result.requireSuccess();

        try (var files = Files.list(workDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(YtDlpMediaDownloader::isMedia)
                    .max(Comparator.comparingLong(YtDlpMediaDownloader::fileSize))
                    .orElseThrow(() -> new IOException("yt-dlp did not produce an audio file"));
        }
    }

    private static boolean isMedia(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".mp3")
                || fileName.endsWith(".m4a")
                || fileName.endsWith(".opus")
                || fileName.endsWith(".wav")
                || fileName.endsWith(".webm");
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1;
        }
    }
}
