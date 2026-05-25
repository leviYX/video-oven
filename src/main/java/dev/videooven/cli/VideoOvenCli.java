package dev.videooven.cli;

import dev.videooven.processor.OutputMode;
import dev.videooven.processor.SubtitleProcessor;
import dev.videooven.platform.SubtitleDownloader;
import dev.videooven.platform.YtDlpSubtitleDownloader;
import dev.videooven.subtitle.SrtParser;
import dev.videooven.subtitle.SubtitleParser;
import dev.videooven.subtitle.VttParser;
import dev.videooven.translation.FakeTranslator;
import dev.videooven.translation.DeepSeekTranslator;
import dev.videooven.translation.OpenAiTranslator;
import dev.videooven.translation.Translator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;

@Command(
        name = "video-oven",
        mixinStandardHelpOptions = true,
        version = "video-oven 0.1.0",
        description = "Translate English SRT/VTT subtitles into Chinese or bilingual SRT subtitles."
)
public final class VideoOvenCli implements Callable<Integer> {
    private final SubtitleDownloader subtitleDownloader;

    @Option(names = "--input", description = "Input .srt or .vtt subtitle file.")
    private Path input;

    @Option(names = "--url", description = "Online video URL. Requires yt-dlp on PATH.")
    private String url;

    @Option(names = "--output", required = true, description = "Output .srt subtitle file.")
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

    public VideoOvenCli() {
        this(new YtDlpSubtitleDownloader());
    }

    public VideoOvenCli(SubtitleDownloader subtitleDownloader) {
        this.subtitleDownloader = subtitleDownloader;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new VideoOvenCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
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
            return input;
        }

        return subtitleDownloader.download(url, sourceLanguage);
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

    private Translator translatorFor(String name) {
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
                yield new DeepSeekTranslator(apiKey, deepSeekBaseUrl, deepSeekModel);
            }
            case "openai" -> {
                String apiKey = System.getenv("OPENAI_API_KEY");
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalArgumentException("OPENAI_API_KEY is required when --translator openai is used");
                }
                yield new OpenAiTranslator(apiKey, openAiModel);
            }
            default -> throw new IllegalArgumentException("Unsupported translator: " + name);
        };
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
