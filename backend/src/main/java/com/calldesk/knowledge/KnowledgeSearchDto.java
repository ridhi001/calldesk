package com.calldesk.knowledge;

import java.util.List;

public record KnowledgeSearchDto(String query, boolean confident, List<KnowledgeMatchDto> matches) {
    public KnowledgeSearchDto { matches = List.copyOf(matches); }
}
