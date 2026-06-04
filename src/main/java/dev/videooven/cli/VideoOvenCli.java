package dev.videooven.cli;

import dev.videooven.asr.AudioTranscriber;
import dev.videooven.asr.WhisperCliAudioTranscriber;
import dev.videooven.article.ArticleFetcher;
import dev.videooven.article.ArticleMarkdownExtractor;
import dev.videooven.article.ArticleProcessor;
import dev.videooven.article.HttpArticleFetcher;
import dev.videooven.processor.OutputMode;
import dev.videooven.processor.SubtitleProcessor;
import dev.videooven.platform.MediaDownloader;
import dev.videooven.platform.SubtitleDownloader;
import dev.videooven.platform.YtDlpMediaDownloader;
import dev.videooven.platform.YtDlpOptions;
import dev.videooven.platform.YtDlpSubtitleDownloader;
import dev.videooven.subtitle.SrtParser;
import dev.videooven.subtitle.SubtitleParser;
import dev.videooven.subtitle.VttParser;
import dev.videooven.translation.BatchedTranslator;
import dev.videooven.translation.FakeTranslator;
import dev.videooven.translation.DeepSeekTranslator;
import dev.videooven.translation.OpenAiTranslator;
import dev.videooven.translation.TranslationFormat;
import dev.videooven.translation.Translator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Callable;

@Command(
        name = "video-oven",
        mixinStandardHelpOptions = true,
        version = "video-oven 0.1.0",
        description = "Translate subtitles, transcribed media, or article URLs into Chinese outputs."
)
public final class VideoOvenCli implements Callable<Integer> {
    private final SubtitleDownloader subtitleDownloader;
    private final MediaDownloader mediaDownloader;
    private final AudioTranscriber injectedAudioTranscriber;
    private final ArticleFetcher articleFetcher;

    @Option(names = "--input", description = "Input .srt/.vtt subtitle file, or audio/video media file for ASR.")
    private Path input;

    @Option(names = "--url", description = "Online video URL. Requires yt-dlp on PATH.")
    private String url;

    @Option(names = "--article-url", description = "Article URL to translate into Markdown.")
    private String articleUrl;

    @Option(names = "--output", required = true, description = "Output .srt subtitle file or .md article file.")
    private Path output;

    @Option(names = "--source", defaultValue = "en", description = "Source language. Default: ${DEFAULT-VALUE}.")
    private String sourceLanguage;

    @Option(names = "--target", defaultValue = "zh-CN", description = "Target language. Default: ${DEFAULT-VALUE}.")
    private String targetLanguage;

    @Option(names = "--mode", defaultValue = "bilingual", description = "Output mode: bilingual or chinese.")
    private String mode;

    @Option(names = "--translator", defaultValue = "fake", description = "Translator: fake, openai, or deepseek.")
    private String translatorName;

    @Option(names = "--openai-model", defaultValue = "${env:OPENAI_MODEL:-gpt-4.1-mini}", description = "OpenAI model.")
    private String openAiModel;

    @Option(names = "--deepseek-model", defaultValue = "${env:DEEPSEEK_MODEL:-deepseek-v4-flash}", description = "DeepSeek model.")
    private String deepSeekModel;

    @Option(names = "--deepseek-base-url", defaultValue = "${env:DEEPSEEK_BASE_URL:-https://api.deepseek.com}", description = "DeepSeek API base URL.")
    private String deepSeekBaseUrl;

    @Option(names = "--deepseek-api-key", description = "DeepSeek API key. Falls back to DEEPSEEK_API_KEY.")
    private String deepSeekApiKey;

    @Option(names = "--translation-batch-size", defaultValue = "50", description = "Subtitle cues per translation request.")
    private int translationBatchSize;

    @Option(names = "--asr", defaultValue = "auto", description = "ASR mode for media without subtitles: auto, never, or force.")
    private String asrMode;

    @Option(names = "--whisper-command", defaultValue = "${env:WHISPER_COMMAND:-whisper}", description = "Whisper CLI command.")
    private String whisperCommand;

    @Option(names = "--whisper-model", defaultValue = "${env:WHISPER_MODEL:-small}", description = "Whisper model used for ASR.")
    private String whisperModel;

    @Option(names = "--asr-timeout-minutes", defaultValue = "120", description = "ASR command timeout in minutes.")
    private long asrTimeoutMinutes;

    @Option(names = "--yt-dlp-cookies-from-browser", description = "Pass browser cookies to yt-dlp, for example: chrome or safari.")
    private String ytDlpCookiesFromBrowser;

    @Option(names = "--yt-dlp-cookies", description = "Pass a cookies.txt file to yt-dlp.")
    private Path ytDlpCookies;

    public VideoOvenCli() {
        this(null, null, null, null);
    }

    public VideoOvenCli(SubtitleDownloader subtitleDownloader) {
        this(subtitleDownloader, null, null, null);
    }

    VideoOvenCli(
            SubtitleDownloader subtitleDownloader,
            MediaDownloader mediaDownloader,
            AudioTranscriber audioTranscriber
    ) {
        this(subtitleDownloader, mediaDownloader, audioTranscriber, null);
    }

    VideoOvenCli(
            SubtitleDownloader subtitleDownloader,
            MediaDownloader mediaDownloader,
            AudioTranscriber audioTranscriber,
            ArticleFetcher articleFetcher
    ) {
        this.subtitleDownloader = subtitleDownloader;
        this.mediaDownloader = mediaDownloader;
        this.injectedAudioTranscriber = audioTranscriber;
        this.articleFetcher = articleFetcher;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new VideoOvenCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (articleUrl != null && !articleUrl.isBlank()) {
            processArticle();
            return 0;
        }

        // CLI 只负责整理参数，字幕处理交给 processor。
        Path resolvedInput = resolveInput();
        SubtitleParser parser = parserFor(resolvedInput);
        Translator translator = translatorFor(translatorName);
        OutputMode outputMode = OutputMode.parse(mode);

        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        new SubtitleProcessor(parser, translator, outputMode).process(resolvedInput, output, sourceLanguage, targetLanguage);
        return 0;
    }

    private void processArticle() throws IOException, InterruptedException {
        if (input != null || (url != null && !url.isBlank())) {
            throw new CommandLine.ParameterException(
                    new CommandLine(this),
                    "Use --article-url by itself; do not combine it with --input or --url"
            );
        }
        URI articleUri;
        try {
            articleUri = URI.create(articleUrl);
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(new CommandLine(this), "Invalid article URL: " + articleUrl, e);
        }
        if (articleUri.getScheme() == null || articleUri.getHost() == null) {
            throw new CommandLine.ParameterException(new CommandLine(this), "Article URL must be absolute: " + articleUrl);
        }

        Translator translator = translatorFor(translatorName, TranslationFormat.MARKDOWN_BLOCK);
        new ArticleProcessor(articleFetcher(), new ArticleMarkdownExtractor(), translator)
                .process(articleUri, output, sourceLanguage, targetLanguage);
    }

    private Path resolveInput() throws IOException, InterruptedException {
        // 本地字幕和在线视频二选一，避免后面流程拿到不明确的输入。
        if ((input == null && (url == null || url.isBlank())) || (input != null && url != null && !url.isBlank())) {
            throw new CommandLine.ParameterException(
                    new CommandLine(this),
                    "Specify exactly one input source: --input=<file> or --url=<video-url>"
            );
        }

        if (input != null) {
            if (!Files.isRegularFile(input)) {
                throw new CommandLine.ParameterException(new CommandLine(this), "Input file does not exist: " + input);
            }
            if (isSubtitleFile(input)) {
                return input;
            }
            if (isMediaFile(input)) {
                if (!asrEnabled()) {
                    throw new CommandLine.ParameterException(
                            new CommandLine(this),
                            "Input is a media file, but ASR is disabled by --asr=never: " + input
                    );
                }
                return audioTranscriber().transcribe(input, sourceLanguage);
            }
            throw new CommandLine.ParameterException(
                    new CommandLine(this),
                    "Input must be a .srt/.vtt subtitle or a supported audio/video file: " + input
            );
        }

        if (!asrForced()) {
            try {
                return subtitleDownloader().download(url, sourceLanguage);
            } catch (IOException e) {
                if (!asrEnabled()) {
                    throw e;
                }
                System.err.println("No usable subtitles found, falling back to ASR: " + e.getMessage());
            }
        }

        Path audio = mediaDownloader().downloadAudio(url);
        return audioTranscriber().transcribe(audio, sourceLanguage);
    }

    private static SubtitleParser parserFor(Path input) {
        String fileName = input.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".srt")) {
            return new SrtParser();
        }
        if (fileName.endsWith(".vtt")) {
            return new VttParser();
        }
        throw new IllegalArgumentException("Unsupported subtitle format: " + input);
    }

    private static boolean isSubtitleFile(Path input) {
        String fileName = input.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".srt") || fileName.endsWith(".vtt");
    }

    private static boolean isMediaFile(Path input) {
        String fileName = input.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".mp3")
                || fileName.endsWith(".m4a")
                || fileName.endsWith(".wav")
                || fileName.endsWith(".flac")
                || fileName.endsWith(".aac")
                || fileName.endsWith(".ogg")
                || fileName.endsWith(".opus")
                || fileName.endsWith(".mp4")
                || fileName.endsWith(".mov")
                || fileName.endsWith(".mkv")
                || fileName.endsWith(".webm");
    }

    private boolean asrEnabled() {
        return switch (asrMode.toLowerCase(Locale.ROOT)) {
            case "auto", "force" -> true;
            case "never" -> false;
            default -> throw new IllegalArgumentException("Unsupported ASR mode: " + asrMode);
        };
    }

    private boolean asrForced() {
        return switch (asrMode.toLowerCase(Locale.ROOT)) {
            case "force" -> true;
            case "auto", "never" -> false;
            default -> throw new IllegalArgumentException("Unsupported ASR mode: " + asrMode);
        };
    }

    private AudioTranscriber audioTranscriber() {
        if (injectedAudioTranscriber != null) {
            return injectedAudioTranscriber;
        }
        return new WhisperCliAudioTranscriber(
                whisperCommand,
                whisperModel,
                Duration.ofMinutes(asrTimeoutMinutes)
        );
    }

    private SubtitleDownloader subtitleDownloader() {
        if (subtitleDownloader != null) {
            return subtitleDownloader;
        }
        return new YtDlpSubtitleDownloader(ytDlpOptions());
    }

    private MediaDownloader mediaDownloader() {
        if (mediaDownloader != null) {
            return mediaDownloader;
        }
        return new YtDlpMediaDownloader(ytDlpOptions());
    }

    private ArticleFetcher articleFetcher() {
        if (articleFetcher != null) {
            return articleFetcher;
        }
        return new HttpArticleFetcher();
    }

    private YtDlpOptions ytDlpOptions() {
        String cookiesFromBrowser = firstNonBlank(
                ytDlpCookiesFromBrowser,
                System.getenv("YT_DLP_COOKIES_FROM_BROWSER")
        );
        Path cookies = ytDlpCookies;
        String cookiesFromEnvironment = System.getenv("YT_DLP_COOKIES");
        if (cookies == null && cookiesFromEnvironment != null && !cookiesFromEnvironment.isBlank()) {
            cookies = Path.of(cookiesFromEnvironment);
        }
        if (cookiesFromBrowser != null && cookies != null) {
            throw new IllegalArgumentException(
                    "Use only one of --yt-dlp-cookies-from-browser or --yt-dlp-cookies"
            );
        }
        return new YtDlpOptions(cookiesFromBrowser, cookies);
    }

    private Translator translatorFor(String name) {
        return translatorFor(name, TranslationFormat.SUBTITLE_CUE);
    }

    private Translator translatorFor(String name, TranslationFormat format) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "fake" -> new FakeTranslator();
            case "deepseek" -> {
                // 命令行参数优先，临时跑一次时不用专门配置环境变量。
                String apiKey = firstNonBlank(deepSeekApiKey, System.getenv("DEEPSEEK_API_KEY"));
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalArgumentException(
                            "--deepseek-api-key or DEEPSEEK_API_KEY is required when --translator deepseek is used"
                    );
                }
                yield new BatchedTranslator(
                        new DeepSeekTranslator(apiKey, deepSeekBaseUrl, deepSeekModel, format),
                        translationBatchSize
                );
            }
            case "openai" -> {
                String apiKey = System.getenv("OPENAI_API_KEY");
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalArgumentException("OPENAI_API_KEY is required when --translator openai is used");
                }
                yield new OpenAiTranslator(apiKey, openAiModel, format);
            }
            default -> throw new IllegalArgumentException("Unsupported translator: " + name);
        };
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
