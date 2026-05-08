package com.calldesk.calls;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "call_turns")
public class TurnRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "call_id", nullable = false)
    private CallRecord call;
    @Column(name = "turn_index", nullable = false)
    private int turnIndex;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TurnRole role;
    @Column(name = "turn_text", length = 8000)
    private String text;
    @Column(nullable = false)
    private Instant startedAt;
    private Long sttMs;
    private Long llmFirstTokenMs;
    private Long ttsFirstAudioMs;
    private Long turnLatencyMs;
    @Column(nullable = false)
    private boolean interrupted;
    @ElementCollection
    @CollectionTable(name = "turn_sources", joinColumns = @JoinColumn(name = "turn_id"))
    @OrderColumn(name = "source_order")
    private java.util.List<TurnSource> sources = new java.util.ArrayList<>();

    protected TurnRecord() { }

    public TurnRecord(int turnIndex, TurnRole role, String text, Instant startedAt, Long sttMs,
                      Long llmFirstTokenMs, Long ttsFirstAudioMs, Long turnLatencyMs, boolean interrupted) {
        this(turnIndex, role, text, startedAt, sttMs, llmFirstTokenMs, ttsFirstAudioMs, turnLatencyMs, interrupted, java.util.List.of());
    }

    public TurnRecord(int turnIndex, TurnRole role, String text, Instant startedAt, Long sttMs,
                      Long llmFirstTokenMs, Long ttsFirstAudioMs, Long turnLatencyMs, boolean interrupted,
                      java.util.List<TurnSource> sources) {
        this.turnIndex = turnIndex;
        this.role = role;
        this.text = text;
        this.startedAt = startedAt;
        this.sttMs = sttMs;
        this.llmFirstTokenMs = llmFirstTokenMs;
        this.ttsFirstAudioMs = ttsFirstAudioMs;
        this.turnLatencyMs = turnLatencyMs;
        this.interrupted = interrupted;
        this.sources = new java.util.ArrayList<>(sources);
    }

    void setCall(CallRecord call) { this.call = call; }
    public void markInterrupted(String spokenText, Long sttMs, Long llmFirstTokenMs, Long ttsFirstAudioMs, Long turnLatencyMs) {
        markInterrupted(spokenText, sttMs, llmFirstTokenMs, ttsFirstAudioMs, turnLatencyMs, sources);
    }
    public void markInterrupted(String spokenText, Long sttMs, Long llmFirstTokenMs, Long ttsFirstAudioMs, Long turnLatencyMs,
                                java.util.List<TurnSource> sources) {
        this.interrupted = true;
        this.text = spokenText;
        this.sttMs = sttMs;
        this.llmFirstTokenMs = llmFirstTokenMs;
        this.ttsFirstAudioMs = ttsFirstAudioMs;
        this.turnLatencyMs = turnLatencyMs;
        this.sources = new java.util.ArrayList<>(sources);
    }
    public int getTurnIndex() { return turnIndex; }
    public TurnRole getRole() { return role; }
    public String getText() { return text; }
    public Instant getStartedAt() { return startedAt; }
    public Long getSttMs() { return sttMs; }
    public Long getLlmFirstTokenMs() { return llmFirstTokenMs; }
    public Long getTtsFirstAudioMs() { return ttsFirstAudioMs; }
    public Long getTurnLatencyMs() { return turnLatencyMs; }
    public boolean isInterrupted() { return interrupted; }
    public java.util.List<TurnSource> getSources() { return java.util.List.copyOf(sources); }
}
