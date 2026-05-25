package dev.videooven.translation;

import java.util.List;

public final class FakeTranslator implements Translator {
    @Override
    public List<String> translate(List<String> texts, String sourceLanguage, String targetLanguage) {
        return texts.stream()
                .map(text -> "[" + targetLanguage + "] " + text)
                .toList();
    }
}
