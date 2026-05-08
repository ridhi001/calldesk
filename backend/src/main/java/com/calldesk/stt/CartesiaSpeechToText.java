package com.calldesk.stt;

import com.calldesk.config.CallDeskProperties;
import com.calldesk.telephony.CartesiaApiDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public class CartesiaSpeechToText implements SpeechToText {
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final CallDeskProperties properties;

    public CartesiaSpeechToText(HttpClient client, ObjectMapper objectMapper, CallDeskProperties properties) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override public SttStream openStream(String callSid, Listener listener) {
        if (properties.getCartesia().getApiKey().isBlank()) throw new IllegalStateException("CARTESIA_API_KEY is required for Cartesia STT");
        String uri = CartesiaApiDetails.STT_WEBSOCKET_URL + "?" + CartesiaApiDetails.MODEL_FIELD + "=" + properties.getCartesia().getSttModel()
                + "&" + CartesiaApiDetails.STT_ENCODING_FIELD + "=" + CartesiaApiDetails.MULAW_ENCODING
                + "&" + CartesiaApiDetails.STT_SAMPLE_RATE_FIELD + "=" + CartesiaApiDetails.SAMPLE_RATE;
        AtomicBoolean closed = new AtomicBoolean();
        WebSocket socket = client.newWebSocketBuilder()
                .header(CartesiaApiDetails.API_KEY_HEADER, properties.getCartesia().getApiKey())
                .header(CartesiaApiDetails.VERSION_HEADER, properties.getCartesia().getVersion())
                .buildAsync(URI.create(uri), new WebSocket.Listener() {
                    @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
                    @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        try {
                            JsonNode message = objectMapper.readTree(data.toString());
                            String transcript = message.path(CartesiaApiDetails.TRANSCRIPT_FIELD).asText("");
                            boolean isFinal = message.path(CartesiaApiDetails.IS_FINAL_FIELD).asBoolean(false)
                                    || message.path(CartesiaApiDetails.TYPE_FIELD).asText("").equals("final");
                            if (!transcript.isBlank()) {
                                if (isFinal) listener.onFinal(transcript); else listener.onPartial(transcript);
                            }
                        } catch (Exception exception) { listener.onError(exception); }
                        webSocket.request(1);
                        return null;
                    }
                    @Override public void onError(WebSocket webSocket, Throwable error) { listener.onError(error); }
                    @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) { closed.set(true); return null; }
                }).join();
        return new SttStream() {
            @Override public void sendAudio(byte[] mulaw) {
                if (!closed.get()) {
                    try {
                        String payload = objectMapper.writeValueAsString(java.util.Map.of(CartesiaApiDetails.STT_AUDIO_FIELD, Base64.getEncoder().encodeToString(mulaw)));
                        socket.sendText(payload, true);
                    } catch (Exception exception) { listener.onError(exception); }
                }
            }
            @Override public void endUtterance() {
                if (!closed.get()) socket.sendText("{\"" + CartesiaApiDetails.STT_FINALIZE_FIELD + "\":true}", true);
            }
            @Override public void close() { if (closed.compareAndSet(false, true)) socket.sendClose(WebSocket.NORMAL_CLOSURE, "call ended"); }
        };
    }
}
