package com.calldesk.knowledge;

import java.time.Instant;

public record KnowledgeGapDto(Long id, String question, int timesAsked, Instant firstAskedAt, Instant lastAskedAt,
                              Long lastCallId, Double bestScore, KnowledgeGapStatus status, Long resolvedEntryId) {
    public static KnowledgeGapDto from(KnowledgeGap gap) {
        return new KnowledgeGapDto(gap.getId(), gap.getQuestion(), gap.getTimesAsked(), gap.getFirstAskedAt(),
                gap.getLastAskedAt(), gap.getLastCallId(), gap.getBestScore(), gap.getStatus(), gap.getResolvedEntryId());
    }
}
