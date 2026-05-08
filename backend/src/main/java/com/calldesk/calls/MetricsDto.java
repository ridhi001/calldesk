package com.calldesk.calls;

import java.util.Map;

public record MetricsDto(long totalCalls, Map<CallOutcome, Long> outcomeBreakdown, double handoffRate,
                         long bargeInCount, LatencyStats turnLatencyMs, KnowledgeStats knowledge,
                         java.util.List<LatencyTrendPoint> latencyTrend) {
    public MetricsDto { outcomeBreakdown = Map.copyOf(outcomeBreakdown); }
    public record LatencyStats(Double p50, Double p95, Double average) { }
    public record KnowledgeStats(long entries, long openGaps, Double coverageRate) { }
    public record LatencyTrendPoint(long callId, java.time.Instant startedAt, Double p50TurnLatencyMs) { }
}
