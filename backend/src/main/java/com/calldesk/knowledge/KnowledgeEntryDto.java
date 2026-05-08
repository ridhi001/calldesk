package com.calldesk.knowledge;

import java.time.Instant;
import java.util.List;

public record KnowledgeEntryDto(Long id, String question, String answer, List<String> tags,
                                Instant createdAt, Instant updatedAt, int timesUsed, Instant lastUsedAt) {
    public KnowledgeEntryDto { tags = List.copyOf(tags); }
    public static KnowledgeEntryDto from(KnowledgeEntry entry) {
        return new KnowledgeEntryDto(entry.getId(), entry.getQuestion(), entry.getAnswer(), entry.getTags(),
                entry.getCreatedAt(), entry.getUpdatedAt(), entry.getTimesUsed(), entry.getLastUsedAt());
    }
}
