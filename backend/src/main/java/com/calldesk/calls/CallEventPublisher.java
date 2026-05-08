package com.calldesk.calls;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class CallEventPublisher {
    private static final long HEARTBEAT_SECONDS = 25;

    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public CallEventPublisher() {
        // Idle connections get a comment line now and then so proxies and load balancers don't drop them.
        heartbeat.scheduleAtFixedRate(() -> sendToAll(SseEmitter.event().comment("heartbeat")),
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        // Spring only commits the response on the first write, so without this the browser's EventSource would not
        // see the connection open until the next call event.
        send(emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    public void publish(String name, Object payload) {
        sendToAll(SseEmitter.event().name(name).data(payload));
    }

    private void sendToAll(SseEmitter.SseEventBuilder event) {
        for (SseEmitter emitter : emitters) send(emitter, event);
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try { emitter.send(event); }
        catch (IOException | IllegalStateException exception) { emitters.remove(emitter); }
    }

    @PreDestroy
    void shutdown() { heartbeat.shutdownNow(); }
}
