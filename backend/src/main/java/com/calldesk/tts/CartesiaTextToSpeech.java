package com.calldesk.tts;

import com.calldesk.config.CallDeskProperties;
import com.calldesk.conversation.CancellationToken;
import com.calldesk.telephony.CartesiaApiDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class CartesiaTextToSpeech implements TextToSpeech {
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final CallDeskProperties properties;

    public CartesiaTextToSpeech(HttpClient client, ObjectMapper objectMapper, CallDeskProperties properties) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override public void stream(String text, AudioListener listener, CancellationToken cancellationToken) {
        if (properties.getCartesia().getApiKey().isBlank() || properties.getCartesia().getVoiceId().isBlank())
            throw new IllegalStateException("CARTESIA_API_KEY and CARTESIA_VOICE_ID are required for Cartesia TTS");
        CompletableFuture<Void> completed = new CompletableFuture<>();
        try {
            WebSocket socket = client.newWebSocketBuilder()
                    .header(CartesiaApiDetails.API_KEY_HEADER, properties.getCartesia().getApiKey())
                    .header(CartesiaApiDetails.VERSION_HEADER, properties.getCartesia().getVersion())
                    .buildAsync(URI.create(CartesiaApiDetails.TTS_WEBSOCKET_URL), new WebSocket.Listener() {
                        @Override public void onOpen(WebSocket webSocket) {
                            try {
                                String payload = objectMapper.writeValueAsString(Map.of(
                                        CartesiaApiDetails.TTS_MODEL_FIELD, properties.getCartesia().getTtsModel(),
                                        CartesiaApiDetails.TTS_TRANSCRIPT_FIELD, text,
                                        CartesiaApiDetails.TTS_VOICE_FIELD, Map.of(CartesiaApiDetails.TTS_VOICE_MODE_FIELD, "id", CartesiaApiDetails.TTS_VOICE_ID_FIELD, properties.getCartesia().getVoiceId()),
                                        CartesiaApiDetails.TTS_OUTPUT_FORMAT_FIELD, Map.of(CartesiaApiDetails.TTS_CONTAINER_FIELD, CartesiaApiDetails.RAW_CONTAINER,
                                                CartesiaApiDetails.TTS_ENCODING_FIELD, CartesiaApiDetails.MULAW_ENCODING,
                                                CartesiaApiDetails.TTS_SAMPLE_RATE_FIELD, CartesiaApiDetails.SAMPLE_RATE),
                                        CartesiaApiDetails.TTS_CONTINUE_FIELD, false));
                                webSocket.sendText(payload, true);
                                webSocket.request(1);
                            } catch (Exception exception) { completed.completeExceptionally(exception); }
                        }
                        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            try {
                                JsonNode message = objectMapper.readTree(data.toString());
                                String audio = message.path(CartesiaApiDetails.TTS_AUDIO_FIELD).asText("");
                                if (!audio.isEmpty()) listener.onAudio(Base64.getDecoder().decode(audio));
                                if (message.path(CartesiaApiDetails.TTS_DONE_FIELD).asBoolean(false)) completed.complete(null);
                            } catch (Exception exception) { completed.completeExceptionally(exception); }
                            webSocket.request(1);
                            return null;
                        }
                        @Override public void onError(WebSocket webSocket, Throwable error) { completed.completeExceptionally(error); }
                        @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) { completed.complete(null); return null; }
                    }).join();
            while (true) {
                cancellationToken.throwIfCancelled();
                try {
                    completed.get(50, TimeUnit.MILLISECONDS);
                    break;
                } catch (java.util.concurrent.TimeoutException ignored) { }
            }
            completed.join();
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "synthesis complete");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cancellationToken.throwIfCancelled();
            throw new IllegalStateException("Cartesia TTS interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Cartesia TTS streaming failed", exception);
        }
    }
}
