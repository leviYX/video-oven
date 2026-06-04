package dev.videooven.translation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class OpenAiCompatibleChatTranslator implements Translator {
    private final String providerName;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final TranslationFormat format;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleChatTranslator(String providerName, URI endpoint, String apiKey, String model) {
        this(providerName, endpoint, apiKey, model, TranslationFormat.SUBTITLE_CUE);
    }

    public OpenAiCompatibleChatTranslator(
            String providerName,
            URI endpoint,
            String apiKey,
            String model,
            TranslationFormat format
    ) {
        this(providerName, endpoint, apiKey, model, format, HttpClient.newHttpClient(), new ObjectMapper());
    }

    OpenAiCompatibleChatTranslator(
            String providerName,
            URI endpoint,
            String apiKey,
            String model,
            TranslationFormat format,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
        this.providerName = providerName;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.format = format;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> translate(List<String> texts, String sourceLanguage, String targetLanguage)
            throws IOException, InterruptedException {
        if (texts.isEmpty()) {
            return List.of();
        }

        // 带编号请求和响应，降低模型合并短字幕后导致条数错位的概率。
        List<Map<String, Object>> inputItems = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            inputItems.add(Map.of("id", i, "text", texts.get(i)));
        }
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "temperature", 0,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", systemPrompt()
                        ),
                        Map.of(
                                "role", "user",
                                "content", userPrompt(sourceLanguage, targetLanguage, inputItems)
                        )
                )
        ));

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(providerName + " API request failed with HTTP "
                    + response.statusCode() + ": " + response.body());
        }

        List<String> translations = parseTranslations(response.body());
        if (translations.size() != texts.size()) {
            throw new IOException(providerName + " returned " + translations.size()
                    + " translations for " + texts.size() + " inputs");
        }
        return translations;
    }

    private List<String> parseTranslations(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new IOException(providerName + " response did not contain choices");
        }
        JsonNode content = choices.get(0).path("message").path("content");
        if (!content.isTextual()) {
            throw new IOException(providerName + " response did not contain message content");
        }
        return parseTranslationItems(content.asText());
    }

    private String systemPrompt() {
        return switch (format) {
            case SUBTITLE_CUE -> """
                    You translate subtitle cues.
                    Return only a JSON array of objects.
                    Each output object must have the same numeric id and one translated text string.
                    Do not merge, omit, add, or renumber items.
                    """;
            case MARKDOWN_BLOCK -> """
                    You translate Markdown article blocks.
                    Return only a JSON array of objects.
                    Each output object must have the same numeric id and one translated text string.
                    Do not merge, omit, add, or renumber items.
                    Preserve Markdown syntax, heading markers, list markers, links, URLs, inline code, and code fences.
                    Translate only natural-language prose.
                    """;
        };
    }

    private String userPrompt(
            String sourceLanguage,
            String targetLanguage,
            List<Map<String, Object>> inputItems
    ) throws JsonProcessingException {
        String inputJson = objectMapper.writeValueAsString(inputItems);
        return switch (format) {
            case SUBTITLE_CUE -> """
                    Translate each subtitle cue from %s to %s.
                    Preserve line breaks inside each cue when natural.
                    Return exactly the same ids as the input, in the same order.
                    Output format:
                    [{"id":0,"text":"translated text"}]

                    Input JSON:
                    %s
                    """.formatted(sourceLanguage, targetLanguage, inputJson);
            case MARKDOWN_BLOCK -> """
                    Translate each Markdown article block from %s to %s.
                    Keep Markdown structure unchanged.
                    Do not translate URLs, file paths, command names, code spans, or fenced code blocks.
                    Return exactly the same ids as the input, in the same order.
                    Output format:
                    [{"id":0,"text":"translated markdown block"}]

                    Input JSON:
                    %s
                    """.formatted(sourceLanguage, targetLanguage, inputJson);
        };
    }

    private List<String> parseTranslationItems(String json) throws IOException {
        // 有些模型会把 JSON 包在 Markdown 代码块里，这里顺手剥掉。
        JsonNode items = objectMapper.readTree(stripMarkdownFence(json));
        if (!items.isArray()) {
            throw new IOException("translation response is not a JSON array");
        }

        List<String> translations = new ArrayList<>(items.size());
        for (int expectedId = 0; expectedId < items.size(); expectedId++) {
            JsonNode item = items.get(expectedId);
            if (item.isTextual()) {
                translations.add(item.asText());
                continue;
            }
            JsonNode id = item.get("id");
            JsonNode text = item.get("text");
            if (id == null || !id.canConvertToInt() || id.asInt() != expectedId || text == null || !text.isTextual()) {
                throw new IOException("translation response item " + expectedId + " did not match expected id/text");
            }
            translations.add(text.asText());
        }
        return translations;
    }

    private static String stripMarkdownFence(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline < 0 || lastFence <= firstNewline) {
            return trimmed;
        }
        return trimmed.substring(firstNewline + 1, lastFence).trim();
    }
}
