package dev.videooven.article;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ArticleMarkdownExtractorTest {
    @Test
    void extractsArticleElementAsMarkdown() {
        String html = """
                <!doctype html>
                <html>
                <head><title>Ignored Site Title</title></head>
                <body>
                  <nav>Navigation should not appear</nav>
                  <article>
                    <h1>Building Reliable CLIs</h1>
                    <p>Use <strong>clear</strong> flags and read the <a href="/docs">docs</a>.</p>
                    <pre><code>java -jar app.jar --help</code></pre>
                    <ul>
                      <li>Parse arguments.</li>
                      <li>Return useful exit codes.</li>
                    </ul>
                  </article>
                </body>
                </html>
                """;

        String markdown = new ArticleMarkdownExtractor().extract(html, URI.create("https://example.test/posts/cli"));

        assertEquals("""
                # Building Reliable CLIs

                Use **clear** flags and read the [docs](https://example.test/docs).

                ```
                java -jar app.jar --help
                ```

                - Parse arguments.
                - Return useful exit codes.
                """.trim(), markdown);
    }
}
