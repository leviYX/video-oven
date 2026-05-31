package dev.videooven.platform;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface MediaDownloader {
    Path downloadAudio(String url) throws IOException, InterruptedException;
}
