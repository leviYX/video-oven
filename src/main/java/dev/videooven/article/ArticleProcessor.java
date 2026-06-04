package dev.videooven.article;

import dev.videooven.translation.Translator;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ArticleProcessor {
    private final ArticleFetcher fetcher;
    private final ArticleMarkdownExtractor extractor;
    private final Translator translator;

    public ArticleProcessor(ArticleFetcher fetcher, ArticleMarkdownExtractor extractor, Translator translator) {
        this.fetcher = fetcher;
        this.extractor = extractor;
        this.translator = translator;
    }

    public void process(URI url, Path output, String sourceLanguage, String targetLanguage)
            throws IOException, InterruptedException {
        String html = fetcher.fetch(url);
        String markdown = extractor.extract(html, url);
        String translated = translateMarkdown(markdown, sourceLanguage, targetLanguage);

        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, translated + System.lineSeparator());
    }

    private String translateMarkdown(String markdown, String sourceLanguage, String targetLanguage)
            throws IOException, InterruptedException {
        List<MarkdownBlock> blocks = splitMarkdownBlocks(markdown);
        List<String> sourceBlocks = blocks.stream()
                .filter(MarkdownBlock::translatable)
                .map(MarkdownBlock::text)
                .toList();
        List<String> translatedBlocks = translator.translate(sourceBlocks, sourceLanguage, targetLanguage);
        if (translatedBlocks.size() != sourceBlocks.size()) {
            throw new IOException("Translator returned " + translatedBlocks.size()
                    + " items for " + sourceBlocks.size() + " article blocks");
        }

        StringBuilder translated = new StringBuilder();
        int translatedIndex = 0;
        for (MarkdownBlock block : blocks) {
            if (!translated.isEmpty()) {
                translated.append("\n\n");
            }
            if (block.translatable()) {
                translated.append(translatedBlocks.get(translatedIndex++).trim());
            } else {
                translated.append(block.text().trim());
            }
        }
        return translated.toString().trim();
    }

    private static List<MarkdownBlock> splitMarkdownBlocks(String markdown) {
        List<MarkdownBlock> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inFence = false;
        boolean currentTranslatable = true;

        for (String line : markdown.lines().toList()) {
            if (line.startsWith("```")) {
                boolean openingFence = !inFence;
                if (openingFence && !current.isEmpty()) {
                    addBlock(blocks, current, currentTranslatable);
                }
                if (openingFence) {
                    currentTranslatable = false;
                }
                inFence = !inFence;
            }

            if (!inFence && line.isBlank()) {
                addBlock(blocks, current, currentTranslatable);
                currentTranslatable = true;
                continue;
            }

            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        addBlock(blocks, current, currentTranslatable);
        return blocks;
    }

    private static void addBlock(List<MarkdownBlock> blocks, StringBuilder current, boolean translatable) {
        String text = current.toString().trim();
        if (!text.isBlank()) {
            blocks.add(new MarkdownBlock(text, translatable));
        }
        current.setLength(0);
    }

    private record MarkdownBlock(String text, boolean translatable) {
    }
}
