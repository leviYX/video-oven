package dev.videooven.subtitle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SubtitleTextCleanerTest {
    @Test
    void decodesHtmlEntitiesAndRemovesVttTags() {
        String cleaned = SubtitleTextCleaner.clean("<c>Tom&nbsp;&amp;&nbsp;Jerry</c> &lt;ok&gt;");

        assertEquals("Tom & Jerry <ok>", cleaned);
    }
}
