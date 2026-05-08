package com.calldesk.simulator;

import com.calldesk.audio.MuLaw;
import com.calldesk.calls.CallOutcome;
import com.calldesk.calls.CallPersistenceService;
import com.calldesk.stt.MockTranscriptScripts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class SimulatedCaller implements DisposableBean {
    private static final long BARGE_IN_AFTER_AGENT_AUDIO_MS = 300;
    private static final long CALLER_LINGER_MS = 1500;
    private final MockTranscriptScripts scripts;
    private final ObjectMapper objectMapper;
    private final CallPersistenceService persistence;
    private final Clock clock;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService runners = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService playback = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, SimulationRun> runs = new java.util.concurrent.ConcurrentHashMap<>();

    public SimulatedCaller(MockTranscriptScripts scripts, ObjectMapper objectMapper, CallPersistenceService persistence, Clock clock) {
        this.scripts = scripts; this.objectMapper = objectMapper; this.persistence = persistence; this.clock = clock;
    }

    public SimulationRun runForCall(String callSid) { return runs.get(callSid); }

    public void start(URI mediaUri, String callSid, String from, String to, ScenarioDefinition scenario) {
        SimulationRun run = new SimulationRun(callSid);
        runs.put(callSid, run);
        scripts.register(callSid, scenario.utterances());
        runners.submit(() -> simulate(mediaUri, callSid, from, to, scenario, run));
    }

    private void simulate(URI mediaUri, String callSid, String from, String to, ScenarioDefinition scenario, SimulationRun run) {
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override public void onOpen(WebSocket webSocket) { run.setConnected(true); webSocket.request(1); }
            @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                try {
                    JsonNode message = objectMapper.readTree(data.toString());
                    String event = message.path("event").asText("");
                    if (event.equals("media")) {
                        byte[] audio = Base64.getDecoder().decode(message.path("media").path("payload").asText(""));
                        run.recordAudio(audio);
                    } else if (event.equals("mark")) {
                        String name = message.path("mark").path("name").asText("");
                        run.recordMark(name);
                        scheduleMark(run, webSocket, message.path("streamSid").asText(""), name);
                    } else if (event.equals("clear")) {
                        run.clearPlayback();
                        for (String name : run.clearPendingMarks()) echoMark(webSocket, message.path("streamSid").asText(""), name);
                    }
                } catch (Exception ignored) { }
                webSocket.request(1);
                return null;
            }
            @Override public void onError(WebSocket webSocket, Throwable error) { run.setConnected(false); }
            @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) { run.setConnected(false); return null; }
        };

        try {
            WebSocket socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).buildAsync(mediaUri, listener).join();
            String streamSid = "MZ" + callSid.substring(Math.max(0, callSid.length() - 20));
            send(socket, Map.of("event", "connected", "protocol", "CallDeskSim", "version", "1.0.0"));
            send(socket, Map.of("event", "start", "sequenceNumber", "1", "streamSid", streamSid,
                    "start", Map.of("callSid", callSid, "streamSid", streamSid, "accountSid", "AC_SIMULATOR", "tracks", List.of("inbound"),
                            "mediaFormat", Map.of("encoding", "audio/x-mulaw", "sampleRate", 8000, "channels", 1),
                            "customParameters", Map.of("from", from, "to", to))));
            if (scenario.silenceDurationMs() > 0) {
                streamSilence(socket, streamSid, scenario.silenceDurationMs());
            } else {
                awaitPlayback(run, Long.MIN_VALUE, 6000);
                for (int index = 0; index < scenario.utterances().size(); index++) {
                    streamUtterance(socket, streamSid, index, scenario.bargeIn());
                    long utteranceEndedNanos = System.nanoTime();
                    if (index + 1 < scenario.utterances().size()) {
                        if (scenario.bargeIn()) interruptAgentReply(run, utteranceEndedNanos);
                        else awaitPlayback(run, utteranceEndedNanos, 12_000);
                    } else {
                        awaitPlayback(run, utteranceEndedNanos, 12_000);
                    }
                }
            }
            awaitServerHangup(run, CALLER_LINGER_MS);
            if (run.isConnected()) send(socket, Map.of("event", "stop", "sequenceNumber", "999", "streamSid", streamSid, "stop", Map.of("callSid", callSid)));
            if (run.isConnected()) socket.sendClose(WebSocket.NORMAL_CLOSURE, "simulation complete");
        } catch (Exception ignored) {
            run.setConnected(false);
            persistence.endCall(callSid, CallOutcome.FAILED, clock.instant());
        }
    }

    private void streamUtterance(WebSocket socket, String streamSid, int utterance, boolean bargeIn) throws Exception {
        byte[] speech = toneFrame(440 + (utterance % 3) * 80);
        byte[] silence = new byte[160];
        Arrays.fill(silence, (byte) 0xff);
        for (int index = 0; index < 30; index++) { sendAudio(socket, streamSid, speech); Thread.sleep(20); }
        for (int index = 0; index < 26; index++) { sendAudio(socket, streamSid, silence); Thread.sleep(20); }
    }

    private void streamSilence(WebSocket socket, String streamSid, long durationMs) throws Exception {
        byte[] silence = new byte[160];
        Arrays.fill(silence, (byte) 0xff);
        long frames = durationMs / 20;
        for (long index = 0; index < frames; index++) { sendAudio(socket, streamSid, silence); Thread.sleep(20); }
        Thread.sleep(1000);
    }

    // Barge-in: wait until the agent's reply audio actually starts, then cut in part-way through it.
    // Waiting for real audio (instead of a fixed delay) keeps the scenario valid whatever the provider latency.
    private void interruptAgentReply(SimulationRun run, long utteranceEndedNanos) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (run.isConnected() && run.lastMediaNanos() <= utteranceEndedNanos && System.nanoTime() < deadline) Thread.sleep(10);
        Thread.sleep(BARGE_IN_AFTER_AGENT_AUDIO_MS);
    }

    // Waits until the agent's reply to what the caller just said has finished playing. Only marks received after
    // `sinceNanos` count, so leftover marks from an earlier (for example interrupted) reply cannot end the wait early.
    // A real caller does not hang up the instant the agent stops talking. Give the server a moment to end the call
    // itself (transfer, voicemail or goodbye) before the simulated caller hangs up.
    private void awaitServerHangup(SimulationRun run, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (run.isConnected() && System.nanoTime() < deadline) Thread.sleep(20);
    }

    private void awaitPlayback(SimulationRun run, long sinceNanos, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (!run.isConnected()) return;
            long ready = run.playbackReadyNanos();
            long quietSince = run.lastMediaNanos();
            long now = System.nanoTime();
            if (run.lastMarkNanos() > sinceNanos && ready > 0 && now >= ready && now - quietSince >= TimeUnit.MILLISECONDS.toNanos(500)) return;
            Thread.sleep(100);
        }
    }

    private void sendAudio(WebSocket socket, String streamSid, byte[] payload) throws Exception {
        send(socket, Map.of("event", "media", "streamSid", streamSid, "media", Map.of("track", "inbound", "payload", Base64.getEncoder().encodeToString(payload))));
    }

    private void scheduleMark(SimulationRun run, WebSocket socket, String streamSid, String name) {
        long delayNanos = Math.max(0, run.playbackReadyNanos() - System.nanoTime());
        playback.schedule(() -> {
            if (run.consumeMark(name)) echoMark(socket, streamSid, name);
        }, delayNanos, TimeUnit.NANOSECONDS);
    }

    private void echoMark(WebSocket socket, String streamSid, String name) {
        try { socket.sendText(objectMapper.writeValueAsString(Map.of("event", "mark", "streamSid", streamSid, "mark", Map.of("name", name))), true); }
        catch (Exception ignored) { }
    }

    private void send(WebSocket socket, Object message) {
        try { socket.sendText(objectMapper.writeValueAsString(message), true).join(); }
        catch (Exception ignored) { }
    }

    private static byte[] toneFrame(int frequency) {
        short[] samples = new short[160];
        for (int index = 0; index < samples.length; index++) samples[index] = (short) (Math.sin(2 * Math.PI * frequency * index / 8000.0) * 10000);
        return MuLaw.encode(samples);
    }

    @Override public void destroy() { runners.shutdownNow(); playback.shutdownNow(); }
}
