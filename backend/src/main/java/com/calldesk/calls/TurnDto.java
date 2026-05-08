package com.calldesk.calls;

import java.time.Instant;
import java.util.List;

public record TurnDto(int index, TurnRole role, String text, Instant startedAt, Long sttMs,
                      Long llmFirstTokenMs, Long ttsFirstAudioMs, Long turnLatencyMs, boolean interrupted,
                      List<TurnSource> sources) {
    public TurnDto { sources = List.copyOf(sources); }
}
