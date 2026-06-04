package dev.videooven.article;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class HttpArticleFetcher implements ArticleFetcher {
    private final HttpClient httpClient;

    public HttpArticleFetcher() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build());
    }

    HttpArticleFetcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String fetch(URI url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "video-oven/0.1.0")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Article request failed with HTTP " + response.statusCode() + ": " + url);
        }
        return response.body();
    }
}
