package com.calldesk.telephony;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

public class SerializedTwilioMediaSender implements TwilioMediaSender {
    private final WebSocketSession session;
    private final ObjectMapper objectMapper;

    public SerializedTwilioMediaSender(WebSocketSession session, ObjectMapper objectMapper) { this.session = session; this.objectMapper = objectMapper; }

    @Override public synchronized void sendMedia(byte[] mulaw) { send(Map.of("event", "media", "streamSid", streamSid(), "media", Map.of("payload", Base64.getEncoder().encodeToString(mulaw)))); }
    @Override public synchronized void sendMark(String name) { send(Map.of("event", "mark", "streamSid", streamSid(), "mark", Map.of("name", name))); }
    @Override public synchronized void clear() { send(Map.of("event", "clear", "streamSid", streamSid())); }
    @Override public synchronized void close() { try { if (session.isOpen()) session.close(); } catch (IOException ignored) { } }

    private String streamSid() { return (String) session.getAttributes().getOrDefault("streamSid", ""); }
    private void send(Object payload) {
        if (!session.isOpen()) return;
        try { session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload))); }
        catch (IOException exception) { throw new IllegalStateException("Could not send Twilio media message", exception); }
    }
}
