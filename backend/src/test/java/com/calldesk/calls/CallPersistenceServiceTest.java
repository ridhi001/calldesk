package com.calldesk.calls;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CallPersistenceService.class, CallEventPublisher.class})
class CallPersistenceServiceTest {

    @Autowired CallPersistenceService calls;

    @Test void filtersCallsByOutcomeNewestFirst() {
        Instant start = Instant.parse("2026-09-26T09:00:00Z");
        endedCall("CA1", start, CallOutcome.COMPLETED);
        endedCall("CA2", start.plusSeconds(60), CallOutcome.HANDED_OFF);
        endedCall("CA3", start.plusSeconds(120), CallOutcome.HANDED_OFF);

        PageDto<CallSummaryDto> handedOff = calls.listCalls(0, 20, CallOutcome.HANDED_OFF);
        PageDto<CallSummaryDto> all = calls.listCalls(0, 20, null);

        assertThat(handedOff.content()).extracting(CallSummaryDto::callSid).containsExactly("CA3", "CA2");
        assertThat(handedOff.totalElements()).isEqualTo(2);
        assertThat(all.totalElements()).isEqualTo(3);
    }

    private void endedCall(String sid, Instant startedAt, CallOutcome outcome) {
        calls.startCall(sid, "+100", "+200", startedAt);
        calls.endCall(sid, outcome, startedAt.plusSeconds(30));
    }
}
