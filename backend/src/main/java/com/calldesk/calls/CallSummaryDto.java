package com.calldesk.calls;

import java.time.Instant;

public record CallSummaryDto(Long id, String callSid, String fromNumber, String toNumber,
                             Instant startedAt, Instant endedAt, CallOutcome outcome,
                             int turnCount, int bargeInCount) { }
