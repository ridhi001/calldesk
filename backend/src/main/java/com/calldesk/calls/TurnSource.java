package com.calldesk.calls;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class TurnSource {
    @Column(name = "entry_id", nullable = false)
    private Long entryId;
    @Column(name = "source_question", nullable = false, length = 300)
    private String question;
    @Column(name = "source_score", nullable = false)
    private Double score;

    protected TurnSource() { }
    public TurnSource(Long entryId, String question, Double score) {
        this.entryId = entryId;
        this.question = question;
        this.score = score;
    }
    public Long getEntryId() { return entryId; }
    public String getQuestion() { return question; }
    public Double getScore() { return score; }
}
