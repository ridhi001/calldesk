package com.calldesk.calls;

import com.calldesk.knowledge.KnowledgeEntryRepository;
import com.calldesk.knowledge.KnowledgeGapRepository;
import com.calldesk.knowledge.KnowledgeGapStatus;
import com.calldesk.knowledge.KnowledgeQuestionClassifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class CallPersistenceService {
    private final CallRecordRepository repository;
    private final CallEventPublisher events;
    private final KnowledgeEntryRepository knowledgeEntries;
    private final KnowledgeGapRepository knowledgeGaps;

    public CallPersistenceService(CallRecordRepository repository, CallEventPublisher events,
                                  KnowledgeEntryRepository knowledgeEntries, KnowledgeGapRepository knowledgeGaps) {
        this.repository = repository;
        this.events = events;
        this.knowledgeEntries = knowledgeEntries;
        this.knowledgeGaps = knowledgeGaps;
    }

    @Transactional
    public CallRecord startCall(String callSid, String from, String to, Instant startedAt) {
        CallRecord existing = repository.findByCallSid(callSid).orElse(null);
        CallRecord call = existing == null ? repository.save(new CallRecord(callSid, from, to, startedAt)) : existing;
        if (existing == null) publishAfterCommit(() -> events.publish("call-started", summary(call)));
        return call;
    }

    @Transactional
    public void addTurn(String callSid, int index, TurnRole role, String text, Instant startedAt,
                        Long sttMs, Long llmFirstTokenMs, Long ttsFirstAudioMs, Long turnLatencyMs, boolean interrupted) {
        addTurn(callSid, index, role, text, startedAt, sttMs, llmFirstTokenMs, ttsFirstAudioMs, turnLatencyMs, interrupted, List.of());
    }

    @Transactional
    public void addAgentTurn(String callSid, int index, String text, Instant startedAt,
                             Long sttMs, Long llmFirstTokenMs, Long ttsFirstAudioMs, Long turnLatencyMs,
                             boolean interrupted, List<TurnSource> sources) {
        addTurn(callSid, index, TurnRole.AGENT, text, startedAt, sttMs, llmFirstTokenMs, ttsFirstAudioMs,
                turnLatencyMs, interrupted, sources);
    }

    private void addTurn(String callSid, int index, TurnRole role, String text, Instant startedAt,
                         Long sttMs, Long llmFirstTokenMs, Long ttsFirstAudioMs, Long turnLatencyMs,
                         boolean interrupted, List<TurnSource> sources) {
        CallRecord call = requireCall(callSid);
        TurnRecord turn = new TurnRecord(index, role, text, startedAt, sttMs, llmFirstTokenMs, ttsFirstAudioMs, turnLatencyMs, interrupted, sources);
        call.addTurn(turn);
        repository.save(call);
        TurnAddedEventDto event = new TurnAddedEventDto(call.getId(),
                new TurnDto(index, role, text, startedAt, sttMs, llmFirstTokenMs, ttsFirstAudioMs, turnLatencyMs, interrupted, sources));
        publishAfterCommit(() -> events.publish("turn-added", event));
    }

    @Transactional
    public void markAgentTurnInterrupted(String callSid, int index, String spokenText, Instant startedAt, Long sttMs,
                                         Long llmFirstTokenMs, Long ttsFirstAudioMs, Long turnLatencyMs) {
        markAgentTurnInterrupted(callSid, index, spokenText, startedAt, sttMs, llmFirstTokenMs, ttsFirstAudioMs, turnLatencyMs, List.of());
    }

    @Transactional
    public void markAgentTurnInterrupted(String callSid, int index, String spokenText, Instant startedAt, Long sttMs,
                                         Long llmFirstTokenMs, Long ttsFirstAudioMs, Long turnLatencyMs,
                                         List<TurnSource> sources) {
        CallRecord call = requireCall(callSid);
        TurnRecord agentTurn = call.getTurns().stream().filter(turn -> turn.getTurnIndex() == index && turn.getRole() == TurnRole.AGENT)
                .findFirst().orElse(null);
        if (agentTurn == null) {
            call.addTurn(new TurnRecord(index, TurnRole.AGENT, spokenText, startedAt, sttMs, llmFirstTokenMs, ttsFirstAudioMs, turnLatencyMs, true, sources));
            agentTurn = call.getTurns().getLast();
        } else {
            agentTurn.markInterrupted(spokenText, sttMs, llmFirstTokenMs, ttsFirstAudioMs, turnLatencyMs, sources);
        }
        repository.save(call);
        TurnAddedEventDto event = new TurnAddedEventDto(call.getId(), turn(agentTurn));
        publishAfterCommit(() -> events.publish("turn-added", event));
    }

    @Transactional
    public void incrementBargeIn(String callSid) {
        CallRecord call = requireCall(callSid);
        call.incrementBargeInCount();
        repository.save(call);
    }

    @Transactional
    public void endCall(String callSid, CallOutcome outcome, Instant endedAt) {
        CallRecord call = requireCall(callSid);
        if (call.getEndedAt() != null) return;
        call.setOutcome(outcome);
        call.setEndedAt(endedAt);
        repository.save(call);
        publishAfterCommit(() -> events.publish("call-ended", summary(call)));
    }

    @Transactional(readOnly = true)
    public PageDto<CallSummaryDto> listCalls(int page, int size, CallOutcome outcome) {
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")));
        Page<CallRecord> result = outcome == null ? repository.findAll(request) : repository.findByOutcome(outcome, request);
        return new PageDto<>(result.getContent().stream().map(CallPersistenceService::summary).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public CallDetailDto getCall(long id) {
        CallRecord call = repository.findById(id).orElseThrow(() -> new CallNotFoundException("Call not found: " + id));
        List<TurnDto> turns = call.getTurns().stream().map(CallPersistenceService::turn).toList();
        return new CallDetailDto(summary(call), turns);
    }

    @Transactional(readOnly = true)
    public MetricsDto metrics() {
        List<CallRecord> calls = repository.findAll();
        Map<CallOutcome, Long> outcomes = new EnumMap<>(CallOutcome.class);
        Arrays.stream(CallOutcome.values()).forEach(outcome -> outcomes.put(outcome, calls.stream().filter(call -> call.getOutcome() == outcome).count()));
        long total = calls.size();
        long handedOff = outcomes.get(CallOutcome.HANDED_OFF);
        long bargeIns = calls.stream().mapToLong(CallRecord::getBargeInCount).sum();
        List<Long> latencies = repository.findTurnLatencies().stream().sorted().toList();
        Double average = latencies.isEmpty() ? null : latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long eligibleQuestions = 0;
        long coveredQuestions = 0;
        for (CallRecord call : calls) {
            for (TurnRecord turn : call.getTurns()) {
                if (turn.getRole() != TurnRole.CALLER || !KnowledgeQuestionClassifier.isKnowledgeQuestion(turn.getText())) continue;
                eligibleQuestions++;
                boolean covered = call.getTurns().stream().anyMatch(agent -> agent.getRole() == TurnRole.AGENT
                        && agent.getTurnIndex() == turn.getTurnIndex() && !agent.getSources().isEmpty());
                if (covered) coveredQuestions++;
            }
        }
        Double coverage = eligibleQuestions == 0 ? null : (double) coveredQuestions / eligibleQuestions;
        List<MetricsDto.LatencyTrendPoint> trend = calls.stream().filter(call -> call.getEndedAt() != null)
                .sorted(java.util.Comparator.comparing(CallRecord::getEndedAt).reversed().thenComparing(CallRecord::getId, java.util.Comparator.reverseOrder()))
                .limit(20)
                .sorted(java.util.Comparator.comparing(CallRecord::getEndedAt).thenComparing(CallRecord::getId))
                .map(call -> {
                    List<Long> callLatencies = call.getTurns().stream().map(TurnRecord::getTurnLatencyMs)
                            .filter(java.util.Objects::nonNull).sorted().toList();
                    return new MetricsDto.LatencyTrendPoint(call.getId(), call.getStartedAt(), percentile(callLatencies, 0.50));
                }).toList();
        return new MetricsDto(total, outcomes, total == 0 ? 0 : (double) handedOff / total, bargeIns,
                new MetricsDto.LatencyStats(percentile(latencies, 0.50), percentile(latencies, 0.95), average),
                new MetricsDto.KnowledgeStats(knowledgeEntries.count(), knowledgeGaps.countByStatus(KnowledgeGapStatus.OPEN), coverage), trend);
    }

    private CallRecord requireCall(String sid) { return repository.findByCallSid(sid).orElseThrow(() -> new CallNotFoundException("Call not found: " + sid)); }
    private void publishAfterCommit(Runnable publish) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { publish.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { publish.run(); }
        });
    }
    private static Double percentile(List<Long> sorted, double fraction) {
        if (sorted.isEmpty()) return null;
        int index = (int) Math.ceil(fraction * sorted.size()) - 1;
        return (double) sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
    private static CallSummaryDto summary(CallRecord call) {
        return new CallSummaryDto(call.getId(), call.getCallSid(), call.getFromNumber(), call.getToNumber(), call.getStartedAt(), call.getEndedAt(), call.getOutcome(), call.getTurnCount(), call.getBargeInCount());
    }
    private static TurnDto turn(TurnRecord turn) {
        return new TurnDto(turn.getTurnIndex(), turn.getRole(), turn.getText(), turn.getStartedAt(), turn.getSttMs(), turn.getLlmFirstTokenMs(), turn.getTtsFirstAudioMs(), turn.getTurnLatencyMs(), turn.isInterrupted(), turn.getSources());
    }
}
