package dev.videooven.translation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BatchedTranslatorTest {
    @Test
    void translatesInBatchesAndPreservesOrder() throws Exception {
        RecordingTranslator delegate = new RecordingTranslator();
        BatchedTranslator translator = new BatchedTranslator(delegate, 50);
        List<String> inputs = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            inputs.add("cue-" + i);
        }

        List<String> translations = translator.translate(inputs, "en", "zh-CN");

        assertEquals(List.of(50, 50, 50), delegate.batchSizes);
        assertEquals(150, translations.size());
        assertEquals("translated cue-0", translations.getFirst());
        assertEquals("translated cue-149", translations.getLast());
    }

    @Test
    void usesSmallerFinalBatchWhenInputDoesNotDivideEvenly() throws Exception {
        RecordingTranslator delegate = new RecordingTranslator();
        BatchedTranslator translator = new BatchedTranslator(delegate, 50);
        List<String> inputs = new ArrayList<>();
        for (int i = 0; i < 142; i++) {
            inputs.add("cue-" + i);
        }

        List<String> translations = translator.translate(inputs, "en", "zh-CN");

        assertEquals(List.of(50, 50, 42), delegate.batchSizes);
        assertEquals(142, translations.size());
        assertEquals("translated cue-141", translations.getLast());
    }

    @Test
    void splitsFailedBatchAndPreservesOrder() throws Exception {
        SometimesMismatchedTranslator delegate = new SometimesMismatchedTranslator();
        BatchedTranslator translator = new BatchedTranslator(delegate, 15);
        List<String> inputs = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            inputs.add("cue-" + i);
        }

        List<String> translations = translator.translate(inputs, "en", "zh-CN");

        assertEquals(List.of(15, 7, 8), delegate.batchSizes);
        assertEquals(15, translations.size());
        assertEquals("translated cue-0", translations.getFirst());
        assertEquals("translated cue-14", translations.getLast());
    }

    private static final class RecordingTranslator implements Translator {
        private final List<Integer> batchSizes = new ArrayList<>();

        @Override
        public List<String> translate(List<String> texts, String sourceLanguage, String targetLanguage)
                throws IOException, InterruptedException {
            batchSizes.add(texts.size());
            return texts.stream().map(text -> "translated " + text).toList();
        }
    }

    private static final class SometimesMismatchedTranslator implements Translator {
        private final List<Integer> batchSizes = new ArrayList<>();

        @Override
        public List<String> translate(List<String> texts, String sourceLanguage, String targetLanguage)
                throws IOException, InterruptedException {
            batchSizes.add(texts.size());
            if (texts.size() == 15) {
                throw new IOException("DeepSeek returned 13 translations for 15 inputs");
            }
            return texts.stream().map(text -> "translated " + text).toList();
        }
    }
}
