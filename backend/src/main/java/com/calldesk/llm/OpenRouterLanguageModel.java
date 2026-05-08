package com.calldesk.llm;

import com.calldesk.config.CallDeskProperties;
import com.calldesk.conversation.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class OpenRouterLanguageModel implements LanguageModel {
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final CallDeskProperties properties;

    public OpenRouterLanguageModel(HttpClient client, ObjectMapper objectMapper, CallDeskProperties properties) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override public void stream(List<ChatMessage> messages, TokenListener listener, CancellationToken cancellationToken) {
        if (properties.getOpenrouter().getApiKey().isBlank()) throw new IllegalStateException("OPENROUTER_API_KEY is required for the OpenRouter provider");
        try {
            String baseUrl = properties.getOpenrouter().getBaseUrl().replaceAll("/+$", "");
            String body = objectMapper.writeValueAsString(Map.of("model", properties.getOpenrouter().getModel(), "stream", true, "messages", messages));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + properties.getOpenrouter().getApiKey())
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "https://calldesk.local")
                    .header("X-Title", "CallDesk")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<Stream<String>> response = client.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IllegalStateException("OpenRouter returned HTTP " + response.statusCode());
            }
            try (Stream<String> lines = response.body()) {
                for (String line : (Iterable<String>) lines::iterator) {
                    cancellationToken.throwIfCancelled();
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) break;
                    JsonNode root = objectMapper.readTree(data);
                    JsonNode content = root.path("choices").path(0).path("delta").path("content");
                    if (content.isTextual() && !content.textValue().isEmpty()) listener.onToken(content.textValue());
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cancellationToken.throwIfCancelled();
            throw new IllegalStateException("OpenRouter request interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("OpenRouter streaming request failed", exception);
        }
    }
}
