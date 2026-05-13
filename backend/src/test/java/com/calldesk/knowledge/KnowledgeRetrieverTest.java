package com.calldesk.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({KnowledgeService.class, KnowledgeRetriever.class, BusinessProfileLoader.class, KnowledgeTestConfiguration.class})
class KnowledgeRetrieverTest {
    @Autowired private KnowledgeService knowledge;
    @Autowired private KnowledgeRetriever retriever;
    @Autowired private BusinessProfileLoader profileLoader;

    @BeforeEach void seedDemoProfile() { knowledge.seedFromProfileIfEmpty(); }

    @Test void demoFaqQuestionsAreConfidentAndUnrelatedQuestionIsNot() {
        for (BusinessProfile.Faq faq : profileLoader.getProfile().faqs()) {
            KnowledgeRetriever.Match match = retriever.search(faq.question(), 1).getFirst();
            assertThat(match.confident()).as(faq.question() + " should be confident; score=" + match.score()).isTrue();
        }

        assertThat(retriever.search("do you sell pet food?", 5)).noneMatch(KnowledgeRetriever.Match::confident);
        assertThat(retriever.search("Do you offer braces for adults?", 5)).noneMatch(KnowledgeRetriever.Match::confident);
        assertThat(retriever.search("Can I pay in monthly installments?", 5)).noneMatch(KnowledgeRetriever.Match::confident);
    }

    @Test void oneSharedGenericWordIsNotAConfidentMatch() {
        // Only "available" overlaps with "Is parking available?"; that must not be treated as an answer.
        assertThat(retriever.search("Is there a female dentist available?", 5)).noneMatch(KnowledgeRetriever.Match::confident);
    }

    @Test void conversationalFillerDoesNotHideAnAnswer() {
        assertThat(retriever.search("Actually, I would like to book an appointment", 1).getFirst().confident()).isTrue();
    }

    @Test void onlyMatchesNearTheTopScoreAreConfident() {
        // The emergency answer mentions "outside opening hours"; it must not be cited for an opening-hours question.
        assertThat(retriever.confidentMatches("What are your opening hours?", 3))
                .extracting(match -> match.entry().question()).containsExactly("What are your opening hours?");
    }

    @Test void stemmingAndStopwordsFindTheRightEntry() {
        assertThat(retriever.search("Are you open on Saturday?", 1).getFirst().entry().question()).isEqualTo("What are your opening hours?");
        assertThat(retriever.search("Are you open on Saturday?", 1).getFirst().confident()).isTrue();
        // "my" used to pull "What should I bring to my first appointment?" to the top.
        assertThat(retriever.search("My tooth hurts, is it an emergency?", 1).getFirst().entry().question())
                .isEqualTo("What should I do in a dental emergency?");
    }
}
