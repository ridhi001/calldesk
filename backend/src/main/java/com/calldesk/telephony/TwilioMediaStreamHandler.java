package com.calldesk.telephony;

import com.calldesk.conversation.CallSession;
import com.calldesk.conversation.CallSessionFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TwilioMediaStreamHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final CallSessionFactory sessionFactory;
    private final Map<String, CallSession> sessions = new ConcurrentHashMap<>();

    public TwilioMediaStreamHandler(ObjectMapper objectMapper, CallSessionFactory sessionFactory) {
        this.objectMapper = objectMapper; this.sessionFactory = sessionFactory;
    }

    @Override protected void handleTextMessage(WebSocketSession webSocket, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String event = root.path("event").asText("");
        switch (event) {
            case "connected" -> { }
            case "start" -> onStart(webSocket, root.path("start"));
            case "media" -> onMedia(webSocket, root.path("media"));
            case "mark" -> onMark(webSocket, root.path("mark"));
            case "stop" -> stop(webSocket, true);
            default -> { }
        }
    }

    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { stop(session, false); }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) { stop(session, false); }

    private void onStart(WebSocketSession socket, JsonNode start) {
        String callSid = start.path("callSid").asText("");
        String streamSid = start.path("streamSid").asText("");
        if (streamSid.isBlank()) streamSid = (String) socket.getAttributes().getOrDefault("streamSid", "");
        socket.getAttributes().put("streamSid", streamSid);
        JsonNode parameters = start.path("customParameters");
        TwilioMediaSender sender = new SerializedTwilioMediaSender(socket, objectMapper);
        CallSession session = sessionFactory.start(callSid, parameters.path("from").asText(""), parameters.path("to").asText(""), sender);
        sessions.put(socket.getId(), session);
    }

    private void onMedia(WebSocketSession socket, JsonNode media) {
        CallSession session = sessions.get(socket.getId());
        if (session == null) return;
        String payload = media.path("payload").asText("");
        if (!payload.isEmpty()) session.onAudio(Base64.getDecoder().decode(payload));
    }

    private void onMark(WebSocketSession socket, JsonNode mark) {
        CallSession session = sessions.get(socket.getId());
        if (session != null) session.onMark(mark.path("name").asText(""));
    }

    private void stop(WebSocketSession socket, boolean normalStop) {
        CallSession session = sessions.remove(socket.getId());
        if (session == null) return;
        if (normalStop) session.onStreamStopped(); else session.close();
    }
}
