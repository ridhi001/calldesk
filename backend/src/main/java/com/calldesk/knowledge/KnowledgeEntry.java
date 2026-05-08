package com.calldesk.knowledge;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "knowledge_entries")
public class KnowledgeEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 300)
    private String question;
    @Column(nullable = false, length = 2000)
    private String answer;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "knowledge_entry_tags", joinColumns = @JoinColumn(name = "entry_id"))
    @OrderColumn(name = "tag_order")
    @Column(name = "tag", nullable = false, length = 100)
    private List<String> tags = new ArrayList<>();
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Column(nullable = false)
    private int timesUsed;
    private Instant lastUsedAt;

    protected KnowledgeEntry() { }

    public KnowledgeEntry(String question, String answer, List<String> tags, Instant now) {
        this.question = question;
        this.answer = answer;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String question, String answer, List<String> tags, Instant now) {
        this.question = question;
        this.answer = answer;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public List<String> getTags() { return List.copyOf(tags); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getTimesUsed() { return timesUsed; }
    public Instant getLastUsedAt() { return lastUsedAt; }
}
