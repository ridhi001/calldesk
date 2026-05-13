package com.calldesk.simulator;

import com.calldesk.calls.CallDetailDto;
import com.calldesk.calls.TurnDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("sim")
@Tag("benchmark")
class LatencyBenchmark {
    @Autowired private TestRestTemplate restTemplate;

    @Test void reportsMockProviderPipelineOverhead() throws Exception {
        int count = Integer.getInteger("benchmark.calls", 10);
        List<Long> stt = new ArrayList<>();
        List<Long> llm = new ArrayList<>();
        List<Long> tts = new ArrayList<>();
        List<Long> total = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            SimulationAccepted accepted = restTemplate.postForObject("/api/simulate", Map.of("scenario", "ask-hours"), SimulationAccepted.class);
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            CallDetailDto detail = null;
            while (System.nanoTime() < deadline) {
                ResponseEntity<CallDetailDto> response = restTemplate.getForEntity("/api/calls/{id}", CallDetailDto.class, accepted.callId());
                detail = response.getBody();
                if (detail != null && detail.call().endedAt() != null) break;
                Thread.sleep(100);
            }
            if (detail == null) continue;
            for (TurnDto turn : detail.turns()) {
                add(stt, turn.sttMs()); add(llm, turn.llmFirstTokenMs()); add(tts, turn.ttsFirstAudioMs()); add(total, turn.turnLatencyMs());
            }
        }
        System.out.println("MOCK-PROVIDER PIPELINE OVERHEAD (not real-world latency)");
        print("sttMs", stt); print("llmFirstTokenMs", llm); print("ttsFirstAudioMs", tts); print("turnLatencyMs", total);
    }

    private static void add(List<Long> values, Long value) { if (value != null) values.add(value); }
    private static void print(String name, List<Long> values) {
        if (values.isEmpty()) { System.out.println(name + ": no samples"); return; }
        List<Long> sorted = new ArrayList<>(values); Collections.sort(sorted);
        double average = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
        System.out.println(name + " p50=" + percentile(sorted, 0.50) + "ms p95=" + percentile(sorted, 0.95) + "ms avg=" + average + "ms");
    }
    private static long percentile(List<Long> sorted, double fraction) { return sorted.get(Math.max(0, (int) Math.ceil(fraction * sorted.size()) - 1)); }
}
