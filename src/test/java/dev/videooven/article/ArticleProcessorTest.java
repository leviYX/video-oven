package dev.videooven.article;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ArticleProcessorTest {
    @TempDir
    Path tempDir;

    @Test
    void translatesMarkdownBlocksAndPreservesCodeBlocks() throws Exception {
        ArticleFetcher fetcher = url -> """
                <article>
                  <h1>Building Reliable CLIs</h1>
                  <p>Keep commands predictable.</p>
                  <pre><code>System.out.println("do not translate");</code></pre>
                </article>
                """;
        Path output = tempDir.resolve("article.zh-CN.md");

        new ArticleProcessor(fetcher, new ArticleMarkdownExtractor(), (texts, source, target) -> {
            assertEquals("en", source);
            assertEquals("zh-CN", target);
            assertEquals(List.of("# Building Reliable CLIs", "Keep commands predictable."), texts);
            return List.of("# 构建可靠的命令行工具", "保持命令可预测。");
        }).process(URI.create("https://example.test/post"), output, "en", "zh-CN");

        assertEquals("""
                # 构建可靠的命令行工具

                保持命令可预测。

                ```
                System.out.println("do not translate");
                ```
                """.trim(), Files.readString(output).trim());
    }
}
