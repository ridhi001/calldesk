package com.calldesk.calls;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "calls")
public class CallRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String callSid;
    private String fromNumber;
    private String toNumber;
    @Column(nullable = false)
    private Instant startedAt;
    private Instant endedAt;
    @Enumerated(EnumType.STRING)
    private CallOutcome outcome;
    @Column(nullable = false)
    private int turnCount;
    @Column(nullable = false)
    private int bargeInCount;
    @OneToMany(mappedBy = "call", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("turnIndex ASC")
    private List<TurnRecord> turns = new ArrayList<>();

    protected CallRecord() { }

    public CallRecord(String callSid, String fromNumber, String toNumber, Instant startedAt) {
        this.callSid = callSid;
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
        this.startedAt = startedAt;
    }

    public void addTurn(TurnRecord turn) { turns.add(turn); turn.setCall(this); turnCount++; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public void setOutcome(CallOutcome outcome) { this.outcome = outcome; }
    public void incrementBargeInCount() { bargeInCount++; }
    public Long getId() { return id; }
    public String getCallSid() { return callSid; }
    public String getFromNumber() { return fromNumber; }
    public String getToNumber() { return toNumber; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public CallOutcome getOutcome() { return outcome; }
    public int getTurnCount() { return turnCount; }
    public int getBargeInCount() { return bargeInCount; }
    public List<TurnRecord> getTurns() { return turns; }
}
