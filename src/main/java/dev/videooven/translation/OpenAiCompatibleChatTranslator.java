package dev.videooven.translation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public final class OpenAiCompatibleChatTranslator implements Translator {
    private final String providerName;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleChatTranslator(String providerName, URI endpoint, String apiKey, String model) {
        this(providerName, endpoint, apiKey, model, HttpClient.newHttpClient(), new ObjectMapper());
    }

    OpenAiCompatibleChatTranslator(
            String providerName,
            URI endpoint,
            String apiKey,
            String model,
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
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> translate(List<String> texts, String sourceLanguage, String targetLanguage)
            throws IOException, InterruptedException {
        if (texts.isEmpty()) {
            return List.of();
        }

        // 要求模型返回 JSON 数组，方便校验字幕条数和顺序。
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "temperature", 0,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "You translate subtitle cues. Return only a JSON array of strings."
                        ),
                        Map.of(
                                "role", "user",
                                "content", """
                                        Translate each subtitle cue from %s to %s.
                                        Preserve line breaks inside each cue when natural.
                                        Return exactly the same number of JSON array items as the input.

                                        Input JSON:
                                        %s
                                        """.formatted(
                                        sourceLanguage,
                                        targetLanguage,
                                        objectMapper.writeValueAsString(texts)
                                )
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
        return parseJsonArray(content.asText());
    }

    private List<String> parseJsonArray(String json) throws JsonProcessingException {
        // 有些模型会把 JSON 包在 Markdown 代码块里，这里顺手剥掉。
        return objectMapper.readValue(stripMarkdownFence(json), new TypeReference<>() {
        });
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
