package com.calldesk.stt;

import com.calldesk.config.CallDeskProperties;
import org.springframework.beans.factory.DisposableBean;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MockSpeechToText implements SpeechToText, DisposableBean {
    private final MockTranscriptScripts scripts;
    private final long delayMs;
    private final ScheduledExecutorService callbacks = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

    public MockSpeechToText(MockTranscriptScripts scripts, CallDeskProperties properties) {
        this.scripts = scripts;
        this.delayMs = properties.getMock().getSttDelayMs();
    }

    @Override public void destroy() { callbacks.shutdownNow(); }

    @Override
    public SttStream openStream(String callSid, Listener listener) {
        return new SttStream() {
            private final AtomicBoolean closed = new AtomicBoolean();
            private String current = "";
            private boolean partialSent;

            @Override public synchronized void sendAudio(byte[] mulaw) {
                if (closed.get() || partialSent) return;
                current = scripts.next(callSid);
                if (!current.isBlank()) {
                    partialSent = true;
                    listener.onPartial(current);
                }
            }

            @Override public synchronized void endUtterance() {
                if (closed.get()) return;
                if (current.isBlank()) current = scripts.next(callSid);
                String finalText = current;
                current = "";
                partialSent = false;
                callbacks.schedule(() -> {
                    if (!closed.get() && !finalText.isBlank()) listener.onFinal(finalText);
                }, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
            }

            @Override public void close() { closed.set(true); }
        };
    }
}
