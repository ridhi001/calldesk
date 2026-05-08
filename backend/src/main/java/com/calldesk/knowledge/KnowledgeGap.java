package com.calldesk.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "knowledge_gaps")
public class KnowledgeGap {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 8000)
    private String question;
    @Column(nullable = false, length = 8000)
    private String normalizedQuestion;
    @Column(nullable = false)
    private Long firstCallId;
    @Column(nullable = false)
    private Long lastCallId;
    @Column(nullable = false)
    private Instant firstAskedAt;
    @Column(nullable = false)
    private Instant lastAskedAt;
    @Column(nullable = false)
    private int timesAsked;
    private Double bestScore;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KnowledgeGapStatus status;
    private Long resolvedEntryId;

    protected KnowledgeGap() { }

    public KnowledgeGap(String question, String normalizedQuestion, Long callId, Instant askedAt, Double bestScore) {
        this.question = question;
        this.normalizedQuestion = normalizedQuestion;
        this.firstCallId = callId;
        this.lastCallId = callId;
        this.firstAskedAt = askedAt;
        this.lastAskedAt = askedAt;
        this.timesAsked = 1;
        this.bestScore = bestScore;
        this.status = KnowledgeGapStatus.OPEN;
    }

    public void recordAsk(String question, Long callId, Instant askedAt, Double score) {
        this.lastCallId = callId;
        this.lastAskedAt = askedAt;
        this.timesAsked++;
        if (score != null && (bestScore == null || score > bestScore)) bestScore = score;
    }
    public void resolve(Long entryId) { status = KnowledgeGapStatus.RESOLVED; resolvedEntryId = entryId; }
    public void dismiss() { status = KnowledgeGapStatus.DISMISSED; }
    public Long getId() { return id; }
    public String getQuestion() { return question; }
    public String getNormalizedQuestion() { return normalizedQuestion; }
    public Long getFirstCallId() { return firstCallId; }
    public Long getLastCallId() { return lastCallId; }
    public Instant getFirstAskedAt() { return firstAskedAt; }
    public Instant getLastAskedAt() { return lastAskedAt; }
    public int getTimesAsked() { return timesAsked; }
    public Double getBestScore() { return bestScore; }
    public KnowledgeGapStatus getStatus() { return status; }
    public Long getResolvedEntryId() { return resolvedEntryId; }
}
