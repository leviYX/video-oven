package dev.videooven.subtitle;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VttParserTest {
    @Test
    void parsesWebVttAndNormalizesTimestampsForSrtOutput() throws Exception {
        Path file = Files.createTempFile("video-oven", ".vtt");
        Files.writeString(file, """
                WEBVTT

                00:00:01.250 --> 00:00:02.750
                First cue.

                cue-id
                00:00:03.000 --> 00:00:04.000 align:start position:0%
                Second cue.
                """);

        List<SubtitleCue> cues = new VttParser().parse(file);

        assertEquals(2, cues.size());
        assertEquals("00:00:01,250", cues.getFirst().start());
        assertEquals("00:00:02,750", cues.getFirst().end());
        assertEquals("Second cue.", cues.get(1).text());
    }

    @Test
    void removesRollingCaptionPrefixFromYoutubeAutoCaptions() throws Exception {
        Path file = Files.createTempFile("video-oven-rolling", ".vtt");
        Files.writeString(file, """
                WEBVTT

                00:00:09.910 --> 00:00:09.920
                yeah good

                00:00:09.920 --> 00:00:12.310
                yeah good
                morning

                00:00:12.310 --> 00:00:12.320
                morning

                00:00:12.320 --> 00:00:16.189
                morning
                um so the first session of the day
                """);

        List<SubtitleCue> cues = new VttParser().parse(file);

        assertEquals(List.of("yeah good"), cues.get(0).lines());
        assertEquals(List.of("morning"), cues.get(1).lines());
        assertEquals(List.of("morning"), cues.get(2).lines());
        assertEquals(List.of("um so the first session of the day"), cues.get(3).lines());
    }
}
