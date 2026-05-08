package com.calldesk.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KnowledgeEntryRequest(
        @NotBlank(message = "question must not be blank") @Size(max = 300, message = "question must be at most 300 characters") String question,
        @NotBlank(message = "answer must not be blank") @Size(max = 2000, message = "answer must be at most 2000 characters") String answer,
        List<String> tags) {
    public KnowledgeEntryRequest { tags = tags == null ? List.of() : List.copyOf(tags); }
}
