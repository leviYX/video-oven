package dev.videooven.cli;

import dev.videooven.asr.AudioTranscriber;
import dev.videooven.platform.MediaDownloader;
import dev.videooven.platform.SubtitleDownloader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VideoOvenCliTest {
    @TempDir
    Path tempDir;

    @Test
    void localSubtitleInputDoesNotRunAsr() throws Exception {
        Path input = subtitleFile("input.srt", "Hello from subtitles");
        Path output = tempDir.resolve("output.srt");
        AtomicBoolean transcribed = new AtomicBoolean(false);

        int exitCode = new CommandLine(new VideoOvenCli(
                unusedSubtitleDownloader(),
                unusedMediaDownloader(),
                (mediaInput, sourceLanguage) -> {
                    transcribed.set(true);
                    return subtitleFile("asr.srt", "Hello from ASR");
                }
        )).execute(
                "--input", input.toString(),
                "--output", output.toString(),
                "--translator", "fake"
        );

        assertEquals(0, exitCode);
        assertFalse(transcribed.get());
        assertTrue(Files.readString(output).contains("[zh-CN] Hello from subtitles"));
    }

    @Test
    void localMediaInputRunsAsr() throws Exception {
        Path input = tempDir.resolve("input.mp3");
        Files.writeString(input, "not real audio");
        Path output = tempDir.resolve("output.srt");

        int exitCode = new CommandLine(new VideoOvenCli(
                unusedSubtitleDownloader(),
                unusedMediaDownloader(),
                (mediaInput, sourceLanguage) -> {
                    assertEquals(input, mediaInput);
                    assertEquals("en", sourceLanguage);
                    return subtitleFile("asr.srt", "Hello from ASR");
                }
        )).execute(
                "--input", input.toString(),
                "--output", output.toString(),
                "--translator", "fake"
        );

        assertEquals(0, exitCode);
        String rendered = Files.readString(output);
        assertTrue(rendered.contains("[zh-CN] Hello from ASR"));
        assertTrue(rendered.contains("Hello from ASR"));
    }

    @Test
    void urlFallsBackToAsrWhenNoSubtitleIsAvailable() throws Exception {
        Path downloadedAudio = tempDir.resolve("downloaded.mp3");
        Files.writeString(downloadedAudio, "not real audio");
        Path output = tempDir.resolve("output.srt");
        AtomicBoolean downloadedMedia = new AtomicBoolean(false);

        SubtitleDownloader subtitleDownloader = (url, sourceLanguage) -> {
            throw new IOException("yt-dlp did not produce a .vtt or .srt subtitle file");
        };
        MediaDownloader mediaDownloader = url -> {
            downloadedMedia.set(true);
            return downloadedAudio;
        };
        AudioTranscriber audioTranscriber = (mediaInput, sourceLanguage) -> {
            assertEquals(downloadedAudio, mediaInput);
            return subtitleFile("asr.srt", "Hello from URL ASR");
        };

        int exitCode = new CommandLine(new VideoOvenCli(
                subtitleDownloader,
                mediaDownloader,
                audioTranscriber
        )).execute(
                "--url", "https://example.test/video",
                "--output", output.toString(),
                "--translator", "fake"
        );

        assertEquals(0, exitCode);
        assertTrue(downloadedMedia.get());
        assertTrue(Files.readString(output).contains("[zh-CN] Hello from URL ASR"));
    }

    private Path subtitleFile(String name, String text) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, """
                1
                00:00:00,000 --> 00:00:01,000
                %s
                """.formatted(text));
        return path;
    }

    private static SubtitleDownloader unusedSubtitleDownloader() {
        return (url, sourceLanguage) -> {
            throw new AssertionError("subtitle downloader should not be called");
        };
    }

    private static MediaDownloader unusedMediaDownloader() {
        return url -> {
            throw new AssertionError("media downloader should not be called");
        };
    }
}
