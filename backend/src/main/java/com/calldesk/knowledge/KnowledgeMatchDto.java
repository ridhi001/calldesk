package com.calldesk.knowledge;

public record KnowledgeMatchDto(KnowledgeEntryDto entry, double score, boolean confident) { }
