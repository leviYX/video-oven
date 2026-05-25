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

public final class OpenAiTranslator implements Translator {
    private static final URI RESPONSES_API = URI.create("https://api.openai.com/v1/responses");

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiTranslator(String apiKey, String model) {
        this(apiKey, model, HttpClient.newHttpClient(), new ObjectMapper());
    }

    OpenAiTranslator(String apiKey, String model, HttpClient httpClient, ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
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

        String prompt = """
                Translate each subtitle cue from %s to %s.
                Preserve line breaks inside each cue when natural.
                Return only a JSON array of strings with exactly the same number of items as the input.

                Input JSON:
                %s
                """.formatted(sourceLanguage, targetLanguage, objectMapper.writeValueAsString(texts));

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "input", prompt
        ));

        HttpRequest request = HttpRequest.newBuilder(RESPONSES_API)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("OpenAI API request failed with HTTP " + response.statusCode() + ": " + response.body());
        }

        List<String> translations = parseTranslations(response.body());
        if (translations.size() != texts.size()) {
            throw new IOException("OpenAI returned " + translations.size() + " translations for " + texts.size() + " inputs");
        }
        return translations;
    }

    private List<String> parseTranslations(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode outputText = root.get("output_text");
        if (outputText != null && outputText.isTextual()) {
            return parseJsonArray(outputText.asText());
        }

        StringBuilder text = new StringBuilder();
        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.get("content");
                if (content == null || !content.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : content) {
                    JsonNode textNode = contentItem.get("text");
                    if (textNode != null && textNode.isTextual()) {
                        text.append(textNode.asText());
                    }
                }
            }
        }
        if (text.isEmpty()) {
            throw new IOException("OpenAI response did not contain output text");
        }
        return parseJsonArray(text.toString());
    }

    private List<String> parseJsonArray(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }
}
