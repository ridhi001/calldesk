package com.calldesk.simulator;

import com.calldesk.calls.CallDetailDto;
import com.calldesk.calls.CallOutcome;
import com.calldesk.calls.CallRecord;
import com.calldesk.calls.CallRecordRepository;
import com.calldesk.knowledge.KnowledgeGapRepository;
import com.calldesk.knowledge.KnowledgeGapStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:simulated-call-it;DB_CLOSE_DELAY=-1")
@ActiveProfiles("sim")
class SimulatedCallIT {
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private CallRecordRepository calls;
    @Autowired private KnowledgeGapRepository gaps;

    @Test void runsEveryScenarioThroughTheTwilioWebSocketHandler() throws Exception {
        assertScenario("book-appointment", CallOutcome.COMPLETED, false);
        assertScenario("ask-hours", CallOutcome.COMPLETED, false);
        assertScenario("interrupt-agent", CallOutcome.COMPLETED, true);
        assertScenario("ask-for-human", CallOutcome.HANDED_OFF, false);
        assertScenario("silent-caller", CallOutcome.ABANDONED, false);
        assertScenario("voicemail", CallOutcome.VOICEMAIL, false);
        long unknownCallId = assertScenario("unknown-question", CallOutcome.COMPLETED, false);
        assertThat(gaps.findAllByStatusOrderByTimesAskedDescLastAskedAtDesc(KnowledgeGapStatus.OPEN))
                .filteredOn(gap -> gap.getFirstCallId().equals(unknownCallId) || gap.getLastCallId().equals(unknownCallId))
                .hasSize(2);
    }

    private long assertScenario(String scenario, CallOutcome expected, boolean expectBargeIn) throws Exception {
        ResponseEntity<SimulationAccepted> response = restTemplate.postForEntity("/api/simulate", Map.of("scenario", scenario), SimulationAccepted.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        long id = response.getBody().callId();
        long deadline = System.nanoTime() + Duration.ofSeconds(55).toNanos();
        CallRecord record;
        do {
            record = calls.findById(id).orElseThrow();
            if (record.getEndedAt() != null) break;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);

        assertThat(record.getEndedAt()).as(scenario + " should finish").isNotNull();
        assertThat(record.getOutcome()).isEqualTo(expected);
        if (expectBargeIn) assertThat(record.getBargeInCount()).isGreaterThan(0);
        ResponseEntity<CallDetailDto> details = restTemplate.getForEntity("/api/calls/{id}", CallDetailDto.class, id);
        assertThat(details.getBody().turns()).isNotEmpty();
        return id;
    }
}
