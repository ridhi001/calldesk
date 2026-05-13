package com.calldesk.knowledge;

import com.calldesk.calls.CallRecord;
import com.calldesk.calls.CallRecordRepository;
import com.calldesk.calls.CallEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({KnowledgeService.class, KnowledgeRetriever.class, BusinessProfileLoader.class, KnowledgeGapService.class,
        KnowledgeQuestionService.class, KnowledgeTestConfiguration.class, CallEventPublisher.class})
class KnowledgeGapTest {
    @Autowired private KnowledgeGapService gaps;
    @Autowired private KnowledgeGapRepository gapRepository;
    @Autowired private KnowledgeEntryRepository entryRepository;
    @Autowired private CallRecordRepository callRepository;
    @Autowired private KnowledgeService knowledge;
    @Autowired private KnowledgeQuestionService questions;

    private Long firstCallId;
    private Long secondCallId;

    @BeforeEach void createCalls() {
        firstCallId = callRepository.saveAndFlush(new CallRecord("CA_GAP_ONE", "+100", "+200", Instant.parse("2026-09-26T10:00:00Z"))).getId();
        secondCallId = callRepository.saveAndFlush(new CallRecord("CA_GAP_TWO", "+100", "+200", Instant.parse("2026-09-26T11:00:00Z"))).getId();
    }

    @Test void createsAndDeduplicatesByNormalizedQuestionAndFiltersSmallTalk() {
        KnowledgeGapDto first = gaps.recordIfEligible("Do you offer adult braces?", "CA_GAP_ONE", 0.8);
        KnowledgeGapDto second = gaps.recordIfEligible("DO you offer adult braces!", "CA_GAP_TWO", 1.1);
        assertThat(first.id()).isEqualTo(second.id());
        assertThat(second.timesAsked()).isEqualTo(2);
        KnowledgeGap saved = gapRepository.findById(first.id()).orElseThrow();
        assertThat(saved.getQuestion()).isEqualTo("Do you offer adult braces?");
        assertThat(saved.getFirstCallId()).isEqualTo(firstCallId);
        assertThat(saved.getLastCallId()).isEqualTo(secondCallId);
        assertThat(saved.getBestScore()).isEqualTo(1.1);

        assertThat(gaps.recordIfEligible("thank you", "CA_GAP_ONE", null)).isNull();
        assertThat(gaps.recordIfEligible("Can I pay?", "CA_GAP_ONE", null)).isNull();
        assertThat(gapRepository.count()).isEqualTo(1);
    }

    @Test void resolvesIntoEntryAndCanDismissAnotherGap() {
        KnowledgeGapDto toResolve = gaps.recordIfEligible("Do you offer adult braces?", "CA_GAP_ONE", 0.8);
        KnowledgeGapResolutionDto resolved = gaps.resolve(toResolve.id(), new KnowledgeEntryRequest(
                "Do you offer adult braces?", "We can arrange an orthodontic consultation.", List.of("orthodontics")));
        assertThat(resolved.gap().status()).isEqualTo(KnowledgeGapStatus.RESOLVED);
        assertThat(resolved.gap().resolvedEntryId()).isEqualTo(resolved.entry().id());
        assertThat(entryRepository.existsById(resolved.entry().id())).isTrue();

        KnowledgeGapDto toDismiss = gaps.recordIfEligible("Can I pay with monthly installments?", "CA_GAP_TWO", null);
        assertThat(gaps.dismiss(toDismiss.id()).status()).isEqualTo(KnowledgeGapStatus.DISMISSED);
        assertThat(gaps.list("OPEN")).isEmpty();
        assertThat(gaps.list("ALL")).hasSize(2);
    }

    @Test void answerPreparationRecordsConfidentSourcesAndUsageOrCreatesAnEligibleGap() {
        knowledge.seedFromProfileIfEmpty();
        KnowledgeEntryDto entry = knowledge.create(new KnowledgeEntryRequest("How do I arrange an adult orthodontic visit?",
                "Call us to arrange an adult orthodontic visit.", List.of("orthodontics")));
        KnowledgeQuestionContext answerable = questions.prepareAnswer("How do I arrange an adult orthodontic visit?", "CA_GAP_ONE");
        assertThat(answerable.sources()).hasSizeBetween(1, 3);
        assertThat(answerable.sources()).anySatisfy(source -> assertThat(source.getEntryId()).isEqualTo(entry.id()));
        assertThat(entryRepository.findById(entry.id()).orElseThrow().getTimesUsed()).isEqualTo(1);

        KnowledgeQuestionContext unanswered = questions.prepareAnswer("Do you sell fresh pet food?", "CA_GAP_ONE");
        assertThat(unanswered.sources()).isEmpty();
        assertThat(gaps.list("OPEN")).extracting(KnowledgeGapDto::question)
                .containsExactly("Do you sell fresh pet food?");
    }
}
