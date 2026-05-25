package dev.videooven.platform;

import dev.videooven.util.ExternalCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

public final class YtDlpSubtitleDownloader implements SubtitleDownloader {
    @Override
    public Path download(String url, String sourceLanguage) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("video-oven-yt-dlp-");
        // 优先拿作者字幕，同时允许自动字幕，普通 YouTube 视频也能跑通。
        List<String> command = List.of(
                "yt-dlp",
                "--skip-download",
                "--write-subs",
                "--write-auto-subs",
                "--sub-langs", sourceLanguage,
                "--sub-format", "vtt/srt",
                "-o", workDir.resolve("subtitle.%(ext)s").toString(),
                url
        );

        ExternalCommand.Result result = ExternalCommand.run(command, Duration.ofMinutes(5));
        result.requireSuccess();

        try (var files = Files.list(workDir)) {
            // yt-dlp 有时会写出多个字幕文件，正常字幕通常体积最大。
            return files
                    .filter(Files::isRegularFile)
                    .filter(YtDlpSubtitleDownloader::isSubtitle)
                    .max(Comparator.comparingLong(YtDlpSubtitleDownloader::fileSize))
                    .orElseThrow(() -> new IOException("yt-dlp did not produce a .vtt or .srt subtitle file"));
        }
    }

    private static boolean isSubtitle(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".vtt") || fileName.endsWith(".srt");
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1;
        }
    }
}
