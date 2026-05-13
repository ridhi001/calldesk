package com.calldesk.knowledge;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeQuestionClassifierTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Do you offer braces for adults?",
            "Can I pay in monthly installments",           // no question mark: speech-to-text often drops it
            "Actually, I need to book an appointment.",
            "I'd like to know about root canals please",
            "My tooth hurts, is it an emergency?",
    })
    void questionsAndRequestsCount(String text) {
        assertThat(KnowledgeQuestionClassifier.isKnowledgeQuestion(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Tomorrow afternoon would work for me.",       // answering the agent, not asking
            "Thank you so much",
            "Yes please",
            "Please leave a message after the tone.",
            "I would like to talk to a person.",
            "Okay.",
    })
    void statementsSmallTalkAndHandoffsDoNot(String text) {
        assertThat(KnowledgeQuestionClassifier.isKnowledgeQuestion(text)).isFalse();
    }
}
