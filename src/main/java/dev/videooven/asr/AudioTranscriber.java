package dev.videooven.asr;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface AudioTranscriber {
    Path transcribe(Path mediaInput, String sourceLanguage) throws IOException, InterruptedException;
}
