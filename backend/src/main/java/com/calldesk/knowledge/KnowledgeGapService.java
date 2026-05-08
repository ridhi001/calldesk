package com.calldesk.knowledge;

import com.calldesk.calls.CallNotFoundException;
import com.calldesk.calls.CallRecordRepository;
import com.calldesk.calls.CallEventPublisher;
import com.calldesk.calls.ApiErrorDto;
import com.calldesk.knowledge.KnowledgeGapStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class KnowledgeGapService {
    private final KnowledgeGapRepository repository;
    private final CallRecordRepository calls;
    private final KnowledgeService knowledge;
    private final CallEventPublisher events;
    private final Clock clock;

    public KnowledgeGapService(KnowledgeGapRepository repository, CallRecordRepository calls, KnowledgeService knowledge,
                               CallEventPublisher events, Clock clock) {
        this.repository = repository;
        this.calls = calls;
        this.knowledge = knowledge;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public KnowledgeGapDto recordIfEligible(String question, String callSid, Double bestScore) {
        if (!KnowledgeQuestionClassifier.isKnowledgeQuestion(question)) return null;
        Long callId = calls.findByCallSid(callSid).orElseThrow(() -> new CallNotFoundException("Call not found: " + callSid)).getId();
        String normalized = KnowledgeQuestionClassifier.normalize(question);
        Instant now = clock.instant();
        KnowledgeGap gap = repository.findFirstByNormalizedQuestionAndStatus(normalized, KnowledgeGapStatus.OPEN).orElse(null);
        if (gap == null) gap = new KnowledgeGap(question.trim(), normalized, callId, now, bestScore);
        else gap.recordAsk(question.trim(), callId, now, bestScore);
        KnowledgeGap saved = repository.saveAndFlush(gap);
        KnowledgeGapDto dto = KnowledgeGapDto.from(saved);
        publishAfterCommit(() -> events.publish("gap-added", dto));
        return dto;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeGapDto> list(String status) {
        String selected = status == null || status.isBlank() ? "OPEN" : status.toUpperCase(java.util.Locale.ROOT);
        if (selected.equals("ALL")) {
            return repository.findAll().stream().sorted(Comparator.comparingInt(KnowledgeGap::getTimesAsked).reversed()
                    .thenComparing(KnowledgeGap::getLastAskedAt, Comparator.reverseOrder())).map(KnowledgeGapDto::from).toList();
        }
        KnowledgeGapStatus parsed;
        try { parsed = KnowledgeGapStatus.valueOf(selected); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("status must be OPEN, RESOLVED, DISMISSED, or ALL"); }
        return repository.findAllByStatusOrderByTimesAskedDescLastAskedAtDesc(parsed).stream().map(KnowledgeGapDto::from).toList();
    }

    @Transactional
    public KnowledgeGapResolutionDto resolve(long id, KnowledgeEntryRequest request) {
        KnowledgeGap gap = requireGap(id);
        KnowledgeEntryDto entry = knowledge.create(request);
        gap.resolve(entry.id());
        KnowledgeGap saved = repository.saveAndFlush(gap);
        return new KnowledgeGapResolutionDto(KnowledgeGapDto.from(saved), entry);
    }

    @Transactional
    public KnowledgeGapDto dismiss(long id) {
        KnowledgeGap gap = requireGap(id);
        gap.dismiss();
        return KnowledgeGapDto.from(repository.saveAndFlush(gap));
    }

    private KnowledgeGap requireGap(long id) {
        return repository.findById(id).orElseThrow(() -> new KnowledgeNotFoundException("Knowledge gap not found: " + id));
    }

    private void publishAfterCommit(Runnable publish) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { publish.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { publish.run(); }
        });
    }
}
