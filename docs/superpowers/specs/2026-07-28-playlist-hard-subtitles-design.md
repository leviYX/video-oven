# Playlist Hard-Subtitle Video Design

## Goal

Add a resumable workflow that processes YouTube playlist entries in a caller-supplied inclusive index range, translates their subtitles into bilingual Chinese-English subtitles, burns those subtitles into each video, and concatenates the processed entries into one MP4 file.

The initial target is playlist indexes 1 through 20 from:

```text
https://www.youtube.com/watch?v=6dTL76DWYQU&list=PLkDaE6sCZn6FNC6YRfRQc_FbeQrF8BwGI
```

The implementation must also work with other YouTube playlist URLs and index ranges.

## Scope

The workflow will:

- process playlist entries in ascending playlist-index order;
- prefer author-provided or YouTube automatic subtitles;
- fall back to Whisper when no usable source-language subtitle is available;
- translate each entry into a bilingual SRT using the existing Video Oven CLI;
- burn the bilingual subtitles into each entry;
- normalize all entries to compatible video and audio parameters;
- concatenate the normalized entries into one hard-subtitled MP4;
- retry transient stage failures three times after the initial attempt;
- stop after retries are exhausted; and
- preserve completed work so the command can resume later.

The first version will not add title cards, transitions, chapters, parallel processing, a graphical interface, or playlist processing to the Java CLI.

## Recommended Approach

Create a shell orchestrator named `scripts/bake-playlist-hard-subtitles.sh`. It will coordinate `yt-dlp`, the existing shaded Video Oven jar, Whisper through the existing CLI behavior, ffmpeg, and ffprobe.

Each playlist entry will be completed independently before final concatenation. This isolates failures, avoids rerunning successful DeepSeek translations, and makes recovery practical for a 20-video job.

Alternatives considered:

1. Concatenate source videos first, then transcribe and translate the combined video. This makes recovery expensive and prevents reliable reuse of each video's YouTube subtitles.
2. Implement the entire workflow inside the Java CLI. This offers stronger type-level structure but would introduce playlist task state, full-video downloading, transcoding, probing, and concatenation into Java at substantially greater initial complexity.

## Command Interface

The script will accept these positional arguments:

```text
scripts/bake-playlist-hard-subtitles.sh \
  <deepseek-api-key> \
  <youtube-playlist-url> \
  <start-index> \
  <end-index> \
  [output.mp4]
```

Example:

```bash
scripts/bake-playlist-hard-subtitles.sh \
  "$DEEPSEEK_API_KEY" \
  "https://www.youtube.com/watch?v=6dTL76DWYQU&list=PLkDaE6sCZn6FNC6YRfRQc_FbeQrF8BwGI" \
  1 \
  20 \
  combined.zh-en.mp4
```

The URL must be quoted because `&` has special meaning in the shell.

The default output name will be `playlist.baked.zh-en.mp4`. The first version will use English as the source language, Simplified Chinese as the target language, bilingual output mode, and DeepSeek as the translator, matching the requested workflow.

Supported environment variables:

- `YT_DLP_COOKIES_FROM_BROWSER` or `YT_DLP_COOKIES`, using the same mutual-exclusion rule as the existing scripts;
- `PLAYLIST_WORK_DIR` to override the default `.playlist-work` directory;
- `PLAYLIST_RETRY_COUNT` to override the default of three retries after the initial attempt; and
- `KEEP_WORK_DIR=0` to remove the working directory only after the final output passes validation.

## Work Directory and State

Each playlist index has an isolated directory:

```text
.playlist-work/
├── index-001/
│   ├── source.mp4
│   ├── source.en.vtt
│   ├── translated.zh-en.srt
│   ├── baked.mp4
│   └── state.properties
├── index-002/
└── concat.txt
```

`state.properties` records successful stages such as download, source subtitle acquisition, translation, and baking. A stage is marked complete only after its output has been validated.

Resume checks will combine state with output validation:

- downloaded and baked video files must pass an ffprobe readability check;
- subtitle files must be regular, non-empty files;
- the translated SRT must be non-empty; and
- a stale state marker with a missing or invalid output causes that stage and dependent stages to run again.

The script will not trust file existence alone because interrupted commands can leave partial files.

## Per-Entry Data Flow

For every index in the inclusive range:

1. Use `yt-dlp --playlist-items <index>` to select the entry from the supplied playlist URL. The implementation must not rewrite the URL's `index` query parameter.
2. Download the video into that index's working directory. Reuse the format fallback behavior from the existing single-video scripts.
3. Ask yt-dlp for source-language author subtitles and automatic subtitles without downloading the video again. Use yt-dlp metadata output to distinguish an empty subtitle inventory from a command, authentication, or network failure.
4. If yt-dlp produces a usable English VTT or SRT, pass that local subtitle file to Video Oven.
5. If yt-dlp successfully determines that no usable subtitle exists, pass the already downloaded `source.mp4` to Video Oven so the existing Whisper path produces source subtitles. A network or authentication failure is not treated as missing subtitles.
6. Invoke the shaded jar with DeepSeek, bilingual mode, source `en`, and target `zh-CN`, producing `translated.zh-en.srt`.
7. Burn the SRT into the video while normalizing video and audio parameters, producing `baked.mp4`.
8. Validate the baked result before marking the entry complete.

The subtitle renderer will place Chinese above English using the current bilingual SRT behavior. The ffmpeg subtitle filter will request `PingFang SC` on macOS to ensure Chinese glyph coverage.

## Media Normalization and Concatenation

Every baked entry will be encoded with compatible parameters:

- video codec: H.264 via `libx264`;
- canvas: 1920x1080;
- aspect handling: preserve source aspect ratio, scale down or up to fit, then pad to 1920x1080;
- sample aspect ratio: 1:1;
- frame rate: 30 fps;
- audio codec: AAC;
- audio sample rate: 48 kHz; and
- audio channels: stereo.

These fixed parameters allow the final step to use the ffmpeg concat demuxer with stream copying:

```text
ffmpeg -f concat -safe 0 -i concat.txt -c copy output.mp4
```

`concat.txt` will list validated `baked.mp4` files in ascending playlist-index order. The output will be written to a temporary file in the destination directory and moved to the requested path only after validation, preventing a failed run from replacing a valid prior output.

## Retry and Failure Behavior

Each external stage has one initial attempt and up to three retries by default, with delays of 2, 5, and 10 seconds before the retries. The retry wrapper will report the playlist index, stage name, attempt number, and final command error.

Stages include:

- video download;
- subtitle lookup/download;
- Whisper and subtitle translation;
- subtitle burning; and
- final concatenation and validation.

When retries are exhausted, the script stops immediately, returns a non-zero exit code, preserves the work directory, and does not publish a partial final video. On the next invocation, validated completed stages are skipped.

The absence of YouTube subtitles is a normal fallback condition, not a failed stage. Authentication, rate-limit, and network failures remain retryable errors and must not silently trigger Whisper.

## Validation

ffprobe will validate downloaded, baked, and final video files. The final output must:

- be readable by ffprobe;
- contain at least one video stream;
- contain at least one audio stream; and
- have a duration close to the sum of all baked segment durations, allowing a small container and timestamp tolerance.

The script will verify that the shaded jar already exists at `target/video-oven-0.1.0.jar`, following the existing scripts' behavior. It will not build the jar automatically.

## Testing

Add shell integration tests under `src/test/java/dev/videooven/script/` using temporary repositories and fake executables. Tests will not access YouTube or translation APIs.

Coverage will include:

- processing the inclusive range in ascending order;
- passing `--playlist-items` for every requested index;
- preferring downloaded YouTube subtitles;
- falling back to the local downloaded video when subtitles are absent;
- retrying a transient stage failure and succeeding;
- stopping after retries are exhausted;
- resuming without repeating validated downloads or translations;
- rebuilding an invalid output despite a stale state marker;
- generating the concat list in index order;
- forwarding browser-cookie and cookie-file options;
- rejecting both cookie mechanisms when set together;
- rejecting invalid or reversed index ranges; and
- publishing the final output only after ffprobe validation.

Run the complete Maven test suite with `mvn test`. Existing single-video, subtitle, translation, and article behavior must remain unchanged.

## Documentation

Update `README.md` with:

- the playlist command example;
- the requirement to quote playlist URLs containing `&`;
- index range semantics;
- default output and work-directory locations;
- cookie usage;
- retry behavior;
- Whisper fallback behavior; and
- instructions for resuming and optionally cleaning completed work.
