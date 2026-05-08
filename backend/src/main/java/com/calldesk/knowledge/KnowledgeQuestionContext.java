package com.calldesk.knowledge;

import com.calldesk.calls.TurnSource;

import java.util.List;

public record KnowledgeQuestionContext(List<TurnSource> sources) {
    public KnowledgeQuestionContext { sources = List.copyOf(sources); }
}
