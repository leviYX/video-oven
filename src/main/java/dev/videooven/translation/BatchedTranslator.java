package dev.videooven.translation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class BatchedTranslator implements Translator {
    private final Translator delegate;
    private final int batchSize;

    public BatchedTranslator(Translator delegate, int batchSize) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.delegate = delegate;
        this.batchSize = batchSize;
    }

    @Override
    public List<String> translate(List<String> texts, String sourceLanguage, String targetLanguage)
            throws IOException, InterruptedException {
        if (texts.isEmpty()) {
            return List.of();
        }

        List<String> translations = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            translations.addAll(translateBatch(texts, start, end, sourceLanguage, targetLanguage));
        }
        return translations;
    }

    private List<String> translateBatch(
            List<String> allTexts,
            int start,
            int end,
            String sourceLanguage,
            String targetLanguage
    ) throws IOException, InterruptedException {
        List<String> batch = allTexts.subList(start, end);
        try {
            List<String> batchTranslations = delegate.translate(batch, sourceLanguage, targetLanguage);
            if (batchTranslations.size() != batch.size()) {
                throw new IOException("Translator returned " + batchTranslations.size()
                        + " translations for batch " + (start + 1) + "-" + end
                        + " of " + allTexts.size() + " inputs");
            }
            return batchTranslations;
        } catch (IOException e) {
            if (batch.size() == 1) {
                throw e;
            }
            int midpoint = start + batch.size() / 2;
            List<String> translations = new ArrayList<>(batch.size());
            translations.addAll(translateBatch(allTexts, start, midpoint, sourceLanguage, targetLanguage));
            translations.addAll(translateBatch(allTexts, midpoint, end, sourceLanguage, targetLanguage));
            return translations;
        }
    }
}
