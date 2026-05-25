package dev.videooven.translation;

import java.io.IOException;
import java.util.List;

public interface Translator {
    List<String> translate(List<String> texts, String sourceLanguage, String targetLanguage) throws IOException, InterruptedException;
}
