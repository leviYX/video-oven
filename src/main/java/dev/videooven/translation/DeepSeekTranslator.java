package dev.videooven.translation;

import java.net.URI;

public final class DeepSeekTranslator implements Translator {
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";

    private final OpenAiCompatibleChatTranslator delegate;

    public DeepSeekTranslator(String apiKey, String baseUrl, String model) {
        this(apiKey, baseUrl, model, TranslationFormat.SUBTITLE_CUE);
    }

    public DeepSeekTranslator(String apiKey, String baseUrl, String model, TranslationFormat format) {
        String resolvedBaseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
        String resolvedModel = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        this.delegate = new OpenAiCompatibleChatTranslator(
                "DeepSeek",
                chatCompletionsEndpoint(resolvedBaseUrl),
                apiKey,
                resolvedModel,
                format
        );
    }

    @Override
    public java.util.List<String> translate(
            java.util.List<String> texts,
            String sourceLanguage,
            String targetLanguage
    ) throws java.io.IOException, InterruptedException {
        return delegate.translate(texts, sourceLanguage, targetLanguage);
    }

    private static URI chatCompletionsEndpoint(String baseUrl) {
        // 兼容裸 base url 和完整的 chat completions 地址。
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/chat/completions")) {
            return URI.create(normalized);
        }
        return URI.create(normalized + "/chat/completions");
    }
}
