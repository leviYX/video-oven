package dev.videooven.platform;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface SubtitleDownloader {
    Path download(String url, String sourceLanguage) throws IOException, InterruptedException;
}
